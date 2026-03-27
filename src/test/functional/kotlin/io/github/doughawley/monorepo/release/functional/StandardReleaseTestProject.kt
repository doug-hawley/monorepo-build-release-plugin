package io.github.doughawley.monorepo.release.functional

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

/**
 * Creates a minimal monorepo test project for release plugin functional testing.
 *
 * Project structure:
 * - root project (monorepo-build-release-plugin applied)
 * - :app subproject (monorepoProject { release { enabled = true } })
 *
 * Includes a bare git remote in a sibling directory for push testing.
 */
object StandardReleaseTestProject {

    fun create(
        projectDir: File,
        globalTagPrefix: String = "release",
        primaryBranchScope: String = "minor"
    ): ReleaseTestProject {
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        // Create root build.gradle.kts
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            monorepo {
                release {
                    globalTagPrefix = "$globalTagPrefix"
                    primaryBranchScope = "$primaryBranchScope"
                }
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
            }
            """.trimIndent()
        )

        // Create settings.gradle.kts
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":app")
            """.trimIndent()
        )

        // Create .gitignore
        File(projectDir, ".gitignore").writeText(
            """
            .gradle/
            .kotlin/
            build/
            """.trimIndent()
        )

        // Create app subproject
        val appDir = File(projectDir, "app")
        appDir.mkdirs()

        File(appDir, "build.gradle.kts").writeText(
            """
            monorepoProject {
                release {
                    enabled = true
                }
            }

            // Lightweight fake build task: creates the expected artifact so release can proceed
            tasks.register("build") {
                doLast {
                    val libsDir = layout.buildDirectory.dir("libs").get().asFile
                    libsDir.mkdirs()
                    java.io.File(libsDir, "${'$'}{project.name}.jar").writeText("built artifact")
                }
            }
            """.trimIndent()
        )

        val srcDir = File(appDir, "src/main/kotlin/com/example")
        srcDir.mkdirs()
        File(srcDir, "App.kt").writeText(
            """
            package com.example

            class App {
                fun run() = "Hello"
            }
            """.trimIndent()
        )

        return ReleaseTestProject(projectDir, remoteDir)
    }

    fun createAndInitialize(
        projectDir: File,
        globalTagPrefix: String = "release",
        primaryBranchScope: String = "minor"
    ): ReleaseTestProject {
        val project = create(projectDir, globalTagPrefix, primaryBranchScope)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()
        return project
    }

    /**
     * Creates a multi-project test setup with root, :app, and :lib subprojects.
     * Both subprojects opt in to releases by default.
     *
     * @param libEnabled whether :lib has enabled = true in monorepoProject { release { } }
     * @param primaryBranchScope scope used when releasing from the primary branch (default "minor")
     */
    fun createMultiProject(
        projectDir: File,
        globalTagPrefix: String = "release",
        libEnabled: Boolean = true,
        primaryBranchScope: String = "minor"
    ): ReleaseTestProject {
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            monorepo {
                release {
                    globalTagPrefix = "$globalTagPrefix"
                    primaryBranchScope = "$primaryBranchScope"
                }
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
            }
            """.trimIndent()
        )

        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":app")
            include(":lib")
            """.trimIndent()
        )

        File(projectDir, ".gitignore").writeText(
            """
            .gradle/
            .kotlin/
            build/
            """.trimIndent()
        )

        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText(
            """
            monorepoProject {
                release {
                    enabled = true
                }
            }

            // Lightweight fake build task: creates the expected artifact so release can proceed
            tasks.register("build") {
                doLast {
                    val libsDir = layout.buildDirectory.dir("libs").get().asFile
                    libsDir.mkdirs()
                    java.io.File(libsDir, "${'$'}{project.name}.jar").writeText("built artifact")
                }
            }
            """.trimIndent()
        )
        File(appDir, "app.txt").writeText("app source")

        val libDir = File(projectDir, "lib")
        libDir.mkdirs()
        File(libDir, "build.gradle.kts").writeText(
            """
            monorepoProject {
                release {
                    enabled = $libEnabled
                }
            }

            // Lightweight fake build task: creates the expected artifact so release can proceed
            tasks.register("build") {
                doLast {
                    val libsDir = layout.buildDirectory.dir("libs").get().asFile
                    libsDir.mkdirs()
                    java.io.File(libsDir, "${'$'}{project.name}.jar").writeText("built artifact")
                }
            }
            """.trimIndent()
        )
        File(libDir, "lib.txt").writeText("lib source")

        return ReleaseTestProject(projectDir, remoteDir)
    }

    fun createMultiProjectAndInitialize(
        projectDir: File,
        globalTagPrefix: String = "release",
        libEnabled: Boolean = true,
        primaryBranchScope: String = "minor"
    ): ReleaseTestProject {
        val project = createMultiProject(projectDir, globalTagPrefix, libEnabled, primaryBranchScope)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()
        return project
    }
}

class ReleaseTestProject(
    val projectDir: File,
    private val remoteDir: File
) {

    fun initGit() {
        // Create bare remote repository
        remoteDir.mkdirs()
        executeCommand(remoteDir, "git", "init", "--bare")

        // Initialize local repo
        executeCommand("git", "init")
        executeCommand("git", "config", "user.email", "test@example.com")
        executeCommand("git", "config", "user.name", "Test User")
        executeCommand("git", "checkout", "-b", "main")
        executeCommand("git", "remote", "add", "origin", remoteDir.absolutePath)
    }

    fun commitAll(message: String) {
        executeCommand("git", "add", ".")
        executeCommand("git", "commit", "-m", message)
    }

    fun pushToRemote() {
        executeCommand("git", "push", "-u", "origin", "main")
    }

    fun setRemoteUrl(remote: String, url: String) {
        executeCommand("git", "remote", "set-url", remote, url)
    }

    fun checkoutBranch(name: String) {
        executeCommand("git", "checkout", name)
    }

    fun createBranch(name: String) {
        executeCommand("git", "checkout", "-b", name)
    }

    fun detachHead() {
        executeCommand("git", "checkout", "--detach")
    }

    fun currentBranch(): String {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .directory(projectDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        return process.inputStream.bufferedReader().readText().trim()
    }

    fun createTag(tag: String) {
        executeCommand("git", "tag", tag)
    }

    fun pushTag(tag: String) {
        executeCommand("git", "push", "origin", tag)
    }

    fun deleteLocalTag(tag: String) {
        executeCommand("git", "tag", "-d", tag)
    }

    fun deleteLocalBranch(branch: String) {
        executeCommand("git", "branch", "-D", branch)
    }

    fun forcePushTag(tag: String) {
        executeCommand("git", "push", "--force", "origin", tag)
    }

    fun localTags(): List<String> {
        val process = ProcessBuilder("git", "tag", "-l")
            .directory(projectDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        return process.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
    }

    fun remoteTags(): List<String> {
        val process = ProcessBuilder("git", "ls-remote", "--tags", "origin")
            .directory(projectDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        return process.inputStream.bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { it.substringAfter("refs/tags/").trim() }
            .filter { it.isNotBlank() && !it.endsWith("^{}") }
    }

    fun remoteBranches(): List<String> {
        val process = ProcessBuilder("git", "ls-remote", "--heads", "origin")
            .directory(projectDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        return process.inputStream.bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { it.substringAfter("refs/heads/").trim() }
            .filter { it.isNotBlank() }
    }

    fun localBranches(): List<String> {
        val process = ProcessBuilder("git", "branch")
            .directory(projectDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        return process.inputStream.bufferedReader().readLines()
            .map { it.trim().removePrefix("* ") }
            .filter { it.isNotBlank() }
    }

    fun headCommit(): String {
        val process = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(projectDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        return process.inputStream.bufferedReader().readText().trim()
    }

    fun commitForTag(tag: String): String {
        val process = ProcessBuilder("git", "rev-parse", tag)
            .directory(projectDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        return process.inputStream.bufferedReader().readText().trim()
    }

    fun remoteTagCommit(tag: String): String {
        val process = ProcessBuilder("git", "ls-remote", "--tags", "origin", "refs/tags/$tag")
            .directory(projectDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        val lines = process.inputStream.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.contains("^{}") }
        return lines.firstOrNull()?.substringBefore("\t")?.trim() ?: ""
    }

    fun createFakeBuiltArtifact() {
        createFakeBuiltArtifact("app")
    }

    fun createFakeBuiltArtifact(subprojectName: String) {
        val libsDir = File(projectDir, "$subprojectName/build/libs")
        libsDir.mkdirs()
        File(libsDir, "$subprojectName.jar").writeText("fake jar content")
    }

    fun releaseVersionFile(): String? {
        val f = File(projectDir, "app/build/release-version.txt")
        return if (f.exists()) f.readText().trim() else null
    }

    fun modifyFile(relativePath: String, content: String) {
        val file = File(projectDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    fun useFileProtocol() {
        setRemoteUrl("origin", "file://${remoteDir.absolutePath}")
    }

    fun runTask(vararg tasks: String, properties: Map<String, String> = emptyMap(), env: Map<String, String> = emptyMap()): BuildResult {
        val args = tasks.toMutableList()
        properties.forEach { (k, v) -> args.add("-P$k=$v") }
        args.addAll(listOf("--stacktrace"))
        return gradleRunner(env).withArguments(args).build()
    }

    fun runTaskAndFail(vararg tasks: String, properties: Map<String, String> = emptyMap(), env: Map<String, String> = emptyMap()): BuildResult {
        val args = tasks.toMutableList()
        properties.forEach { (k, v) -> args.add("-P$k=$v") }
        args.addAll(listOf("--stacktrace"))
        return gradleRunner(env).withArguments(args).buildAndFail()
    }

    private fun gradleRunner(extraEnv: Map<String, String> = emptyMap()): GradleRunner {
        val env = HashMap(System.getenv())
        env.keys.removeIf { it.startsWith("DEVELOCITY_") || it.startsWith("GRADLE_BUILD_ACTION_") }
        env.putAll(extraEnv)
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withEnvironment(env)
            .withPluginClasspath()
    }

    private fun executeCommand(vararg command: String) {
        executeCommand(projectDir, *command)
    }

    private fun executeCommand(workingDir: File, vararg command: String) {
        val process = ProcessBuilder(*command)
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val error = process.errorStream.bufferedReader().readText()
            throw RuntimeException("Command failed: ${command.joinToString(" ")}\n$error")
        }
    }
}

class ReleaseTestProjectListener : TestListener {

    private var currentTestDir: File? = null

    fun getTestProjectDir(): File {
        return currentTestDir
            ?: throw IllegalStateException("Test project directory not initialized.")
    }

    override suspend fun beforeEach(testCase: TestCase) {
        val sanitizedTestName = testCase.name.testName.replace(Regex("[:<>\"|?*/]"), "-")
        val tempDir = kotlin.io.path.createTempDirectory("release-test-$sanitizedTestName").toFile()
        currentTestDir = tempDir
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        currentTestDir?.let { dir ->
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            currentTestDir = null
        }
    }
}
