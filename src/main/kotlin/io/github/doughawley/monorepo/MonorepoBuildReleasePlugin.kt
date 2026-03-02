package io.github.doughawley.monorepo

import io.github.doughawley.monorepo.build.MonorepoBuildExtension
import io.github.doughawley.monorepo.build.MonorepoProjectConfigExtension
import io.github.doughawley.monorepo.build.domain.MonorepoProjects
import io.github.doughawley.monorepo.build.domain.ProjectFileMapper
import io.github.doughawley.monorepo.build.domain.ProjectMetadataFactory
import io.github.doughawley.monorepo.build.git.GitChangedFilesDetector
import io.github.doughawley.monorepo.build.git.GitRepository
import io.github.doughawley.monorepo.build.task.PrintChangedProjectsFromRefTask
import io.github.doughawley.monorepo.build.task.PrintChangedProjectsTask
import io.github.doughawley.monorepo.build.task.WriteChangedProjectsFromRefTask
import io.github.doughawley.monorepo.release.MonorepoReleaseConfigExtension
import io.github.doughawley.monorepo.release.MonorepoReleaseExtension
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

    private enum class DetectionMode { FROM_BRANCH, FROM_REF }

    private companion object {
        val REF_TASKS = setOf(
            "printChangedProjectsFromRef",
            "buildChangedProjectsFromRef",
            "writeChangedProjectsFromRef",
            "releaseChangedProjects"
        )
        val BRANCH_TASKS = setOf("printChangedProjectsFromBranch", "buildChangedProjectsFromBranch")
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

        // Root-level release aggregator task
        val releasedProjectPaths = mutableListOf<String>()
        val releaseChangedProjectsTask = project.tasks.register("releaseChangedProjects") {
            group = RELEASE_TASK_GROUP
            description = "Releases all opted-in projects that have changed since the configured commit ref"
            dependsOn(project.tasks.named("buildChangedProjectsFromRef"))
            doLast {
                if (releasedProjectPaths.isEmpty()) {
                    logger.lifecycle("No opted-in projects changed — nothing to release.")
                } else {
                    logger.lifecycle("Released projects: ${releasedProjectPaths.joinToString(", ")}")
                }
            }
        }

        // Compute metadata in configuration phase after ALL projects are evaluated.
        // Under --parallel, multiple threads may fire this callback concurrently.
        // computationGuard.compareAndSet(false, true) ensures only the first thread proceeds.
        project.gradle.projectsEvaluated {
            if (rootBuildExtension.computationGuard.compareAndSet(false, true)) {
                try {
                    val mode = resolveMode(project.rootProject)
                    if (mode == DetectionMode.FROM_REF) {
                        val commitRef = resolveCommitRef(project.rootProject, rootBuildExtension)
                            ?: throw GradleException(
                                "printChangedProjectsFromRef / buildChangedProjectsFromRef / writeChangedProjectsFromRef / releaseChangedProjects requires " +
                                "a commitRef. Set it in the monorepo { build { } } DSL or pass " +
                                "-Pmonorepo.commitRef=<sha>."
                            )
                        rootBuildExtension.commitRef = commitRef
                        computeMetadata(project.rootProject, rootBuildExtension, commitRef)
                        wireDependsOn(project, "buildChangedProjectsFromRef", rootBuildExtension.allAffectedProjects)
                    } else {
                        computeMetadata(project.rootProject, rootBuildExtension, commitRef = null)
                        wireDependsOn(project, "buildChangedProjectsFromBranch", rootBuildExtension.allAffectedProjects)
                    }
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

            // Wire release tasks for opted-in changed projects.
            // This fires after the metadata computation above (registered second), so
            // allAffectedProjects is already populated.
            val buildChangedTask = project.tasks.named("buildChangedProjectsFromRef")
            rootBuildExtension.allAffectedProjects.forEach { projectPath ->
                val sub = project.rootProject.findProject(projectPath) ?: return@forEach
                val projectExtension = sub.extensions
                    .findByType(MonorepoProjectExtension::class.java) ?: return@forEach
                if (!projectExtension.release.enabled) {
                    return@forEach
                }
                val releaseTask = sub.tasks.findByName("release") ?: return@forEach
                releasedProjectPaths.add(projectPath)
                releaseTask.mustRunAfter(buildChangedTask)
                releaseChangedProjectsTask.configure {
                    dependsOn(releaseTask)
                }
            }
        }

        // ── Build tasks ──────────────────────────────────────────────────────────

        project.tasks.register("printChangedProjectsFromBranch", PrintChangedProjectsTask::class.java).configure {
            group = BUILD_TASK_GROUP
            description = "Detects which projects have changed based on git history"
        }

        project.tasks.register("buildChangedProjectsFromBranch").configure {
            group = BUILD_TASK_GROUP
            description = "Builds only the projects that have been affected by changes"
            doLast {
                val ext = project.rootProject.extensions.getByType(MonorepoExtension::class.java).build
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
                    project.logger.lifecycle("No projects have changed - nothing to build")
                } else {
                    project.logger.lifecycle("Building changed projects: ${changedProjects.joinToString(", ")}")
                }
            }
        }

        project.tasks.register("printChangedProjectsFromRef", PrintChangedProjectsFromRefTask::class.java).configure {
            group = BUILD_TASK_GROUP
            description = "Detects which projects changed since a specific commit ref"
        }

        project.tasks.register("buildChangedProjectsFromRef").configure {
            group = BUILD_TASK_GROUP
            description = "Builds only the projects affected by changes since a specific commit ref"
            doLast {
                val ext = project.rootProject.extensions.getByType(MonorepoExtension::class.java).build
                if (!ext.metadataComputed) {
                    throw IllegalStateException(
                        "Changed project metadata was not computed in the configuration phase. " +
                        "Possible causes: the plugin was not applied to the root project, " +
                        "or an error occurred during project evaluation. " +
                        "Re-run with --info or --debug for more details."
                    )
                }
                val changedProjects = ext.allAffectedProjects
                val ref = ext.commitRef
                if (changedProjects.isEmpty()) {
                    project.logger.lifecycle("No projects have changed - nothing to build")
                } else {
                    project.logger.lifecycle("Building changed projects (since $ref): ${changedProjects.joinToString(", ")}")
                }
            }
        }

        project.tasks.register("writeChangedProjectsFromRef", WriteChangedProjectsFromRefTask::class.java).configure {
            group = BUILD_TASK_GROUP
            description = "Writes changed projects since a specific commit ref to a file for CI/CD pipeline consumption"
            val customPath = project.findProperty("monorepo.outputFile") as? String
            if (customPath != null) {
                outputFile.set(project.layout.projectDirectory.file(customPath))
            } else {
                outputFile.convention(project.layout.buildDirectory.file("monorepo/changed-projects.txt"))
            }
        }

        project.logger.info("Monorepo Build & Release Plugin applied to ${project.name}")
    }

    /**
     * Determines which detection mode to use based on the tasks requested in this invocation.
     * Fails fast if both branch-mode and ref-mode tasks appear in the same invocation.
     */
    private fun resolveMode(project: Project): DetectionMode {
        val requested = project.gradle.startParameter.taskNames
            .map { it.substringAfterLast(":") }
            .toSet()
        val wantsRef = requested.any { it in REF_TASKS }
        val wantsBranch = requested.any { it in BRANCH_TASKS }
        if (wantsRef && wantsBranch) {
            throw GradleException(
                "Cannot run branch-mode and ref-mode tasks in the same invocation. " +
                "Run printChangedProjectsFromBranch/buildChangedProjectsFromBranch OR " +
                "printChangedProjectsFromRef/buildChangedProjectsFromRef — not both."
            )
        }
        return if (wantsRef) DetectionMode.FROM_REF else DetectionMode.FROM_BRANCH
    }

    /**
     * Resolves the commit ref to use, preferring the project property over the DSL value.
     */
    private fun resolveCommitRef(project: Project, extension: MonorepoBuildExtension): String? {
        val fromProperty = project.findProperty("monorepo.commitRef") as? String
        return (fromProperty ?: extension.commitRef).takeIf { it.isNotBlank() }
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
    internal fun computeMetadata(project: Project, extension: MonorepoBuildExtension, commitRef: String? = null) {
        val logger = project.logger

        logger.info("Computing changed project metadata...")
        if (commitRef != null) {
            logger.info("Commit ref: $commitRef")
        } else {
            logger.info("Base branch: ${extension.baseBranch}")
        }
        logger.info("Include untracked: ${extension.includeUntracked}")

        val gitRepository = GitRepository(project.rootDir, logger)
        val gitDetector = GitChangedFilesDetector(logger, gitRepository)
        val projectMapper = ProjectFileMapper()
        val metadataFactory = ProjectMetadataFactory(logger)

        val changedFiles = if (commitRef != null) {
            gitDetector.getChangedFilesFromRef(commitRef, extension.excludePatterns)
        } else {
            gitDetector.getChangedFiles(extension)
        }
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
