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
    fun forReleaseBranch(latestInLine: SemanticVersion?, major: Int, minor: Int, scope: Scope): SemanticVersion {
        return if (latestInLine == null) {
            SemanticVersion(major, minor, 0)
        } else {
            latestInLine.bump(scope)
        }
    }
}
