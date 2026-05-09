package io.github.doughawley.monorepo.release.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

class CreateDevBranchFunctionalTest : FunSpec({

    val testListener = extension(ReleaseTestProjectListener())

    test("creates dev branch and pushes it to remote") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTask(
            ":app:createDevBranch",
            properties = mapOf("dev.branch.name" to "feature-auth")
        )

        // then
        result.task(":app:createDevBranch")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "dev/app/feature-auth"
        project.localBranches() shouldContain "dev/app/feature-auth"
    }

    test("fails with clear message when dev.branch.name not provided") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTaskAndFail(":app:createDevBranch")

        // then
        result.output shouldContain "-Pdev.branch.name"
    }

    test("fails with clear message when dev.branch.name is empty") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTaskAndFail(
            ":app:createDevBranch",
            properties = mapOf("dev.branch.name" to "")
        )

        // then
        result.output shouldContain "dev.branch.name"
    }

    test("fails with clear message when branch name contains invalid characters") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTaskAndFail(
            ":app:createDevBranch",
            properties = mapOf("dev.branch.name" to "has space")
        )

        // then
        result.output shouldContain "Invalid dev branch name"
    }

    test("fails when dev branch already exists on remote") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("dev/app/feature-auth")
        pushBranch(project, "dev/app/feature-auth")
        project.checkoutBranch("main")

        // when
        val result = project.runTaskAndFail(
            ":app:createDevBranch",
            properties = mapOf("dev.branch.name" to "feature-auth")
        )

        // then
        result.output shouldContain "already exists on the remote"
    }

    test("fails when dev branch already exists locally") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("dev/app/feature-auth")
        project.checkoutBranch("main")

        // when
        val result = project.runTaskAndFail(
            ":app:createDevBranch",
            properties = mapOf("dev.branch.name" to "feature-auth")
        )

        // then
        result.output shouldContain "already exists locally"
    }

    test("works with custom tagPrefix") {
        // given
        val projectDir = testListener.getTestProjectDir()
        val project = StandardReleaseTestProject.create(projectDir)
        // Override app's build.gradle.kts to use custom tagPrefix
        java.io.File(projectDir, "app/build.gradle.kts").writeText(
            """
            monorepoProject {
                release {
                    enabled = true
                    tagPrefix = "my-custom-app"
                }
            }

            tasks.register("build") {
                doLast {
                    val libsDir = layout.buildDirectory.dir("libs").get().asFile
                    libsDir.mkdirs()
                    java.io.File(libsDir, "${'$'}{project.name}.jar").writeText("built artifact")
                }
            }
            """.trimIndent()
        )
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when
        val result = project.runTask(
            ":app:createDevBranch",
            properties = mapOf("dev.branch.name" to "feature-auth")
        )

        // then
        result.task(":app:createDevBranch")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "dev/my-custom-app/feature-auth"
    }

    test("fails when release is not enabled") {
        // given
        val projectDir = testListener.getTestProjectDir()
        val project = StandardReleaseTestProject.create(projectDir)
        // Override app's build.gradle.kts to disable release
        java.io.File(projectDir, "app/build.gradle.kts").writeText(
            """
            monorepoProject {
                release {
                    enabled = false
                }
            }

            tasks.register("build") {
                doLast {
                    val libsDir = layout.buildDirectory.dir("libs").get().asFile
                    libsDir.mkdirs()
                    java.io.File(libsDir, "${'$'}{project.name}.jar").writeText("built artifact")
                }
            }
            """.trimIndent()
        )
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when
        val result = project.runTaskAndFail(
            ":app:createDevBranch",
            properties = mapOf("dev.branch.name" to "feature-auth")
        )

        // then
        result.output shouldContain "Release is not enabled"
    }

    test("fails when branch name starts with a dash") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTaskAndFail(
            ":app:createDevBranch",
            properties = mapOf("dev.branch.name" to "-bad-name")
        )

        // then
        result.output shouldContain "Invalid dev branch name"
    }
})

private fun pushBranch(project: ReleaseTestProject, branch: String) {
    val process = ProcessBuilder("git", "push", "origin", branch)
        .directory(project.projectDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        val error = process.errorStream.bufferedReader().readText()
        throw RuntimeException("Failed to push branch $branch: $error")
    }
}
