package io.github.doughawley.monorepo

import io.github.doughawley.monorepo.build.MonorepoBuildExtension
import io.github.doughawley.monorepo.build.git.LastSuccessfulBuildTagUpdater
import io.github.doughawley.monorepo.build.domain.MonorepoProjects
import io.github.doughawley.monorepo.build.domain.ProjectFileMapper
import io.github.doughawley.monorepo.build.domain.ProjectMetadataFactory
import io.github.doughawley.monorepo.build.git.GitChangedFilesDetector
import io.github.doughawley.monorepo.build.git.GitRepository
import io.github.doughawley.monorepo.build.task.PrintChangedProjectsTask
import io.github.doughawley.monorepo.release.MonorepoReleaseConfigExtension
import io.github.doughawley.monorepo.release.MonorepoReleaseExtension
import io.github.doughawley.monorepo.release.domain.Scope
import io.github.doughawley.monorepo.release.domain.TagPattern
import io.github.doughawley.monorepo.release.git.AtomicReleaseBranchCreator
import io.github.doughawley.monorepo.release.task.ReleaseTask
import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.github.doughawley.monorepo.release.git.GitReleaseExecutor
import io.github.doughawley.monorepo.release.git.GitTagScanner
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.logging.Logger
import javax.inject.Inject

/**
 * Gradle plugin that combines change detection and per-project versioned tagging
 * for Gradle monorepos.
 */
class MonorepoBuildReleasePlugin @Inject constructor(
    private val buildFeatures: BuildFeatures
) : Plugin<Project> {

    private companion object {
        const val BUILD_TASK_GROUP = "monorepo"
        const val RELEASE_TASK_GROUP = "monorepo-release"
    }

    override fun apply(project: Project) {
        if (buildFeatures.configurationCache.requested.getOrElse(false)) {
            throw GradleException(
                "monorepo-build-release-plugin is incompatible with the Gradle configuration cache " +
                "because it executes git commands during the configuration phase. " +
                "Set org.gradle.configuration-cache=false in your gradle.properties."
            )
        }

        // Register the root-level monorepo extension on the root project to ensure it's shared
        val rootExtension = if (project == project.rootProject) {
            project.extensions.create("monorepo", MonorepoExtension::class.java)
        } else {
            project.rootProject.extensions.findByType(MonorepoExtension::class.java)
                ?: project.rootProject.extensions.create("monorepo", MonorepoExtension::class.java)
        }
        val rootBuildExtension = rootExtension.build
        val rootReleaseExtension = rootExtension.release

        // Register per-project extension on every subproject
        if (project == project.rootProject) {
            project.subprojects.forEach { subproject ->
                subproject.extensions.create("monorepoProject", MonorepoProjectExtension::class.java)
            }
        }

        // Register per-subproject release tasks eagerly so that build scripts can configure
        // tasks like postRelease during their own configuration phase.
        project.subprojects.forEach { sub ->
            val projectExtension = sub.extensions.findByType(MonorepoProjectExtension::class.java)
                ?: sub.extensions.create("monorepoProject", MonorepoProjectExtension::class.java)
            registerReleaseTasks(sub, rootReleaseExtension, projectExtension.release)
        }

        // Compute metadata in configuration phase after ALL projects are evaluated.
        // Under --parallel, multiple threads may fire this callback concurrently.
        // computationGuard.compareAndSet(false, true) ensures only the first thread proceeds.
        project.gradle.projectsEvaluated {
            if (rootBuildExtension.computationGuard.compareAndSet(false, true)) {
                try {
                    val resolvedRef = resolveBaseRef(project.rootProject, rootExtension)
                    rootBuildExtension.resolvedBaseRef = resolvedRef

                    val gitRepository = GitRepository(project.rootProject.rootDir, project.logger)
                    if (resolvedRef != null) {
                        val resolvedCommit = gitRepository.resolveCommit(resolvedRef)
                        rootBuildExtension.resolvedBaseCommit = resolvedCommit
                        project.logger.lifecycle("Change detection baseline: $resolvedRef ($resolvedCommit)")
                    } else {
                        project.logger.lifecycle("Change detection baseline: none (all projects treated as changed)")
                    }

                    computeMetadata(project.rootProject, rootBuildExtension, resolvedRef)
                    wireDependsOn(project, "buildChangedProjects", rootBuildExtension.allAffectedProjects)
                    rootBuildExtension.metadataComputed = true
                    project.logger.debug("Changed project metadata computed successfully in configuration phase")
                } catch (e: GradleException) {
                    throw e
                } catch (e: Exception) {
                    throw GradleException(
                        "Failed to compute changed project metadata in configuration phase: ${e.message}",
                        e
                    )
                }
            }
        }

        // ── Build tasks ──────────────────────────────────────────────────────────

        project.tasks.register("printChangedProjects", PrintChangedProjectsTask::class.java).configure {
            group = BUILD_TASK_GROUP
            description = "Detects which projects have changed based on git history"
            buildExtension = rootBuildExtension
        }

        project.tasks.register("buildChangedProjects").configure {
            group = BUILD_TASK_GROUP
            description = "Builds only the projects that have been affected by changes"
            val ext = rootBuildExtension
            doLast {
                if (!ext.metadataComputed) {
                    throw IllegalStateException(
                        "Changed project metadata was not computed in the configuration phase. " +
                        "Possible causes: the plugin was not applied to the root project, " +
                        "or an error occurred during project evaluation. " +
                        "Re-run with --info or --debug for more details."
                    )
                }
                val changedProjects = ext.allAffectedProjects
                if (changedProjects.isEmpty()) {
                    logger.lifecycle("No projects have changed - nothing to build")
                } else {
                    logger.lifecycle("Building changed projects: ${changedProjects.joinToString(", ")}")
                }
            }
        }

        // ── Aggregate release task ────────────────────────────────────────────

        project.tasks.register("createReleaseBranches").configure {
            group = RELEASE_TASK_GROUP
            description = "Creates release branches for changed projects"
            dependsOn("buildChangedProjects")
            val buildExt = rootBuildExtension
            val releaseExt = rootReleaseExtension
            val ext = rootExtension
            val rootDir = project.rootProject.rootDir
            val rootProject = project.rootProject
            doLast {
                if (!buildExt.metadataComputed) {
                    throw IllegalStateException(
                        "Changed project metadata was not computed in the configuration phase."
                    )
                }

                // Branch guard: must be on primaryBranch
                val executor = GitCommandExecutor(logger)
                val releaseExecutor = GitReleaseExecutor(rootDir, executor, logger)
                val currentBranch = releaseExecutor.currentBranch()
                if (currentBranch != ext.primaryBranch) {
                    throw GradleException(
                        "createReleaseBranches must run on '${ext.primaryBranch}', " +
                        "but the current branch is '$currentBranch'."
                    )
                }

                // Collect opted-in changed projects
                val changedProjects = buildExt.allAffectedProjects
                val optedInProjects = changedProjects.mapNotNull { projectPath ->
                    val targetProject = rootProject.findProject(projectPath) ?: return@mapNotNull null
                    val projectExt = targetProject.extensions.findByType(MonorepoProjectExtension::class.java)
                        ?: return@mapNotNull null
                    if (!projectExt.release.enabled) return@mapNotNull null
                    val tagPrefix = projectExt.release.tagPrefix
                        ?: TagPattern.deriveProjectTagPrefix(projectPath)
                    projectPath to tagPrefix
                }.toMap()

                val tagUpdater = LastSuccessfulBuildTagUpdater(rootDir, executor, logger)

                if (changedProjects.isEmpty()) {
                    logger.lifecycle("No projects have changed — nothing to do")
                    tagUpdater.updateTag(buildExt.lastSuccessfulBuildTag)
                    return@doLast
                }

                if (optedInProjects.isEmpty()) {
                    logger.lifecycle("No opted-in changed projects — no release branches to create")
                    tagUpdater.updateTag(buildExt.lastSuccessfulBuildTag)
                    return@doLast
                }

                // Resolve scope
                val scope = Scope.fromString(releaseExt.primaryBranchScope)
                    ?: throw GradleException(
                        "Invalid primaryBranchScope: '${releaseExt.primaryBranchScope}'. " +
                        "Must be one of: major, minor"
                    )
                if (scope == Scope.PATCH) {
                    throw GradleException(
                        "Cannot use primaryBranchScope 'patch'. Use 'minor' or 'major'."
                    )
                }

                // Atomic branch creation
                val tagScanner = GitTagScanner(rootDir, executor)
                val branchCreator = AtomicReleaseBranchCreator(releaseExecutor, tagScanner, logger)
                branchCreator.createReleaseBranches(optedInProjects, releaseExt.globalTagPrefix, scope)

                // Update last-successful-build tag
                tagUpdater.updateTag(buildExt.lastSuccessfulBuildTag)
            }
        }

        project.logger.info("Monorepo Build & Release Plugin applied to ${project.name}")
    }

    /**
     * Resolves the base ref for change detection.
     *
     * The baseline depends on which task the user requested:
     * - If `createReleaseBranches` is requested (CI release build) →
     *   fetch the last-successful-build tag from origin (many CI environments
     *   do not fetch tags by default), then use it; if the tag does not exist,
     *   return null so all projects are treated as changed (the first build on
     *   main has no prior successful build to compare against)
     * - Otherwise (local dev / PR build) → use `origin/{primaryBranch}` directly
     *
     * If the chosen ref does not exist, returns null (all projects treated as changed).
     */
    private fun resolveBaseRef(project: Project, rootExtension: MonorepoExtension): String? {
        val buildExtension = rootExtension.build
        val gitRepository = GitRepository(project.rootDir, project.logger)
        val tag = buildExtension.lastSuccessfulBuildTag
        val primaryBranch = rootExtension.primaryBranch
        val remoteBranch = "origin/$primaryBranch"

        val requestedTasks = project.gradle.startParameter.taskNames
        val isReleaseRun = requestedTasks.any { taskName ->
            taskName == "createReleaseBranches" ||
            taskName == ":createReleaseBranches"
        }

        if (isReleaseRun) {
            // Fetch the tag from origin before checking locally.
            // Many CI environments (e.g. actions/checkout@v4) do not fetch tags
            // by default, so this ensures the tag is available if it exists on the remote.
            gitRepository.fetchTag("origin", tag)

            if (gitRepository.refExists(tag)) {
                project.logger.debug("Using last-successful-build tag '$tag' as base ref")
                return tag
            }
            project.logger.lifecycle(
                "Tag '$tag' not found — no baseline exists. " +
                "All projects will be treated as changed."
            )
            return null
        }

        if (gitRepository.refExists(remoteBranch)) {
            project.logger.debug("Using '$remoteBranch' as base ref")
            return remoteBranch
        }

        project.logger.lifecycle(
            "'$remoteBranch' is not available — no baseline exists. " +
            "All projects will be treated as changed."
        )
        return null
    }

    /**
     * Wires dependsOn from a build-aggregation task to the build tasks of all affected projects.
     */
    private fun wireDependsOn(project: Project, taskName: String, affectedProjects: Set<String>) {
        val buildChangedTask = project.tasks.named(taskName)
        affectedProjects.forEach { projectPath ->
            val targetProject = project.rootProject.findProject(projectPath)
            if (targetProject != null) {
                val buildTask = targetProject.tasks.findByName("build")
                if (buildTask != null) {
                    buildChangedTask.configure {
                        dependsOn(buildTask)
                    }
                } else {
                    project.logger.warn("No build task found for $projectPath")
                }
            } else {
                project.logger.warn("Project not found: $projectPath")
            }
        }
    }

    /**
     * Computes changed project metadata.
     * Called during the configuration phase to ensure all dependencies are fully resolved.
     */
    internal fun computeMetadata(project: Project, extension: MonorepoBuildExtension, resolvedBaseRef: String?) {
        val logger = project.logger

        logger.info("Computing changed project metadata...")
        logger.info("Resolved base ref: $resolvedBaseRef")
        logger.info("Include untracked: ${extension.includeUntracked}")

        val gitRepository = GitRepository(project.rootDir, logger)
        val gitDetector = GitChangedFilesDetector(logger, gitRepository)
        val projectMapper = ProjectFileMapper()
        val metadataFactory = ProjectMetadataFactory(logger)

        val changedFiles = gitDetector.getChangedFiles(resolvedBaseRef, extension.includeUntracked, extension.excludePatterns)
        val changedFilesMap = projectMapper.mapChangedFilesToProjects(project.rootProject, changedFiles)
        val filteredChangedFilesMap = applyPerProjectExcludes(project.rootProject, changedFilesMap, logger)
        val metadataMap = metadataFactory.buildProjectMetadataMap(project.rootProject, filteredChangedFilesMap)

        val monorepoProjects = MonorepoProjects(metadataMap.values.toList())
        extension.monorepoProjects = monorepoProjects

        val allAffectedProjects = monorepoProjects.getChangedProjectPaths()
            .filter { path ->
                path != ":" && hasBuildFile(project.rootProject, path).also { hasBuild ->
                    if (!hasBuild) {
                        logger.debug("Excluding $path from affected projects: no build file found")
                    }
                }
            }
            .toSet()
        extension.allAffectedProjects = allAffectedProjects

        logger.info("Changed files count: ${changedFiles.size}")
        logger.info("All affected projects (including dependents): ${allAffectedProjects.joinToString(", ").ifEmpty { "none" }}")
    }

    /**
     * Applies per-project exclude patterns to filter files from the changed files map.
     */
    private fun applyPerProjectExcludes(
        rootProject: Project,
        changedFilesMap: Map<String, List<String>>,
        logger: Logger
    ): Map<String, List<String>> {
        return changedFilesMap.mapValues { (projectPath, files) ->
            val targetProject = rootProject.findProject(projectPath)
            val ext = targetProject?.extensions?.findByType(MonorepoProjectExtension::class.java)
            val patterns = ext?.build?.excludePatterns?.map { Regex(it) } ?: emptyList()
            if (patterns.isEmpty()) {
                files
            } else {
                val projectRelPath = targetProject
                    ?.projectDir?.relativeTo(rootProject.rootDir)?.path?.replace('\\', '/')
                    ?: ""
                files.filterNot { file ->
                    val localFile = if (projectRelPath.isNotEmpty() && file.startsWith("$projectRelPath/")) {
                        file.removePrefix("$projectRelPath/")
                    } else {
                        file
                    }
                    patterns.any { pattern -> localFile.matches(pattern) }
                }.also { filtered ->
                    val excluded = files.size - filtered.size
                    if (excluded > 0) {
                        logger.debug("[$projectPath] Per-project excludes removed $excluded file(s)")
                    }
                }
            }
        }
    }

    /**
     * Checks if a project has a build file (build.gradle or build.gradle.kts).
     */
    private fun hasBuildFile(rootProject: Project, projectPath: String): Boolean {
        val targetProject = rootProject.findProject(projectPath) ?: return false
        val projectDir = targetProject.projectDir
        return projectDir.resolve("build.gradle.kts").exists() ||
               projectDir.resolve("build.gradle").exists()
    }

    private fun registerReleaseTasks(
        sub: Project,
        rootExtension: MonorepoReleaseExtension,
        config: MonorepoReleaseConfigExtension
    ) {
        val executor = GitCommandExecutor(sub.logger)
        val scanner = GitTagScanner(sub.rootProject.rootDir, executor)
        val releaseExecutor = GitReleaseExecutor(sub.rootProject.rootDir, executor, sub.logger)

        val postRelease = sub.tasks.register("postRelease") {
            group = RELEASE_TASK_GROUP
            description = "Lifecycle hook: wire publish tasks here via finalizedBy"
        }

        val releaseTask = sub.tasks.register("release", ReleaseTask::class.java) {
            group = RELEASE_TASK_GROUP
            description = "Creates a versioned git tag for this project"
            this.rootExtension = rootExtension
            this.projectConfig = config
            this.gitTagScanner = scanner
            this.gitReleaseExecutor = releaseExecutor
            this.projectPath = sub.path
            this.buildDir.set(sub.layout.buildDirectory)
            this.releaseScopeProperty = sub.findProperty("release.scope") as? String
            dependsOn("build")
            finalizedBy(postRelease)
        }

        postRelease.configure {
            onlyIf {
                val state = releaseTask.get().state
                val failure: Throwable? = state.failure
                state.executed && failure == null
            }
        }
    }
}
