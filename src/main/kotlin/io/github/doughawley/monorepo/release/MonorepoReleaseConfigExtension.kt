package io.github.doughawley.monorepo.release

open class MonorepoReleaseConfigExtension {
    var enabled: Boolean = false
    var tagPrefix: String? = null
    var minimumVersion: String? = null
}
