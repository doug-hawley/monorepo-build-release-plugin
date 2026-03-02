package io.github.doughawley.monorepo

import io.github.doughawley.monorepo.build.MonorepoProjectConfigExtension
import io.github.doughawley.monorepo.release.MonorepoReleaseConfigExtension
import org.gradle.api.Action

/**
 * Per-subproject extension for the monorepo-build-release plugin.
 * Registered automatically on all subprojects by the plugin.
 *
 * Usage in a subproject's build.gradle.kts:
 * ```
 * monorepoProject {
 *     build {
 *         excludePatterns = listOf("generated/.*", ".*\\.json")
 *     }
 *     release {
 *         enabled = true
 *     }
 * }
 * ```
 */
open class MonorepoProjectExtension {

    /**
     * Per-project change detection configuration.
     */
    val build: MonorepoProjectConfigExtension = MonorepoProjectConfigExtension()

    /**
     * Per-project release configuration.
     */
    val release: MonorepoReleaseConfigExtension = MonorepoReleaseConfigExtension()

    /**
     * Configures per-project change detection settings.
     */
    fun build(action: Action<MonorepoProjectConfigExtension>) {
        action.execute(build)
    }

    /**
     * Configures per-project release settings.
     */
    fun release(action: Action<MonorepoReleaseConfigExtension>) {
        action.execute(release)
    }
}
