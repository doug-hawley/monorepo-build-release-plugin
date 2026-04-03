package io.github.doughawley.monorepo.release.domain

/**
 * Computes the next release version based on the current branch context,
 * the latest existing version, and the requested scope.
 */
object NextVersionResolver {

    /**
     * Resolves the next version for a patch release from a release branch.
     *
     * @param latestInLine the highest existing version within this version line, or null if no tags exist for it
     * @param major the major version from the release branch name
     * @param minor the minor version from the release branch name
     * @param scope the bump scope (always PATCH on release branches)
     */
    /**
     * Resolves the next version for a release from the primary branch.
     *
     * @param latestVersion the highest existing version across all version lines, or null if no tags exist
     * @param scope the bump scope (major or minor)
     */
    fun forMainBranch(
        latestVersion: SemanticVersion?,
        scope: Scope,
        minimumVersion: SemanticVersion? = null
    ): SemanticVersion {
        val effectiveLatest = maxOfNullable(latestVersion, minimumVersion)
        return if (effectiveLatest == null) {
            SemanticVersion(0, 1, 0)
        } else {
            effectiveLatest.bump(scope)
        }
    }

    private fun <T : Comparable<T>> maxOfNullable(a: T?, b: T?): T? {
        return when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }
    }

    fun forReleaseBranch(latestInLine: SemanticVersion?, major: Int, minor: Int, scope: Scope): SemanticVersion {
        return if (latestInLine == null) {
            SemanticVersion(major, minor, 0)
        } else {
            latestInLine.bump(scope)
        }
    }
}
