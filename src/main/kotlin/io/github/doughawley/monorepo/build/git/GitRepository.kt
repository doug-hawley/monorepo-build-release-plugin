package io.github.doughawley.monorepo.build.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Provides semantic git operations scoped to a single repository root.
 * Responsible for all communication with git; callers are shielded from
 * raw command construction and exit-code handling.
 *
 * Marked open so that test code can mock it with MockK without requiring
 * the inline mock-maker agent.
 */
open class GitRepository(
    rootDir: File,
    private val logger: Logger,
    private val gitExecutor: GitCommandExecutor = GitCommandExecutor(logger)
) {
    private val gitDir: File? = findGitRoot(rootDir)

    /** Returns true when rootDir is inside a git repository. */
    open fun isRepository(): Boolean = gitDir != null

    /**
     * Two-dot diff against a specific commit ref.
     * Returns files changed between [commitRef] and HEAD.
     *
     * @throws IllegalArgumentException if [commitRef] does not exist in this repository
     */
    open fun diffFromRef(commitRef: String): List<String> {
        val dir = gitDir ?: return emptyList()
        val result = gitExecutor.execute(dir, "diff", "--name-only", commitRef, "HEAD")
        if (!result.success) {
            if (isRelativeParentRef(commitRef) && isInitialCommit(dir)) {
                logger.lifecycle(
                    "Commit ref '$commitRef' does not exist, but this appears to be the initial commit. " +
                    "Treating all files as changed."
                )
                return diffFromEmptyTree(dir)
            }
            throw IllegalArgumentException(
                "Commit ref '$commitRef' does not exist in this repository."
            )
        }
        return result.output
    }

    private fun isRelativeParentRef(ref: String): Boolean {
        return ref.contains("~") || ref.contains("^")
    }

    private fun isInitialCommit(dir: File): Boolean {
        val result = gitExecutor.execute(dir, "rev-list", "--count", "HEAD")
        return result.success && result.output.firstOrNull()?.trim() == "1"
    }

    private fun diffFromEmptyTree(dir: File): List<String> {
        return gitExecutor.executeForOutput(dir, "diff-tree", "--root", "--name-only", "--no-commit-id", "-r", "HEAD")
    }

    /** Returns files modified in the working tree but not yet staged. */
    open fun workingTreeChanges(): List<String> {
        val dir = gitDir ?: return emptyList()
        return gitExecutor.executeForOutput(dir, "diff", "--name-only", "HEAD")
    }

    /** Returns files staged in the git index but not yet committed. */
    open fun stagedFiles(): List<String> {
        val dir = gitDir ?: return emptyList()
        return gitExecutor.executeForOutput(dir, "diff", "--name-only", "--cached")
    }

    /** Returns untracked files not covered by .gitignore. */
    open fun untrackedFiles(): List<String> {
        val dir = gitDir ?: return emptyList()
        return gitExecutor.executeForOutput(dir, "ls-files", "--others", "--exclude-standard")
    }

    /** Returns all tracked files in the repository. */
    open fun allTrackedFiles(): List<String> {
        val dir = gitDir ?: return emptyList()
        return gitExecutor.executeForOutput(dir, "ls-files")
    }

    /** Returns true if [ref] resolves to an existing object in this repository. */
    open fun refExists(ref: String): Boolean {
        val dir = gitDir ?: return false
        return gitExecutor.executeSilently(dir, "rev-parse", "--verify", ref).success
    }

    /** Returns the abbreviated commit SHA for [ref], or null if the ref does not exist. */
    open fun resolveCommit(ref: String): String? {
        val dir = gitDir ?: return null
        val result = gitExecutor.executeSilently(dir, "rev-parse", "--short", ref)
        return if (result.success) result.output.firstOrNull()?.trim() else null
    }

    /**
     * Fetches a tag from a remote, updating the local tag to match.
     * Uses `git fetch <remote> tag <tagName> --force --quiet` so the local
     * tag ref is created or updated (not just FETCH_HEAD).
     *
     * @return true if the fetch succeeded, false if the tag does not exist on the remote
     * @throws RuntimeException if the fetch fails for an unexpected reason
     *         (network error, authentication failure, remote misconfigured, etc.)
     */
    open fun fetchTag(remote: String, tagName: String): Boolean {
        val dir = gitDir ?: return false
        val result = gitExecutor.executeSilently(dir, "fetch", remote, "tag", tagName, "--force", "--quiet")
        if (result.success) {
            return true
        }
        if (result.errorOutput.contains("couldn't find remote ref")) {
            return false
        }
        throw RuntimeException(
            "Failed to fetch tag '$tagName' from '$remote': ${result.errorOutput}"
        )
    }

    private fun findGitRoot(startDir: File): File? {
        var current: File? = startDir
        while (current != null) {
            if (File(current, ".git").exists()) {
                return current
            }
            current = current.parentFile
        }
        return null
    }
}
