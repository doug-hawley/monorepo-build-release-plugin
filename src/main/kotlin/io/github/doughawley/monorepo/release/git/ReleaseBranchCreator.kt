package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.release.domain.NextVersionResolver
import io.github.doughawley.monorepo.release.domain.Scope
import io.github.doughawley.monorepo.release.domain.SemanticVersion
import io.github.doughawley.monorepo.release.domain.TagPattern
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Creates release branches for changed projects.
 *
 * Phase 1: Create all branches locally. If any local creation fails,
 *          roll back all previously created branches and fail.
 * Phase 2: Push all branches atomically with `git push --atomic`.
 *          If the push fails, delete all local branches and fail.
 *
 * Tagging is delegated to the `release` task running on each release branch.
 */
class ReleaseBranchCreator(
    private val gitReleaseExecutor: GitReleaseExecutor,
    private val gitTagScanner: GitTagScanner,
    private val logger: Logger
) {

    data class ReleaseResult(
        val createdBranches: List<String>,
        val projectToVersion: Map<String, SemanticVersion>,
        val projectToBranch: Map<String, String>
    )

    /**
     * Creates release branches for the given projects atomically.
     *
     * @param projects map of Gradle project path to its resolved tag prefix
     * @param globalPrefix the global tag prefix (e.g., "release")
     * @param scope the version bump scope (major or minor)
     * @return the result containing created branches and version mappings
     * @throws GradleException if any phase fails (all local branches are rolled back)
     */
    fun releaseProjects(
        projects: Map<String, String>,
        globalPrefix: String,
        scope: Scope
    ): ReleaseResult {
        if (projects.isEmpty()) {
            logger.lifecycle("No opted-in changed projects — nothing to release")
            return ReleaseResult(emptyList(), emptyMap(), emptyMap())
        }

        val allResolved = resolveReleases(projects, globalPrefix, scope)

        val branches = allResolved.values.map { it.branch }

        // Phase 1: Create all branches locally
        val createdBranches = mutableListOf<String>()
        try {
            for (resolved in allResolved.values) {
                if (gitReleaseExecutor.branchExistsLocally(resolved.branch)) {
                    throw GradleException(
                        "Release branch '${resolved.branch}' already exists locally. " +
                        "Delete it manually or skip this project."
                    )
                }
                gitReleaseExecutor.createBranchLocally(resolved.branch)
                createdBranches.add(resolved.branch)
            }
        } catch (e: Exception) {
            logger.error("Local branch creation failed, rolling back ${createdBranches.size} branch(es): ${e.message}")
            rollbackLocalBranches(createdBranches)
            throw GradleException("Failed to create release branches locally: ${e.message}", e)
        }

        // Phase 2: Push all branches atomically
        try {
            gitReleaseExecutor.pushBranchesAtomically(branches)
        } catch (e: Exception) {
            logger.error("Atomic push failed, rolling back ${createdBranches.size} local branch(es): ${e.message}")
            rollbackLocalBranches(createdBranches)
            throw GradleException("Atomic push of release branches failed: ${e.message}", e)
        }

        allResolved.forEach { (projectPath, resolved) ->
            logger.lifecycle("Created release branch for $projectPath: ${resolved.branch} (version line ${resolved.version.major}.${resolved.version.minor}.x)")
        }

        return ReleaseResult(
            createdBranches = branches,
            projectToVersion = allResolved.mapValues { it.value.version },
            projectToBranch = allResolved.mapValues { it.value.branch }
        )
    }

    private data class ResolvedRelease(
        val version: SemanticVersion,
        val branch: String
    )

    private fun resolveReleases(
        projects: Map<String, String>,
        globalPrefix: String,
        scope: Scope
    ): Map<String, ResolvedRelease> {
        return projects.mapValues { (_, projectPrefix) ->
            val latestTagVersion = gitTagScanner.findLatestVersion(globalPrefix, projectPrefix)
            val latestBranchVersion = gitTagScanner.findLatestBranchVersion(globalPrefix, projectPrefix)
            val latestVersion = maxOfNullable(latestTagVersion, latestBranchVersion)
            val nextVersion = NextVersionResolver.forMainBranch(latestVersion, scope)
            ResolvedRelease(
                version = nextVersion,
                branch = TagPattern.formatReleaseBranch(globalPrefix, projectPrefix, nextVersion)
            )
        }
    }

    private fun <T : Comparable<T>> maxOfNullable(a: T?, b: T?): T? {
        return when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }
    }

    private fun rollbackLocalBranches(branches: List<String>) {
        branches.forEach { branch ->
            gitReleaseExecutor.deleteLocalBranch(branch)
        }
    }
}
