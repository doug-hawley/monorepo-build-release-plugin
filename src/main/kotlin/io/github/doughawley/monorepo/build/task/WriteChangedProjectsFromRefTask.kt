package io.github.doughawley.monorepo.build.task

import io.github.doughawley.monorepo.MonorepoExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task that writes the list of changed projects to a file for machine consumption
 * in CI/CD pipelines (e.g. shell scripts).
 *
 * Output format: one Gradle project path per line, no decoration.
 *
 * Example:
 *   :common-lib
 *   :modules:module1
 *   :apps:app1
 *
 * An empty file is written when no projects have changed, so downstream
 * scripts can always assume the file exists after this task runs.
 */
abstract class WriteChangedProjectsFromRefTask : DefaultTask() {

    /**
     * The file to write changed project paths to.
     * Defaults to build/monorepo/changed-projects.txt.
     * Override via tasks.named<WriteChangedProjectsFromRefTask>(...) { outputFile.set(...) }
     * or at runtime with -Pmonorepo.outputFile=<path>.
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun write() {
        val extension = project.rootProject.extensions.getByType(MonorepoExtension::class.java).build

        if (!extension.metadataComputed) {
            throw GradleException(
                "Changed project metadata was not computed in the configuration phase. " +
                "Possible causes: the plugin was not applied to the root project, " +
                "or an error occurred during project evaluation. " +
                "Re-run with --info or --debug for more details."
            )
        }

        val projects = extension.allAffectedProjects.sorted()
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(projects.joinToString("\n").let { if (it.isNotEmpty()) "$it\n" else it })

        if (projects.isEmpty()) {
            logger.lifecycle("No projects have changed — wrote empty output file: ${file.path}")
        } else {
            logger.lifecycle("Wrote ${projects.size} changed project(s) to ${file.path}: ${projects.joinToString(", ")}")
        }
    }
}
