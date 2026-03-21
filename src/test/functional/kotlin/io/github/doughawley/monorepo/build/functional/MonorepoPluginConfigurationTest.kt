package io.github.doughawley.monorepo.build.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

/**
 * Functional tests for plugin configuration options.
 */
class MonorepoPluginConfigurationTest : FunSpec({
    val testProjectListener = extension(TestProjectListener())

    test("per-project exclude patterns prevent project from being marked changed") {
        // given: :api excludes generated files; :core has no excludes
        val projectDir = testProjectListener.getTestProjectDir()
        val project = TestProjectBuilder(projectDir)
            .withSubproject("api", excludePatterns = listOf("generated/.*"))
            .withSubproject("core")
            .applyPlugin()
            .withRemote()
            .build()
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when: a file matching the :api exclude pattern is created (untracked)
        project.createNewFile("api/generated/Code.kt", "// generated code")

        val result = project.runTask("printChanged")

        // then: :api is not considered changed because the only changed file is excluded
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val changedProjects = result.extractChangedProjects()
        changedProjects shouldNotContain ":api"
    }

    test("per-project excludes only apply to their own project, not others") {
        // given: :api excludes generated files; :core has no excludes
        val projectDir = testProjectListener.getTestProjectDir()
        val project = TestProjectBuilder(projectDir)
            .withSubproject("api", excludePatterns = listOf("generated/.*"))
            .withSubproject("core")
            .applyPlugin()
            .withRemote()
            .build()
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when: matching files are created in both :api and :core (both untracked)
        project.createNewFile("api/generated/Code.kt", "// generated code")
        project.createNewFile("core/generated/Stub.kt", "// generated stub")

        val result = project.runTask("printChanged")

        // then: :api is excluded (pattern matches), :core is detected (no pattern)
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val changedProjects = result.extractChangedProjects()
        changedProjects shouldNotContain ":api"
        changedProjects shouldContain ":core"
    }

    test("plugin fails with helpful error when configuration cache is requested") {
        // given: a standard project with the plugin applied
        val project = testProjectListener.createStandardProject()

        // when: a task is run with --configuration-cache enabled
        val result = project.runTaskAndFail("printChanged", "--configuration-cache")

        // then: the build fails with a clear incompatibility message pointing to the fix
        result.output shouldContain "monorepo-build-release-plugin is incompatible with the Gradle configuration cache"
        result.output shouldContain "org.gradle.configuration-cache=false"
    }
})
