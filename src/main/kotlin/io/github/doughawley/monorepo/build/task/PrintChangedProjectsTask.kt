package io.github.doughawley.monorepo.build.task

import io.github.doughawley.monorepo.build.ChangedProjectsPrinter
import io.github.doughawley.monorepo.MonorepoExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Task that prints which projects have changed based on git history and dependency analysis.
 *
 * Output format:
 *   - Directly changed projects are listed by path.
 *   - Transitively affected projects are listed with an "(affected via ...)" annotation naming
 *     the direct dependencies that carry the change.
 */
@DisableCachingByDefault(because = "Prints git-based change detection results")
abstract class PrintChangedProjectsTask : DefaultTask() {

    @TaskAction
    fun detectChanges() {
        val extension = project.rootProject.extensions.getByType(MonorepoExtension::class.java).build

        if (!extension.metadataComputed) {
            throw GradleException(
                "Changed project metadata was not computed in the configuration phase. " +
                "Possible causes: the plugin was not applied to the root project, " +
                "or an error occurred during project evaluation. " +
                "Re-run with --info or --debug for more details."
            )
        }

        val resolvedRef = extension.resolvedBaseRef
        logger.lifecycle(ChangedProjectsPrinter().buildReport(
            header = "Changed projects (since $resolvedRef):",
            monorepoProjects = extension.monorepoProjects
        ))
    }
}
