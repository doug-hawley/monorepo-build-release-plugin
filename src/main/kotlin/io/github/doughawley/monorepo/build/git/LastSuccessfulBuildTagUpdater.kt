package io.github.doughawley.monorepo.build.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Force-updates the last-successful-build tag to HEAD and pushes it to the remote.
 * Called after a successful build + release branch creation on the primary branch.
 */
class LastSuccessfulBuildTagUpdater(
    private val rootDir: File,
    private val executor: GitCommandExecutor,
    private val logger: Logger
) {

    /**
     * Updates the tag to point at HEAD and pushes it to the remote.
     *
     * @param tagName the tag to update (e.g., "monorepo/last-successful-build")
     * @throws RuntimeException if the local tag creation fails
     */
    fun updateTag(tagName: String) {
        // Force-create tag locally at HEAD
        val tagResult = executor.execute(rootDir, "tag", "-f", tagName, "HEAD")
        if (!tagResult.success) {
            throw RuntimeException(
                "Failed to update local tag '$tagName': ${tagResult.errorOutput}"
            )
        }
        logger.lifecycle("Updated local tag '$tagName' to HEAD")

        // Push tag to remote (force, since we're moving it)
        val pushResult = executor.execute(rootDir, "push", "origin", "-f", "refs/tags/$tagName")
        if (!pushResult.success) {
            logger.warn(
                "Failed to push tag '$tagName' to remote: ${pushResult.errorOutput}. " +
                "The build succeeded but the tag was not pushed. " +
                "The next build may re-process already-built changes."
            )
            return
        }
        logger.lifecycle("Pushed tag '$tagName' to remote")
    }
}
