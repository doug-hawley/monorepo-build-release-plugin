package io.github.doughawley.monorepo.release.task

import io.github.doughawley.monorepo.release.MonorepoReleaseConfigExtension
import io.github.doughawley.monorepo.release.MonorepoReleaseExtension
import io.github.doughawley.monorepo.release.domain.Scope
import io.github.doughawley.monorepo.release.domain.SemanticVersion
import io.github.doughawley.monorepo.release.domain.TagPattern
import io.github.doughawley.monorepo.release.git.GitReleaseExecutor
import io.github.doughawley.monorepo.release.git.GitTagScanner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

open class CreateReleaseBranchTask : DefaultTask() {

    @get:Internal
    lateinit var rootExtension: MonorepoReleaseExtension

    @get:Internal
    lateinit var projectConfig: MonorepoReleaseConfigExtension

    @get:Internal
    lateinit var gitTagScanner: GitTagScanner

    @get:Internal
    lateinit var gitReleaseExecutor: GitReleaseExecutor

    @TaskAction
    fun createReleaseBranch() {
        // 1. Opt-in check
        if (!projectConfig.enabled) {
            throw GradleException(
                "Release is not enabled for ${project.path}. " +
                "Set monorepoProject { release { enabled = true } } to opt in."
            )
        }

        // 2. Branch validation
        val globalPrefix = rootExtension.globalTagPrefix
        val currentBranch = gitReleaseExecutor.currentBranch()
        if (currentBranch == "HEAD") {
            throw GradleException(
                "Cannot create a release branch from a detached HEAD state. " +
                "Check out a branch before releasing."
            )
        }
        if (TagPattern.isReleaseBranch(currentBranch, globalPrefix)) {
            throw GradleException(
                "Cannot create a release branch from release branch '$currentBranch'. " +
                "createReleaseBranch is intended for the primary branch only."
            )
        }
        val isAllowedBranch = rootExtension.releaseBranchPatterns.any { pattern ->
            currentBranch.matches(Regex(pattern))
        }
        if (!isAllowedBranch) {
            throw GradleException(
                "'$currentBranch' is not a permitted release branch. " +
                "Switch to an allowed branch before running createReleaseBranch. " +
                "Allowed patterns: ${rootExtension.releaseBranchPatterns.joinToString(", ")}"
            )
        }

        // 3. Scope resolution (always uses primaryBranchScope; patch is not valid here)
        val scope = Scope.fromString(rootExtension.primaryBranchScope)
            ?: throw GradleException(
                "Invalid primaryBranchScope in monorepo { release { } } DSL: " +
                "'${rootExtension.primaryBranchScope}'. " +
                "Must be one of: major, minor"
            )
        if (scope == Scope.PATCH) {
            throw GradleException(
                "Cannot configure primaryBranchScope as 'patch'. " +
                "Use 'minor' or 'major'."
            )
        }

        // 4. Determine tag prefix
        val projectPrefix = projectConfig.tagPrefix
            ?: TagPattern.deriveProjectTagPrefix(project.path)

        // 5. Scan tags to determine next version
        val latestVersion = gitTagScanner.findLatestVersion(globalPrefix, projectPrefix)
        val nextVersion = if (latestVersion == null) {
            SemanticVersion(0, 1, 0)
        } else {
            latestVersion.bump(scope)
        }

        // 6. Create release branch locally
        val releaseBranch = TagPattern.formatReleaseBranch(globalPrefix, projectPrefix, nextVersion)
        gitReleaseExecutor.createBranchLocally(releaseBranch)

        // 7. Push release branch to remote (with rollback on failure)
        try {
            gitReleaseExecutor.pushBranch(releaseBranch)
        } catch (e: GradleException) {
            logger.error("Push failed, rolling back local branch: ${e.message}")
            gitReleaseExecutor.deleteLocalBranch(releaseBranch)
            throw e
        }

        logger.lifecycle("Created release branch $releaseBranch for ${project.path}")
    }
}
