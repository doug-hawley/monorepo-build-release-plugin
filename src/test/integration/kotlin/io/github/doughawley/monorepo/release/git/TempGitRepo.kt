package io.github.doughawley.monorepo.release.git

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import java.io.File
import java.nio.file.Files

class TempGitRepo {
    private val tempDir: File = Files.createTempDirectory("git-integration-test").toFile()
    val remoteDir: File = File(tempDir, "remote.git")
    val localDir: File = File(tempDir, "local")

    fun initialize() {
        localDir.mkdirs()
        runGit(localDir, "init")
        runGit(localDir, "config", "user.email", "test@test.com")
        runGit(localDir, "config", "user.name", "Test User")

        File(localDir, "README.md").writeText("Initial commit")
        runGit(localDir, "add", ".")
        runGit(localDir, "commit", "-m", "Initial commit")

        runGit(tempDir, "init", "--bare", remoteDir.absolutePath)
        runGit(localDir, "remote", "add", "origin", remoteDir.absolutePath)
        runGit(localDir, "push", "-u", "origin", "HEAD")
    }

    // --- Tag helpers ---

    fun pushTag(tag: String) {
        runGit(localDir, "tag", tag)
        runGit(localDir, "push", "origin", tag)
    }

    fun createLocalTag(tag: String) {
        runGit(localDir, "tag", tag)
    }

    fun deleteLocalTag(tag: String) {
        runGit(localDir, "tag", "-d", tag)
    }

    fun localTagExists(tag: String): Boolean {
        val output = runGitForOutput(localDir, "tag", "-l", tag)
        return output.isNotEmpty()
    }

    fun remoteTagExists(tag: String): Boolean {
        val refPattern = "refs/tags/$tag"
        val output = runGitForOutput(localDir, "ls-remote", "--tags", "origin", refPattern)
        return output.isNotEmpty()
    }

    // --- Branch helpers ---

    fun checkoutNewBranch(branch: String) {
        runGit(localDir, "checkout", "-b", branch)
    }

    fun localBranchExists(branch: String): Boolean {
        val output = runGitForOutput(localDir, "branch", "-l", branch)
        return output.isNotEmpty()
    }

    fun remoteBranchExists(branch: String): Boolean {
        val refPattern = "refs/heads/$branch"
        val output = runGitForOutput(localDir, "ls-remote", "--heads", "origin", refPattern)
        return output.isNotEmpty()
    }

    // --- Working tree helpers ---

    fun createUntrackedFile(name: String) {
        File(localDir, name).writeText("untracked content")
    }

    fun modifyTrackedFile(name: String, content: String) {
        File(localDir, name).writeText(content)
    }

    fun stageFile(name: String) {
        runGit(localDir, "add", name)
    }

    fun commitAll(message: String) {
        runGit(localDir, "add", ".")
        runGit(localDir, "commit", "-m", message)
    }

    // --- Cleanup ---

    fun deleteRecursively() {
        tempDir.deleteRecursively()
    }

    // --- Internal ---

    private fun runGit(dir: File, vararg args: String) {
        val cmd = listOf("git") + args.toList()
        val process = ProcessBuilder(cmd)
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException(
                "Git command failed (exit $exitCode): ${cmd.joinToString(" ")}\n$output"
            )
        }
    }

    private fun runGitForOutput(dir: File, vararg args: String): String {
        val cmd = listOf("git") + args.toList()
        val process = ProcessBuilder(cmd)
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output.trim()
    }
}

class TempGitRepoListener : TestListener {
    lateinit var repo: TempGitRepo
        private set

    override suspend fun beforeEach(testCase: TestCase) {
        repo = TempGitRepo()
        repo.initialize()
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        if (::repo.isInitialized) {
            repo.deleteRecursively()
        }
    }
}
