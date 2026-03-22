package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.release.domain.NextVersionResolver
import io.github.doughawley.monorepo.release.domain.Scope
import io.github.doughawley.monorepo.release.domain.SemanticVersion
import io.github.doughawley.monorepo.release.domain.TagPattern
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Two-phase atomic release creator for tags and branches.
 *
 * Phase 1: Create all tags and branches locally. If any local creation fails,
 *          roll back all previously created refs and fail.
 * Phase 2: Push all refs atomically with `git push --atomic`.
 *          If the push fails, delete all local refs and fail.
 */
class AtomicReleaseCreator(
    private val gitReleaseExecutor: GitReleaseExecutor,
    private val gitTagScanner: GitTagScanner,
    private val logger: Logger
) {

    data class ReleaseResult(
        val createdTags: List<String>,
        val createdBranches: List<String>,
        val projectToVersion: Map<String, SemanticVersion>,
        val projectToTag: Map<String, String>,
        val projectToBranch: Map<String, String>
    )

    /**
     * Creates version tags and release branches for the given projects atomically.
     *
     * @param projects map of Gradle project path to its resolved tag prefix
     * @param globalPrefix the global tag prefix (e.g., "release")
     * @param scope the version bump scope (major or minor)
     * @return the result containing created tags, branches, and version mappings
     * @throws GradleException if any phase fails (all local refs are rolled back)
     */
    fun releaseProjects(
        projects: Map<String, String>,
        globalPrefix: String,
        scope: Scope
    ): ReleaseResult {
        if (projects.isEmpty()) {
            logger.lifecycle("No opted-in changed projects — nothing to release")
            return ReleaseResult(emptyList(), emptyList(), emptyMap(), emptyMap(), emptyMap())
        }

        val allResolved = resolveReleases(projects, globalPrefix, scope)

        // Skip projects whose release branch already exists on remote.
        // Since tags and branches are pushed atomically, if the branch exists
        // the tag does too — this covers the case where a prior run's atomic push
        // succeeded but the lastSuccessfulBuildTag update failed afterward.
        val filtered = allResolved.filterNot { (projectPath, resolved) ->
            val exists = gitReleaseExecutor.branchExistsOnRemote(resolved.branch)
            if (exists) {
                logger.warn(
                    "Skipping $projectPath: release branch '${resolved.branch}' already exists on remote. " +
                    "This usually means a prior releaseChanged run already released this version."
                )
            }
            exists
        }

        if (filtered.isEmpty()) {
            logger.warn("All releases already exist on remote — nothing to create")
            return ReleaseResult(emptyList(), emptyList(), emptyMap(), emptyMap(), emptyMap())
        }

        val tags = filtered.values.map { it.tag }
        val branches = filtered.values.map { it.branch }

        // Phase 1: Create all tags and branches locally
        val createdTags = mutableListOf<String>()
        val createdBranches = mutableListOf<String>()
        try {
            for (resolved in filtered.values) {
                gitReleaseExecutor.createTagLocally(resolved.tag)
                createdTags.add(resolved.tag)
            }
            for (resolved in filtered.values) {
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
            logger.error("Local ref creation failed, rolling back ${createdTags.size} tag(s) and ${createdBranches.size} branch(es): ${e.message}")
            rollbackLocalRefs(createdTags, createdBranches)
            throw GradleException("Failed to create release refs locally: ${e.message}", e)
        }

        // Phase 2: Push all refs atomically
        try {
            gitReleaseExecutor.pushRefsAtomically(tags + branches)
        } catch (e: Exception) {
            logger.error("Atomic push failed, rolling back ${createdTags.size} tag(s) and ${createdBranches.size} local branch(es): ${e.message}")
            rollbackLocalRefs(createdTags, createdBranches)
            throw GradleException("Atomic push of release refs failed: ${e.message}", e)
        }

        filtered.forEach { (projectPath, resolved) ->
            logger.lifecycle("Released $projectPath as ${resolved.version} (tag: ${resolved.tag}, branch: ${resolved.branch})")
        }

        return ReleaseResult(
            createdTags = tags,
            createdBranches = branches,
            projectToVersion = filtered.mapValues { it.value.version },
            projectToTag = filtered.mapValues { it.value.tag },
            projectToBranch = filtered.mapValues { it.value.branch }
        )
    }

    private data class ResolvedRelease(
        val version: SemanticVersion,
        val tag: String,
        val branch: String
    )

    private fun resolveReleases(
        projects: Map<String, String>,
        globalPrefix: String,
        scope: Scope
    ): Map<String, ResolvedRelease> {
        return projects.mapValues { (_, projectPrefix) ->
            val latestVersion = gitTagScanner.findLatestVersion(globalPrefix, projectPrefix)
            val nextVersion = NextVersionResolver.forMainBranch(latestVersion, scope)
            ResolvedRelease(
                version = nextVersion,
                tag = TagPattern.formatTag(globalPrefix, projectPrefix, nextVersion),
                branch = TagPattern.formatReleaseBranch(globalPrefix, projectPrefix, nextVersion)
            )
        }
    }

    private fun rollbackLocalRefs(tags: List<String>, branches: List<String>) {
        tags.forEach { tag ->
            gitReleaseExecutor.deleteLocalTag(tag)
        }
        branches.forEach { branch ->
            gitReleaseExecutor.deleteLocalBranch(branch)
        }
    }
}
