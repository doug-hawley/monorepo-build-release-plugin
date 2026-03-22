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
    // Single project changed — creates tag + branch
    // ─────────────────────────────────────────────────────────────

    test("creates tag and branch and updates last-successful-build tag for the changed opted-in project") {
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
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteTags().filter { it.startsWith("release/lib/") } shouldBe emptyList()
        project.remoteBranches().filter { it.startsWith("release/lib/") } shouldBe emptyList()
        project.remoteTagCommit("monorepo/last-successful-build") shouldBe headCommit
    }

    // ─────────────────────────────────────────────────────────────
    // Both projects changed
    // ─────────────────────────────────────────────────────────────

    test("creates tags and branches for all changed opted-in projects") {
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
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.remoteTags() shouldContain "release/lib/v0.1.0"
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldContain "release/lib/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Writes release-version.txt per subproject
    // ─────────────────────────────────────────────────────────────

    test("writes release-version.txt per subproject after successful release") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask("releaseChanged")

        // then
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        project.releaseVersionFile() shouldBe "0.1.0"
        val libVersionFile = java.io.File(project.projectDir, "lib/build/release-version.txt")
        libVersionFile.exists() shouldBe true
        libVersionFile.readText().trim() shouldBe "0.1.0"
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
        // If using tag: app changed (diff B..C) → release for app
        // If using origin/main: nothing changed (diff C..C) → no releases

        // when
        val result = project.runTask("releaseChanged")

        // then: proves the tag is used — app has a release tag
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Change detection baseline: monorepo/last-successful-build ("
        project.remoteTags() shouldContain "release/app/v0.1.0"
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
        project.remoteTags() shouldContain "release/app/v0.1.0"
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
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteTags().filter { it.startsWith("release/lib/") } shouldBe emptyList()
        project.remoteBranches().filter { it.startsWith("release/lib/") } shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Scope override
    // ─────────────────────────────────────────────────────────────

    test("primaryBranchScope=major creates v1.0.0 tags and v1.0.x branches when prior v0.x.x tags exist") {
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
        project.remoteTags() shouldContain "release/app/v1.0.0"
        project.remoteTags() shouldContain "release/lib/v1.0.0"
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
        project.remoteTags() shouldContain "release/app/v0.2.0"
        project.remoteBranches() shouldContain "release/app/v0.2.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Skips projects whose branch already exists on remote
    // ─────────────────────────────────────────────────────────────

    test("skips projects whose release branch already exists on remote") {
        // given: simulate a prior releaseChanged run that pushed tags and branches
        // but the lastSuccessfulBuildTag update did not complete.
        // Manually create the release refs that would have been pushed.
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.createTag("monorepo/last-successful-build")
        project.pushTag("monorepo/last-successful-build")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // Manually create the release refs on remote (simulating a prior atomic push)
        // No prior tags → forMainBranch(null, MINOR) → v0.1.0, branch → v0.1.x
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createTag("release/lib/v0.1.0")
        project.pushTag("release/lib/v0.1.0")
        project.createBranch("release/app/v0.1.x")
        project.executeGitPush("release/app/v0.1.x")
        project.checkoutBranch("main")
        project.createBranch("release/lib/v0.1.x")
        project.executeGitPush("release/lib/v0.1.x")
        project.checkoutBranch("main")

        // Now tags v0.1.0 exist, so next version = v0.2.0, branch = v0.2.x.
        // Those don't exist on remote, so projects won't be skipped — they'll get v0.2.0.
        // That's correct: the prior run already created v0.1.0, so the next run creates v0.2.0.
        // To test the skip, we also need v0.2.x branches on remote.
        project.createTag("release/app/v0.2.0")
        project.pushTag("release/app/v0.2.0")
        project.createTag("release/lib/v0.2.0")
        project.pushTag("release/lib/v0.2.0")
        project.createBranch("release/app/v0.2.x")
        project.executeGitPush("release/app/v0.2.x")
        project.checkoutBranch("main")
        project.createBranch("release/lib/v0.2.x")
        project.executeGitPush("release/lib/v0.2.x")
        project.checkoutBranch("main")

        // Now: findLatestVersion → v0.2.0 for both, next → v0.3.0, branch → v0.3.x.
        // v0.3.x doesn't exist → not skipped. This cascading makes it hard to test skip
        // with real tag scanning. Instead, verify at integration test level and just test
        // that the task handles the "all skipped" case gracefully here.
        project.createBranch("release/app/v0.3.x")
        project.executeGitPush("release/app/v0.3.x")
        project.checkoutBranch("main")
        project.createBranch("release/lib/v0.3.x")
        project.executeGitPush("release/lib/v0.3.x")
        project.checkoutBranch("main")

        // when: findLatestVersion → v0.2.0, next → v0.3.0, branch → v0.3.x — exists → skip
        val result = project.runTask("releaseChanged")

        // then: task succeeds; skips both projects
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "already exists on remote"
    }

    // ─────────────────────────────────────────────────────────────
    // Rollback on local branch collision
    // ─────────────────────────────────────────────────────────────

    test("rolls back all local refs and does not update tag when one already exists locally") {
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

        // then: task fails; neither tag nor branch pushed; last-successful-build tag not updated
        result.output shouldContain "already exists locally"
        project.remoteTags().filter { it.startsWith("release/app/") } shouldBe emptyList()
        project.remoteTags().filter { it.startsWith("release/lib/") } shouldBe emptyList()
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

    test("creates releases for all opted-in projects when tag does not exist") {
        // given: no tag — all projects treated as changed (no baseline)
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())

        // when
        val result = project.runTask("releaseChanged")

        // then: no baseline, all projects treated as changed, releases created
        result.task(":releaseChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "no baseline exists"
        result.output shouldContain "Change detection baseline: none (all projects treated as changed)"
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.remoteTags() shouldContain "release/lib/v0.1.0"
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldContain "release/lib/v0.1.x"
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
