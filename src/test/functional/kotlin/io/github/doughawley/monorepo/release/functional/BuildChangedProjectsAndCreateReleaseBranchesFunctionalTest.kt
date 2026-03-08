package io.github.doughawley.monorepo.release.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

class BuildChangedProjectsAndCreateReleaseBranchesFunctionalTest : FunSpec({

    val testListener = extension(ReleaseTestProjectListener())

    // ─────────────────────────────────────────────────────────────
    // Branch guard
    // ─────────────────────────────────────────────────────────────

    test("fails fast when not on primaryBranch") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")
        project.createBranch("feature/something")

        // when
        val result = project.runTaskAndFail("buildChangedProjectsAndCreateReleaseBranches")

        // then
        result.output shouldContain "must run on 'main'"
        result.output shouldContain "current branch is 'feature/something'"
    }

    // ─────────────────────────────────────────────────────────────
    // No changed projects
    // ─────────────────────────────────────────────────────────────

    test("succeeds with no branches created and updates tag when no projects have changed") {
        // given: tag at HEAD so no changes detected
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        val headCommit = project.headCommit()

        // when
        val result = project.runTask("buildChangedProjectsAndCreateReleaseBranches")

        // then: tag still updated (idempotent) even when no changes detected
        result.task(":buildChangedProjectsAndCreateReleaseBranches")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No projects have changed"
        project.remoteBranches().filter { it.startsWith("release/") } shouldBe emptyList()
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe headCommit
    }

    // ─────────────────────────────────────────────────────────────
    // Single project changed
    // ─────────────────────────────────────────────────────────────

    test("creates release branch and updates tag for the changed opted-in project") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")
        val headCommit = project.headCommit()

        // when
        val result = project.runTask("buildChangedProjectsAndCreateReleaseBranches")

        // then
        result.task(":buildChangedProjectsAndCreateReleaseBranches")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldNotContain "release/lib/v0.1.x"
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe headCommit
    }

    // ─────────────────────────────────────────────────────────────
    // Both projects changed
    // ─────────────────────────────────────────────────────────────

    test("creates release branches for all changed opted-in projects") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask("buildChangedProjectsAndCreateReleaseBranches")

        // then
        result.task(":buildChangedProjectsAndCreateReleaseBranches")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldContain "release/lib/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Opt-in model
    // ─────────────────────────────────────────────────────────────

    test("skips project with enabled=false even when it has changed") {
        // given: lib has enabled=false
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(
            testListener.getTestProjectDir(),
            libEnabled = false
        )
        project.createTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask("buildChangedProjectsAndCreateReleaseBranches")

        // then
        result.task(":buildChangedProjectsAndCreateReleaseBranches")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldNotContain "release/lib/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Scope override
    // ─────────────────────────────────────────────────────────────

    test("primaryBranchScope=major creates v1.0.x branches when prior v0.x.x tags exist") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(
            testListener.getTestProjectDir(),
            primaryBranchScope = "major"
        )
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createTag("release/lib/v0.1.0")
        project.pushTag("release/lib/v0.1.0")
        project.createTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask("buildChangedProjectsAndCreateReleaseBranches")

        // then
        result.task(":buildChangedProjectsAndCreateReleaseBranches")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v1.0.x"
        project.remoteBranches() shouldContain "release/lib/v1.0.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Rollback on local branch collision
    // ─────────────────────────────────────────────────────────────

    test("rolls back all local branches and does not update tag when one already exists locally") {
        // given: pre-create a local branch for app so creation fails
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        val tagCommitBefore = project.commitForTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")
        project.createBranch("release/app/v0.1.x")
        project.checkoutBranch("main")

        // when
        val result = project.runTaskAndFail("buildChangedProjectsAndCreateReleaseBranches")

        // then: task fails; neither branch pushed; tag not updated
        result.output shouldContain "already exists locally"
        project.remoteBranches() shouldNotContain "release/app/v0.1.x"
        project.remoteBranches() shouldNotContain "release/lib/v0.1.x"
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe tagCommitBefore
    }

    // ─────────────────────────────────────────────────────────────
    // All disabled
    // ─────────────────────────────────────────────────────────────

    test("does not update tag when a subproject build fails") {
        // given: app's build task will throw an error
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        val appBuild = java.io.File(project.projectDir, "app/build.gradle.kts")
        appBuild.writeText(
            """
            monorepoProject {
                release {
                    enabled = true
                }
            }

            tasks.register("build") {
                doLast {
                    throw GradleException("Simulated build failure")
                }
            }
            """.trimIndent()
        )
        project.commitAll("Make app build fail")
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        val tagCommitBefore = project.commitForTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")

        // when
        val result = project.runTaskAndFail("buildChangedProjectsAndCreateReleaseBranches")

        // then: tag should NOT have moved
        result.output shouldContain "Simulated build failure"
        project.remoteBranches().filter { it.startsWith("release/") } shouldBe emptyList()
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe tagCommitBefore
    }

    // ─────────────────────────────────────────────────────────────
    // No baseline (tag does not exist)
    // ─────────────────────────────────────────────────────────────

    test("creates release branches for all opted-in projects when tag does not exist") {
        // given: no tag — all projects treated as changed
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTask("buildChangedProjectsAndCreateReleaseBranches")

        // then: both opted-in projects get release branches
        result.task(":buildChangedProjectsAndCreateReleaseBranches")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "no baseline"
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldContain "release/lib/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // All disabled
    // ─────────────────────────────────────────────────────────────

    test("updates tag but creates no branches when all changed projects have release disabled") {
        // given: both disabled
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(
            testListener.getTestProjectDir(),
            libEnabled = false
        )
        // Override app to also be disabled
        val appBuild = java.io.File(project.projectDir, "app/build.gradle.kts")
        appBuild.writeText(
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
        project.commitAll("Disable app release")
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")
        val headCommit = project.headCommit()

        // when
        val result = project.runTask("buildChangedProjectsAndCreateReleaseBranches")

        // then: tag still updated even though no release branches were created
        result.task(":buildChangedProjectsAndCreateReleaseBranches")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "no release branches to create"
        project.remoteBranches().filter { it.startsWith("release/") } shouldBe emptyList()
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe headCommit
    }
})
