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
 * Functional tests for the buildChanged task.
 */
class BuildChangedFunctionalTest : FunSpec({
    val testProjectListener = extension(TestProjectListener())

    test("buildChanged task builds only affected projects") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Change common-lib")

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val builtProjects = result.extractBuiltProjects()
        builtProjects shouldContainAll setOf(
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("buildChanged builds only affected apps when module changes") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.MODULE1_SOURCE, "\n// Modified")
        project.commitAll("Change module1")

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val builtProjects = result.extractBuiltProjects()
        builtProjects shouldContainAll setOf(Projects.MODULE1, Projects.APP1)
        builtProjects shouldNotContain Projects.APP2
    }

    test("buildChanged reports no changes when nothing modified") {
        // given
        val project = testProjectListener.createStandardProject()

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No projects have changed - nothing to build"
    }

    test("buildChanged handles multiple independent app changes") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.APP1_SOURCE, "\n// Modified A")
        project.appendToFile(Files.APP2_SOURCE, "\n// Modified B")
        project.commitAll("Change both apps")

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val builtProjects = result.extractBuiltProjects()
        builtProjects shouldContainAll setOf(Projects.APP1, Projects.APP2)
    }

    test("buildChanged succeeds without running printChanged") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.MODULE2_SOURCE, "\n// Changed")
        project.commitAll("Change module2")

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":printChanged") shouldBe null
    }

    test("buildChanged builds only leaf project when changed") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.APP2_SOURCE, "\n// App changed")
        project.commitAll("Change app2")

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val builtProjects = result.extractBuiltProjects()
        builtProjects shouldContain Projects.APP2
        builtProjects shouldNotContain Projects.COMMON_LIB
        builtProjects shouldNotContain Projects.MODULE1
        builtProjects shouldNotContain Projects.MODULE2
        builtProjects shouldNotContain Projects.APP1
    }

    test("buildChanged builds projects affected by BOM changes") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.PLATFORM_BUILD, "\n// BOM version bump")
        project.commitAll("Bump BOM version")

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
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
        val result = project.runTask("printChanged")

        // then
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
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
        val result = project.runTask("printChanged")

        // then
        result.task(":printChanged")?.outcome shouldBe TaskOutcome.SUCCESS
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

    test("buildChanged logs the change detection baseline") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Modify app2")

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Change detection baseline: origin/main ("
    }

    // --- origin/main baseline scenarios ---

    test("buildChanged uses origin/main as baseline and detects direct change") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Modify app2")

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP2
        built shouldNotContain Projects.COMMON_LIB
        built shouldNotContain Projects.MODULE1
    }

    test("buildChanged uses origin/main as baseline and detects transitive dependents") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Modify common-lib")

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjects()
        built shouldContainAll setOf(
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("buildChanged reports no changes when nothing changed since origin/main") {
        // given: origin/main at HEAD, no changes
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        // when
        val result = project.runTask("buildChanged")

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No projects have changed - nothing to build"
    }

    test("buildChanged ignores last-successful-build tag and uses origin/main") {
        // given: tag exists but buildChanged should use origin/main instead
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
        val result = project.runTask("buildChanged")

        // then: uses origin/main (initial commit), so both changes are detected
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP2
        built shouldContain Projects.MODULE1
    }

    test("buildChanged treats all projects as changed when no baseline is available") {
        // given: project without remote and no tag
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )

        // when
        val result = project.runTask("buildChanged")

        // then: no origin/main — no baseline exists
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "no baseline"
        result.output shouldContain "Change detection baseline: none"
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP1
        built shouldContain Projects.APP2
        built shouldContain Projects.COMMON_LIB
    }

    test("buildChanged does not update the last-successful-build tag") {
        // given: tag created by createAndInitialize, make a change, run buildChanged
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )
        val tagCommitBefore = project.getLastCommitSha()

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Change app2")

        // when
        val result = project.runTask("buildChanged")

        // then: tag should still point at the original commit
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP2

        // Verify tag was NOT moved by checking it still resolves to the original commit
        val tagCommitAfter = project.getLastCommitSha("monorepo/last-successful-build")
        tagCommitAfter shouldBe tagCommitBefore
    }

    // ─────────────────────────────────────────────────────────────
    // Remote branch fetch (CI scenario)
    // ─────────────────────────────────────────────────────────────

    test("buildChanged fetches origin/main when not available locally") {
        // given: project with remote, but remote-tracking ref deleted (simulates CI checkout)
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Change app2")

        // Delete the remote-tracking ref to simulate a CI checkout that only fetches the build ref
        project.executeGitCommand("update-ref", "-d", "refs/remotes/origin/main")

        // when
        val result = project.runTask("buildChanged")

        // then: plugin fetches origin/main and detects the change
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Change detection baseline: origin/main"
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP2
    }

    // ─────────────────────────────────────────────────────────────
    // monorepo.targetBranch property scenarios
    // ─────────────────────────────────────────────────────────────

    test("buildChanged uses monorepo.targetBranch as baseline when set") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        // Create a release branch at the current commit
        project.executeGitCommand("branch", "release/v1.0.x")
        project.executeGitCommand("push", "origin", "release/v1.0.x")

        // Make changes on main after the branch point
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Change on main")
        project.commitAll("Change common-lib on main")

        project.appendToFile(Files.MODULE1_SOURCE, "\n// Feature change")
        project.commitAll("Change module1")

        // when: compare against the release branch, not origin/main
        val result = project.runTaskWithProperties(
            "buildChanged",
            properties = mapOf("monorepo.targetBranch" to "release/v1.0.x")
        )

        // then: both changes since the branch point are detected
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Change detection baseline: origin/release/v1.0.x"
        val built = result.extractBuiltProjects()
        built shouldContainAll setOf(
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("buildChanged with monorepo.targetBranch overrides origin/main") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Change app2")

        // when: explicitly pass main — same as default but exercises the property path
        val result = project.runTaskWithProperties(
            "buildChanged",
            properties = mapOf("monorepo.targetBranch" to "main")
        )

        // then: same behaviour as without the property
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Change detection baseline: origin/main"
        val built = result.extractBuiltProjects()
        built shouldContain Projects.APP2
        built shouldNotContain Projects.COMMON_LIB
    }

    test("buildChanged fetches target branch when not available locally") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        // Create and push a release branch
        project.executeGitCommand("branch", "release/v1.0.x")
        project.executeGitCommand("push", "origin", "release/v1.0.x")

        // Delete the local remote-tracking ref to simulate CI
        project.executeGitCommand("update-ref", "-d", "refs/remotes/origin/release/v1.0.x")

        project.appendToFile(Files.MODULE2_SOURCE, "\n// Feature change")
        project.commitAll("Change module2")

        // when
        val result = project.runTaskWithProperties(
            "buildChanged",
            properties = mapOf("monorepo.targetBranch" to "release/v1.0.x")
        )

        // then: plugin fetches the branch and resolves successfully
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Change detection baseline: origin/release/v1.0.x"
        val built = result.extractBuiltProjects()
        built shouldContain Projects.MODULE2
        built shouldContain Projects.APP2
    }

    test("buildChanged treats all as changed when target branch does not exist") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        // when
        val result = project.runTaskWithProperties(
            "buildChanged",
            properties = mapOf("monorepo.targetBranch" to "nonexistent-branch")
        )

        // then
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "from -Pmonorepo.targetBranch"
        result.output shouldContain "not available"
        result.output shouldContain "Change detection baseline: none"
        val built = result.extractBuiltProjects()
        built shouldContainAll setOf(
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("buildChanged with monorepo.targetBranch detects changes relative to release branch") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        // Push initial state, then create release branch
        project.executeGitCommand("branch", "release/v1.0.x")
        project.executeGitCommand("push", "origin", "release/v1.0.x")

        // Switch to a feature branch and change only module1
        project.executeGitCommand("checkout", "-b", "feature/add-widget")
        project.appendToFile(Files.MODULE1_SOURCE, "\n// Widget feature")
        project.commitAll("Add widget to module1")

        // when: compare against the release branch
        val result = project.runTaskWithProperties(
            "buildChanged",
            properties = mapOf("monorepo.targetBranch" to "release/v1.0.x")
        )

        // then: only module1 and its dependents are affected
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        val built = result.extractBuiltProjects()
        built shouldContainAll setOf(Projects.MODULE1, Projects.APP1)
        built shouldNotContain Projects.COMMON_LIB
        built shouldNotContain Projects.MODULE2
        built shouldNotContain Projects.APP2
    }

    // ─────────────────────────────────────────────────────────────
    // Git command failure propagation
    // ─────────────────────────────────────────────────────────────

    test("buildChanged fails when git state is corrupted") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = true
        )

        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Change app2")

        // Corrupt HEAD so all git commands fail
        java.io.File(project.projectDir, ".git/HEAD").writeText("garbage")

        // when
        val result = project.runTaskAndFail("buildChanged")

        // then
        result.output shouldContain "Failed to compute changed project metadata"
    }
})
