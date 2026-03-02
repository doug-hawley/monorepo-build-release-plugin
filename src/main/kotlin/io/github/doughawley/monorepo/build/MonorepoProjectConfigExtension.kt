package io.github.doughawley.monorepo.build

/**
 * Per-project extension that allows individual subprojects to declare project-specific
 * configuration. Registered automatically on all subprojects by the plugin.
 *
 * Usage in a subproject's build.gradle.kts:
 * ```
 * monorepoProject {
 *     build {
 *         excludePatterns = listOf("generated/.*", ".*\\.json")
 *     }
 * }
 * ```
 */
open class MonorepoProjectConfigExtension {
    /**
     * File patterns to exclude from change detection for this project.
     * Patterns are Java regex strings matched against file paths relative to this
     * subproject's directory. Applied after global excludePatterns.
     */
    var excludePatterns: List<String> = listOf()
}
