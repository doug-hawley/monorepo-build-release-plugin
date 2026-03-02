package io.github.doughawley.monorepo.build.functional

import io.github.doughawley.monorepo.build.functional.StandardTestProject.Files
import io.github.doughawley.monorepo.build.functional.StandardTestProject.Projects
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

/**
 * Functional tests for the buildChangedProjectsFromRef task.
 */
class BuildChangedProjectsFromRefFunctionalTest : FunSpec({
    val testProjectListener = listener(TestProjectListener())

    test("buildChangedProjectsFromRef fails with helpful error when no commitRef provided") {
        // given
        val project = testProjectListener.createStandardProject()

        // when: run without commitRef
        val result = project.runTaskAndFail("buildChangedProjectsFromRef")

        // then: error message mentions how to supply commitRef
        result.output shouldContain "commitRef"
        result.output shouldContain "monorepo.commitRef"
    }

    test("buildChangedProjectsFromRef builds directly changed project") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Modify app2")

        // when
        val result = project.runTaskWithProperties(
            "buildChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then
        result.task(":buildChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjectsFromRef()
        built shouldContain Projects.APP2
        built shouldNotContain Projects.COMMON_LIB
        built shouldNotContain Projects.MODULE1
    }

    test("buildChangedProjectsFromRef builds all projects affected by common-lib change") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()

        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Modify common-lib")

        // when
        val result = project.runTaskWithProperties(
            "buildChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then: common-lib plus all transitive dependents are built
        result.task(":buildChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjectsFromRef()
        built shouldContainAll setOf(
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("buildChangedProjectsFromRef reports no changes when nothing modified since ref") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val headSha = project.getLastCommitSha()

        // when: compare HEAD against itself — no changes
        val result = project.runTaskWithProperties(
            "buildChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to headSha)
        )

        // then
        result.task(":buildChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No projects have changed - nothing to build"
    }

    test("buildChangedProjectsFromRef succeeds without running printChangedProjectsFromRef") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()

        project.appendToFile(Files.MODULE1_SOURCE, "\n// Changed")
        project.commitAll("Change module1")

        // when
        val result = project.runTaskWithProperties(
            "buildChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then: buildChangedProjectsFromRef runs independently
        result.task(":buildChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":printChangedProjectsFromRef") shouldBe null
    }
})
