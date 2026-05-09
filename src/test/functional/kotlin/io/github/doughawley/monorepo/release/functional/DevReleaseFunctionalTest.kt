package io.github.doughawley.monorepo.release.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class DevReleaseFunctionalTest : FunSpec({

    val testListener = extension(ReleaseTestProjectListener())

    test("creates first dev tag when no prior dev tags exist") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("dev/app/feature-auth")
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// changed")
        project.commitAll("dev work")
        pushBranch(project, "dev/app/feature-auth")

        // when
        val result = project.runTask(":app:devRelease")

        // then
        result.task(":app:devRelease")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "dev/app/feature-auth/1"
    }

    test("increments tag number when prior dev tags exist") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("dev/app/feature-auth")
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// changed")
        project.commitAll("dev work")
        pushBranch(project, "dev/app/feature-auth")

        // Create prior dev tags on remote
        project.createTag("dev/app/feature-auth/1")
        project.pushTag("dev/app/feature-auth/1")
        project.createTag("dev/app/feature-auth/2")
        project.pushTag("dev/app/feature-auth/2")

        // when
        val result = project.runTask(":app:devRelease")

        // then
        result.task(":app:devRelease")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "dev/app/feature-auth/3"
    }

    test("writes correct version to release-version.txt") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("dev/app/feature-auth")
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// changed")
        project.commitAll("dev work")
        pushBranch(project, "dev/app/feature-auth")

        // when
        project.runTask(":app:devRelease")

        // then
        project.releaseVersionFile() shouldBe "feature-auth.1"
    }

    test("depends on build task") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("dev/app/feature-auth")
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// changed")
        project.commitAll("dev work")
        pushBranch(project, "dev/app/feature-auth")

        // when
        val result = project.runTask(":app:devRelease")

        // then
        result.task(":app:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:devRelease")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    test("fails when not on a dev branch") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:devRelease")

        // then
        result.output shouldContain "Dev releases must be made from a dev branch"
    }

    test("fails when on a dev branch for a different project") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("dev/other-project/feature-auth")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:devRelease")

        // then
        result.output shouldContain "not 'app'"
    }

    test("fails when working tree is dirty") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("dev/app/feature-auth")
        pushBranch(project, "dev/app/feature-auth")
        project.createFakeBuiltArtifact()
        // Dirty the working tree after build
        project.modifyFile("app/dirty.txt", "uncommitted change")

        // when
        val result = project.runTaskAndFail(":app:devRelease")

        // then
        result.output shouldContain "uncommitted changes"
    }

    test("fails when release is not enabled") {
        // given
        val projectDir = testListener.getTestProjectDir()
        val project = StandardReleaseTestProject.create(projectDir)
        // Override app's build.gradle.kts to disable release
        File(projectDir, "app/build.gradle.kts").writeText(
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
        project.createBranch("dev/app/feature-auth")
        pushBranch(project, "dev/app/feature-auth")

        // when
        val result = project.runTaskAndFail(":app:devRelease")

        // then
        result.output shouldContain "Release is not enabled"
    }

    test("fails when on detached HEAD") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()
        project.detachHead()

        // when
        val result = project.runTaskAndFail(":app:devRelease")

        // then
        result.output shouldContain "detached HEAD"
    }

    test("multiple dev releases increment correctly") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("dev/app/feature-auth")
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// v1")
        project.commitAll("dev work 1")
        pushBranch(project, "dev/app/feature-auth")

        // when — first release
        project.runTask(":app:devRelease")
        project.releaseVersionFile() shouldBe "feature-auth.1"

        // second release
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// v2")
        project.commitAll("dev work 2")
        project.runTask(":app:devRelease")
        project.releaseVersionFile() shouldBe "feature-auth.2"

        // third release
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// v3")
        project.commitAll("dev work 3")
        project.runTask(":app:devRelease")

        // then
        project.releaseVersionFile() shouldBe "feature-auth.3"
        project.remoteTags() shouldContain "dev/app/feature-auth/1"
        project.remoteTags() shouldContain "dev/app/feature-auth/2"
        project.remoteTags() shouldContain "dev/app/feature-auth/3"
    }

    test("works with custom tagPrefix") {
        // given
        val projectDir = testListener.getTestProjectDir()
        val project = StandardReleaseTestProject.create(projectDir)
        File(projectDir, "app/build.gradle.kts").writeText(
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
        project.createBranch("dev/my-custom-app/feature-auth")
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// changed")
        project.commitAll("dev work")
        pushBranch(project, "dev/my-custom-app/feature-auth")

        // when
        val result = project.runTask(":app:devRelease")

        // then
        result.task(":app:devRelease")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "dev/my-custom-app/feature-auth/1"
        project.releaseVersionFile() shouldBe "feature-auth.1"
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
