package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Handles mutating git operations required for the release process.
 */
class GitReleaseExecutor(
    private val rootDir: File,
    private val executor: GitCommandExecutor,
    private val logger: Logger
) {

    fun isDirty(): Boolean {
        val result = executor.execute(rootDir, "status", "--porcelain")
        return result.output.isNotEmpty()
    }

    fun currentBranch(): String {
        val result = executor.execute(rootDir, "rev-parse", "--abbrev-ref", "HEAD")
        if (!result.success || result.output.isEmpty()) {
            throw GradleException("Failed to determine current git branch: ${result.errorOutput}")
        }
        return result.output.first().trim()
    }

    fun createTagLocally(tag: String) {
        val result = executor.execute(rootDir, "tag", tag)
        if (!result.success) {
            throw GradleException("Failed to create local tag '$tag': ${result.errorOutput}")
        }
        logger.lifecycle("Created local tag: $tag")
    }

    fun createBranchLocally(branch: String) {
        val result = executor.execute(rootDir, "branch", branch)
        if (!result.success) {
            throw GradleException("Failed to create local branch '$branch': ${result.errorOutput}")
        }
        logger.lifecycle("Created local branch: $branch")
    }

    fun pushTag(tag: String) {
        val result = executor.execute(rootDir, "push", "origin", tag)
        if (!result.success) {
            throw GradleException("Failed to push tag '$tag' to remote: ${result.errorOutput}")
        }
        logger.lifecycle("Pushed tag '$tag' to remote")
    }

    fun pushBranch(branch: String) {
        val result = executor.execute(rootDir, "push", "origin", branch)
        if (!result.success) {
            throw GradleException("Failed to push branch '$branch' to remote: ${result.errorOutput}")
        }
        logger.lifecycle("Pushed branch '$branch' to remote")
    }

    fun deleteLocalTag(tag: String) {
        val result = executor.execute(rootDir, "tag", "-d", tag)
        if (!result.success) {
            logger.warn("Failed to delete local tag '$tag': ${result.errorOutput}")
        } else {
            logger.lifecycle("Deleted local tag: $tag")
        }
    }

    fun deleteLocalBranch(branch: String) {
        val result = executor.execute(rootDir, "branch", "-D", branch)
        if (!result.success) {
            logger.warn("Failed to delete local branch '$branch': ${result.errorOutput}")
        } else {
            logger.lifecycle("Deleted local branch: $branch")
        }
    }

    fun pushBranchesAtomically(branches: List<String>) {
        val args = mutableListOf("push", "--atomic", "origin")
        args.addAll(branches)
        val result = executor.execute(rootDir, *args.toTypedArray())
        if (!result.success) {
            throw GradleException(
                "Atomic push of ${branches.size} branch(es) failed: ${result.errorOutput}"
            )
        }
        logger.lifecycle("Pushed ${branches.size} branch(es) atomically to remote")
    }

    fun branchExistsLocally(branch: String): Boolean {
        val result = executor.execute(rootDir, "branch", "--list", branch)
        return result.success && result.output.isNotEmpty()
    }
}
