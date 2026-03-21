package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.release.domain.Scope
import io.github.doughawley.monorepo.release.domain.SemanticVersion
import io.github.doughawley.monorepo.release.domain.TagPattern
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Two-phase atomic release branch creation.
 *
 * Phase 1: Create all release branches locally. If any local creation fails,
 *          roll back all previously created branches and fail.
 * Phase 2: Push all branches atomically with `git push --atomic`.
 *          If the push fails, delete all local branches and fail.
 */
class AtomicReleaseBranchCreator(
    private val gitReleaseExecutor: GitReleaseExecutor,
    private val gitTagScanner: GitTagScanner,
    private val logger: Logger
) {

    data class ReleaseBranchResult(
        val createdBranches: List<String>,
        val projectToBranch: Map<String, String>
    )

    /**
     * Creates release branches for the given projects atomically.
     *
     * @param projects map of Gradle project path to its resolved tag prefix
     * @param globalPrefix the global tag prefix (e.g., "release")
     * @param scope the version bump scope (major or minor)
     * @return the result containing created branch names
     * @throws GradleException if any phase fails (all local branches are rolled back)
     */
    fun createReleaseBranches(
        projects: Map<String, String>,
        globalPrefix: String,
        scope: Scope
    ): ReleaseBranchResult {
        if (projects.isEmpty()) {
            logger.lifecycle("No opted-in changed projects — no release branches to create")
            return ReleaseBranchResult(emptyList(), emptyMap())
        }

        val allResolved = resolveReleaseBranches(projects, globalPrefix, scope)

        // Skip projects whose release branch already exists on the remote.
        // This happens when a prior prepareReleases run succeeded but the
        // subsequent release branch build did not run, so no version tag was created
        // and the branch name resolves to the same value on the next run.
        val projectToBranch = allResolved.filterNot { (projectPath, branch) ->
            val exists = gitReleaseExecutor.branchExistsOnRemote(branch)
            if (exists) {
                logger.warn(
                    "Skipping $projectPath: release branch '$branch' already exists on remote. " +
                    "This usually means the previous release branch build did not complete successfully."
                )
            }
            exists
        }

        if (projectToBranch.isEmpty()) {
            logger.warn("All release branches already exist on remote — nothing to create")
            return ReleaseBranchResult(emptyList(), emptyMap())
        }

        val branchNames = projectToBranch.values.toList()

        // Phase 1: Create all branches locally
        val createdBranches = mutableListOf<String>()
        try {
            for (branch in branchNames) {
                if (gitReleaseExecutor.branchExistsLocally(branch)) {
                    throw GradleException(
                        "Release branch '$branch' already exists locally. " +
                        "Delete it manually or skip this project."
                    )
                }
                gitReleaseExecutor.createBranchLocally(branch)
                createdBranches.add(branch)
            }
        } catch (e: Exception) {
            logger.error("Local branch creation failed, rolling back ${createdBranches.size} branch(es): ${e.message}")
            rollbackLocalBranches(createdBranches)
            throw GradleException("Failed to create release branches locally: ${e.message}", e)
        }

        // Phase 2: Push all branches atomically
        try {
            gitReleaseExecutor.pushBranchesAtomically(branchNames)
        } catch (e: Exception) {
            logger.error("Atomic push failed, rolling back ${createdBranches.size} local branch(es): ${e.message}")
            rollbackLocalBranches(createdBranches)
            throw GradleException("Atomic push of release branches failed: ${e.message}", e)
        }

        projectToBranch.forEach { (projectPath, branch) ->
            logger.lifecycle("Created release branch '$branch' for $projectPath")
        }

        return ReleaseBranchResult(branchNames, projectToBranch)
    }

    private fun resolveReleaseBranches(
        projects: Map<String, String>,
        globalPrefix: String,
        scope: Scope
    ): Map<String, String> {
        return projects.mapValues { (_, projectPrefix) ->
            val latestVersion = gitTagScanner.findLatestVersion(globalPrefix, projectPrefix)
            val nextVersion = if (latestVersion == null) {
                SemanticVersion(0, 1, 0)
            } else {
                latestVersion.bump(scope)
            }
            TagPattern.formatReleaseBranch(globalPrefix, projectPrefix, nextVersion)
        }
    }

    private fun rollbackLocalBranches(branches: List<String>) {
        branches.forEach { branch ->
            gitReleaseExecutor.deleteLocalBranch(branch)
        }
    }
}
