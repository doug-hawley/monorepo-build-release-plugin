package io.github.doughawley.monorepo.build.git

import org.gradle.api.logging.Logger

/**
 * Combines and filters the git change signals produced by GitRepository into a single set of
 * changed file paths. Uses a two-dot diff from a resolved base ref and optionally includes
 * working-tree, staged, and untracked sources. Applies global exclude patterns.
 */
class GitChangedFilesDetector(
    private val logger: Logger,
    private val gitRepository: GitRepository
) {

    /**
     * Gets the set of changed files by comparing HEAD against a resolved base ref.
     *
     * Always includes:
     * - Files changed between [resolvedBaseRef] and HEAD (two-dot diff)
     *
     * When [includeUntracked] is true, also includes:
     * - Files modified in the working tree (unstaged)
     * - Files staged in the git index
     * - Untracked files not covered by .gitignore
     *
     * @param resolvedBaseRef The git ref to diff against HEAD (tag, branch, or SHA)
     * @param includeUntracked Whether to include working-tree, staged, and untracked files
     * @param excludePatterns Regex patterns for files to exclude from results
     * @return Set of changed file paths relative to the repository root
     */
    fun getChangedFiles(
        resolvedBaseRef: String,
        includeUntracked: Boolean,
        excludePatterns: List<String>
    ): Set<String> {
        if (!gitRepository.isRepository()) {
            logger.warn("Not a git repository")
            return emptySet()
        }

        val changedFiles = mutableSetOf<String>()

        val refChanges = gitRepository.diffFromRef(resolvedBaseRef)
        logger.debug("Files from ref comparison: ${refChanges.size}")
        changedFiles.addAll(refChanges)

        if (includeUntracked) {
            val workingTreeChanges = gitRepository.workingTreeChanges()
            logger.debug("Working tree changes: ${workingTreeChanges.size}")
            changedFiles.addAll(workingTreeChanges)

            val staged = gitRepository.stagedFiles()
            logger.debug("Staged files: ${staged.size}")
            changedFiles.addAll(staged)

            val untracked = gitRepository.untrackedFiles()
            logger.debug("Untracked files: ${untracked.size}")
            changedFiles.addAll(untracked)
        }

        logger.debug("Total changed files detected: ${changedFiles.size}")
        if (changedFiles.isNotEmpty()) {
            logger.debug("Changed files: ${changedFiles.take(5).joinToString(", ")}${if (changedFiles.size > 5) "..." else ""}")
        }

        val compiledExcludePatterns = excludePatterns.map { Regex(it) }
        return changedFiles.filterNot { file ->
            compiledExcludePatterns.any { pattern -> file.matches(pattern) }
        }.toSet()
    }
}
