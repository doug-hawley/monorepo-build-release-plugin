package io.github.doughawley.monorepo.build.git

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.io.File
import java.nio.file.Files

class GitRepositoryTest : FunSpec({

    lateinit var repoDir: File
    val logger = mockk<org.gradle.api.logging.Logger>(relaxed = true)

    beforeEach {
        repoDir = Files.createTempDirectory("test-git-repo").toFile()
        git(repoDir, "init")
        git(repoDir, "config", "user.email", "test@example.com")
        git(repoDir, "config", "user.name", "Test User")
        File(repoDir, "initial.txt").writeText("initial content")
        git(repoDir, "add", "initial.txt")
        git(repoDir, "commit", "-m", "initial commit")
    }

    afterEach {
        repoDir.deleteRecursively()
    }

    // --- isRepository ---

    test("isRepository returns true when directory is inside a git repository") {
        GitRepository(repoDir, logger).isRepository() shouldBe true
    }

    test("isRepository returns true when started from a subdirectory of the repository") {
        // given
        val subDir = File(repoDir, "sub/dir").also { it.mkdirs() }

        // when / then
        GitRepository(subDir, logger).isRepository() shouldBe true
    }

    test("isRepository returns false when no git repository exists") {
        // given
        val nonGitDir = Files.createTempDirectory("test-no-git").toFile()
        try {
            GitRepository(nonGitDir, logger).isRepository() shouldBe false
        } finally {
            nonGitDir.deleteRecursively()
        }
    }

    // --- diffFromRef ---

    test("diffFromRef returns files changed since the given commit ref") {
        // given
        val initialSha = captureOutput(repoDir, "rev-parse", "HEAD")
        File(repoDir, "added.kt").writeText("new code")
        git(repoDir, "add", "added.kt")
        git(repoDir, "commit", "-m", "add file")

        // when
        val result = GitRepository(repoDir, logger).diffFromRef(initialSha)

        // then
        result shouldContain "added.kt"
    }

    test("diffFromRef returns empty list when nothing has changed since the ref") {
        // given — diff HEAD against itself
        val headSha = captureOutput(repoDir, "rev-parse", "HEAD")

        // when
        val result = GitRepository(repoDir, logger).diffFromRef(headSha)

        // then
        result.shouldBeEmpty()
    }

    test("diffFromRef throws IllegalArgumentException when ref does not exist") {
        // given — add a second commit so this is NOT an initial-commit repo
        File(repoDir, "second.txt").writeText("second")
        git(repoDir, "add", "second.txt")
        git(repoDir, "commit", "-m", "second commit")

        // when / then
        shouldThrow<IllegalArgumentException> {
            GitRepository(repoDir, logger).diffFromRef("nonexistent-ref-xyz")
        }
    }

    test("diffFromRef falls back to empty-tree diff on initial commit when ref does not exist") {
        // given — repo has only one commit (initial), so HEAD~1 does not exist

        // when
        val result = GitRepository(repoDir, logger).diffFromRef("HEAD~1")

        // then — all files from the initial commit are returned
        result shouldContain "initial.txt"
    }

    // --- workingTreeChanges ---

    test("workingTreeChanges returns files modified in the working tree") {
        // given — modify a committed file without staging it
        File(repoDir, "initial.txt").appendText("\nmodified")

        // when
        val result = GitRepository(repoDir, logger).workingTreeChanges()

        // then
        result shouldContain "initial.txt"
    }

    test("workingTreeChanges returns empty list when working tree is clean") {
        GitRepository(repoDir, logger).workingTreeChanges().shouldBeEmpty()
    }

    // --- stagedFiles ---

    test("stagedFiles returns files that have been staged") {
        // given
        File(repoDir, "staged.kt").writeText("staged content")
        git(repoDir, "add", "staged.kt")

        // when
        val result = GitRepository(repoDir, logger).stagedFiles()

        // then
        result shouldContain "staged.kt"
    }

    test("stagedFiles returns empty list when nothing is staged") {
        GitRepository(repoDir, logger).stagedFiles().shouldBeEmpty()
    }

    // --- untrackedFiles ---

    test("untrackedFiles returns files that are not tracked by git") {
        // given
        File(repoDir, "untracked.kt").writeText("untracked content")

        // when
        val result = GitRepository(repoDir, logger).untrackedFiles()

        // then
        result shouldContain "untracked.kt"
    }

    test("untrackedFiles does not return staged files") {
        // given
        File(repoDir, "staged.kt").writeText("content")
        git(repoDir, "add", "staged.kt")

        // when
        val result = GitRepository(repoDir, logger).untrackedFiles()

        // then
        result shouldNotContain "staged.kt"
    }

    test("untrackedFiles returns empty list when no untracked files exist") {
        GitRepository(repoDir, logger).untrackedFiles().shouldBeEmpty()
    }

    // --- refExists ---

    test("refExists returns true for a branch that exists") {
        git(repoDir, "branch", "my-branch")
        GitRepository(repoDir, logger).refExists("my-branch") shouldBe true
    }

    test("refExists returns true for a remote tracking ref created manually") {
        git(repoDir, "update-ref", "refs/remotes/origin/main", "HEAD")
        GitRepository(repoDir, logger).refExists("origin/main") shouldBe true
    }

    test("refExists returns false for a ref that does not exist") {
        GitRepository(repoDir, logger).refExists("nonexistent-branch") shouldBe false
    }
})

private fun git(directory: File, vararg command: String) {
    val process = ProcessBuilder("git", *command)
        .directory(directory)
        .redirectErrorStream(true)
        .start()
    process.waitFor()
    if (process.exitValue() != 0) {
        val error = process.inputStream.bufferedReader().readText()
        throw RuntimeException("git ${command.joinToString(" ")} failed:\n$error")
    }
}

private fun captureOutput(directory: File, vararg command: String): String {
    val process = ProcessBuilder("git", *command)
        .directory(directory)
        .redirectErrorStream(false)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    return output
}
