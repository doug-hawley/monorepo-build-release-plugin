package io.github.doughawley.monorepo.build.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

/**
 * Builder for creating test Gradle projects for functional testing.
 */
class TestProjectBuilder(private val projectDir: File) {
    private val subprojects = mutableListOf<SubprojectConfig>()
    private var pluginApplied = false
    private var useRemote = false
    private var remoteDir: File? = null

    data class SubprojectConfig(
        val name: String,
        val dependencies: List<String> = emptyList(),
        val isBom: Boolean = false,
        val usePlatform: Boolean = false,
        val excludePatterns: List<String> = emptyList()
    )

    fun withSubproject(
        name: String,
        dependsOn: List<String> = emptyList(),
        isBom: Boolean = false,
        usePlatform: Boolean = false,
        excludePatterns: List<String> = emptyList()
    ): TestProjectBuilder {
        subprojects.add(SubprojectConfig(name, dependsOn, isBom, usePlatform, excludePatterns))
        return this
    }

    fun applyPlugin(pluginId: String = "io.github.doug-hawley.monorepo-build-release-plugin"): TestProjectBuilder {
        pluginApplied = true
        return this
    }

    fun withRemote(): TestProjectBuilder {
        useRemote = true
        return this
    }

    fun build(): TestProject {
        // Create root build.gradle.kts
        val rootBuild = if (pluginApplied) {
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            monorepo {
                build {
                    includeUntracked = true
                }
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
            }
            """.trimIndent()
        } else {
            ""
        }

        File(projectDir, "build.gradle.kts").writeText(rootBuild)

        // Create .gitignore to exclude build artifacts
        val gitignoreContent = """
            .gradle/
            build/
            *.iml
            .idea/
            out/
        """.trimIndent()
        File(projectDir, ".gitignore").writeText(gitignoreContent)

        // Create settings.gradle.kts
        val settingsContent = buildString {
            appendLine("rootProject.name = \"test-project\"")
            subprojects.forEach { subproject ->
                // Convert path separators to Gradle project notation
                // e.g., "modules/module1" becomes ":modules:module1"
                val gradlePath = subproject.name.replace("/", ":")
                appendLine("include(\":$gradlePath\")")
            }
        }
        File(projectDir, "settings.gradle.kts").writeText(settingsContent)

        // Create each subproject
        subprojects.forEach { subproject ->
            val subprojectDir = File(projectDir, subproject.name)
            subprojectDir.mkdirs()

            // Create build.gradle.kts with dependencies
            val buildContent = buildString {
                appendLine("plugins {")
                if (subproject.isBom) {
                    appendLine("    `java-platform`")
                } else {
                    appendLine("    kotlin(\"jvm\") version \"2.0.21\"")
                }
                appendLine("}")
                appendLine()
                if (subproject.dependencies.isNotEmpty()) {
                    appendLine("dependencies {")

                    // Separate platform dependencies from regular dependencies
                    if (subproject.usePlatform) {
                        // First dependency is the platform
                        val platformDep = subproject.dependencies.first()
                        val platformPath = platformDep.replace("/", ":")
                        appendLine("    api(platform(project(\":$platformPath\")))")

                        // Rest are regular dependencies
                        subproject.dependencies.drop(1).forEach { dep ->
                            val gradlePath = dep.replace("/", ":")
                            appendLine("    implementation(project(\":$gradlePath\"))")
                        }
                    } else {
                        // All regular dependencies
                        subproject.dependencies.forEach { dep ->
                            val gradlePath = dep.replace("/", ":")
                            appendLine("    implementation(project(\":$gradlePath\"))")
                        }
                    }

                    appendLine("}")
                }
                if (subproject.excludePatterns.isNotEmpty()) {
                    appendLine()
                    appendLine("monorepoProject {")
                    appendLine("    build {")
                    appendLine("        excludePatterns = listOf(${subproject.excludePatterns.joinToString { "\"$it\"" }})")
                    appendLine("    }")
                    appendLine("}")
                }
            }
            File(subprojectDir, "build.gradle.kts").writeText(buildContent)

            // Create source directory and sample file (skip for BOM projects)
            if (!subproject.isBom) {
                val srcDir = File(subprojectDir, "src/main/kotlin/com/example")
                srcDir.mkdirs()
                // Use just the last part of the path for the class name (e.g., "Module1" from "modules/module1")
                val simpleClassName = subproject.name.split("/").last()
                    .split("-")
                    .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
                File(srcDir, "$simpleClassName.kt").writeText(
                    """
                package com.example

                class $simpleClassName {
                    fun doSomething() = "Hello from ${subproject.name}"
                }
                """.trimIndent()
                )
            }
        }

        // Set up remote directory if needed
        if (useRemote) {
            remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")
        }

        return TestProject(projectDir, useRemote, remoteDir)
    }
}

/**
 * Represents a test Gradle project with utilities for git operations and running tasks.
 */
class TestProject(
    val projectDir: File,
    private val useRemote: Boolean = false,
    private val remoteDir: File? = null
) {

    fun initGit() {
        if (useRemote && remoteDir != null) {
            // Create a bare repository to act as origin
            remoteDir.mkdirs()
            executeCommand(remoteDir, "git", "init", "--bare")

            // Initialize local repo and add remote
            executeCommand("git", "init")
            executeCommand("git", "config", "user.email", "test@example.com")
            executeCommand("git", "config", "user.name", "Test User")
            executeCommand("git", "checkout", "-b", "main")
            executeCommand("git", "remote", "add", "origin", remoteDir.absolutePath)
        } else {
            // Regular local-only git repo
            executeCommand("git", "init")
            executeCommand("git", "config", "user.email", "test@example.com")
            executeCommand("git", "config", "user.name", "Test User")
            executeCommand("git", "checkout", "-b", "main")
        }
    }

    fun commitAll(message: String) {
        executeCommand("git", "add", ".")
        executeCommand("git", "commit", "-m", message)
    }

    fun pushToRemote() {
        if (useRemote) {
            executeCommand("git", "push", "-u", "origin", "main")
        }
    }

    fun stageFile(path: String) {
        executeCommand("git", "add", path)
    }

    fun modifyFile(relativePath: String, content: String) {
        val file = File(projectDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    fun appendToFile(relativePath: String, content: String) {
        val file = File(projectDir, relativePath)
        if (file.exists()) {
            file.appendText("\n$content")
        } else {
            file.parentFile.mkdirs()
            file.writeText(content)
        }
    }

    fun createNewFile(relativePath: String, content: String) {
        val file = File(projectDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    fun runTask(vararg tasks: String): BuildResult {
        return gradleRunner()
            .withArguments(tasks.toList() + listOf("--parallel", "--stacktrace"))
            .build()
    }

    fun runTaskAndFail(vararg tasks: String): BuildResult {
        return gradleRunner()
            .withArguments(tasks.toList() + listOf("--parallel", "--stacktrace"))
            .buildAndFail()
    }

    fun runTaskWithProperties(vararg tasks: String, properties: Map<String, String> = emptyMap()): BuildResult {
        val args = tasks.toMutableList()
        properties.forEach { (k, v) -> args.add("-P$k=$v") }
        args.addAll(listOf("--parallel", "--stacktrace"))
        return gradleRunner().withArguments(args).build()
    }

    fun runTaskWithPropertiesAndFail(vararg tasks: String, properties: Map<String, String> = emptyMap()): BuildResult {
        val args = tasks.toMutableList()
        properties.forEach { (k, v) -> args.add("-P$k=$v") }
        args.addAll(listOf("--parallel", "--stacktrace"))
        return gradleRunner().withArguments(args).buildAndFail()
    }

    fun executeGitCommand(vararg args: String) {
        executeCommand("git", *args)
    }

    fun getLastCommitSha(): String {
        val process = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(projectDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        return process.inputStream.bufferedReader().readText().trim()
    }

    private fun gradleRunner(): GradleRunner {
        val env = HashMap(System.getenv())
        // Strip env vars that trigger Develocity auto-injection via gradle/actions/setup-gradle.
        // The injected init script interferes with projectsEvaluated task registration in CI.
        env.keys.removeIf { it.startsWith("DEVELOCITY_") || it.startsWith("GRADLE_BUILD_ACTION_") }
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

/**
 * Extension functions for parsing build results.
 */
fun BuildResult.extractChangedProjects(): Set<String> {
    // Match all project path lines: "  :project-path" and "  :project-path  (affected via ...)"
    val regex = """^ {2}(:[^\s(]+)""".toRegex(RegexOption.MULTILINE)
    return regex.findAll(output).map { it.groupValues[1] }.toSet()
}

fun BuildResult.extractDirectlyChangedProjects(): Set<String> {
    // Match project path lines with no "(affected via ...)" annotation — these are directly changed.
    // Padded transitively-affected lines (e.g. "  :apps:app1        (affected via ...)") are excluded
    // because the annotation is non-whitespace after the path.
    val regex = """^ {2}(:[^\s(]+)\s*$""".toRegex(RegexOption.MULTILINE)
    return regex.findAll(output).map { it.groupValues[1] }.toSet()
}

fun BuildResult.extractBuiltProjects(): Set<String> {
    val regex = """Building changed projects: (.*)""".toRegex()
    val match = regex.find(output)
    val projectsString = match?.groupValues?.get(1)?.trim() ?: ""

    return if (projectsString.isEmpty()) {
        emptySet()
    } else {
        projectsString
            .split(", ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}