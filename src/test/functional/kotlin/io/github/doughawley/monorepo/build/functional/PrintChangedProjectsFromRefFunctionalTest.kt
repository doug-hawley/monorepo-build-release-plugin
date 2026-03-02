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
 * Functional tests for the printChangedProjectsFromRef task.
 */
class PrintChangedProjectsFromRefFunctionalTest : FunSpec({
    val testProjectListener = listener(TestProjectListener())

    test("printChangedProjectsFromRef fails with helpful error when no commitRef provided") {
        // given
        val project = testProjectListener.createStandardProject()

        // when: run without commitRef
        val result = project.runTaskAndFail("printChangedProjectsFromRef")

        // then: error message mentions how to supply commitRef
        result.output shouldContain "commitRef"
        result.output shouldContain "monorepo.commitRef"
    }

    test("printChangedProjectsFromRef detects directly changed project using commit SHA") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()

        // Make a change to common-lib and commit
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Modify common-lib")

        // when
        val result = project.runTaskWithProperties(
            "printChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then
        result.task(":printChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        val changed = result.extractChangedProjects()
        changed shouldContain Projects.COMMON_LIB
    }

    test("printChangedProjectsFromRef detects transitive dependents") {
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
            "printChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then: common-lib plus all projects that depend on it are affected
        result.task(":printChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        val changed = result.extractChangedProjects()
        changed shouldContainAll setOf(
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("printChangedProjectsFromRef only shows projects changed since the given ref") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )

        // Change common-lib and commit
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// First change")
        project.commitAll("Modify common-lib")
        val afterFirstChangeSha = project.getLastCommitSha()

        // Change only module1 in a second commit
        project.appendToFile(Files.MODULE1_SOURCE, "\n// Second change")
        project.commitAll("Modify module1")

        // when: compare against the SHA after the first change — only module1 changes are newer
        val result = project.runTaskWithProperties(
            "printChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to afterFirstChangeSha)
        )

        // then: only module1 and its dependents (app1) are affected, not common-lib
        result.task(":printChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        val changed = result.extractChangedProjects()
        changed shouldContain Projects.MODULE1
        changed shouldContain Projects.APP1
        changed shouldNotContain Projects.COMMON_LIB
    }

    test("printChangedProjectsFromRef property overrides DSL commitRef value") {
        // given: build file sets commitRef to an invalid value in DSL
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()

        // Rewrite the build file to add an invalid DSL commitRef
        project.modifyFile(
            "build.gradle.kts",
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            monorepo {
                build {
                    baseBranch = "HEAD"
                    includeUntracked = true
                    commitRef = "this-ref-does-not-exist"
                }
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
            }
            """.trimIndent()
        )
        project.commitAll("Add invalid DSL commitRef")

        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Modify common-lib")

        // when: valid SHA passed via property overrides the invalid DSL value
        val result = project.runTaskWithProperties(
            "printChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then: property wins and task succeeds, detecting changes
        result.task(":printChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        val changed = result.extractChangedProjects()
        changed shouldContain Projects.COMMON_LIB
    }

    test("printChangedProjectsFromRef does not pick up untracked or staged-only changes") {
        // given: two-dot diff skips working-tree and staged files
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()

        // Stage a change but do not commit
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Staged but not committed")
        project.stageFile(Files.COMMON_LIB_SOURCE)

        // Create an untracked file
        project.createNewFile(
            "common-lib/src/main/kotlin/com/example/Untracked.kt",
            "class Untracked"
        )

        // when
        val result = project.runTaskWithProperties(
            "printChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then: no projects reported as changed (staged/untracked changes are invisible to two-dot diff)
        result.task(":printChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No projects have changed"
    }

    test("printChangedProjectsFromRef fails with helpful error when commitRef does not exist") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )

        // when: pass a SHA that does not exist
        val result = project.runTaskWithPropertiesAndFail(
            "printChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
        )

        // then
        result.output shouldContain "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        result.output shouldContain "does not exist"
    }

    test("printChangedProjectsFromRef fails when both branch-mode and ref-mode tasks are invoked together") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )

        // when
        val result = project.runTaskWithPropertiesAndFail(
            "printChangedProjectsFromBranch",
            "printChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to "HEAD")
        )

        // then
        result.output shouldContain "branch-mode"
        result.output shouldContain "ref-mode"
    }
})
