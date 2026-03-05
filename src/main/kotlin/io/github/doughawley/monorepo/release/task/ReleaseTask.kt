package io.github.doughawley.monorepo.release.task

import io.github.doughawley.monorepo.release.MonorepoReleaseConfigExtension
import io.github.doughawley.monorepo.release.MonorepoReleaseExtension
import io.github.doughawley.monorepo.release.domain.NextVersionResolver
import io.github.doughawley.monorepo.release.domain.Scope
import io.github.doughawley.monorepo.release.domain.SemanticVersion
import io.github.doughawley.monorepo.release.domain.TagPattern
import io.github.doughawley.monorepo.release.git.GitReleaseExecutor
import io.github.doughawley.monorepo.release.git.GitTagScanner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

open class ReleaseTask : DefaultTask() {

    @get:Internal
    lateinit var rootExtension: MonorepoReleaseExtension

    @get:Internal
    lateinit var projectConfig: MonorepoReleaseConfigExtension

    @get:Internal
    lateinit var gitTagScanner: GitTagScanner

    @get:Internal
    lateinit var gitReleaseExecutor: GitReleaseExecutor

    @TaskAction
    fun release() {
        // 1. Opt-in check
        if (!projectConfig.enabled) {
            throw GradleException(
                "Release is not enabled for ${project.path}. " +
                "Set monorepoProject { release { enabled = true } } to opt in."
            )
        }

        // 2. Dirty check
        if (gitReleaseExecutor.isDirty()) {
            throw GradleException(
                "Cannot release with uncommitted changes. " +
                "Please commit or stash all changes before releasing."
            )
        }

        // 3. Branch validation
        val globalPrefix = rootExtension.globalTagPrefix
        val currentBranch = gitReleaseExecutor.currentBranch()
        if (currentBranch == "HEAD") {
            throw GradleException(
                "Cannot release from a detached HEAD state. " +
                "Check out a branch before releasing."
            )
        }
        val isReleaseBranch = TagPattern.isReleaseBranch(currentBranch, globalPrefix)
        val isAllowedBranch = isReleaseBranch || rootExtension.releaseBranchPatterns.any { pattern ->
            currentBranch.matches(Regex(pattern))
        }
        if (!isAllowedBranch) {
            throw GradleException(
                "Cannot release from branch '$currentBranch'. " +
                "Releases must be made from a configured release branch. " +
                "Allowed patterns: ${rootExtension.releaseBranchPatterns.joinToString(", ")}"
            )
        }

        // 4. Scope resolution
        val scope = resolveScope(isReleaseBranch)

        // 5. Determine tag prefix
        val projectPrefix = projectConfig.tagPrefix
            ?: TagPattern.deriveProjectTagPrefix(project.path)

        // 6. Scan tags to find next version
        val nextVersion = if (isReleaseBranch) {
            val (major, minor) = TagPattern.parseVersionLineFromBranch(currentBranch)
            val latestInLine = gitTagScanner.findLatestVersionInLine(globalPrefix, projectPrefix, major, minor)
            NextVersionResolver.forReleaseBranch(latestInLine, major, minor, scope)
        } else {
            val latestVersion = gitTagScanner.findLatestVersion(globalPrefix, projectPrefix)
            NextVersionResolver.forPrimaryBranch(latestVersion, scope)
        }

        // 7. Tag collision check
        val tag = TagPattern.formatTag(globalPrefix, projectPrefix, nextVersion)
        if (gitTagScanner.tagExists(tag)) {
            throw GradleException(
                "Tag '$tag' already exists. " +
                "This version has already been released."
            )
        }

        // 8. Build outputs check
        val libsDir = project.layout.buildDirectory.dir("libs").get().asFile
        val libsFiles = libsDir.listFiles()
        if (!libsDir.exists() || libsFiles == null || libsFiles.isEmpty()) {
            throw GradleException(
                "Project must be built before releasing — run ${project.path}:build first."
            )
        }

        // 9. Set project.version
        project.version = nextVersion.toString()
        logger.lifecycle("Releasing ${project.path} as version $nextVersion")

        // 10. Create tag locally
        gitReleaseExecutor.createTagLocally(tag)

        // 11. Create release branch locally (only when not on a release branch)
        val releaseBranch: String? = if (!isReleaseBranch) {
            val branch = TagPattern.formatReleaseBranch(globalPrefix, projectPrefix, nextVersion)
            try {
                gitReleaseExecutor.createBranchLocally(branch)
            } catch (e: GradleException) {
                gitReleaseExecutor.deleteLocalTag(tag)
                throw e
            }
            branch
        } else {
            null
        }

        // 12. Push to remote (with rollback on failure)
        try {
            gitReleaseExecutor.pushTag(tag)
        } catch (e: GradleException) {
            logger.error("Push failed, rolling back local changes: ${e.message}")
            gitReleaseExecutor.deleteLocalTag(tag)
            if (releaseBranch != null) {
                gitReleaseExecutor.deleteLocalBranch(releaseBranch)
            }
            throw e
        }
        if (releaseBranch != null) {
            try {
                gitReleaseExecutor.pushBranch(releaseBranch)
            } catch (e: GradleException) {
                gitReleaseExecutor.deleteLocalTag(tag)
                gitReleaseExecutor.deleteLocalBranch(releaseBranch)
                logger.error(
                    "Branch push failed. Tag '$tag' was already pushed to remote — " +
                    "it cannot be rolled back automatically."
                )
                throw e
            }
        }

        // 13. Write build/release-version.txt
        val versionFile = project.layout.buildDirectory.file("release-version.txt").get().asFile
        versionFile.parentFile.mkdirs()
        versionFile.writeText(nextVersion.toString())
        logger.lifecycle("Wrote release version to: ${versionFile.absolutePath}")
    }

    private fun resolveScope(isReleaseBranch: Boolean): Scope {
        if (isReleaseBranch) {
            val scopeProperty = project.findProperty("release.scope") as? String
            if (scopeProperty != null) {
                val parsed = Scope.fromString(scopeProperty)
                    ?: throw GradleException(
                        "Invalid release.scope value: '$scopeProperty'. " +
                        "Must be one of: major, minor, patch"
                    )
                if (parsed != Scope.PATCH) {
                    throw GradleException(
                        "Cannot use scope '$scopeProperty' on a release branch. " +
                        "Patch releases only — remove the -Prelease.scope flag or use 'patch'."
                    )
                }
            }
            return Scope.PATCH
        }

        val scopeProperty = project.findProperty("release.scope") as? String
        if (scopeProperty != null) {
            val parsed = Scope.fromString(scopeProperty)
                ?: throw GradleException(
                    "Invalid release.scope value: '$scopeProperty'. " +
                    "Must be one of: major, minor, patch"
                )
            if (parsed == Scope.PATCH) {
                throw GradleException(
                    "Cannot use scope 'patch' on the main branch. " +
                    "Use 'minor' or 'major' for new feature releases."
                )
            }
            return parsed
        }

        val dslScope = Scope.fromString(rootExtension.primaryBranchScope)
            ?: throw GradleException(
                "Invalid primaryBranchScope in monorepo { release { } } DSL: " +
                "'${rootExtension.primaryBranchScope}'. " +
                "Must be one of: major, minor"
            )
        if (dslScope == Scope.PATCH) {
            throw GradleException(
                "Cannot configure primaryBranchScope as 'patch' on the main branch. " +
                "Use 'minor' or 'major'."
            )
        }
        return dslScope
    }

}
