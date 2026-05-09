package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.io.File

class DevTagScannerTest : FunSpec({

    val executor = mockk<GitCommandExecutor>()
    val rootDir = File("/fake/root")
    val scanner = DevTagScanner(rootDir, executor)

    afterEach { clearAllMocks() }

    // findLatestDevTagNumber

    test("findLatestDevTagNumber returns null when remote has no matching tags") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/dev/app/feature-auth/*")
        } returns emptyList()

        // when
        val result = scanner.findLatestDevTagNumber("app", "feature-auth")

        // then
        result.shouldBeNull()
    }

    test("findLatestDevTagNumber returns the maximum number from ls-remote output") {
        // given
        val lines = listOf(
            "abc123\trefs/tags/dev/app/feature-auth/1",
            "def456\trefs/tags/dev/app/feature-auth/3",
            "ghi789\trefs/tags/dev/app/feature-auth/2",
        )
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/dev/app/feature-auth/*")
        } returns lines

        // when
        val result = scanner.findLatestDevTagNumber("app", "feature-auth")

        // then
        result shouldBe 3
    }

    test("findLatestDevTagNumber ignores malformed tag lines") {
        // given
        val lines = listOf(
            "abc123\trefs/tags/dev/app/feature-auth/1",
            "bad-line-with-no-tab",
            "abc123\trefs/tags/dev/app/feature-auth/notanumber",
        )
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/dev/app/feature-auth/*")
        } returns lines

        // when
        val result = scanner.findLatestDevTagNumber("app", "feature-auth")

        // then
        result shouldBe 1
    }

    test("findLatestDevTagNumber uses projectPrefix and branchName in the ref pattern") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/dev/services/auth/experiment/*")
        } returns listOf("abc123\trefs/tags/dev/services/auth/experiment/5")

        // when
        val result = scanner.findLatestDevTagNumber("services/auth", "experiment")

        // then
        result shouldBe 5
    }

    test("findLatestDevTagNumber throws when git ls-remote fails") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/dev/app/feature-auth/*")
        } throws RuntimeException("Git command failed (exit code 128): fatal: could not read from remote repository")

        // when / then
        val ex = shouldThrow<RuntimeException> { scanner.findLatestDevTagNumber("app", "feature-auth") }
        ex.message shouldContain "Git command failed"
    }

    // devBranchExistsOnRemote

    test("devBranchExistsOnRemote returns true when remote has the branch") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", "refs/heads/dev/app/feature-auth")
        } returns listOf("abc123\trefs/heads/dev/app/feature-auth")

        // when
        val result = scanner.devBranchExistsOnRemote("app", "feature-auth")

        // then
        result shouldBe true
    }

    test("devBranchExistsOnRemote returns false when remote does not have the branch") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", "refs/heads/dev/app/feature-auth")
        } returns emptyList()

        // when
        val result = scanner.devBranchExistsOnRemote("app", "feature-auth")

        // then
        result shouldBe false
    }

    test("devBranchExistsOnRemote throws when git ls-remote fails") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", "refs/heads/dev/app/feature-auth")
        } throws RuntimeException("Git command failed (exit code 128): fatal: could not read from remote repository")

        // when / then
        shouldThrow<RuntimeException> { scanner.devBranchExistsOnRemote("app", "feature-auth") }
    }
})
