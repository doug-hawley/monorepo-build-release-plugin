package io.github.doughawley.monorepo.build.domain

import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Resolves the project path of a Gradle project dependency across Gradle 8.0-9.x.
 *
 * ProjectDependency.getPath() replaced getDependencyProject() in Gradle 8.11;
 * getDependencyProject() was removed in Gradle 9.0. Neither method exists across
 * the whole 8.0-9.x range, and once compiled against a Gradle 9 API, the removed
 * method is no longer a valid symbol to reference in source at all. Both are
 * therefore looked up reflectively against the dependency's runtime class rather
 * than called directly, so resolution follows whichever Gradle version actually
 * runs the plugin instead of whichever version compiled it.
 */
class ProjectDependencyPathResolver(private val logger: Logger) {

    fun resolve(dependency: Any): String? {
        return try {
            dependency.javaClass.getMethod("getPath").invoke(dependency) as String
        } catch (e: NoSuchMethodException) {
            resolveViaDeprecatedDependencyProject(dependency)
        }
    }

    private fun resolveViaDeprecatedDependencyProject(dependency: Any): String? {
        return try {
            val method = dependency.javaClass.getMethod("getDependencyProject")
            val dependencyProject = method.invoke(dependency) as Project
            dependencyProject.path
        } catch (e: ReflectiveOperationException) {
            logger.debug("Could not resolve project dependency path for $dependency: ${e.message}")
            null
        }
    }
}
