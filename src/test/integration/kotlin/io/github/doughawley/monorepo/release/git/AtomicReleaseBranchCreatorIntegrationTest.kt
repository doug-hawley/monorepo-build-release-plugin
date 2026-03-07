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

class AtomicReleaseBranchCreatorIntegrationTest : FunSpec({

    val repoListener = TempGitRepoListener()
    listener(repoListener)

    val logger = mockk<Logger>(relaxed = true)

    fun createCreator(): AtomicReleaseBranchCreator {
        val executor = GitCommandExecutor(logger)
        val releaseExecutor = GitReleaseExecutor(repoListener.repo.localDir, executor, logger)
        val tagScanner = GitTagScanner(repoListener.repo.localDir, executor)
        return AtomicReleaseBranchCreator(releaseExecutor, tagScanner, logger)
    }

    test("creates release branches for multiple projects and pushes them atomically") {
        // given
        val creator = createCreator()
        val projects = mapOf(
            ":app" to "app",
            ":lib" to "lib"
        )

        // when
        val result = creator.createReleaseBranches(projects, "release", Scope.MINOR)

        // then
        result.createdBranches shouldContainExactlyInAnyOrder listOf("release/app/v0.1.x", "release/lib/v0.1.x")
        result.projectToBranch[":app"] shouldBe "release/app/v0.1.x"
        result.projectToBranch[":lib"] shouldBe "release/lib/v0.1.x"
        repoListener.repo.remoteBranchExists("release/app/v0.1.x") shouldBe true
        repoListener.repo.remoteBranchExists("release/lib/v0.1.x") shouldBe true
    }

    test("returns empty result when project map is empty") {
        // given
        val creator = createCreator()

        // when
        val result = creator.createReleaseBranches(emptyMap(), "release", Scope.MINOR)

        // then
        result.createdBranches.shouldBeEmpty()
        result.projectToBranch.shouldBeEmpty()
    }

    test("bumps version based on existing tags") {
        // given: app already has v0.1.0
        repoListener.repo.pushTag("release/app/v0.1.0")
        val creator = createCreator()
        val projects = mapOf(":app" to "app")

        // when
        val result = creator.createReleaseBranches(projects, "release", Scope.MINOR)

        // then: next minor is v0.2.x
        result.createdBranches shouldContainExactlyInAnyOrder listOf("release/app/v0.2.x")
        repoListener.repo.remoteBranchExists("release/app/v0.2.x") shouldBe true
    }

    test("uses major scope when requested") {
        // given: app already has v0.1.0
        repoListener.repo.pushTag("release/app/v0.1.0")
        val creator = createCreator()
        val projects = mapOf(":app" to "app")

        // when
        val result = creator.createReleaseBranches(projects, "release", Scope.MAJOR)

        // then: major bump → v1.0.x
        result.createdBranches shouldContainExactlyInAnyOrder listOf("release/app/v1.0.x")
        repoListener.repo.remoteBranchExists("release/app/v1.0.x") shouldBe true
    }

    test("rolls back all local branches when one already exists locally") {
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
            creator.createReleaseBranches(projects, "release", Scope.MINOR)
        }
        ex.message shouldContain "already exists locally"

        // neither branch pushed to remote
        repoListener.repo.remoteBranchExists("release/app/v0.1.x") shouldBe false
        repoListener.repo.remoteBranchExists("release/lib/v0.1.x") shouldBe false
    }
})
