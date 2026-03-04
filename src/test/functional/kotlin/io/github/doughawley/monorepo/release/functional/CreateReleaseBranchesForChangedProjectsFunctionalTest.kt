package io.github.doughawley.monorepo.release.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class CreateReleaseBranchesForChangedProjectsFunctionalTest : FunSpec({

    val testListener = listener(ReleaseTestProjectListener())

    // ─────────────────────────────────────────────────────────────
    // No changed projects
    // ─────────────────────────────────────────────────────────────

    test("succeeds with no branches created when no projects have changed") {
        // given: two commits so HEAD~1 exists; second commit only touches a root file
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("gradle.properties", "# updated")
        project.commitAll("Update root file")

        // when: HEAD~1 diff covers only the root file — no subproject changes
        val result = project.runTask(
            "createReleaseBranchesForChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then
        result.task(":createReleaseBranchesForChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldBe emptyList()
        project.remoteBranches().filter { it.startsWith("release/") } shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Single project changed
    // ─────────────────────────────────────────────────────────────

    test("creates release branch only for the changed opted-in project") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")

        // when
        val result = project.runTask(
            "createReleaseBranchesForChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: release branch created for app only; lib untouched; no tags created
        result.task(":createReleaseBranchesForChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldNotContain "release/lib/v0.1.x"
        project.remoteTags() shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Both projects changed
    // ─────────────────────────────────────────────────────────────

    test("creates release branches for all changed opted-in projects") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask(
            "createReleaseBranchesForChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: release branches created; no tags created
        result.task(":createReleaseBranchesForChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldContain "release/lib/v0.1.x"
        project.remoteTags() shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Opt-in model
    // ─────────────────────────────────────────────────────────────

    test("skips project with enabled=false even when it has changed") {
        // given: lib has enabled=false, both projects changed
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(
            testListener.getTestProjectDir(),
            libEnabled = false
        )
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask(
            "createReleaseBranchesForChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: only app gets a release branch; lib skipped because it is not opted in
        result.task(":createReleaseBranchesForChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldNotContain "release/lib/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Scope override
    // ─────────────────────────────────────────────────────────────

    test("primaryBranchScope=major creates v1.0.x branches when prior v0.x.x tags exist") {
        // given: both projects have a prior v0.1.0 release; scope configured to major
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(
            testListener.getTestProjectDir(),
            primaryBranchScope = "major"
        )
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createTag("release/lib/v0.1.0")
        project.pushTag("release/lib/v0.1.0")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask(
            "createReleaseBranchesForChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: major bump → v1.0.x branches for both; no tags created
        result.task(":createReleaseBranchesForChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v1.0.x"
        project.remoteBranches() shouldContain "release/lib/v1.0.x"
        project.remoteTags().filter { it.startsWith("release/app/v1") || it.startsWith("release/lib/v1") } shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Branch collision resilience (--continue)
    // ─────────────────────────────────────────────────────────────

    test("branch collision on one project does not prevent the other from getting a release branch") {
        // given: both projects changed; a pre-existing local branch for app causes its createReleaseBranch to fail
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // Pre-create the branch locally so createReleaseBranch fails for app
        project.createBranch("release/app/v0.1.x")
        project.checkoutBranch("main")

        // when: --continue lets lib:createReleaseBranch run despite app:createReleaseBranch failing
        project.runTaskAndFail(
            "createReleaseBranchesForChangedProjects", "--continue",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: lib got its branch; app did not (local collision prevented push)
        project.remoteBranches() shouldContain "release/lib/v0.1.x"
        project.remoteBranches() shouldNotContain "release/app/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Nothing to create
    // ─────────────────────────────────────────────────────────────

    test("changed projects with all disabled emits nothing-to-create log and creates no branches") {
        // given: both :app and :lib have enabled = false
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            monorepo {
                release {
                    primaryBranchScope = "minor"
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
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")

        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText(
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
        File(appDir, "app.txt").writeText("app source")

        val libDir = File(projectDir, "lib")
        libDir.mkdirs()
        File(libDir, "build.gradle.kts").writeText(
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
        File(libDir, "lib.txt").writeText("lib source")

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask(
            "createReleaseBranchesForChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then
        result.task(":createReleaseBranchesForChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches().filter { it.startsWith("release/") } shouldBe emptyList()
        result.output shouldContain "no release branches to create"
    }
})
