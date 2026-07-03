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

    test("plugin handles self-referencing dependency from java-test-fixtures") {
        // given: :lib applies java-test-fixtures, which adds a ProjectDependency on :lib itself;
        // :app depends on :lib (issue #204 — StackOverflowError in ProjectMetadataFactory)
        val projectDir = testProjectListener.getTestProjectDir()
        val project = TestProjectBuilder(projectDir)
            .withSubproject("lib", extraPlugins = listOf("java-test-fixtures"))
            .withSubproject("app", dependsOn = listOf("lib"))
            .applyPlugin()
            .withRemote()
            .build()
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when: a file in :lib changes
        project.appendToFile("lib/src/main/kotlin/com/example/Lib.kt", "// Modified lib")
        project.commitAll("Change lib")
        val result = project.runTask("printChanged")

        // then: no StackOverflowError; :lib and its dependent :app are detected
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val changedProjects = result.extractChangedProjects()
        changedProjects shouldContain ":lib"
        changedProjects shouldContain ":app"
    }

    test("plugin handles cross-configuration dependency cycle between projects") {
        // given: :cycle-a testImplementation-depends on :cycle-b while :cycle-b
        // implementation-depends on :cycle-a — legal in Gradle, but a declared cycle
        // (issue #204 — StackOverflowError in ProjectMetadataFactory)
        val projectDir = testProjectListener.getTestProjectDir()
        val project = TestProjectBuilder(projectDir)
            .withSubproject("cycle-a", testDependsOn = listOf("cycle-b"))
            .withSubproject("cycle-b", dependsOn = listOf("cycle-a"))
            .applyPlugin()
            .withRemote()
            .build()
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when: a file in :cycle-a changes
        project.appendToFile("cycle-a/src/main/kotlin/com/example/CycleA.kt", "// Modified cycle-a")
        project.commitAll("Change cycle-a")
        val result = project.runTask("printChanged")

        // then: no StackOverflowError; the directly changed project is detected
        // and the user is warned about the cycle
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.extractChangedProjects() shouldContain ":cycle-a"
        result.output shouldContain "Dependency cycle detected"
    }

    test("plugin fails with helpful error when not inside a git repository") {
        // given: a project with the plugin applied but no git init
        val projectDir = testProjectListener.getTestProjectDir()
        val project = TestProjectBuilder(projectDir)
            .withSubproject("app")
            .applyPlugin()
            .build()

        // when
        val result = project.runTaskAndFail("printChanged")

        // then
        result.output shouldContain "not inside a git repository"
        result.output shouldContain "git init"
    }
})
