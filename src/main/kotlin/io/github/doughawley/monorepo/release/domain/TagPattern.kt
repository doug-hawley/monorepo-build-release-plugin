package io.github.doughawley.monorepo.release.domain

object TagPattern {

    fun formatTag(globalPrefix: String, projectPrefix: String, version: SemanticVersion): String {
        return "$globalPrefix/$projectPrefix/v$version"
    }

    fun formatReleaseBranch(globalPrefix: String, projectPrefix: String, version: SemanticVersion): String {
        return "$globalPrefix/$projectPrefix/v${version.major}.${version.minor}.x"
    }

    fun deriveProjectTagPrefix(gradlePath: String): String {
        return gradlePath.trimStart(':').replace(':', '-')
    }

    fun parseVersionFromTag(tag: String, globalPrefix: String, projectPrefix: String): SemanticVersion? {
        val expectedPrefix = "$globalPrefix/$projectPrefix/v"
        if (!tag.startsWith(expectedPrefix)) return null
        val versionStr = tag.removePrefix(expectedPrefix)
        return SemanticVersion.parse(versionStr)
    }

    fun isReleaseBranch(branch: String, globalPrefix: String): Boolean {
        return Regex("^${Regex.escape(globalPrefix)}/.+/v\\d+\\.\\d+\\.x$").matches(branch)
    }

    fun parseVersionLineFromBranch(branch: String): Pair<Int, Int> {
        // Expected format: release/<prefix>/v<major>.<minor>.x
        val versionPart = branch.substringAfterLast("/")
        val match = Regex("^v(\\d+)\\.(\\d+)\\.x$").matchEntire(versionPart)
            ?: throw IllegalArgumentException(
                "Cannot parse version line from branch '$branch'. " +
                "Expected format: <prefix>/v<major>.<minor>.x"
            )
        return Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    fun parseProjectPrefixFromBranch(branch: String, globalPrefix: String): String {
        // Expected format: <globalPrefix>/<projectPrefix>/v<major>.<minor>.x
        val withoutGlobal = branch.removePrefix("$globalPrefix/")
        return withoutGlobal.substringBeforeLast("/")
    }
}
