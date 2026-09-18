package io.github.doughawley.monorepo.build.domain

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.logging.Logger

/**
 * Factory for building ProjectMetadata trees from Gradle Project objects.
 */
class ProjectMetadataFactory(private val logger: Logger) {

    private val dependencyPathResolver = ProjectDependencyPathResolver(logger)

    /**
     * Builds a map of ProjectMetadata objects for all projects in the hierarchy.
     * Each ProjectMetadata includes its dependencies as a list of other ProjectMetadata objects.
     *
     * @param rootProject The root Gradle project
     * @param changedFilesMap Optional map of project paths to their changed files
     * @return Map of project paths to ProjectMetadata objects
     */
    fun buildProjectMetadataMap(
        rootProject: Project,
        changedFilesMap: Map<String, List<String>> = emptyMap()
    ): Map<String, ProjectMetadata> {
        val metadataMap = mutableMapOf<String, ProjectMetadata>()
        val projectMap = mutableMapOf<String, Project>()

        // Collect all projects
        rootProject.allprojects.forEach { project ->
            projectMap[project.path] = project
        }

        // Build metadata recursively for each project
        projectMap.forEach { (_, project) ->
            buildMetadataRecursively(project, projectMap, metadataMap, changedFilesMap, mutableSetOf())
        }

        return metadataMap
    }

    /**
     * Recursively builds ProjectMetadata for a project and its dependencies.
     *
     * [inProgress] is the current recursion stack (issue #204). Without it, a project that
     * depends on itself — which java-test-fixtures adds automatically via
     * testImplementation(testFixtures(project)) — or a legal cross-configuration cycle between
     * two projects (":a" testImplementation-depends on ":b" while ":b" implementation-depends on
     * ":a") recurses forever, since metadata is only cached *after* fully recursing into a
     * project's dependencies. Revisiting a project already on the stack means we've found a
     * cycle; that one edge is cut to break the recursion.
     *
     * The cut edge still contributes a ProjectMetadata node with the target's own changedFiles
     * (so a direct change to the cycle target is still detected) but no dependencies of its own
     * (its transitive dependencies were already being resolved further up the same call stack,
     * so they're unavailable here without re-deriving them). This node is deliberately built
     * fresh rather than pulled from [metadataMap] and is not cached into it either: the cycle's
     * other member reaches its *real*, fully-connected metadata once its own top-level build
     * completes — only this one back edge sees the incomplete view. An earlier version of this
     * fix dropped the cyclic edge entirely instead of stubbing it; that undercounted changes,
     * since a project's own direct edits stopped propagating to anything that depended on it
     * solely through the dropped edge.
     */
    private fun buildMetadataRecursively(
        project: Project,
        projectMap: Map<String, Project>,
        metadataMap: MutableMap<String, ProjectMetadata>,
        changedFilesMap: Map<String, List<String>>,
        inProgress: MutableSet<String>
    ): ProjectMetadata {
        // Return cached metadata if already built
        metadataMap[project.path]?.let {
            return it
        }

        inProgress.add(project.path)

        // Find dependency paths
        val dependencyPaths = findProjectDependencies(project)

        // Recursively build metadata for each dependency (nested objects)
        val dependencyMetadataList = dependencyPaths.mapNotNull { depPath ->
            if (depPath == project.path) {
                // A pure self-dependency carries no change-detection information beyond what
                // this project's own changedFiles already provides, and is routine enough
                // (java-test-fixtures adds one to every fixtures-enabled project) not to warn on.
                logger.debug("Skipping self-referencing dependency of ${project.path}")
                null
            } else if (depPath in inProgress) {
                logger.warn(
                    "Dependency cycle detected between ${project.path} and $depPath; " +
                        "ignoring the ${project.path} -> $depPath edge for change detection. " +
                        "Projects in a dependency cycle may not be detected as affected by each other's changes."
                )
                ProjectMetadata(
                    name = projectMap[depPath]?.name ?: depPath.substringAfterLast(':'),
                    fullyQualifiedName = depPath,
                    dependencies = emptyList(),
                    changedFiles = changedFilesMap[depPath] ?: emptyList()
                )
            } else {
                projectMap[depPath]?.let { depProject ->
                    buildMetadataRecursively(depProject, projectMap, metadataMap, changedFilesMap, inProgress)
                }
            }
        }

        // Get changed files for this project
        val changedFiles = changedFilesMap[project.path] ?: emptyList()

        // Create metadata with nested dependency objects
        val metadata = ProjectMetadata(
            name = project.name,
            fullyQualifiedName = project.path,
            dependencies = dependencyMetadataList,
            changedFiles = changedFiles
        )

        // Cache the metadata
        metadataMap[project.path] = metadata
        inProgress.remove(project.path)

        return metadata
    }

    /**
     * Builds a ProjectMetadata tree for a specific project.
     *
     * @param project The Gradle project to build metadata for
     * @param changedFilesMap Optional map of project paths to their changed files
     * @return ProjectMetadata for the specified project
     */
    fun buildProjectMetadata(
        project: Project,
        changedFilesMap: Map<String, List<String>> = emptyMap()
    ): ProjectMetadata {
        val metadataMap = buildProjectMetadataMap(project.rootProject, changedFilesMap)
        return metadataMap[project.path] ?: ProjectMetadata(
            name = project.name,
            fullyQualifiedName = project.path,
            dependencies = emptyList(),
            changedFiles = changedFilesMap[project.path] ?: emptyList()
        )
    }

    /**
     * Finds all project paths that the given project depends on.
     * This includes regular project dependencies and platform/BOM dependencies.
     * Only examines declared dependencies, not resolved dependencies, to avoid
     * triggering configuration resolution during the configuration phase.
     *
     * @param project The project to find dependencies for
     * @return Set of project paths that are dependencies
     */
    private fun findProjectDependencies(project: Project): Set<String> {
        val dependencies = mutableSetOf<String>()

        try {
            project.configurations.forEach { config ->
                try {
                    // Only look at declared dependencies, don't resolve
                    config.dependencies.forEach { dep ->
                        // ProjectDependency covers both regular and platform project dependencies.
                        // Calling platform(project(":X")) in Gradle returns the same ProjectDependency
                        // object with endorseStrictVersions() and the Category.REGULAR_PLATFORM
                        // attribute set on it — it does not wrap it in a different type.
                        if (dep is ProjectDependency) {
                            dependencyPathResolver.resolve(dep)?.let { dependencies.add(it) }
                        }
                    }
                } catch (e: Exception) {
                    // Individual configuration might not be accessible, skip
                    logger.debug("Could not access configuration ${config.name} for ${project.path}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            // Configuration might not be available yet, skip
            logger.debug("Could not access dependencies for ${project.path}: ${e.message}")
        }

        return dependencies
    }
}
