package io.github.doughawley.monorepo.build.git

import io.github.doughawley.monorepo.build.MonorepoBuildExtension
import org.gradle.api.logging.Logger

/**
 * Combines and filters the git change signals produced by GitRepository into a single set of
 * changed file paths. Responsible for merging branch diff, working-tree, staged, and untracked
 * sources and for applying global exclude patterns.
 */
class GitChangedFilesDetector(
    private val logger: Logger,
    private val gitRepository: GitRepository,
    private val baseBranchResolver: BaseBranchResolver = BaseBranchResolver(logger, gitRepository)
) {

    /**
     * Gets the set of changed files based on the plugin configuration.
     * Includes:
     * - Files changed between the resolved base branch and HEAD (committed)
     * - Files modified in the working tree (unstaged)
     * - Files staged in the git index
     * - Untracked files (when includeUntracked is true)
     *
     * @param extension The plugin configuration
     * @return Set of changed file paths relative to the repository root
     */
    fun getChangedFiles(extension: MonorepoBuildExtension): Set<String> {
        if (!gitRepository.isRepository()) {
            logger.warn("Not a git repository")
            return emptySet()
        }

        val changedFiles = mutableSetOf<String>()

        val branchChanges = getChangedFilesSinceBaseBranch(extension.baseBranch)
        logger.info("Files from branch comparison: ${branchChanges.size}")
        changedFiles.addAll(branchChanges)

        val workingTreeChanges = gitRepository.workingTreeChanges()
        logger.info("Working tree changes: ${workingTreeChanges.size}")
        changedFiles.addAll(workingTreeChanges)

        val staged = gitRepository.stagedFiles()
        logger.info("Staged files: ${staged.size}")
        changedFiles.addAll(staged)

        if (extension.includeUntracked) {
            val untracked = gitRepository.untrackedFiles()
            logger.info("Untracked files: ${untracked.size}")
            changedFiles.addAll(untracked)
        }

        logger.info("Total changed files detected: ${changedFiles.size}")
        if (changedFiles.isNotEmpty()) {
            logger.info("Changed files: ${changedFiles.take(5).joinToString(", ")}${if (changedFiles.size > 5) "..." else ""}")
        }

        val compiledExcludePatterns = extension.excludePatterns.map { Regex(it) }
        return changedFiles.filterNot { file ->
            compiledExcludePatterns.any { pattern -> file.matches(pattern) }
        }.toSet()
    }

    /**
     * Gets the set of changed files between a specific commit ref and HEAD using a two-dot diff.
     * Skips working-tree, staged, and untracked files — intended for CI automation
     * where the workspace is clean.
     *
     * @param commitRef A git commit SHA, tag, or ref to diff against HEAD
     * @param excludePatterns Regex patterns for files to exclude from results
     * @return Set of changed file paths relative to the repository root
     * @throws IllegalArgumentException if the commitRef does not exist in the repository
     */
    fun getChangedFilesFromRef(commitRef: String, excludePatterns: List<String>): Set<String> {
        if (!gitRepository.isRepository()) {
            logger.warn("Not a git repository")
            return emptySet()
        }
        val changedFiles = gitRepository.diffFromRef(commitRef)
        val compiled = excludePatterns.map { Regex(it) }
        return changedFiles.filterNot { file -> compiled.any { it.matches(file) } }.toSet()
    }

    private fun getChangedFilesSinceBaseBranch(baseBranch: String): Set<String> {
        val resolvedRef = baseBranchResolver.resolve(baseBranch)
        if (resolvedRef == null) {
            logger.warn(
                "Could not resolve base branch '$baseBranch' as a remote (origin/$baseBranch) " +
                "or local ref — skipping branch comparison. " +
                "Check that 'baseBranch' is set correctly in your monorepo { build { } } configuration."
            )
            return emptySet()
        }
        return gitRepository.diffBranch(resolvedRef).toSet()
    }
}
