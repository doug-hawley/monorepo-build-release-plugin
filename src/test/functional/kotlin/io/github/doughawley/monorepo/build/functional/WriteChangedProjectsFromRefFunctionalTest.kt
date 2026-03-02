package io.github.doughawley.monorepo.build.functional

import io.github.doughawley.monorepo.build.functional.StandardTestProject.Files
import io.github.doughawley.monorepo.build.functional.StandardTestProject.Projects
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

/**
 * Functional tests for the writeChangedProjectsFromRef task.
 */
class WriteChangedProjectsFromRefFunctionalTest : FunSpec({
    val testProjectListener = listener(TestProjectListener())

    test("writeChangedProjectsFromRef fails with helpful error when no commitRef provided") {
        // given
        val project = testProjectListener.createStandardProject()

        // when
        val result = project.runTaskAndFail("writeChangedProjectsFromRef")

        // then
        result.output shouldContain "commitRef"
        result.output shouldContain "monorepo.commitRef"
    }

    test("writeChangedProjectsFromRef writes changed project paths to default output file") {
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
            "writeChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then
        result.task(":writeChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        val outputFile = File(project.projectDir, "build/monorepo/changed-projects.txt")
        outputFile.exists() shouldBe true
        val lines = outputFile.readLines().filter { it.isNotEmpty() }
        lines shouldContainAll setOf(
            Projects.COMMON_LIB,
            Projects.MODULE1,
            Projects.MODULE2,
            Projects.APP1,
            Projects.APP2
        )
    }

    test("writeChangedProjectsFromRef writes empty file when no projects have changed") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val headSha = project.getLastCommitSha()

        // when: compare HEAD against itself — no changes
        val result = project.runTaskWithProperties(
            "writeChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to headSha)
        )

        // then: task succeeds and output file exists but is empty
        result.task(":writeChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        val outputFile = File(project.projectDir, "build/monorepo/changed-projects.txt")
        outputFile.exists() shouldBe true
        outputFile.readText() shouldBe ""
    }

    test("writeChangedProjectsFromRef output path can be overridden at runtime") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()

        project.appendToFile(Files.APP1_SOURCE, "\n// Modified")
        project.commitAll("Modify app1")

        val customPath = "ci/output/changed.txt"

        // when
        val result = project.runTaskWithProperties(
            "writeChangedProjectsFromRef",
            properties = mapOf(
                "monorepo.commitRef" to initialSha,
                "monorepo.outputFile" to customPath
            )
        )

        // then: file is at the custom path, not the default
        result.task(":writeChangedProjectsFromRef")?.outcome shouldBe TaskOutcome.SUCCESS
        val defaultFile = File(project.projectDir, "build/monorepo/changed-projects.txt")
        val customFile = File(project.projectDir, customPath)
        defaultFile.exists() shouldBe false
        customFile.exists() shouldBe true
        customFile.readLines().filter { it.isNotEmpty() } shouldContain Projects.APP1
    }

    test("writeChangedProjectsFromRef output is one bare project path per line with no decoration") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()

        project.appendToFile(Files.MODULE2_SOURCE, "\n// Modified")
        project.commitAll("Modify module2")

        // when
        project.runTaskWithProperties(
            "writeChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then: each line is a bare Gradle project path — no headers, annotations, or indentation
        val lines = File(project.projectDir, "build/monorepo/changed-projects.txt")
            .readLines()
            .filter { it.isNotEmpty() }
        lines.forEach { line ->
            line shouldStartWith ":"
            line shouldNotContain " "
            line shouldNotContain "("
        }
    }

    test("writeChangedProjectsFromRef only lists directly and transitively changed projects") {
        // given
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )
        val initialSha = project.getLastCommitSha()

        // Change only module1 — only app1 depends on it, not app2
        project.appendToFile(Files.MODULE1_SOURCE, "\n// Modified")
        project.commitAll("Modify module1")

        // when
        project.runTaskWithProperties(
            "writeChangedProjectsFromRef",
            properties = mapOf("monorepo.commitRef" to initialSha)
        )

        // then
        val lines = File(project.projectDir, "build/monorepo/changed-projects.txt")
            .readLines()
            .filter { it.isNotEmpty() }
        lines shouldContain Projects.MODULE1
        lines shouldContain Projects.APP1
        lines shouldNotContain Projects.APP2
        lines shouldNotContain Projects.COMMON_LIB
    }
})
