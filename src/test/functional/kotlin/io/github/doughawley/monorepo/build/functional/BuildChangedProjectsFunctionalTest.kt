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
    val testProjectListener = extension(TestProjectListener())

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

    test("buildChangedProjects logs the change detection baseline") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Modify app2")

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Change detection baseline: origin/main ("
    }

    // --- origin/main baseline scenarios ---

    test("buildChangedProjects uses origin/main as baseline and detects direct change") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

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

    test("buildChangedProjects uses origin/main as baseline and detects transitive dependents") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

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

    test("buildChangedProjects reports no changes when nothing changed since origin/main") {
        // given: origin/main at HEAD, no changes
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        // when
        val result = project.runTask("buildChangedProjects")

        // then
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No projects have changed - nothing to build"
    }

    test("buildChangedProjects ignores last-successful-build tag and uses origin/main") {
        // given: tag exists but buildChangedProjects should use origin/main instead
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Change app2")

        // Move the tag forward — if the plugin were using the tag, no changes would be detected
        project.executeGitCommand("tag", "-f", "monorepo/last-successful-build")

        // Make another change after the tag
        project.appendToFile(Files.MODULE1_SOURCE, "\n// Second change")
        project.commitAll("Change module1")

        // when
        val result = project.runTask("buildChangedProjects")

        // then: uses origin/main (initial commit), so both changes are detected
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP2
        built shouldContain Projects.MODULE1
    }

    test("buildChangedProjects treats all projects as changed when no baseline is available") {
        // given: project without remote and no tag
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )

        // when
        val result = project.runTask("buildChangedProjects")

        // then: no origin/main — no baseline exists
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "no baseline"
        result.output shouldContain "Change detection baseline: none"
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP1
        built shouldContain Projects.APP2
        built shouldContain Projects.COMMON_LIB
    }

    test("buildChangedProjects does not update the last-successful-build tag") {
        // given: tag created by createAndInitialize, make a change, run buildChangedProjects
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )
        val tagCommitBefore = project.getLastCommitSha()

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Change app2")

        // when
        val result = project.runTask("buildChangedProjects")

        // then: tag should still point at the original commit
        result.task(":buildChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP2

        // Verify tag was NOT moved by checking it still resolves to the original commit
        val tagCommitAfter = project.getLastCommitSha("monorepo/last-successful-build")
        tagCommitAfter shouldBe tagCommitBefore
    }
})
