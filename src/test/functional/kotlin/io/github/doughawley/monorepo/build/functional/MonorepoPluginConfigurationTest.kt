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

    // --- Dependency cycle handling (issue #204) ---
    // ProjectMetadataFactory recurses over declared project dependencies and only memoized a
    // project's metadata after fully recursing into its dependencies, so any dependency cycle —
    // a project depending on itself, or two projects depending on each other across
    // configurations — recursed forever and crashed every build with a StackOverflowError.

    test("plugin handles self-referencing dependency from java-test-fixtures") {
        // given: :lib applies java-test-fixtures, which adds a ProjectDependency on :lib itself;
        // :app depends on :lib
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
        project.appendToFile("lib/src/main/kotlin/com/example/Lib.kt", "\n// Modified lib")
        project.commitAll("Change lib")
        val result = project.runTask("printChanged")

        // then: no StackOverflowError; :lib and its dependent :app are detected
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val changedProjects = result.extractChangedProjects()
        changedProjects shouldContain ":lib"
        changedProjects shouldContain ":app"
    }

    // :cycle-a testImplementation-depends on :cycle-b while :cycle-b implementation-depends on
    // :cycle-a — legal in Gradle since the two edges live in different configurations, but a
    // genuine cycle from ProjectMetadataFactory's point of view, since it walks every
    // configuration without distinguishing them. Breaking a cycle necessarily drops one of its
    // two edges (see ProjectMetadataFactory.buildMetadataRecursively), so which project's direct
    // changes still reach the other via a *retained* forward edge depends on project-visitation
    // order, not on which project you happen to change. Asserting both directions here — rather
    // than only the scenario that happens to line up with visitation order — is what makes this
    // test actually exercise the case that regressed change detection under an edge-dropping fix
    // for issue #204: dropping the back edge instead of stubbing it silently lost propagation of
    // a project's own direct changes to anything depending on it solely through that edge.
    test("plugin detects a change in cycle-a propagating to cycle-b") {
        // given
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

        // when: only :cycle-a changes
        project.appendToFile("cycle-a/src/main/kotlin/com/example/CycleA.kt", "\n// Modified cycle-a")
        project.commitAll("Change cycle-a")
        val result = project.runTask("printChanged")

        // then: no StackOverflowError; both cycle members are detected as changed
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val changedProjects = result.extractChangedProjects()
        changedProjects shouldContain ":cycle-a"
        changedProjects shouldContain ":cycle-b"
    }

    test("plugin detects a change in cycle-b propagating to cycle-a") {
        // given
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

        // when: only :cycle-b changes
        project.appendToFile("cycle-b/src/main/kotlin/com/example/CycleB.kt", "\n// Modified cycle-b")
        project.commitAll("Change cycle-b")
        val result = project.runTask("printChanged")

        // then: no StackOverflowError; both cycle members are detected as changed
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val changedProjects = result.extractChangedProjects()
        changedProjects shouldContain ":cycle-a"
        changedProjects shouldContain ":cycle-b"
    }

    test("plugin warns when a dependency cycle affects change detection") {
        // given
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

        // when
        project.appendToFile("cycle-a/src/main/kotlin/com/example/CycleA.kt", "\n// Modified cycle-a")
        project.commitAll("Change cycle-a")
        val result = project.runTask("printChanged")

        // then: the user is warned that detection may be incomplete for the cycle
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
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
