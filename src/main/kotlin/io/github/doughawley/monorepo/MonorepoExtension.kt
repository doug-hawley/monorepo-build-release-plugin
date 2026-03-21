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
 *     primaryBranch = "main"
 *     build {
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
     * The primary integration branch name (e.g. "main", "master", "develop").
     * Used as fallback ref (`origin/<primaryBranch>`) when the last-successful-build tag
     * doesn't exist, and as the branch guard for CI tasks like
     * `prepareReleasesForChanged`.
     */
    var primaryBranch: String = "main"

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
