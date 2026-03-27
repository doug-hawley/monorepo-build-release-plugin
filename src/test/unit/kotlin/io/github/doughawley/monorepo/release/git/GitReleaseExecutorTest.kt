package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.github.doughawley.monorepo.git.GitCommandExecutor.CommandResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File

class GitReleaseExecutorTest : FunSpec({

    val executor = mockk<GitCommandExecutor>()
    val logger = mockk<Logger>(relaxed = true)
    val rootDir = File("/fake/root")
    val releaseExecutor = GitReleaseExecutor(rootDir, executor, logger)

    afterEach { clearAllMocks() }

    // isDirty

    test("isDirty returns false when working tree is clean") {
        // given
        every { executor.execute(rootDir, "status", "--porcelain") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when / then
        releaseExecutor.isDirty() shouldBe false
    }

    test("isDirty returns true when there are uncommitted changes") {
        // given
        every { executor.execute(rootDir, "status", "--porcelain") } returns
            CommandResult(success = true, output = listOf(" M src/Main.kt"), exitCode = 0)

        // when / then
        releaseExecutor.isDirty() shouldBe true
    }

    test("isDirty returns false when output contains only non-porcelain lines like stderr trace output") {
        // given: trace output from GIT_TRACE=1 merged via redirectErrorStream(true)
        every { executor.execute(rootDir, "status", "--porcelain") } returns
            CommandResult(success = true, output = listOf("trace: built-in: git status --porcelain"), exitCode = 0)

        // when / then
        releaseExecutor.isDirty() shouldBe false
    }

    test("isDirty throws GradleException when git status command fails") {
        // given
        every { executor.execute(rootDir, "status", "--porcelain") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 128, errorOutput = "fatal: not a git repository")

        // when / then
        val ex = shouldThrow<GradleException> { releaseExecutor.isDirty() }
        ex.message shouldContain "Failed to check working tree status"
    }

    // currentBranch

    test("currentBranch returns the branch name from git output") {
        // given
        every { executor.execute(rootDir, "rev-parse", "--abbrev-ref", "HEAD") } returns
            CommandResult(success = true, output = listOf("main"), exitCode = 0)

        // when / then
        releaseExecutor.currentBranch() shouldBe "main"
    }

    test("currentBranch trims whitespace from output") {
        // given
        every { executor.execute(rootDir, "rev-parse", "--abbrev-ref", "HEAD") } returns
            CommandResult(success = true, output = listOf("  release/app/v1.0.x  "), exitCode = 0)

        // when / then
        releaseExecutor.currentBranch() shouldBe "release/app/v1.0.x"
    }

    test("currentBranch throws GradleException when git command fails") {
        // given
        every { executor.execute(rootDir, "rev-parse", "--abbrev-ref", "HEAD") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 128, errorOutput = "fatal: not a git repo")

        // when / then
        val ex = shouldThrow<GradleException> { releaseExecutor.currentBranch() }
        ex.message shouldContain "Failed to determine current git branch"
    }

    test("currentBranch throws GradleException when output is empty despite success") {
        // given
        every { executor.execute(rootDir, "rev-parse", "--abbrev-ref", "HEAD") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when / then
        shouldThrow<GradleException> { releaseExecutor.currentBranch() }
    }

    test("currentBranch returns correct branch when trace output precedes actual output") {
        // given: trace output from GIT_TRACE=1 appears before the branch name
        every { executor.execute(rootDir, "rev-parse", "--abbrev-ref", "HEAD") } returns
            CommandResult(
                success = true,
                output = listOf("trace: built-in: git rev-parse --abbrev-ref HEAD", "main"),
                exitCode = 0
            )

        // when / then
        releaseExecutor.currentBranch() shouldBe "main"
    }

    // createTagLocally

    test("createTagLocally succeeds when git tag command exits zero") {
        // given
        every { executor.execute(rootDir, "tag", "release/app/v1.0.0") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when / then — no exception
        releaseExecutor.createTagLocally("release/app/v1.0.0")
    }

    test("createTagLocally throws GradleException when git tag command fails") {
        // given
        every { executor.execute(rootDir, "tag", "release/app/v1.0.0") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 128, errorOutput = "tag already exists")

        // when / then
        val ex = shouldThrow<GradleException> { releaseExecutor.createTagLocally("release/app/v1.0.0") }
        ex.message shouldContain "Failed to create local tag"
        ex.message shouldContain "release/app/v1.0.0"
    }

    // createBranchLocally

    test("createBranchLocally succeeds when git branch command exits zero") {
        // given
        every { executor.execute(rootDir, "branch", "release/app/v1.0.x") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when / then — no exception
        releaseExecutor.createBranchLocally("release/app/v1.0.x")
    }

    test("createBranchLocally throws GradleException when git branch command fails") {
        // given
        every { executor.execute(rootDir, "branch", "release/app/v1.0.x") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 128, errorOutput = "branch already exists")

        // when / then
        val ex = shouldThrow<GradleException> { releaseExecutor.createBranchLocally("release/app/v1.0.x") }
        ex.message shouldContain "Failed to create local branch"
        ex.message shouldContain "release/app/v1.0.x"
    }

    // pushTag

    test("pushTag executes push origin with the tag") {
        // given
        every { executor.execute(rootDir, "push", "origin", "release/app/v1.0.0") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when
        releaseExecutor.pushTag("release/app/v1.0.0")

        // then
        verify { executor.execute(rootDir, "push", "origin", "release/app/v1.0.0") }
    }

    test("pushTag throws GradleException when push fails") {
        // given
        every { executor.execute(rootDir, "push", "origin", "release/app/v1.0.0") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 1, errorOutput = "remote rejected")

        // when / then
        val ex = shouldThrow<GradleException> { releaseExecutor.pushTag("release/app/v1.0.0") }
        ex.message shouldContain "Failed to push tag"
    }

    // pushBranch

    test("pushBranch executes push origin with the branch") {
        // given
        every { executor.execute(rootDir, "push", "origin", "release/app/v1.0.x") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when
        releaseExecutor.pushBranch("release/app/v1.0.x")

        // then
        verify { executor.execute(rootDir, "push", "origin", "release/app/v1.0.x") }
    }

    test("pushBranch throws GradleException when push fails") {
        // given
        every { executor.execute(rootDir, "push", "origin", "release/app/v1.0.x") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 1, errorOutput = "already exists")

        // when / then
        val ex = shouldThrow<GradleException> { releaseExecutor.pushBranch("release/app/v1.0.x") }
        ex.message shouldContain "Failed to push branch"
    }

    // deleteLocalTag

    test("deleteLocalTag succeeds without throwing") {
        // given
        every { executor.execute(rootDir, "tag", "-d", "release/app/v1.0.0") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when / then — no exception
        releaseExecutor.deleteLocalTag("release/app/v1.0.0")
    }

    test("deleteLocalTag logs a warning and does not throw when deletion fails") {
        // given
        every { executor.execute(rootDir, "tag", "-d", "release/app/v1.0.0") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 1, errorOutput = "tag not found")

        // when / then — no exception
        releaseExecutor.deleteLocalTag("release/app/v1.0.0")
        verify { logger.warn(any()) }
    }

    // deleteLocalBranch

    test("deleteLocalBranch succeeds without throwing") {
        // given
        every { executor.execute(rootDir, "branch", "-D", "release/app/v1.0.x") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when / then — no exception
        releaseExecutor.deleteLocalBranch("release/app/v1.0.x")
    }

    test("deleteLocalBranch logs a warning and does not throw when deletion fails") {
        // given
        every { executor.execute(rootDir, "branch", "-D", "release/app/v1.0.x") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 1, errorOutput = "branch not found")

        // when / then — no exception
        releaseExecutor.deleteLocalBranch("release/app/v1.0.x")
        verify { logger.warn(any()) }
    }

    // pushRefsAtomically

    test("pushRefsAtomically pushes tags and branches in a single atomic call") {
        // given
        val refs = listOf("release/app/v0.1.0", "release/app/v0.1.x")
        every { executor.execute(rootDir, "push", "--atomic", "origin", "release/app/v0.1.0", "release/app/v0.1.x") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when
        releaseExecutor.pushRefsAtomically(refs)

        // then
        verify { executor.execute(rootDir, "push", "--atomic", "origin", "release/app/v0.1.0", "release/app/v0.1.x") }
    }

    test("pushRefsAtomically throws GradleException when push fails") {
        // given
        val refs = listOf("release/app/v0.1.0", "release/app/v0.1.x")
        every { executor.execute(rootDir, "push", "--atomic", "origin", "release/app/v0.1.0", "release/app/v0.1.x") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 1, errorOutput = "atomic push rejected")

        // when / then
        val ex = shouldThrow<GradleException> { releaseExecutor.pushRefsAtomically(refs) }
        ex.message shouldContain "Atomic push of 2 ref(s) failed"
    }

    // branchExistsLocally

    test("branchExistsLocally returns true when output contains the branch name") {
        // given
        every { executor.execute(rootDir, "branch", "--list", "release/app/v0.1.x") } returns
            CommandResult(success = true, output = listOf("  release/app/v0.1.x"), exitCode = 0)

        // when / then
        releaseExecutor.branchExistsLocally("release/app/v0.1.x") shouldBe true
    }

    test("branchExistsLocally returns true when branch is the current branch") {
        // given: git branch --list shows current branch with asterisk prefix
        every { executor.execute(rootDir, "branch", "--list", "release/app/v0.1.x") } returns
            CommandResult(success = true, output = listOf("* release/app/v0.1.x"), exitCode = 0)

        // when / then
        releaseExecutor.branchExistsLocally("release/app/v0.1.x") shouldBe true
    }

    test("branchExistsLocally returns false when output is empty") {
        // given
        every { executor.execute(rootDir, "branch", "--list", "release/app/v0.1.x") } returns
            CommandResult(success = true, output = emptyList(), exitCode = 0)

        // when / then
        releaseExecutor.branchExistsLocally("release/app/v0.1.x") shouldBe false
    }

    test("branchExistsLocally returns false when output contains only non-branch lines like stderr trace output") {
        // given: trace output from GIT_TRACE=1 merged via redirectErrorStream(true)
        every { executor.execute(rootDir, "branch", "--list", "release/app/v0.1.x") } returns
            CommandResult(success = true, output = listOf("trace: built-in: git branch --list release/app/v0.1.x"), exitCode = 0)

        // when / then
        releaseExecutor.branchExistsLocally("release/app/v0.1.x") shouldBe false
    }

    test("branchExistsLocally throws GradleException when command fails") {
        // given
        every { executor.execute(rootDir, "branch", "--list", "release/app/v0.1.x") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 128, errorOutput = "fatal error")

        // when / then
        val ex = shouldThrow<GradleException> { releaseExecutor.branchExistsLocally("release/app/v0.1.x") }
        ex.message shouldContain "Failed to list local branches"
    }

    // branchExistsOnRemote

    test("branchExistsOnRemote returns true when ls-remote exits with code 0") {
        // given
        every { executor.executeSilently(rootDir, "ls-remote", "--exit-code", "--heads", "origin", "release/app/v0.1.x") } returns
            CommandResult(success = true, output = listOf("abc123\trefs/heads/release/app/v0.1.x"), exitCode = 0)

        // when / then
        releaseExecutor.branchExistsOnRemote("release/app/v0.1.x") shouldBe true
    }

    test("branchExistsOnRemote returns false when ls-remote exits with code 2") {
        // given
        every { executor.executeSilently(rootDir, "ls-remote", "--exit-code", "--heads", "origin", "release/app/v0.1.x") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 2, errorOutput = "")

        // when / then
        releaseExecutor.branchExistsOnRemote("release/app/v0.1.x") shouldBe false
    }

    test("branchExistsOnRemote returns false when command fails") {
        // given
        every { executor.executeSilently(rootDir, "ls-remote", "--exit-code", "--heads", "origin", "release/app/v0.1.x") } returns
            CommandResult(success = false, output = emptyList(), exitCode = 128, errorOutput = "remote error")

        // when / then
        releaseExecutor.branchExistsOnRemote("release/app/v0.1.x") shouldBe false
    }
})
