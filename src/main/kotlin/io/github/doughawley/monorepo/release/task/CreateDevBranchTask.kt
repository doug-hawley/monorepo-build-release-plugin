package io.github.doughawley.monorepo.release.task

import io.github.doughawley.monorepo.release.MonorepoReleaseConfigExtension
import io.github.doughawley.monorepo.release.domain.DevTagPattern
import io.github.doughawley.monorepo.release.domain.TagPattern
import io.github.doughawley.monorepo.release.git.DevTagScanner
import io.github.doughawley.monorepo.release.git.GitReleaseExecutor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Creates git branches and pushes to remote")
abstract class CreateDevBranchTask : DefaultTask() {

    @get:Internal
    lateinit var gitReleaseExecutor: GitReleaseExecutor

    @get:Internal
    lateinit var devTagScanner: DevTagScanner

    @get:Internal
    lateinit var projectPath: String

    @get:Internal
    lateinit var projectConfig: MonorepoReleaseConfigExtension

    @get:Internal
    var devBranchNameProperty: String? = null

    @TaskAction
    fun createDevBranch() {
        // 1. Validate branch name is provided
        val branchName = devBranchNameProperty
        if (branchName.isNullOrBlank()) {
            throw GradleException(
                "The -Pdev.branch.name property is required. " +
                "Usage: ./gradlew $projectPath:createDevBranch -Pdev.branch.name=<name>"
            )
        }

        // 2. Validate branch name characters
        DevTagPattern.validateBranchName(branchName)

        // 3. Determine project prefix
        val projectPrefix = projectConfig.tagPrefix
            ?: TagPattern.deriveProjectTagPrefix(projectPath)

        // 4. Format the full branch name
        val fullBranch = DevTagPattern.formatDevBranch(projectPrefix, branchName)

        // 5. Check remote for existing branch
        if (devTagScanner.devBranchExistsOnRemote(projectPrefix, branchName)) {
            throw GradleException(
                "Dev branch '$fullBranch' already exists on the remote. " +
                "Choose a different name or delete the existing branch first."
            )
        }

        // 6. Check local for existing branch
        if (gitReleaseExecutor.branchExistsLocally(fullBranch)) {
            throw GradleException(
                "Dev branch '$fullBranch' already exists locally. " +
                "Delete it manually or choose a different name."
            )
        }

        // 7. Create branch locally
        gitReleaseExecutor.createBranchLocally(fullBranch)

        // 8. Push to remote (with rollback on failure)
        try {
            gitReleaseExecutor.pushBranch(fullBranch)
        } catch (e: GradleException) {
            logger.error("Push failed, rolling back local branch: ${e.message}")
            gitReleaseExecutor.deleteLocalBranch(fullBranch)
            throw e
        }

        logger.lifecycle("Created dev branch: $fullBranch")
    }
}
