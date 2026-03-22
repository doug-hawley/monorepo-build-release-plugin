package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.github.doughawley.monorepo.release.domain.Scope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

class AtomicReleaseCreatorIntegrationTest : FunSpec({

    val repoListener = TempGitRepoListener()
    extension(repoListener)

    val logger = mockk<Logger>(relaxed = true)

    fun createCreator(): AtomicReleaseCreator {
        val executor = GitCommandExecutor(logger)
        val releaseExecutor = GitReleaseExecutor(repoListener.repo.localDir, executor, logger)
        val tagScanner = GitTagScanner(repoListener.repo.localDir, executor)
        return AtomicReleaseCreator(releaseExecutor, tagScanner, logger)
    }

    test("creates tags and branches for multiple projects and pushes them atomically") {
        // given
        val creator = createCreator()
        val projects = mapOf(
            ":app" to "app",
            ":lib" to "lib"
        )

        // when
        val result = creator.releaseProjects(projects, "release", Scope.MINOR)

        // then
        result.createdTags shouldContainExactlyInAnyOrder listOf("release/app/v0.1.0", "release/lib/v0.1.0")
        result.createdBranches shouldContainExactlyInAnyOrder listOf("release/app/v0.1.x", "release/lib/v0.1.x")
        result.projectToTag[":app"] shouldBe "release/app/v0.1.0"
        result.projectToTag[":lib"] shouldBe "release/lib/v0.1.0"
        result.projectToBranch[":app"] shouldBe "release/app/v0.1.x"
        result.projectToBranch[":lib"] shouldBe "release/lib/v0.1.x"
        result.projectToVersion[":app"]?.toString() shouldBe "0.1.0"
        result.projectToVersion[":lib"]?.toString() shouldBe "0.1.0"
        repoListener.repo.remoteTagExists("release/app/v0.1.0") shouldBe true
        repoListener.repo.remoteTagExists("release/lib/v0.1.0") shouldBe true
        repoListener.repo.remoteBranchExists("release/app/v0.1.x") shouldBe true
        repoListener.repo.remoteBranchExists("release/lib/v0.1.x") shouldBe true
    }

    test("returns empty result when project map is empty") {
        // given
        val creator = createCreator()

        // when
        val result = creator.releaseProjects(emptyMap(), "release", Scope.MINOR)

        // then
        result.createdTags.shouldBeEmpty()
        result.createdBranches.shouldBeEmpty()
        result.projectToVersion.shouldBeEmpty()
        result.projectToTag.shouldBeEmpty()
        result.projectToBranch.shouldBeEmpty()
    }

    test("bumps version based on existing tags") {
        // given: app already has v0.1.0
        repoListener.repo.pushTag("release/app/v0.1.0")
        val creator = createCreator()
        val projects = mapOf(":app" to "app")

        // when
        val result = creator.releaseProjects(projects, "release", Scope.MINOR)

        // then: next minor is v0.2.0
        result.createdTags shouldContainExactlyInAnyOrder listOf("release/app/v0.2.0")
        result.createdBranches shouldContainExactlyInAnyOrder listOf("release/app/v0.2.x")
        repoListener.repo.remoteTagExists("release/app/v0.2.0") shouldBe true
        repoListener.repo.remoteBranchExists("release/app/v0.2.x") shouldBe true
    }

    test("uses major scope when requested") {
        // given: app already has v0.1.0
        repoListener.repo.pushTag("release/app/v0.1.0")
        val creator = createCreator()
        val projects = mapOf(":app" to "app")

        // when
        val result = creator.releaseProjects(projects, "release", Scope.MAJOR)

        // then: major bump → v1.0.0 tag and v1.0.x branch
        result.createdTags shouldContainExactlyInAnyOrder listOf("release/app/v1.0.0")
        result.createdBranches shouldContainExactlyInAnyOrder listOf("release/app/v1.0.x")
        repoListener.repo.remoteTagExists("release/app/v1.0.0") shouldBe true
        repoListener.repo.remoteBranchExists("release/app/v1.0.x") shouldBe true
    }

    test("skips project whose release branch already exists on remote") {
        // given: simulate a prior releaseChanged run for app where the atomic push
        // succeeded but the lastSuccessfulBuildTag update failed.
        // No prior tags exist, so forMainBranch(null, MINOR) → v0.1.0, branch → v0.1.x.
        // Push the branch (but NOT the tag) to simulate the branch existing without the tag.
        // However, with atomic push both tag and branch are pushed together.
        // So the realistic scenario: both tag and branch exist from prior run.
        // findLatestVersion → v0.1.0, next → v0.2.0, branch → v0.2.x — not skipped.
        //
        // The skip actually covers idempotent re-runs: the prior run pushed tag + branch
        // at v0.1.0, and the tag updater failed. On the next run, findLatestVersion still
        // finds v0.1.0, computes v0.2.0, and v0.2.x doesn't exist → creates it normally.
        // That's the correct behavior — no data loss, no duplicate.
        //
        // For this test, we verify the skip with a scenario where only the branch exists
        // (manually created, e.g., someone pushed it by hand).
        val executor = GitCommandExecutor(logger)
        val releaseExecutor = GitReleaseExecutor(repoListener.repo.localDir, executor, logger)
        releaseExecutor.createBranchLocally("release/app/v0.1.x")
        releaseExecutor.pushBranch("release/app/v0.1.x")
        releaseExecutor.deleteLocalBranch("release/app/v0.1.x")

        val creator = createCreator()
        val projects = mapOf(
            ":app" to "app",
            ":lib" to "lib"
        )

        // when: no tags exist, so forMainBranch(null, MINOR) → v0.1.0, branch → v0.1.x.
        // v0.1.x exists on remote → app skipped. lib has no branch → created.
        val result = creator.releaseProjects(projects, "release", Scope.MINOR)

        // then: app skipped, lib created
        result.createdTags shouldContainExactlyInAnyOrder listOf("release/lib/v0.1.0")
        result.createdBranches shouldContainExactlyInAnyOrder listOf("release/lib/v0.1.x")
        result.projectToTag.keys shouldContainExactlyInAnyOrder listOf(":lib")
        repoListener.repo.remoteTagExists("release/lib/v0.1.0") shouldBe true
        repoListener.repo.remoteBranchExists("release/lib/v0.1.x") shouldBe true
    }

    test("returns empty result when all releases already exist on remote") {
        // given: push branches for both projects at v0.1.x (no tags, so version resolves to v0.1.0)
        val executor = GitCommandExecutor(logger)
        val releaseExecutor = GitReleaseExecutor(repoListener.repo.localDir, executor, logger)
        releaseExecutor.createBranchLocally("release/app/v0.1.x")
        releaseExecutor.pushBranch("release/app/v0.1.x")
        releaseExecutor.deleteLocalBranch("release/app/v0.1.x")
        releaseExecutor.createBranchLocally("release/lib/v0.1.x")
        releaseExecutor.pushBranch("release/lib/v0.1.x")
        releaseExecutor.deleteLocalBranch("release/lib/v0.1.x")

        val creator = createCreator()
        val projects = mapOf(
            ":app" to "app",
            ":lib" to "lib"
        )

        // when: forMainBranch(null, MINOR) → v0.1.0, branch → v0.1.x — both exist on remote
        val result = creator.releaseProjects(projects, "release", Scope.MINOR)

        // then
        result.createdTags.shouldBeEmpty()
        result.createdBranches.shouldBeEmpty()
        result.projectToVersion.shouldBeEmpty()
    }

    test("rolls back all local refs when a branch already exists locally") {
        // given: pre-create a branch for lib
        val executor = GitCommandExecutor(logger)
        val releaseExecutor = GitReleaseExecutor(repoListener.repo.localDir, executor, logger)
        releaseExecutor.createBranchLocally("release/lib/v0.1.x")

        val creator = createCreator()
        val projects = mapOf(
            ":app" to "app",
            ":lib" to "lib"
        )

        // when / then
        val ex = shouldThrow<GradleException> {
            creator.releaseProjects(projects, "release", Scope.MINOR)
        }
        ex.message shouldContain "already exists locally"

        // neither tag nor branch pushed to remote
        repoListener.repo.remoteTagExists("release/app/v0.1.0") shouldBe false
        repoListener.repo.remoteTagExists("release/lib/v0.1.0") shouldBe false
        repoListener.repo.remoteBranchExists("release/app/v0.1.x") shouldBe false
        repoListener.repo.remoteBranchExists("release/lib/v0.1.x") shouldBe false
        // local tags also rolled back
        repoListener.repo.localTagExists("release/app/v0.1.0") shouldBe false
    }
})
