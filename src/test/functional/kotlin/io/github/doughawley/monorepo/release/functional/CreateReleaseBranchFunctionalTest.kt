package io.github.doughawley.monorepo.release.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class CreateReleaseBranchFunctionalTest : FunSpec({

    val testListener = listener(ReleaseTestProjectListener())

    // ─────────────────────────────────────────────────────────────
    // Core versioning (branch naming)
    // ─────────────────────────────────────────────────────────────

    test("no prior tag creates first release branch as v0.1.x") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTask(":app:createReleaseBranch")

        // then: release branch created; no tag created
        result.task(":app:createReleaseBranch")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteTags() shouldBe emptyList()
    }

    test("prior v0.1.0 tag with default minor scope creates v0.2.x branch") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")

        // when
        val result = project.runTask(":app:createReleaseBranch")

        // then
        result.task(":app:createReleaseBranch")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.2.x"
        project.remoteTags().filter { it.startsWith("release/app/v0.2") } shouldBe emptyList()
    }

    test("multiple version lines scans global latest for next minor branch") {
        // given: v0.1.2 and v0.2.0 exist — global latest is v0.2.0 → next minor is v0.3.x
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.createTag("release/app/v0.1.2")
        project.createTag("release/app/v0.2.0")
        project.pushTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.2")
        project.pushTag("release/app/v0.2.0")

        // when
        val result = project.runTask(":app:createReleaseBranch")

        // then
        result.task(":app:createReleaseBranch")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.3.x"
    }

    test("primaryBranchScope=major with prior v0.1.0 creates v1.0.x branch") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(
            testListener.getTestProjectDir(),
            primaryBranchScope = "major"
        )
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")

        // when
        val result = project.runTask(":app:createReleaseBranch")

        // then
        result.task(":app:createReleaseBranch")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v1.0.x"
        project.remoteTags().filter { it.startsWith("release/app/v1") } shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Branch name format
    // ─────────────────────────────────────────────────────────────

    test("custom globalTagPrefix overrides default release prefix in branch name") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(
            testListener.getTestProjectDir(),
            globalTagPrefix = "publish"
        )

        // when
        val result = project.runTask(":app:createReleaseBranch")

        // then
        result.task(":app:createReleaseBranch")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "publish/app/v0.1.x"
    }

    test("custom tagPrefix in monorepoProject { release { } } overrides path-derived value") {
        // given
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }
            """.trimIndent()
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":app")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")
        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.0.21"
            }
            monorepoProject {
                release {
                    enabled = true
                    tagPrefix = "my-custom-app"
                }
            }
            """.trimIndent()
        )

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when
        val result = project.runTask(":app:createReleaseBranch")

        // then
        result.task(":app:createReleaseBranch")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/my-custom-app/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Guardrails
    // ─────────────────────────────────────────────────────────────

    test("detached HEAD fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.detachHead()

        // when
        val result = project.runTaskAndFail(":app:createReleaseBranch")

        // then
        result.output shouldContain "Cannot create a release branch from a detached HEAD state. Check out a branch before releasing."
    }

    test("feature branch fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("feature/my-feature")

        // when
        val result = project.runTaskAndFail(":app:createReleaseBranch")

        // then: message names the offending branch and tells the user what to do
        result.output shouldContain "'feature/my-feature' is not a permitted release branch"
        result.output shouldContain "Switch to an allowed branch before running createReleaseBranch"
    }

    test("running on a release branch fails with clear message") {
        // given: createReleaseBranch is intended for the primary branch only
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("release/app/v0.1.x")

        // when
        val result = project.runTaskAndFail(":app:createReleaseBranch")

        // then
        result.output shouldContain "Cannot create a release branch from release branch 'release/app/v0.1.x'. createReleaseBranch is intended for the primary branch only."
    }

    test("primaryBranchScope=patch fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(
            testListener.getTestProjectDir(),
            primaryBranchScope = "patch"
        )

        // when
        val result = project.runTaskAndFail(":app:createReleaseBranch")

        // then
        result.output shouldContain "Cannot configure primaryBranchScope as 'patch'. Use 'minor' or 'major'."
    }

    test("subproject with enabled=false fails with clear message") {
        // given
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }
            """.trimIndent()
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":app")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")
        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.0.21"
            }
            monorepoProject {
                release {
                    enabled = false
                }
            }
            """.trimIndent()
        )

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when
        val result = project.runTaskAndFail(":app:createReleaseBranch")

        // then
        result.output shouldContain "Release is not enabled for :app. Set monorepoProject { release { enabled = true } } to opt in."
    }

    test("custom releaseBranchPatterns restricts valid branches") {
        // given: only 'develop' is configured as a valid release branch
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            monorepo {
                release {
                    releaseBranchPatterns = listOf("^develop${'$'}")
                }
            }
            """.trimIndent()
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":app")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")
        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.0.21"
            }
            monorepoProject {
                release {
                    enabled = true
                }
            }
            """.trimIndent()
        )

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when: on 'main' which is not in the custom patterns
        val result = project.runTaskAndFail(":app:createReleaseBranch")

        // then: message names the offending branch and shows the configured allowed patterns
        result.output shouldContain "'main' is not a permitted release branch"
        result.output shouldContain "Allowed patterns: ^develop$"
    }

    // ─────────────────────────────────────────────────────────────
    // Push and rollback
    // ─────────────────────────────────────────────────────────────

    test("push fails when no remote configured — local branch is deleted, task fails cleanly") {
        // given: project without remote
        val projectDir = testListener.getTestProjectDir()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }
            """.trimIndent()
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":app")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")
        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.0.21"
            }
            monorepoProject {
                release {
                    enabled = true
                }
            }
            """.trimIndent()
        )

        val noRemote = File(projectDir.parentFile, "${projectDir.name}-no-remote.git")
        val project = ReleaseTestProject(projectDir, noRemote)
        fun runGit(vararg cmd: String) {
            ProcessBuilder(*cmd).directory(projectDir).start().waitFor()
        }
        runGit("git", "init")
        runGit("git", "config", "user.email", "test@example.com")
        runGit("git", "config", "user.name", "Test User")
        runGit("git", "checkout", "-b", "main")
        runGit("git", "add", ".")
        runGit("git", "commit", "-m", "Initial commit")

        // when
        val result = project.runTaskAndFail(":app:createReleaseBranch")

        // then: push failed, local branch rolled back
        result.output shouldContain "Push failed, rolling back local branch"
        project.localBranches() shouldNotContain "release/app/v0.1.x"
    }
})
