package io.github.doughawley.monorepo.release.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

class ReleaseChangedFunctionalTest : FunSpec({

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
        val result = project.runTaskAndFail("releaseChanged")

        // then
        result.output shouldContain "must run on 'main'"
        result.output shouldContain "current branch is 'feature/something'"
    }

    // ─────────────────────────────────────────────────────────────
    // Build dependency chain
    // ─────────────────────────────────────────────────────────────

    test("runs buildChanged and subproject build tasks before creating releases") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask("releaseChanged")

        // then: the full dependency chain executed
        result.task(":buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":lib:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    // ─────────────────────────────────────────────────────────────
    // No changed projects
    // ─────────────────────────────────────────────────────────────

    test("succeeds and updates tag when no projects have changed") {
        // given: tag at HEAD so no changes detected
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        val headCommit = project.headCommit()

        // when
        val result = project.runTask("releaseChanged")

        // then: tag still updated (idempotent) even when no changes detected
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "No projects have changed"
        project.remoteBranches().filter { it.startsWith("release/") } shouldBe emptyList()
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe headCommit
    }

    // ─────────────────────────────────────────────────────────────
    // Single project changed — creates branch (no tag)
    // ─────────────────────────────────────────────────────────────

    test("creates release branch and updates last-successful-build tag for the changed opted-in project") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")
        val headCommit = project.headCommit()

        // when
        val result = project.runTask("releaseChanged")

        // then
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches().filter { it.startsWith("release/lib/") } shouldBe emptyList()
        // no tags created — tagging is delegated to the release task on the branch
        project.remoteTags().filter { it.startsWith("release/app/") } shouldBe emptyList()
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
        val result = project.runTask("releaseChanged")

        // then
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldContain "release/lib/v0.1.x"
        // no tags created
        project.remoteTags().filter { it.startsWith("release/") } shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Tag-based baseline (not origin/main)
    // ─────────────────────────────────────────────────────────────

    test("uses last-successful-build tag as baseline, not origin/main") {
        // given: tag and origin/main at different commits to prove which is used
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())

        // Advance past initial state: change lib, push (origin/main moves forward)
        project.modifyFile("lib/lib.txt", "first change")
        project.commitAll("Change lib")
        project.pushToRemote()

        // Create tag HERE — tag is now behind origin/main after next push
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")

        // Change app and push — origin/main advances to HEAD, tag stays behind
        project.modifyFile("app/app.txt", "changed after tag")
        project.commitAll("Change app after tag")
        project.pushToRemote()

        // Now: tag = commit B, origin/main = HEAD = commit C
        // If using tag: app changed (diff B..C) → release branch for app
        // If using origin/main: nothing changed (diff C..C) → no releases

        // when
        val result = project.runTask("releaseChanged")

        // then: proves the tag is used — app has a release branch
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Change detection baseline: monorepo/last-successful-build ("
        project.remoteBranches() shouldContain "release/app/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Tag fetch from remote (CI scenario)
    // ─────────────────────────────────────────────────────────────

    test("fetches tag from remote when not available locally") {
        // given: tag exists on remote but not locally (simulates CI checkout without tags)
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())

        // Advance past initial state: change lib, push (origin/main moves forward)
        project.modifyFile("lib/lib.txt", "first change")
        project.commitAll("Change lib")
        project.pushToRemote()

        // Create tag HERE — tag is now behind origin/main after next push
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")

        // Change app and push — origin/main advances to HEAD, tag stays behind
        project.modifyFile("app/app.txt", "changed after tag")
        project.commitAll("Change app after tag")
        project.pushToRemote()

        // Delete local tag to simulate CI checkout that doesn't fetch tags
        project.deleteLocalTag("monorepo/last-successful-build")

        // when
        val result = project.runTask("releaseChanged")

        // then: proves the tag was fetched from remote and used as baseline
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
    }

    test("fails when remote is unreachable during tag fetch") {
        // given: set up project, then break the remote URL
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.deleteLocalTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")

        // Point origin to a non-existent path to simulate network/remote failure
        project.setRemoteUrl("origin", "/nonexistent/path/to/repo")

        // when
        val result = project.runTaskAndFail("releaseChanged")

        // then: build fails with a clear error instead of silently falling back
        result.output shouldContain "Failed to fetch tag"
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
        val result = project.runTask("releaseChanged")

        // then
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches().filter { it.startsWith("release/lib/") } shouldBe emptyList()
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
        val result = project.runTask("releaseChanged")

        // then
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v1.0.x"
        project.remoteBranches() shouldContain "release/lib/v1.0.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Bumps version based on existing tags
    // ─────────────────────────────────────────────────────────────

    test("bumps version based on existing tags") {
        // given: app already has v0.1.0
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")

        // when
        val result = project.runTask("releaseChanged")

        // then: next minor is v0.2.0
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.2.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Branch scanning — bumps past existing branches
    // ─────────────────────────────────────────────────────────────

    test("second run creates higher version branch when first release has not completed") {
        // given: simulate a prior releaseChanged run that created branch v0.1.x
        // but the release task hasn't run yet (no tag) and the tag updater failed.
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())

        // Manually push branch v0.1.x to simulate a prior releaseChanged run
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.checkoutBranch("main")

        // Tag is still at the old position (tag updater failed)
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")

        // when: branch scanning finds v0.1.x → baseline v0.1.0 → next v0.2.0
        val result = project.runTask("releaseChanged")

        // then: creates v0.2.x (not v0.1.x again)
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.2.x"
    }

    test("bumps version past both existing branch and tag") {
        // given: app has completed release cycle at v0.1.0 (tag + branch exist)
        // and a second releaseChanged created v0.2.x branch (release not yet completed, no v0.2.0 tag)
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())

        // Set up completed prior release: tag + branch at v0.1
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.checkoutBranch("main")

        // Set up in-flight release: branch at v0.2.x but no v0.2.0 tag
        project.createBranch("release/app/v0.2.x")
        project.executeGitPush("release/app/v0.2.x")
        project.checkoutBranch("main")

        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")

        // when: max(tag v0.1.0, branch v0.2.0) = v0.2.0 → next v0.3.0
        val result = project.runTask("releaseChanged")

        // then
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.3.x"
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
        val result = project.runTaskAndFail("releaseChanged")

        // then: task fails; no branches pushed; last-successful-build tag not updated
        result.output shouldContain "already exists locally"
        project.remoteBranches() shouldNotContain "release/app/v0.1.x"
        project.remoteBranches() shouldNotContain "release/lib/v0.1.x"
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe tagCommitBefore
    }

    // ─────────────────────────────────────────────────────────────
    // Build failure
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
        val result = project.runTaskAndFail("releaseChanged")

        // then: tag should NOT have moved
        result.output shouldContain "Simulated build failure"
        project.remoteBranches().filter { it.startsWith("release/") } shouldBe emptyList()
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe tagCommitBefore
    }

    // ─────────────────────────────────────────────────────────────
    // No tag — all projects treated as changed
    // ─────────────────────────────────────────────────────────────

    test("creates release branches for all opted-in projects when tag does not exist") {
        // given: no tag — all projects treated as changed (no baseline)
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTask("releaseChanged")

        // then: no baseline, all projects treated as changed, release branches created
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "no baseline exists"
        result.output shouldContain "Change detection baseline: none (all projects treated as changed)"
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldContain "release/lib/v0.1.x"
        // no tags created — tagging is delegated to release task on the branch
        project.remoteTags().filter { it.startsWith("release/") } shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // End-to-end handoff: releaseChanged → release
    // ─────────────────────────────────────────────────────────────

    test("releaseChanged creates branch, then release task on that branch creates tag") {
        // given: releaseChanged creates branch release/app/v0.1.x (no tag)
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")

        val releaseResult = project.runTask("releaseChanged")
        releaseResult.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        // no tag created by releaseChanged
        project.remoteTags().filter { it.startsWith("release/app/") } shouldBe emptyList()

        // when: checkout the release branch and run :app:release
        project.checkoutBranch("release/app/v0.1.x")
        val result = project.runTask(":app:release")

        // then: release task creates the version tag
        result.task(":app:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.releaseVersionFile() shouldBe "0.1.0"
    }

    // ─────────────────────────────────────────────────────────────
    // All disabled
    // ─────────────────────────────────────────────────────────────

    test("updates tag but creates no releases when all changed projects have release disabled") {
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
        val result = project.runTask("releaseChanged")

        // then: tag still updated even though no releases were created
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "nothing to release"
        project.remoteBranches().filter { it.startsWith("release/") } shouldBe emptyList()
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe headCommit
    }
})
