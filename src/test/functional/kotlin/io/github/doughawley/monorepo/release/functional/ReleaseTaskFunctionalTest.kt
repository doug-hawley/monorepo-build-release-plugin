package io.github.doughawley.monorepo.release.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class ReleaseTaskFunctionalTest : FunSpec({

    val testListener = listener(ReleaseTestProjectListener())

    // ─────────────────────────────────────────────────────────────
    // Core versioning
    // ─────────────────────────────────────────────────────────────

    test("no prior tag creates first release as v0.1.0 with tag and release branch") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.localTags() shouldContain "release/app/v0.1.0"
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.remoteBranches() shouldContain "release/app/v0.1.x"
    }

    test("prior v0.1.0 tag with default minor scope creates v0.2.0") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.2.0"
    }

    test("-Prelease.scope=major bumps major version") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release", properties = mapOf("release.scope" to "major"))

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v1.0.0"
    }

    test("-Prelease.scope=minor bumps minor version") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release", properties = mapOf("release.scope" to "minor"))

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.2.0"
    }

    test("multiple version lines on main scans global latest for next minor") {
        // given: v0.1.2 and v0.2.0 exist — global latest is v0.2.0 → next minor is v0.3.0
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.createTag("release/app/v0.1.2")
        project.createTag("release/app/v0.2.0")
        project.pushTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.2")
        project.pushTag("release/app/v0.2.0")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.3.0"
    }

    test("on release branch scans only version line and creates patch") {
        // given: v0.1.0, v0.1.1, and v0.2.0 exist; on release/app/v0.1.x → should produce v0.1.2
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.createTag("release/app/v0.1.1")
        project.createTag("release/app/v0.2.0")
        project.pushTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.1")
        project.pushTag("release/app/v0.2.0")

        // Create and switch to a release branch (push to remote so it exists there)
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.2"
        // Should NOT create another release branch
        project.remoteBranches() shouldNotContain "release/app/v0.1.x.x"
    }

    test("release branch ignores DSL primaryBranchScope and always uses patch") {
        // given: primaryBranchScope=major in DSL should have no effect on a release branch
        val project = StandardReleaseTestProject.createAndInitialize(
            testListener.getTestProjectDir(),
            primaryBranchScope = "major"
        )
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when: no scope flag provided
        val result = project.runTask(":app:release")

        // then: patch applied, not major
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.1"
    }

    // ─────────────────────────────────────────────────────────────
    // Tag format
    // ─────────────────────────────────────────────────────────────

    test("single-level path :app produces tag release/app/v...") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()

        // when
        project.runTask(":app:release")

        // then
        project.remoteTags().any { it.startsWith("release/app/v") } shouldBe true
    }

    test("custom globalTagPrefix overrides default release prefix") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir(), globalTagPrefix = "publish")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "publish/app/v0.1.0"
    }

    test("custom tagPrefix in monorepoProject { release { } } overrides path-derived value") {
        // given: set up project with custom tagPrefix
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
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/my-custom-app/v0.1.0"
    }

    test("nested subproject path produces hyphenated tag prefix") {
        // given: :services:auth → deriveProjectTagPrefix → "services-auth"
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
            include(":services:auth")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")

        val authDir = File(projectDir, "services/auth")
        authDir.mkdirs()
        File(authDir, "build.gradle.kts").writeText(
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
        File(projectDir, "services/auth/build/libs").mkdirs()
        File(projectDir, "services/auth/build/libs/auth.jar").writeText("fake jar content")

        // when
        val result = project.runTask(":services:auth:release")

        // then
        result.task(":services:auth:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/services-auth/v0.1.0"
    }

    // ─────────────────────────────────────────────────────────────
    // Guardrails
    // ─────────────────────────────────────────────────────────────

    test("uncommitted changes causes release to fail with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// modified content")

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Cannot release with uncommitted changes. Please commit or stash all changes before releasing."
    }

    test("staged but uncommitted changes causes release to fail") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()
        project.modifyFile("app/src/main/kotlin/com/example/App.kt", "// staged modification")
        project.stageFile("app/src/main/kotlin/com/example/App.kt")

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Cannot release with uncommitted changes. Please commit or stash all changes before releasing."
    }

    test("feature branch causes release to fail with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("feature/my-feature")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release")

        // then: message names the offending branch and tells the user which branches are allowed
        result.output shouldContain "Cannot release from branch 'feature/my-feature'"
        result.output shouldContain "Allowed patterns"
    }

    test("custom releaseBranchPatterns restricts valid branches") {
        // given: configure only 'develop' as a valid release branch
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
        project.createFakeBuiltArtifact()

        // when: on 'main' which is not in the custom patterns
        val result = project.runTaskAndFail(":app:release")

        // then: message names the offending branch and shows the configured allowed patterns
        result.output shouldContain "Cannot release from branch 'main'"
        result.output shouldContain "Allowed patterns: ^develop$"
    }

    test("tag already exists causes release to fail with clear message") {
        // given: v0.1.0 is the latest released version (on remote); v0.2.0 was created locally
        // (e.g. a previous failed release attempt left the local tag behind)
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        // Create v0.2.0 locally only (not pushed) — this is the "collision" tag
        project.createTag("release/app/v0.2.0")
        project.createFakeBuiltArtifact()

        // when: scanner finds v0.1.0 on remote → next = v0.2.0; tagExists finds local v0.2.0 → fail
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Tag 'release/app/v0.2.0' already exists. This version has already been released."
    }

    test("build outputs missing causes release to fail mentioning build task") {
        // given: no fake artifact created
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Project must be built before releasing — run :app:build first."
    }

    test("build outputs present allows release to proceed") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    // ─────────────────────────────────────────────────────────────
    // Scope enforcement
    // ─────────────────────────────────────────────────────────────

    test("main + -Prelease.scope=patch fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release", properties = mapOf("release.scope" to "patch"))

        // then
        result.output shouldContain "Cannot use scope 'patch' on the main branch. Use 'minor' or 'major' for new feature releases."
    }

    test("release branch with -Prelease.scope=minor fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release", properties = mapOf("release.scope" to "minor"))

        // then
        result.output shouldContain "Cannot use scope 'minor' on a release branch. Patch releases only — remove the -Prelease.scope flag or use 'patch'."
    }

    test("release branch with -Prelease.scope=major fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release", properties = mapOf("release.scope" to "major"))

        // then
        result.output shouldContain "Cannot use scope 'major' on a release branch. Patch releases only — remove the -Prelease.scope flag or use 'patch'."
    }

    test("release branch with no scope flag succeeds with PATCH") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.1"
    }

    test("invalid -Prelease.scope value fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release", properties = mapOf("release.scope" to "bogus"))

        // then
        result.output shouldContain "Invalid release.scope value: 'bogus'. Must be one of: major, minor, patch"
    }

    test("DSL primaryBranchScope = \"major\" bumps major version") {
        // given: prior v0.1.0 exists; major scope via DSL should produce v1.0.0
        val project = StandardReleaseTestProject.createAndInitialize(
            testListener.getTestProjectDir(),
            primaryBranchScope = "major"
        )
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v1.0.0"
    }

    test("DSL primaryBranchScope = \"patch\" on main fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(
            testListener.getTestProjectDir(),
            primaryBranchScope = "patch"
        )
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Cannot configure primaryBranchScope as 'patch' on the main branch. Use 'minor' or 'major'."
    }

    test("release branch accepts -Prelease.scope=patch and applies patch") {
        // given: on a release branch, explicit patch scope should be accepted (not rejected)
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release", properties = mapOf("release.scope" to "patch"))

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.1"
    }

    // ─────────────────────────────────────────────────────────────
    // Push and rollback
    // ─────────────────────────────────────────────────────────────

    test("push fails when no remote configured — local tag and branch are deleted, task fails cleanly") {
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

        // Initialize without remote
        val noRemote = File(projectDir.parentFile, "${projectDir.name}-no-remote.git")
        val project = ReleaseTestProject(projectDir, noRemote)
        // Manual init without remote
        fun runGit(vararg cmd: String) {
            ProcessBuilder(*cmd).directory(projectDir).start().waitFor()
        }
        runGit("git", "init")
        runGit("git", "config", "user.email", "test@example.com")
        runGit("git", "config", "user.name", "Test User")
        runGit("git", "checkout", "-b", "main")
        runGit("git", "add", ".")
        runGit("git", "commit", "-m", "Initial commit")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release")

        // then: push failed, local tag rolled back
        result.output shouldContain "Push failed, rolling back local changes"
        project.localTags() shouldNotContain "release/app/v0.1.0"
    }

    test("on release branch no release branch is created, only tag pushed") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        project.runTask(":app:release")

        // then: new release branch not created
        val remoteBranches = project.remoteBranches()
        remoteBranches shouldContain "release/app/v0.1.x"
        // Should not contain a new branch like release/app/v0.1.x.x
        remoteBranches.filter { it.startsWith("release/app/v0.1.") && it != "release/app/v0.1.x" } shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Version communication
    // ─────────────────────────────────────────────────────────────

    test("build/release-version.txt is written after successful release") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()

        // when
        project.runTask(":app:release")

        // then
        project.releaseVersionFile() shouldBe "0.1.0"
    }

    // ─────────────────────────────────────────────────────────────
    // postRelease hook
    // ─────────────────────────────────────────────────────────────

    test("postRelease task runs after successful release") {
        // given: wire a task to postRelease via finalizedBy in a custom subproject build
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
                }
            }
            tasks.named("postRelease") {
                doLast {
                    println("POST_RELEASE_RAN")
                }
            }
            """.trimIndent()
        )

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:postRelease")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "POST_RELEASE_RAN"
    }

    test("postRelease hook does not run if release task fails") {
        // given: release will fail because no build output
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
                }
            }
            tasks.named("postRelease") {
                doLast {
                    println("POST_RELEASE_RAN")
                }
            }
            """.trimIndent()
        )

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()
        // deliberately no createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldNotContain "POST_RELEASE_RAN"
    }

    // ─────────────────────────────────────────────────────────────
    // Opt-in model
    // ─────────────────────────────────────────────────────────────

    test("subproject without monorepoProject { release { } } has no release task") {
        // given: a project where no subproject has enabled opt-in
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
            include(":lib")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")
        val libDir = File(projectDir, "lib")
        libDir.mkdirs()
        // No monorepoProject { release { } } block at all
        File(libDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.0.21"
            }
            """.trimIndent()
        )

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when
        val result = project.runTaskAndFail(":lib:release")

        // then: task does not exist
        result.output shouldContain "release"
    }

    // ─────────────────────────────────────────────────────────────
    // New gap-fix tests
    // ─────────────────────────────────────────────────────────────

    test("detached HEAD fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.detachHead()
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Cannot release from a detached HEAD state. Check out a branch before releasing."
    }

    test("local release branch collision rolls back the local tag") {
        // given: create release/app/v0.1.x locally but do NOT push it
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("release/app/v0.1.x")
        project.checkoutBranch("main")
        project.createFakeBuiltArtifact()

        // when: release tries to create release/app/v0.1.x locally → fails → tag rolled back
        project.runTaskAndFail(":app:release")

        // then: local tag was rolled back by Bug 2 fix
        project.localTags() shouldNotContain "release/app/v0.1.0"
    }

    test("custom non-main primary branch with configured pattern creates release branch") {
        // given: trunk is configured as the allowed release branch
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            monorepo {
                release {
                    releaseBranchPatterns = listOf("^trunk${'$'}")
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

        remoteDir.mkdirs()
        fun runGit(vararg cmd: String) {
            val exitCode = ProcessBuilder(*cmd).directory(projectDir).start().waitFor()
            if (exitCode != 0) throw RuntimeException("Command failed: ${cmd.joinToString(" ")}")
        }
        ProcessBuilder("git", "init", "--bare").directory(remoteDir).start().waitFor()
        runGit("git", "init")
        runGit("git", "config", "user.email", "test@example.com")
        runGit("git", "config", "user.name", "Test User")
        runGit("git", "checkout", "-b", "trunk")
        runGit("git", "remote", "add", "origin", remoteDir.absolutePath)
        runGit("git", "add", ".")
        runGit("git", "commit", "-m", "Initial commit")
        runGit("git", "push", "-u", "origin", "trunk")

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.createFakeBuiltArtifact()

        // when: on trunk (a non-release-branch primary branch) → should create release branch
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
    }

    test("subproject with enabled=false has no release task") {
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
            include(":lib")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")
        val libDir = File(projectDir, "lib")
        libDir.mkdirs()
        File(libDir, "build.gradle.kts").writeText(
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
        val result = project.runTaskAndFail(":lib:release")

        // then: task does not exist
        result.output shouldContain "release"
    }

    // ─────────────────────────────────────────────────────────────
    // Branch behavior
    // ─────────────────────────────────────────────────────────────

    test("non-main allowed branch creates release branch and pushes tag") {
        // given: 'develop' added to releaseBranchPatterns; it is not a release branch,
        // so the !isReleaseBranch path fires and a release branch is created
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }
            monorepo {
                release {
                    releaseBranchPatterns = listOf("^main${'$'}", "^develop${'$'}", "^release/.*")
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
        project.createBranch("develop")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then: tag and release branch both pushed (develop is a primary branch, not a release branch)
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.remoteBranches() shouldContain "release/app/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Multi-project
    // ─────────────────────────────────────────────────────────────

    test("multiple opted-in subprojects are versioned independently") {
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
            include(":lib")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")

        listOf("app", "lib").forEach { name ->
            val dir = File(projectDir, name)
            dir.mkdirs()
            File(dir, "build.gradle.kts").writeText(
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
        }

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()
        File(projectDir, "app/build/libs").mkdirs()
        File(projectDir, "app/build/libs/app.jar").writeText("fake jar content")
        File(projectDir, "lib/build/libs").mkdirs()
        File(projectDir, "lib/build/libs/lib.jar").writeText("fake jar content")

        // when: release each subproject independently
        project.runTask(":app:release")
        project.runTask(":lib:release")

        // then: each has its own v0.1.0 tag, unaffected by the other
        val tags = project.remoteTags()
        tags shouldContain "release/app/v0.1.0"
        tags shouldContain "release/lib/v0.1.0"
    }
})

// Extension to push a branch to remote (used in tests where we need a named branch on remote)
fun ReleaseTestProject.executeGitPush(branch: String) {
    val process = ProcessBuilder("git", "push", "origin", branch)
        .directory(projectDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        val error = process.errorStream.bufferedReader().readText()
        throw RuntimeException("Failed to push branch $branch: $error")
    }
}

fun ReleaseTestProject.stageFile(path: String) {
    val process = ProcessBuilder("git", "add", path)
        .directory(projectDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    process.waitFor()
}
