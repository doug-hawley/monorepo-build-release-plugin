package io.github.doughawley.monorepo.build.task

import io.github.doughawley.monorepo.build.ChangedProjectsPrinter
import io.github.doughawley.monorepo.build.MonorepoBuildExtension
import io.github.doughawley.monorepo.MonorepoBuildReleasePlugin
import io.github.doughawley.monorepo.MonorepoExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Task that prints which projects have changed based on git history and dependency analysis.
 *
 * Output format:
 *   - Directly changed projects are listed by path.
 *   - Transitively affected projects are listed with an "(affected via ...)" annotation naming
 *     the direct dependencies that carry the change.
 */
abstract class PrintChangedProjectsTask : DefaultTask() {

    @TaskAction
    fun detectChanges() {
        val extension = project.rootProject.extensions.getByType(MonorepoExtension::class.java).build

        // If metadata wasn't computed in configuration phase (e.g., in unit tests),
        // compute it now as a fallback
        if (!extension.metadataComputed) {
            logger.warn("Metadata was not computed in configuration phase. Computing now (this may indicate a test environment).")
            val plugin = project.plugins.findPlugin(MonorepoBuildReleasePlugin::class.java)
            if (plugin != null) {
                plugin.computeMetadata(project.rootProject, extension)
                extension.metadataComputed = true
            } else {
                throw IllegalStateException(
                    "Changed project metadata was not computed and plugin instance not found. " +
                    "This indicates a plugin initialization error."
                )
            }
        }

        logger.lifecycle(ChangedProjectsPrinter().buildReport(
            header = "Changed projects:",
            monorepoProjects = extension.monorepoProjects
        ))
    }
}
