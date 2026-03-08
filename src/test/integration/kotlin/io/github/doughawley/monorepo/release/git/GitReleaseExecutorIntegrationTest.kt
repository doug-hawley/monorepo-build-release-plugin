package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

class GitReleaseExecutorIntegrationTest : FunSpec({

    val repoListener = TempGitRepoListener()
    extension(repoListener)

    val logger = mockk<Logger>(relaxed = true)

    fun executor(): GitReleaseExecutor {
        return GitReleaseExecutor(repoListener.repo.localDir, GitCommandExecutor(logger), logger)
    }

    // isDirty

    test("isDirty returns false on a clean repo") {
        // given / when / then
        executor().isDirty() shouldBe false
    }

    test("isDirty returns true when an untracked file is present") {
        // given
        repoListener.repo.createUntrackedFile("new-file.txt")

        // when / then
        executor().isDirty() shouldBe true
    }

    test("isDirty returns true when a tracked file has unstaged modifications") {
        // given
        repoListener.repo.modifyTrackedFile("README.md", "modified content")

        // when / then
        executor().isDirty() shouldBe true
    }

    test("isDirty returns true when a change is staged but not yet committed") {
        // given
        repoListener.repo.modifyTrackedFile("README.md", "staged content")
        repoListener.repo.stageFile("README.md")

        // when / then
        executor().isDirty() shouldBe true
    }

    test("isDirty returns false after committing all staged changes") {
        // given
        repoListener.repo.modifyTrackedFile("README.md", "committed content")
        repoListener.repo.commitAll("update readme")

        // when / then
        executor().isDirty() shouldBe false
    }

    // currentBranch

    test("currentBranch returns the name of the current branch") {
        // given / when
        val branch = executor().currentBranch()

        // then — the exact default name varies by git config, but it must be non-blank
        branch.isNotBlank() shouldBe true
    }

    test("currentBranch returns the correct name for a slash-delimited release branch") {
        // given
        repoListener.repo.checkoutNewBranch("release/app/v1.0.x")

        // when / then
        executor().currentBranch() shouldBe "release/app/v1.0.x"
    }

    // createTagLocally

    test("createTagLocally creates a tag that is visible in the local repo") {
        // given / when
        executor().createTagLocally("release/app/v1.0.0")

        // then
        repoListener.repo.localTagExists("release/app/v1.0.0") shouldBe true
    }

    test("createTagLocally works correctly with slash-delimited tag names") {
        // given / when
        executor().createTagLocally("release/my-service/v2.3.4")

        // then
        repoListener.repo.localTagExists("release/my-service/v2.3.4") shouldBe true
    }

    test("createTagLocally throws GradleException when the tag already exists") {
        // given
        executor().createTagLocally("release/app/v1.0.0")

        // when / then
        val ex = shouldThrow<GradleException> { executor().createTagLocally("release/app/v1.0.0") }
        ex.message shouldContain "Failed to create local tag"
        ex.message shouldContain "release/app/v1.0.0"
    }

    // createBranchLocally

    test("createBranchLocally creates a branch that is visible in the local repo") {
        // given / when
        executor().createBranchLocally("release/app/v1.0.x")

        // then
        repoListener.repo.localBranchExists("release/app/v1.0.x") shouldBe true
    }

    test("createBranchLocally works correctly with slash-delimited branch names") {
        // given / when
        executor().createBranchLocally("release/my-service/v2.3.x")

        // then
        repoListener.repo.localBranchExists("release/my-service/v2.3.x") shouldBe true
    }

    test("createBranchLocally throws GradleException when the branch already exists") {
        // given
        executor().createBranchLocally("release/app/v1.0.x")

        // when / then
        val ex = shouldThrow<GradleException> { executor().createBranchLocally("release/app/v1.0.x") }
        ex.message shouldContain "Failed to create local branch"
        ex.message shouldContain "release/app/v1.0.x"
    }

    // pushTag / pushBranch

    test("pushTag pushes the tag to remote") {
        // given
        executor().createTagLocally("release/app/v1.0.0")

        // when
        executor().pushTag("release/app/v1.0.0")

        // then
        repoListener.repo.remoteTagExists("release/app/v1.0.0") shouldBe true
    }

    test("pushTag does not push any branch") {
        // given
        executor().createTagLocally("release/app/v1.0.0")
        executor().pushTag("release/app/v1.0.0")

        // then
        repoListener.repo.remoteBranchExists("release/app/v1.0.x") shouldBe false
    }

    test("pushTag and pushBranch together push both tag and branch to remote") {
        // given
        executor().createTagLocally("release/app/v1.0.0")
        executor().createBranchLocally("release/app/v1.0.x")

        // when
        executor().pushTag("release/app/v1.0.0")
        executor().pushBranch("release/app/v1.0.x")

        // then
        repoListener.repo.remoteTagExists("release/app/v1.0.0") shouldBe true
        repoListener.repo.remoteBranchExists("release/app/v1.0.x") shouldBe true
    }

    test("tag is absent from remote before push") {
        // given — tag created locally but not pushed
        executor().createTagLocally("release/app/v1.0.0")

        // then
        repoListener.repo.remoteTagExists("release/app/v1.0.0") shouldBe false
    }

    // deleteLocalTag

    test("deleteLocalTag removes the tag from the local repo") {
        // given
        executor().createTagLocally("release/app/v1.0.0")

        // when
        executor().deleteLocalTag("release/app/v1.0.0")

        // then
        repoListener.repo.localTagExists("release/app/v1.0.0") shouldBe false
    }

    test("deleteLocalTag does not affect a tag that was pushed to remote") {
        // given
        executor().createTagLocally("release/app/v1.0.0")
        executor().pushTag("release/app/v1.0.0")

        // when
        executor().deleteLocalTag("release/app/v1.0.0")

        // then — tag gone locally but still on remote
        repoListener.repo.localTagExists("release/app/v1.0.0") shouldBe false
        repoListener.repo.remoteTagExists("release/app/v1.0.0") shouldBe true
    }

    test("deleteLocalTag does not throw when the tag does not exist") {
        // given / when / then — no exception
        executor().deleteLocalTag("release/app/v9.9.9")
    }

    // deleteLocalBranch

    test("deleteLocalBranch removes the branch from the local repo") {
        // given
        executor().createBranchLocally("release/app/v1.0.x")

        // when
        executor().deleteLocalBranch("release/app/v1.0.x")

        // then
        repoListener.repo.localBranchExists("release/app/v1.0.x") shouldBe false
    }

    test("deleteLocalBranch force-deletes an unmerged branch without throwing") {
        // given — branch with no new commits is still considered "not fully merged"
        // from the perspective of git branch -d, but -D should always succeed
        executor().createBranchLocally("release/app/v1.0.x")

        // when / then — no exception
        executor().deleteLocalBranch("release/app/v1.0.x")
    }

    test("deleteLocalBranch does not throw when the branch does not exist") {
        // given / when / then — no exception
        executor().deleteLocalBranch("release/app/v9.9.x")
    }

    // branchExistsLocally

    test("branchExistsLocally returns true for a branch that exists") {
        // given
        executor().createBranchLocally("release/app/v1.0.x")

        // when / then
        executor().branchExistsLocally("release/app/v1.0.x") shouldBe true
    }

    test("branchExistsLocally returns false for a branch that does not exist") {
        // given / when / then
        executor().branchExistsLocally("release/nonexistent/v9.9.x") shouldBe false
    }

    test("branchExistsLocally returns false after a branch is deleted") {
        // given
        executor().createBranchLocally("release/app/v1.0.x")
        executor().deleteLocalBranch("release/app/v1.0.x")

        // when / then
        executor().branchExistsLocally("release/app/v1.0.x") shouldBe false
    }

    // pushBranchesAtomically

    test("pushBranchesAtomically pushes all branches to remote in one operation") {
        // given
        executor().createBranchLocally("release/app/v1.0.x")
        executor().createBranchLocally("release/lib/v2.0.x")

        // when
        executor().pushBranchesAtomically(listOf("release/app/v1.0.x", "release/lib/v2.0.x"))

        // then
        repoListener.repo.remoteBranchExists("release/app/v1.0.x") shouldBe true
        repoListener.repo.remoteBranchExists("release/lib/v2.0.x") shouldBe true
    }

    test("pushBranchesAtomically pushes a single branch") {
        // given
        executor().createBranchLocally("release/app/v1.0.x")

        // when
        executor().pushBranchesAtomically(listOf("release/app/v1.0.x"))

        // then
        repoListener.repo.remoteBranchExists("release/app/v1.0.x") shouldBe true
    }

    test("pushBranchesAtomically throws when a branch does not exist locally") {
        // given / when / then
        val ex = shouldThrow<GradleException> {
            executor().pushBranchesAtomically(listOf("release/nonexistent/v1.0.x"))
        }
        ex.message shouldContain "Atomic push"
        ex.message shouldContain "failed"
    }
})
