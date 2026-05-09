package io.github.doughawley.monorepo.release.task

import io.github.doughawley.monorepo.release.MonorepoReleaseConfigExtension
import io.github.doughawley.monorepo.release.domain.DevTagPattern
import io.github.doughawley.monorepo.release.domain.TagPattern
import io.github.doughawley.monorepo.release.git.DevTagScanner
import io.github.doughawley.monorepo.release.git.GitReleaseExecutor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Creates git tags and pushes to remote")
abstract class DevReleaseTask : DefaultTask() {

    @get:Internal
    lateinit var gitReleaseExecutor: GitReleaseExecutor

    @get:Internal
    lateinit var devTagScanner: DevTagScanner

    @get:Internal
    lateinit var projectPath: String

    @get:Internal
    lateinit var projectConfig: MonorepoReleaseConfigExtension

    @get:Internal
    abstract val buildDir: DirectoryProperty

    @TaskAction
    fun devRelease() {
        // 1. Opt-in check
        if (!projectConfig.enabled) {
            throw GradleException(
                "Release is not enabled for $projectPath. " +
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

        // 3. Branch validation — must be on a dev branch
        val currentBranch = gitReleaseExecutor.currentBranch()
        if (currentBranch == "HEAD") {
            throw GradleException(
                "Cannot create a dev release from a detached HEAD state. " +
                "Check out a dev branch before releasing."
            )
        }
        val branchInfo = DevTagPattern.parseDevBranch(currentBranch)
            ?: throw GradleException(
                "Cannot create a dev release from branch '$currentBranch'. " +
                "Dev releases must be made from a dev branch " +
                "(e.g., dev/<project>/<name>)."
            )

        // 4. Project-to-branch validation
        val expectedPrefix = projectConfig.tagPrefix
            ?: TagPattern.deriveProjectTagPrefix(projectPath)
        if (branchInfo.projectPrefix != expectedPrefix) {
            throw GradleException(
                "Cannot create a dev release for $projectPath from branch '$currentBranch'. " +
                "This branch is for project '${branchInfo.projectPrefix}', not '$expectedPrefix'."
            )
        }

        // 5. Build outputs check
        val libsDir = buildDir.dir("libs").get().asFile
        val libsFiles = libsDir.listFiles()
        if (!libsDir.exists() || libsFiles == null || libsFiles.isEmpty()) {
            throw GradleException(
                "Project must be built before releasing — run $projectPath:build first."
            )
        }

        // 6. Scan for latest dev tag number and increment
        val latestNumber = devTagScanner.findLatestDevTagNumber(branchInfo.projectPrefix, branchInfo.branchName)
        val nextNumber = (latestNumber ?: 0) + 1

        // 7. Format tag
        val tag = DevTagPattern.formatDevTag(branchInfo.projectPrefix, branchInfo.branchName, nextNumber)
        val version = DevTagPattern.formatDevVersion(branchInfo.branchName, nextNumber)

        logger.lifecycle("Dev releasing $projectPath as version $version")

        // 8. Create tag locally
        gitReleaseExecutor.createTagLocally(tag)

        // 9. Push to remote (with rollback on failure)
        try {
            gitReleaseExecutor.pushTag(tag)
        } catch (e: GradleException) {
            logger.error("Push failed, rolling back local tag: ${e.message}")
            gitReleaseExecutor.deleteLocalTag(tag)
            throw e
        }

        // 10. Write build/release-version.txt
        val versionFile = buildDir.file("release-version.txt").get().asFile
        versionFile.parentFile.mkdirs()
        versionFile.writeText(version)
        val relativePath = project.rootDir.toPath().relativize(versionFile.toPath())
        logger.lifecycle("Wrote dev release version to: $relativePath")
    }
}
