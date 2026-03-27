package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.github.doughawley.monorepo.release.domain.SemanticVersion
import io.github.doughawley.monorepo.release.domain.TagPattern
import java.io.File

/**
 * Scans git tags to find version information for a project.
 *
 * Version scanning uses the remote (authoritative released versions).
 * Tag existence checks use local tags (fast pre-flight before attempting tag creation).
 */
class GitTagScanner(
    private val rootDir: File,
    private val executor: GitCommandExecutor
) {

    /**
     * Returns the highest released version for the given project by querying the remote,
     * or null if no tags matching the project's prefix exist on the remote.
     */
    fun findLatestVersion(globalPrefix: String, projectPrefix: String): SemanticVersion? {
        val refPattern = "refs/tags/$globalPrefix/$projectPrefix/v*"
        val lines = executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", refPattern)
        return lines
            .mapNotNull { parseTagFromLsRemoteLine(it, globalPrefix, projectPrefix) }
            .maxOrNull()
    }

    /**
     * Returns the highest patch version within the given major.minor line by querying the remote,
     * or null if no tags for that version line exist on the remote.
     */
    fun findLatestVersionInLine(
        globalPrefix: String,
        projectPrefix: String,
        major: Int,
        minor: Int
    ): SemanticVersion? {
        val refPattern = "refs/tags/$globalPrefix/$projectPrefix/v$major.$minor.*"
        val lines = executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", refPattern)
        return lines
            .mapNotNull { parseTagFromLsRemoteLine(it, globalPrefix, projectPrefix) }
            .maxOrNull()
    }

    /**
     * Returns the highest version line (as major.minor.0) found among remote release branches
     * for the given project, or null if no matching branches exist on the remote.
     *
     * For example, if branches `release/app/v0.1.x` and `release/app/v0.2.x` exist,
     * returns SemanticVersion(0, 2, 0).
     */
    fun findLatestBranchVersion(globalPrefix: String, projectPrefix: String): SemanticVersion? {
        val refPattern = "refs/heads/$globalPrefix/$projectPrefix/v*"
        val lines = executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", refPattern)
        return lines
            .mapNotNull { parseBranchFromLsRemoteLine(it, globalPrefix, projectPrefix) }
            .maxOrNull()
    }

    /**
     * Returns true if the given tag exists in the local repository.
     * Uses local tag lookup only — a tag that exists on the remote but has not been fetched
     * locally will return false.
     */
    fun tagExists(tag: String): Boolean {
        val output = executor.executeForOutput(rootDir, "tag", "-l", tag)
        return output.any { it.trim() == tag }
    }

    private fun parseBranchFromLsRemoteLine(line: String, globalPrefix: String, projectPrefix: String): SemanticVersion? {
        // ls-remote output format: "<sha>\trefs/heads/<branchname>"
        val branchName = line.substringAfter("refs/heads/").trim()
        val expectedPrefix = "$globalPrefix/$projectPrefix/v"
        if (!branchName.startsWith(expectedPrefix)) return null
        val versionPart = branchName.removePrefix(expectedPrefix)
        val match = Regex("^(\\d+)\\.(\\d+)\\.x$").matchEntire(versionPart) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        return SemanticVersion(major, minor, 0)
    }

    private fun parseTagFromLsRemoteLine(line: String, globalPrefix: String, projectPrefix: String): SemanticVersion? {
        // ls-remote output format: "<sha>\trefs/tags/<tagname>"
        val tagName = line.substringAfter("refs/tags/").trim()
        return TagPattern.parseVersionFromTag(tagName, globalPrefix, projectPrefix)
    }
}
