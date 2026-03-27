package io.github.doughawley.monorepo.release.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class ReleaseTaskFunctionalTest : FunSpec({

    val testListener = extension(ReleaseTestProjectListener())

    // ─────────────────────────────────────────────────────────────
    // Core versioning
    // ─────────────────────────────────────────────────────────────

    test("no prior tag on release branch creates first release as major.minor.0") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.localTags() shouldContain "release/app/v0.1.0"
        project.remoteTags() shouldContain "release/app/v0.1.0"
    }

    test("prior tag in version line bumps patch") {
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

    test("on release branch scans only version line and creates patch") {
        // given: v0.1.0, v0.1.1, and v0.2.0 exist; on release/app/v0.1.x → should produce v0.1.2
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.createTag("release/app/v0.1.1")
        project.createTag("release/app/v0.2.0")
        project.pushTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.1")
        project.pushTag("release/app/v0.2.0")

        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.2"
    }

    test("on release branch with no prior tags in that version line creates correct initial version") {
        // given: v0.1.0 exists; release/app/v0.2.x branch exists but has no v0.2.* tags
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")

        project.createBranch("release/app/v0.2.x")
        project.executeGitPush("release/app/v0.2.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then: should create v0.2.0, NOT v0.1.0
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.2.0"
    }

    test("on release branch v1.0.x with no prior tags in that line creates v1.0.0") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")

        project.createBranch("release/app/v1.0.x")
        project.executeGitPush("release/app/v1.0.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v1.0.0"
    }

    // ─────────────────────────────────────────────────────────────
    // Tag format
    // ─────────────────────────────────────────────────────────────

    test("single-level path :app produces tag release/app/v...") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        project.runTask(":app:release")

        // then
        project.remoteTags().any { it.startsWith("release/app/v") } shouldBe true
    }

    test("custom globalTagPrefix overrides default release prefix") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir(), globalTagPrefix = "publish")
        project.createBranch("publish/app/v0.1.x")
        project.executeGitPush("publish/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "publish/app/v0.1.0"
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

            allprojects {
                repositories {
                    mavenCentral()
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
        File(projectDir, ".gitignore").writeText(".gradle/\n.kotlin/\nbuild/")

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
        project.createBranch("release/my-custom-app/v0.1.x")
        project.executeGitPush("release/my-custom-app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/my-custom-app/v0.1.0"
    }

    test("nested subproject path produces hierarchical tag prefix") {
        // given: :services:auth → deriveProjectTagPrefix → "services/auth"
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
            }
            """.trimIndent()
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":services:auth")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\n.kotlin/\nbuild/")

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
        project.createBranch("release/services/auth/v0.1.x")
        project.executeGitPush("release/services/auth/v0.1.x")
        File(projectDir, "services/auth/build/libs").mkdirs()
        File(projectDir, "services/auth/build/libs/auth.jar").writeText("fake jar content")

        // when
        val result = project.runTask(":services:auth:release")

        // then
        result.task(":services:auth:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/services/auth/v0.1.0"
    }

    // ─────────────────────────────────────────────────────────────
    // Branch restrictions
    // ─────────────────────────────────────────────────────────────

    test("release from main fails with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Cannot release from branch 'main'"
        result.output shouldContain "release branch"
    }

    test("feature branch causes release to fail with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("feature/my-feature")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Cannot release from branch 'feature/my-feature'"
        result.output shouldContain "release branch"
    }

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

    test("release from wrong project release branch fails with clear message") {
        // given: on release/app/v0.1.x, try to release :lib
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
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
        File(projectDir, ".gitignore").writeText(".gradle/\n.kotlin/\nbuild/")

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
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        File(projectDir, "lib/build/libs").mkdirs()
        File(projectDir, "lib/build/libs/lib.jar").writeText("fake jar content")

        // when: try to release :lib from :app's release branch
        val result = project.runTaskAndFail(":lib:release")

        // then
        result.output shouldContain "Cannot release :lib from branch 'release/app/v0.1.x'"
        result.output shouldContain "This branch is for project 'app', not 'lib'"
    }

    // ─────────────────────────────────────────────────────────────
    // Guardrails
    // ─────────────────────────────────────────────────────────────

    test("uncommitted changes causes release to fail with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()
        project.modifyFile("app/dirty.txt", "uncommitted change")

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Cannot release with uncommitted changes. Please commit or stash all changes before releasing."
    }

    test("staged but uncommitted changes causes release to fail") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()
        project.modifyFile("app/dirty.txt", "staged modification")
        project.stageFile("app/dirty.txt")

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Cannot release with uncommitted changes. Please commit or stash all changes before releasing."
    }

    test("tag already exists causes release to fail with clear message") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        // Create v0.1.1 locally only (not pushed) — this is the "collision" tag
        project.createTag("release/app/v0.1.1")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release")

        // then
        result.output shouldContain "Tag 'release/app/v0.1.1' already exists. This version has already been released."
    }

    test("release task runs buildChanged and build before releasing") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        // deliberately not creating a fake built artifact — build task should produce one

        // when
        val result = project.runTask(":app:release")

        // then
        result.task(":app:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    // ─────────────────────────────────────────────────────────────
    // Scope enforcement
    // ─────────────────────────────────────────────────────────────

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
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release", properties = mapOf("release.scope" to "bogus"))

        // then
        result.output shouldContain "Invalid release.scope value: 'bogus'. Must be one of: major, minor, patch"
    }

    test("release branch accepts -Prelease.scope=patch and applies patch") {
        // given
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

    test("push fails when no remote configured — local tag is deleted, task fails cleanly") {
        // given: project without remote
        val projectDir = testListener.getTestProjectDir()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            allprojects {
                repositories {
                    mavenCentral()
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
        File(projectDir, ".gitignore").writeText(".gradle/\n.kotlin/\nbuild/")
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
        runGit("git", "checkout", "-b", "release/app/v0.1.x")
        runGit("git", "add", ".")
        runGit("git", "commit", "-m", "Initial commit")
        runGit("git", "tag", "monorepo/last-successful-build")
        project.createFakeBuiltArtifact()

        // when
        val result = project.runTaskAndFail(":app:release")

        // then: push failed, local tag rolled back
        result.output shouldContain "Push failed, rolling back local changes"
        project.localTags() shouldNotContain "release/app/v0.1.0"
    }

    test("on release branch no new release branch is created, only tag pushed") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        project.runTask(":app:release")

        // then: no new branch created
        val remoteBranches = project.remoteBranches()
        remoteBranches shouldContain "release/app/v0.1.x"
        remoteBranches.filter { it.startsWith("release/app/v0.1.") && it != "release/app/v0.1.x" } shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Version communication
    // ─────────────────────────────────────────────────────────────

    test("build/release-version.txt is written after successful release") {
        // given
        val project = StandardReleaseTestProject.createAndInitialize(testListener.getTestProjectDir())
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.createFakeBuiltArtifact()

        // when
        project.runTask(":app:release")

        // then
        project.releaseVersionFile() shouldBe "0.1.0"
    }

    // ─────────────────────────────────────────────────────────────
    // Opt-in model
    // ─────────────────────────────────────────────────────────────

    test("subproject without monorepoProject { release { } } has no release task") {
        // given
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
            }
            """.trimIndent()
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":lib")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\n.kotlin/\nbuild/")
        val libDir = File(projectDir, "lib")
        libDir.mkdirs()
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

    test("subproject with enabled=false has no release task") {
        // given
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
            }
            """.trimIndent()
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":lib")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\n.kotlin/\nbuild/")
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
    // Multi-project
    // ─────────────────────────────────────────────────────────────

    test("multiple opted-in subprojects are versioned independently on their own release branches") {
        // given
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
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
        File(projectDir, ".gitignore").writeText(".gradle/\n.kotlin/\nbuild/")

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

        // Release app from app's release branch
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        File(projectDir, "app/build/libs").mkdirs()
        File(projectDir, "app/build/libs/app.jar").writeText("fake jar content")
        project.runTask(":app:release")

        // Switch to lib's release branch and release lib
        project.checkoutBranch("main")
        project.createBranch("release/lib/v0.1.x")
        project.executeGitPush("release/lib/v0.1.x")
        File(projectDir, "lib/build/libs").mkdirs()
        File(projectDir, "lib/build/libs/lib.jar").writeText("fake jar content")
        project.runTask(":lib:release")

        // then: each has its own v0.1.0 tag
        val tags = project.remoteTags()
        tags shouldContain "release/app/v0.1.0"
        tags shouldContain "release/lib/v0.1.0"
    }

    // ─────────────────────────────────────────────────────────────
    // Upstream dependency testing via buildChanged
    // ─────────────────────────────────────────────────────────────

    test("release tests changed upstream dependency via buildChanged") {
        // given: :app depends on :lib, lib has changed since origin/main
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
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
        File(projectDir, ".gitignore").writeText(".gradle/\n.kotlin/\nbuild/")

        val libDir = File(projectDir, "lib")
        libDir.mkdirs()
        File(libDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.0.21"
            }
            """.trimIndent()
        )
        File(File(libDir, "src/main/kotlin/com/example").apply { mkdirs() }, "Lib.kt").writeText(
            """
            package com.example
            class Lib { fun greet() = "Hello" }
            """.trimIndent()
        )

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
            dependencies {
                implementation(project(":lib"))
            }
            """.trimIndent()
        )
        File(File(appDir, "src/main/kotlin/com/example").apply { mkdirs() }, "App.kt").writeText(
            """
            package com.example
            class App { fun run() = Lib().greet() }
            """.trimIndent()
        )

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // Make a change to lib after the baseline
        File(File(libDir, "src/main/kotlin/com/example"), "Lib.kt").writeText(
            """
            package com.example
            class Lib { fun greet() = "Hello World" }
            """.trimIndent()
        )
        project.commitAll("Change lib")

        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")

        // when
        val result = project.runTask(":app:release")

        // then: release succeeds and lib was fully built (not just assembled)
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":lib:build")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.0"
    }

    test("release does not test unchanged upstream dependency") {
        // given: :app depends on :lib, but lib has NOT changed since origin/main
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
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
        File(projectDir, ".gitignore").writeText(".gradle/\n.kotlin/\nbuild/")

        val libDir = File(projectDir, "lib")
        libDir.mkdirs()
        File(libDir, "build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.0.21"
            }
            """.trimIndent()
        )
        File(File(libDir, "src/main/kotlin/com/example").apply { mkdirs() }, "Lib.kt").writeText(
            """
            package com.example
            class Lib { fun greet() = "Hello" }
            """.trimIndent()
        )

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
            dependencies {
                implementation(project(":lib"))
            }
            """.trimIndent()
        )
        File(File(appDir, "src/main/kotlin/com/example").apply { mkdirs() }, "App.kt").writeText(
            """
            package com.example
            class App { fun run() = Lib().greet() }
            """.trimIndent()
        )

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // Change only app, not lib
        File(File(appDir, "src/main/kotlin/com/example"), "App.kt").writeText(
            """
            package com.example
            class App { fun run() = Lib().greet() + "!" }
            """.trimIndent()
        )
        project.commitAll("Change app only")

        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")

        // when
        val result = project.runTask(":app:release")

        // then: release succeeds, lib was only assembled (not fully built)
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":lib:build") shouldBe null
        project.remoteTags() shouldContain "release/app/v0.1.0"
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
