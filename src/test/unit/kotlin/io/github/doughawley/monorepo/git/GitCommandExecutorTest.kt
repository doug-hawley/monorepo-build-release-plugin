package io.github.doughawley.monorepo.git

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files

class GitCommandExecutorTest : FunSpec({

    test("should execute successful git command and return output") {
        // given
        val tempDir = Files.createTempDirectory("test-git-success").toFile()
        executeGitCommand(tempDir, "init")
        executeGitCommand(tempDir, "config", "user.email", "test@example.com")
        executeGitCommand(tempDir, "config", "user.name", "Test User")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(tempDir, "status", "--short")

        // then
        result.success shouldBe true
        result.exitCode shouldBe 0
        result.errorOutput shouldBe ""
        tempDir.deleteRecursively()
    }

    test("should return empty output for successful command with no results") {
        // given
        val tempDir = Files.createTempDirectory("test-git-empty").toFile()
        executeGitCommand(tempDir, "init")
        executeGitCommand(tempDir, "config", "user.email", "test@example.com")
        executeGitCommand(tempDir, "config", "user.name", "Test User")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(tempDir, "diff", "--name-only")

        // then
        result.success shouldBe true
        result.output.shouldBeEmpty()
        result.exitCode shouldBe 0
        tempDir.deleteRecursively()
    }

    test("should handle failed git command with non-zero exit code") {
        // given
        val tempDir = Files.createTempDirectory("test-git-fail").toFile()
        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(tempDir, "status")

        // then
        result.success shouldBe false
        result.output.shouldBeEmpty()
        result.exitCode shouldBe 128  // git exits with 128 when not in a repo
        result.errorOutput.isNotEmpty() shouldBe true
        tempDir.deleteRecursively()
    }

    test("should filter out blank lines from git output") {
        // given
        val tempDir = Files.createTempDirectory("test-git-blanks").toFile()
        executeGitCommand(tempDir, "init")
        executeGitCommand(tempDir, "config", "user.email", "test@example.com")
        executeGitCommand(tempDir, "config", "user.name", "Test User")

        // Create and add a file
        File(tempDir, "test.txt").writeText("content")
        executeGitCommand(tempDir, "add", "test.txt")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(tempDir, "diff", "--name-only", "--cached")

        // then
        result.success shouldBe true
        result.output shouldContain "test.txt"
        // Verify no blank lines in output
        result.output.forEach { line ->
            line.isNotBlank() shouldBe true
        }
        tempDir.deleteRecursively()
    }

    test("should execute diff command and return changed files") {
        // given
        val tempDir = Files.createTempDirectory("test-git-diff").toFile()
        executeGitCommand(tempDir, "init")
        executeGitCommand(tempDir, "config", "user.email", "test@example.com")
        executeGitCommand(tempDir, "config", "user.name", "Test User")

        // Create initial commit
        File(tempDir, "initial.txt").writeText("initial")
        executeGitCommand(tempDir, "add", "initial.txt")
        executeGitCommand(tempDir, "commit", "-m", "initial")

        // Stage new file
        File(tempDir, "changed.kt").writeText("changed content")
        executeGitCommand(tempDir, "add", "changed.kt")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(tempDir, "diff", "--name-only", "--cached")

        // then
        result.success shouldBe true
        result.output.size shouldBe 1
        result.output shouldContain "changed.kt"
        tempDir.deleteRecursively()
    }

    test("should execute ls-files command and return untracked files") {
        // given
        val tempDir = Files.createTempDirectory("test-git-ls-files").toFile()
        executeGitCommand(tempDir, "init")
        executeGitCommand(tempDir, "config", "user.email", "test@example.com")
        executeGitCommand(tempDir, "config", "user.name", "Test User")

        // Create untracked file
        File(tempDir, "untracked.kt").writeText("untracked content")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(tempDir, "ls-files", "--others", "--exclude-standard")

        // then
        result.success shouldBe true
        result.output shouldContain "untracked.kt"
        tempDir.deleteRecursively()
    }

    test("should handle multiple files in output") {
        // given
        val tempDir = Files.createTempDirectory("test-git-multiple").toFile()
        executeGitCommand(tempDir, "init")
        executeGitCommand(tempDir, "config", "user.email", "test@example.com")
        executeGitCommand(tempDir, "config", "user.name", "Test User")

        // Create initial commit
        File(tempDir, "initial.txt").writeText("initial")
        executeGitCommand(tempDir, "add", "initial.txt")
        executeGitCommand(tempDir, "commit", "-m", "initial")

        // Stage multiple files
        File(tempDir, "file1.kt").writeText("content 1")
        File(tempDir, "file2.kt").writeText("content 2")
        File(tempDir, "file3.kt").writeText("content 3")
        executeGitCommand(tempDir, "add", ".")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(tempDir, "diff", "--name-only", "--cached")

        // then
        result.success shouldBe true
        result.output.size shouldBe 3
        result.output shouldContain "file1.kt"
        result.output shouldContain "file2.kt"
        result.output shouldContain "file3.kt"
        tempDir.deleteRecursively()
    }

    test("executeForOutput should return output on success") {
        // given
        val tempDir = Files.createTempDirectory("test-for-output-success").toFile()
        executeGitCommand(tempDir, "init")
        executeGitCommand(tempDir, "config", "user.email", "test@example.com")
        executeGitCommand(tempDir, "config", "user.name", "Test User")

        File(tempDir, "test.txt").writeText("content")
        executeGitCommand(tempDir, "add", "test.txt")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val output = executor.executeForOutput(tempDir, "diff", "--name-only", "--cached")

        // then
        output shouldContain "test.txt"
        tempDir.deleteRecursively()
    }

    test("executeForOutput should return empty list on failure") {
        // given
        val tempDir = Files.createTempDirectory("test-for-output-fail").toFile()
        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val output = executor.executeForOutput(tempDir, "status")

        // then
        output.shouldBeEmpty()
        tempDir.deleteRecursively()
    }

    test("should handle files in subdirectories") {
        // given
        val tempDir = Files.createTempDirectory("test-git-subdirs").toFile()
        executeGitCommand(tempDir, "init")
        executeGitCommand(tempDir, "config", "user.email", "test@example.com")
        executeGitCommand(tempDir, "config", "user.name", "Test User")

        // Create initial commit
        File(tempDir, "initial.txt").writeText("initial")
        executeGitCommand(tempDir, "add", "initial.txt")
        executeGitCommand(tempDir, "commit", "-m", "initial")

        // Create files in subdirectories
        val subDir = File(tempDir, "src/main/kotlin")
        subDir.mkdirs()
        File(subDir, "App.kt").writeText("app code")
        executeGitCommand(tempDir, "add", ".")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(tempDir, "diff", "--name-only", "--cached")

        // then
        result.success shouldBe true
        result.output shouldContain "src/main/kotlin/App.kt"
        tempDir.deleteRecursively()
    }

    test("should handle exception when process fails to start") {
        // given
        val nonExistentDir = File("/nonexistent/path/that/does/not/exist")
        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(nonExistentDir, "status")

        // then
        result.success shouldBe false
        result.output.shouldBeEmpty()
        result.exitCode shouldBe -1
        result.errorOutput.isNotEmpty() shouldBe true
    }

    test("should handle invalid git command") {
        // given
        val tempDir = Files.createTempDirectory("test-invalid-cmd").toFile()
        executeGitCommand(tempDir, "init")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.execute(tempDir, "invalid-command-xyz")

        // then
        result.success shouldBe false
        result.output.shouldBeEmpty()
        result.exitCode shouldBe 1
        tempDir.deleteRecursively()
    }

    // --- executeSilently ---

    test("executeSilently should return success for a valid command") {
        // given
        val tempDir = Files.createTempDirectory("test-silent-success").toFile()
        executeGitCommand(tempDir, "init")
        executeGitCommand(tempDir, "config", "user.email", "test@example.com")
        executeGitCommand(tempDir, "config", "user.name", "Test User")

        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.executeSilently(tempDir, "status", "--short")

        // then
        result.success shouldBe true
        result.exitCode shouldBe 0
        tempDir.deleteRecursively()
    }

    test("executeSilently should return failure without logging warnings") {
        // given
        val tempDir = Files.createTempDirectory("test-silent-fail").toFile()
        val logger = ProjectBuilder.builder().build().logger
        val executor = GitCommandExecutor(logger)

        // when
        val result = executor.executeSilently(tempDir, "status")

        // then
        result.success shouldBe false
        result.output.shouldBeEmpty()
        result.exitCode shouldBe 128
        result.errorOutput.isNotEmpty() shouldBe true
        tempDir.deleteRecursively()
    }
})

private fun executeGitCommand(directory: File, vararg command: String) {
    val fullCommand = arrayOf("git") + command
    val process = ProcessBuilder(*fullCommand)
        .directory(directory)
        .redirectErrorStream(true)
        .start()

    process.waitFor()
    if (process.exitValue() != 0) {
        val error = process.inputStream.bufferedReader().readText()
        throw RuntimeException("Git command failed: ${command.joinToString(" ")}\n$error")
    }
}
