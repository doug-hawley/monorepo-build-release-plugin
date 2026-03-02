package io.github.doughawley.monorepo

import io.github.doughawley.monorepo.build.MonorepoBuildExtension
import io.github.doughawley.monorepo.release.MonorepoReleaseExtension
import org.gradle.api.Action

/**
 * Root-level extension for the monorepo-build-release plugin.
 *
 * Usage in the root build.gradle.kts:
 * ```
 * monorepo {
 *     build {
 *         baseBranch = "main"
 *         excludePatterns = listOf(".*\\.md")
 *     }
 *     release {
 *         globalTagPrefix = "release"
 *         primaryBranchScope = "minor"
 *     }
 * }
 * ```
 */
open class MonorepoExtension {

    /**
     * Change detection configuration.
     */
    val build: MonorepoBuildExtension = MonorepoBuildExtension()

    /**
     * Release versioning configuration.
     */
    val release: MonorepoReleaseExtension = MonorepoReleaseExtension()

    /**
     * Configures change detection settings.
     */
    fun build(action: Action<MonorepoBuildExtension>) {
        action.execute(build)
    }

    /**
     * Configures release versioning settings.
     */
    fun release(action: Action<MonorepoReleaseExtension>) {
        action.execute(release)
    }
}
