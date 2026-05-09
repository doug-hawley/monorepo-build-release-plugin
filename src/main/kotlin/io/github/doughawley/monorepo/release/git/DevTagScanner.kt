package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.github.doughawley.monorepo.release.domain.DevTagPattern
import java.io.File

/**
 * Scans the remote for dev release tags and branches.
 *
 * Uses `git ls-remote` to query the remote, following the same
 * authoritative-remote pattern as [GitTagScanner].
 */
class DevTagScanner(
    private val rootDir: File,
    private val executor: GitCommandExecutor
) {

    /**
     * Returns the highest dev tag number for the given project and branch,
     * or null if no dev tags exist on the remote.
     */
    fun findLatestDevTagNumber(projectPrefix: String, branchName: String): Int? {
        val refPattern = "refs/tags/dev/$projectPrefix/$branchName/*"
        val lines = executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", refPattern)
        return lines
            .mapNotNull { DevTagPattern.parseDevTagNumber(it, projectPrefix, branchName) }
            .maxOrNull()
    }

    /**
     * Returns true if a dev branch for the given project and name exists on the remote.
     */
    fun devBranchExistsOnRemote(projectPrefix: String, branchName: String): Boolean {
        val refPattern = "refs/heads/dev/$projectPrefix/$branchName"
        val lines = executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", refPattern)
        return lines.isNotEmpty()
    }
}
