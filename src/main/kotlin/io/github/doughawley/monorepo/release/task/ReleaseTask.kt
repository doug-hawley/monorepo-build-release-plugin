package io.github.doughawley.monorepo.release.task

import io.github.doughawley.monorepo.release.MonorepoReleaseConfigExtension
import io.github.doughawley.monorepo.release.MonorepoReleaseExtension
import io.github.doughawley.monorepo.release.domain.NextVersionResolver
import io.github.doughawley.monorepo.release.domain.Scope
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

        // 3. Branch validation — must be on a release branch
        val globalPrefix = rootExtension.globalTagPrefix
        val currentBranch = gitReleaseExecutor.currentBranch()
        if (currentBranch == "HEAD") {
            throw GradleException(
                "Cannot release from a detached HEAD state. " +
                "Check out a branch before releasing."
            )
        }
        if (!TagPattern.isReleaseBranch(currentBranch, globalPrefix)) {
            throw GradleException(
                "Cannot release from branch '$currentBranch'. " +
                "Releases must be made from a release branch " +
                "(e.g., $globalPrefix/<project>/v<major>.<minor>.x)."
            )
        }

        // 4. Determine tag prefix
        val projectPrefix = projectConfig.tagPrefix
            ?: TagPattern.deriveProjectTagPrefix(project.path)

        // 5. Branch-to-project validation
        val branchProjectPrefix = TagPattern.parseProjectPrefixFromBranch(currentBranch, globalPrefix)
        if (branchProjectPrefix != projectPrefix) {
            throw GradleException(
                "Cannot release ${project.path} from branch '$currentBranch'. " +
                "This branch is for project '$branchProjectPrefix', not '$projectPrefix'."
            )
        }

        // 6. Scope validation
        val scope = resolveScope()

        // 7. Scan tags to find next version
        val (major, minor) = TagPattern.parseVersionLineFromBranch(currentBranch)
        val latestInLine = gitTagScanner.findLatestVersionInLine(globalPrefix, projectPrefix, major, minor)
        val nextVersion = NextVersionResolver.forReleaseBranch(latestInLine, major, minor, scope)

        // 8. Tag collision check
        val tag = TagPattern.formatTag(globalPrefix, projectPrefix, nextVersion)
        if (gitTagScanner.tagExists(tag)) {
            throw GradleException(
                "Tag '$tag' already exists. " +
                "This version has already been released."
            )
        }

        // 9. Build outputs check
        val libsDir = project.layout.buildDirectory.dir("libs").get().asFile
        val libsFiles = libsDir.listFiles()
        if (!libsDir.exists() || libsFiles == null || libsFiles.isEmpty()) {
            throw GradleException(
                "Project must be built before releasing — run ${project.path}:build first."
            )
        }

        // 10. Set project.version
        project.version = nextVersion.toString()
        logger.lifecycle("Releasing ${project.path} as version $nextVersion")

        // 11. Create tag locally
        gitReleaseExecutor.createTagLocally(tag)

        // 12. Push to remote (with rollback on failure)
        try {
            gitReleaseExecutor.pushTag(tag)
        } catch (e: GradleException) {
            logger.error("Push failed, rolling back local changes: ${e.message}")
            gitReleaseExecutor.deleteLocalTag(tag)
            throw e
        }

        // 13. Write build/release-version.txt
        val versionFile = project.layout.buildDirectory.file("release-version.txt").get().asFile
        versionFile.parentFile.mkdirs()
        versionFile.writeText(nextVersion.toString())
        logger.lifecycle("Wrote release version to: ${versionFile.absolutePath}")
    }

    private fun resolveScope(): Scope {
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

}
