package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.github.doughawley.monorepo.git.GitCommandExecutor.CommandResult
import io.github.doughawley.monorepo.release.domain.SemanticVersion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.io.File

class GitTagScannerTest : FunSpec({

    val executor = mockk<GitCommandExecutor>()
    val rootDir = File("/fake/root")
    val scanner = GitTagScanner(rootDir, executor)

    afterEach { clearAllMocks() }

    // findLatestVersion

    test("findLatestVersion returns null when remote has no matching tags") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/release/app/v*")
        } returns emptyList()

        // when
        val result = scanner.findLatestVersion("release", "app")

        // then
        result.shouldBeNull()
    }

    test("findLatestVersion returns the maximum version from ls-remote output") {
        // given
        val lines = listOf(
            "abc123\trefs/tags/release/app/v0.1.0",
            "def456\trefs/tags/release/app/v0.2.0",
            "ghi789\trefs/tags/release/app/v0.1.5",
        )
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/release/app/v*")
        } returns lines

        // when
        val result = scanner.findLatestVersion("release", "app")

        // then
        result shouldBe SemanticVersion(0, 2, 0)
    }

    test("findLatestVersion ignores malformed tag lines") {
        // given
        val lines = listOf(
            "abc123\trefs/tags/release/app/v1.0.0",
            "bad-line-with-no-tab",
            "abc123\trefs/tags/release/app/not-semver",
        )
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/release/app/v*")
        } returns lines

        // when
        val result = scanner.findLatestVersion("release", "app")

        // then
        result shouldBe SemanticVersion(1, 0, 0)
    }

    test("findLatestVersion uses globalPrefix and projectPrefix in the ref pattern") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/custom/my-svc/v*")
        } returns listOf("abc123\trefs/tags/custom/my-svc/v2.3.4")

        // when
        val result = scanner.findLatestVersion("custom", "my-svc")

        // then
        result shouldBe SemanticVersion(2, 3, 4)
    }

    // findLatestVersionInLine

    test("findLatestVersionInLine returns null when no matching tags exist for the version line") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/release/app/v1.2.*")
        } returns emptyList()

        // when
        val result = scanner.findLatestVersionInLine("release", "app", 1, 2)

        // then
        result.shouldBeNull()
    }

    test("findLatestVersionInLine returns the highest patch version in the line") {
        // given
        val lines = listOf(
            "abc123\trefs/tags/release/app/v1.2.0",
            "def456\trefs/tags/release/app/v1.2.3",
            "ghi789\trefs/tags/release/app/v1.2.1",
        )
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/release/app/v1.2.*")
        } returns lines

        // when
        val result = scanner.findLatestVersionInLine("release", "app", 1, 2)

        // then
        result shouldBe SemanticVersion(1, 2, 3)
    }

    test("findLatestVersionInLine uses the major and minor in the ref pattern") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/release/app/v0.1.*")
        } returns listOf("abc123\trefs/tags/release/app/v0.1.7")

        // when
        val result = scanner.findLatestVersionInLine("release", "app", 0, 1)

        // then
        result shouldBe SemanticVersion(0, 1, 7)
    }

    // findLatestBranchVersion

    test("findLatestBranchVersion returns null when remote has no matching branches") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", "refs/heads/release/app/v*")
        } returns emptyList()

        // when
        val result = scanner.findLatestBranchVersion("release", "app")

        // then
        result.shouldBeNull()
    }

    test("findLatestBranchVersion returns the maximum version from ls-remote branch output") {
        // given
        val lines = listOf(
            "abc123\trefs/heads/release/app/v0.1.x",
            "def456\trefs/heads/release/app/v0.3.x",
            "ghi789\trefs/heads/release/app/v0.2.x",
        )
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", "refs/heads/release/app/v*")
        } returns lines

        // when
        val result = scanner.findLatestBranchVersion("release", "app")

        // then
        result shouldBe SemanticVersion(0, 3, 0)
    }

    test("findLatestBranchVersion ignores malformed branch names") {
        // given
        val lines = listOf(
            "abc123\trefs/heads/release/app/v1.0.x",
            "bad-line-with-no-tab",
            "abc123\trefs/heads/release/app/not-a-version",
        )
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", "refs/heads/release/app/v*")
        } returns lines

        // when
        val result = scanner.findLatestBranchVersion("release", "app")

        // then
        result shouldBe SemanticVersion(1, 0, 0)
    }

    test("findLatestBranchVersion uses globalPrefix and projectPrefix in the ref pattern") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", "refs/heads/custom/my-svc/v*")
        } returns listOf("abc123\trefs/heads/custom/my-svc/v2.3.x")

        // when
        val result = scanner.findLatestBranchVersion("custom", "my-svc")

        // then
        result shouldBe SemanticVersion(2, 3, 0)
    }

    // tagExists

    test("tagExists returns true when local tag list is non-empty") {
        // given
        every {
            executor.executeForOutput(rootDir, "tag", "-l", "release/app/v1.0.0")
        } returns listOf("release/app/v1.0.0")

        // when
        val result = scanner.tagExists("release/app/v1.0.0")

        // then
        result shouldBe true
    }

    test("tagExists returns false when local tag list is empty") {
        // given
        every {
            executor.executeForOutput(rootDir, "tag", "-l", "release/app/v1.0.0")
        } returns emptyList()

        // when
        val result = scanner.tagExists("release/app/v1.0.0")

        // then
        result shouldBe false
    }

    test("tagExists returns false when output contains only non-tag lines like stderr trace output") {
        // given: trace output from GIT_TRACE=1 merged via redirectErrorStream(true)
        every {
            executor.executeForOutput(rootDir, "tag", "-l", "release/app/v1.0.0")
        } returns listOf("trace: built-in: git tag -l release/app/v1.0.0")

        // when
        val result = scanner.tagExists("release/app/v1.0.0")

        // then
        result shouldBe false
    }

    // --- git command failure propagation ---

    test("findLatestVersion throws when git ls-remote fails") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/release/app/v*")
        } throws RuntimeException("Git command failed (exit code 128): git ls-remote --tags --refs origin refs/tags/release/app/v*\nfatal: could not read from remote repository")

        // when / then
        val ex = shouldThrow<RuntimeException> { scanner.findLatestVersion("release", "app") }
        ex.message shouldContain "Git command failed"
    }

    test("findLatestVersionInLine throws when git ls-remote fails") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--tags", "--refs", "origin", "refs/tags/release/app/v1.2.*")
        } throws RuntimeException("Git command failed (exit code 128): fatal: could not read from remote repository")

        // when / then
        shouldThrow<RuntimeException> { scanner.findLatestVersionInLine("release", "app", 1, 2) }
    }

    test("findLatestBranchVersion throws when git ls-remote fails") {
        // given
        every {
            executor.executeForOutput(rootDir, "ls-remote", "--heads", "origin", "refs/heads/release/app/v*")
        } throws RuntimeException("Git command failed (exit code 128): fatal: could not read from remote repository")

        // when / then
        shouldThrow<RuntimeException> { scanner.findLatestBranchVersion("release", "app") }
    }

    test("tagExists throws when git tag command fails") {
        // given
        every {
            executor.executeForOutput(rootDir, "tag", "-l", "release/app/v1.0.0")
        } throws RuntimeException("Git command failed (exit code 128): fatal: not a git repository")

        // when / then
        val ex = shouldThrow<RuntimeException> { scanner.tagExists("release/app/v1.0.0") }
        ex.message shouldContain "Git command failed"
    }
})
