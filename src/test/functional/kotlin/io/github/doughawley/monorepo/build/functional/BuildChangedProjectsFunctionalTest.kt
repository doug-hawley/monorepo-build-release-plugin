package io.github.doughawley.monorepo.build.functional

import io.github.doughawley.monorepo.build.functional.StandardTestProject.Files
import io.github.doughawley.monorepo.build.functional.StandardTestProject.Projects
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

/**
 * Functional tests for the buildChangedProjects task.
 */
class BuildChangedProjectsFunctionalTest : FunSpec({
    val testProjectListener = listener(TestProjectListener())

    test("buildChangedProjects task builds only affected projects") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Change common-lib")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val builtProjects = result.extractBuiltProjects()
        builtProjects shouldContainAll setOf(
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("buildChangedProjects builds only affected apps when module changes") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.MODULE1_SOURCE, "\n// Modified")
        project.commitAll("Change module1")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val builtProjects = result.extractBuiltProjects()
        builtProjects shouldContainAll setOf(Projects.MODULE1, Projects.APP1)
        builtProjects shouldNotContain Projects.APP2
    }

    test("buildChangedProjects reports no changes when nothing modified") {
        // given
        val project = testProjectListener.createStandardProject()

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No projects have changed - nothing to build"
    }

    test("buildChangedProjects handles multiple independent app changes") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.APP1_SOURCE, "\n// Modified A")
        project.appendToFile(Files.APP2_SOURCE, "\n// Modified B")
        project.commitAll("Change both apps")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val builtProjects = result.extractBuiltProjects()
        builtProjects shouldContainAll setOf(Projects.APP1, Projects.APP2)
    }

    test("buildChangedProjects succeeds without running printChangedProjects") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.MODULE2_SOURCE, "\n// Changed")
        project.commitAll("Change module2")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":printChangedProjects") shouldBe null
    }

    test("buildChangedProjects builds only leaf project when changed") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.APP2_SOURCE, "\n// App changed")
        project.commitAll("Change app2")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val builtProjects = result.extractBuiltProjects()
        builtProjects shouldContain Projects.APP2
        builtProjects shouldNotContain Projects.COMMON_LIB
        builtProjects shouldNotContain Projects.MODULE1
        builtProjects shouldNotContain Projects.MODULE2
        builtProjects shouldNotContain Projects.APP1
    }

    test("buildChangedProjects builds projects affected by BOM changes") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.PLATFORM_BUILD, "\n// BOM version bump")
        project.commitAll("Bump BOM version")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val builtProjects = result.extractBuiltProjects()
        builtProjects shouldContainAll setOf(
            Projects.PLATFORM,
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("plugin detects BOM changes and marks all dependent projects as changed") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.PLATFORM_BUILD, "\n// BOM version update")
        project.commitAll("Update BOM")

        // when
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val changedProjects = result.extractChangedProjects()
        changedProjects shouldHaveSize 6
        changedProjects shouldContainAll setOf(
            Projects.PLATFORM,
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
        val directlyChanged = result.extractDirectlyChangedProjects()
        directlyChanged shouldHaveSize 1
        directlyChanged shouldContainAll setOf(Projects.PLATFORM)
    }

    test("plugin detects changes when both BOM and common-lib change") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.PLATFORM_BUILD, "\n// BOM update")
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Common-lib change")
        project.commitAll("Update BOM and common-lib")

        // when
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val changedProjects = result.extractChangedProjects()
        changedProjects shouldHaveSize 6
        changedProjects shouldContainAll setOf(
            Projects.PLATFORM,
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    // --- Tag-based scenarios (formerly ref-mode) ---

    test("buildChangedProjects builds directly changed project using tag as base ref") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()
        project.executeGitCommand("tag", "monorepo/last-successful-build", initialSha)

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Modify app2")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP2
        built shouldNotContain Projects.COMMON_LIB
        built shouldNotContain Projects.MODULE1
    }

    test("buildChangedProjects builds all projects affected by common-lib change using tag") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()
        project.executeGitCommand("tag", "monorepo/last-successful-build", initialSha)

        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Modify common-lib")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjects()
        built shouldContainAll setOf(
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("buildChangedProjects reports no changes when tag points at HEAD") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        project.executeGitCommand("tag", "monorepo/last-successful-build")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No projects have changed - nothing to build"
    }
})
