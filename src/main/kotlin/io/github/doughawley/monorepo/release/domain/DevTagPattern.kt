package io.github.doughawley.monorepo.release.domain

import org.gradle.api.GradleException

/**
 * Naming conventions for dev branches and tags.
 *
 * Dev releases use a simple incrementing integer instead of semver,
 * and are completely decoupled from stable release versioning.
 *
 * Branch format: `dev/<projectPrefix>/<branchName>`
 * Tag format:    `dev/<projectPrefix>/<branchName>/<number>`
 * Version:       `<branchName>.<number>`
 */
object DevTagPattern {

    private val VALID_BRANCH_NAME = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]*$")

    fun formatDevBranch(projectPrefix: String, branchName: String): String {
        return "dev/$projectPrefix/$branchName"
    }

    fun formatDevTag(projectPrefix: String, branchName: String, number: Int): String {
        return "dev/$projectPrefix/$branchName/$number"
    }

    fun formatDevVersion(branchName: String, number: Int): String {
        return "$branchName.$number"
    }

    fun isDevBranch(branch: String): Boolean {
        return Regex("^dev/.+/.+$").matches(branch)
    }

    /**
     * Parses a dev branch name into its project prefix and branch name components.
     *
     * The branch name is always the last path segment (slashes are not allowed
     * in branch names), and the project prefix is everything between `dev/` and
     * the last `/`. This handles nested project prefixes like `services/auth`.
     *
     * @return parsed info, or null if the branch does not match the dev branch format
     */
    fun parseDevBranch(branch: String): DevBranchInfo? {
        if (!branch.startsWith("dev/")) return null
        val withoutPrefix = branch.removePrefix("dev/")
        val lastSlash = withoutPrefix.lastIndexOf('/')
        if (lastSlash <= 0) return null
        val projectPrefix = withoutPrefix.substring(0, lastSlash)
        val branchName = withoutPrefix.substring(lastSlash + 1)
        if (branchName.isEmpty()) return null
        return DevBranchInfo(projectPrefix, branchName)
    }

    /**
     * Extracts the integer tag number from a `git ls-remote` output line.
     *
     * @param tagRef a line from `git ls-remote`, e.g. `"<sha>\trefs/tags/dev/app/feature-auth/3"`
     * @return the tag number, or null if the line does not match
     */
    fun parseDevTagNumber(tagRef: String, projectPrefix: String, branchName: String): Int? {
        val expectedPrefix = "refs/tags/dev/$projectPrefix/$branchName/"
        val refPart = tagRef.substringAfter("\t").trim()
        if (!refPart.startsWith(expectedPrefix)) return null
        val numberStr = refPart.removePrefix(expectedPrefix)
        return numberStr.toIntOrNull()
    }

    /**
     * Validates that a branch name is safe for use in git ref names and tag paths.
     *
     * @throws GradleException if the name is blank or contains disallowed characters
     */
    fun validateBranchName(name: String) {
        if (name.isBlank()) {
            throw GradleException(
                "Dev branch name must not be blank. " +
                "Usage: -Pdev.branch.name=<name>"
            )
        }
        if (!VALID_BRANCH_NAME.matches(name)) {
            throw GradleException(
                "Invalid dev branch name '$name'. " +
                "Must start with a letter or digit, and contain only letters, digits, '.', '_', or '-'."
            )
        }
    }
}

data class DevBranchInfo(
    val projectPrefix: String,
    val branchName: String
)
