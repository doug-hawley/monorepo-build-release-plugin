package io.github.doughawley.monorepo.build.functional

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import java.io.File

/**
 * Kotest TestListener that manages test project directories for functional tests.
 *
 * Automatically creates a temporary directory before each test and cleans it up after.
 * Provides factory methods to create and initialize test projects.
 *
 * Usage:
 * ```kotlin
 * class MyFunctionalTest : FunSpec({
 *     val functionalTest = listener(FunctionalTestListener())
 *
 *     test("my test") {
 *         val project = functionalTest.createStandardProject()
 *         // ... test code
 *     }
 * })
 * ```
 */
class TestProjectListener : TestListener {

    private var currentTestDir: File? = null
    private var currentTestProject: TestProject? = null

    /**
     * Creates and initializes a standard test project structure.
     * The project includes common-lib, modules (module1, module2), and apps (app1, app2).
     */
    fun createStandardProject(): TestProject {
        val testDir = currentTestDir
            ?: throw IllegalStateException("Test project directory not initialized. Test may not have started yet.")
        return StandardTestProject.createAndInitialize(testDir).also { currentTestProject = it }
    }

    /**
     * Creates a standard test project structure without initializing git.
     * Useful for tests that need custom git setup.
     */
    fun createStandardProjectWithoutGit(): TestProject {
        val testDir = currentTestDir
            ?: throw IllegalStateException("Test project directory not initialized. Test may not have started yet.")
        return StandardTestProject.create(testDir).also { currentTestProject = it }
    }

    /**
     * Gets the test project directory for advanced use cases.
     * Most tests should use createStandardProject() instead.x
     */
    fun getTestProjectDir(): File {
        return currentTestDir
            ?: throw IllegalStateException("Test project directory not initialized. Test may not have started yet.")
    }

    override suspend fun beforeEach(testCase: TestCase) {
        // Sanitize test name for use in file paths (Windows doesn't allow : < > " | ? *)
        val sanitizedTestName = testCase.name.testName.replace(Regex("[:<>\"|?*/]"), "-")
        val tempDir = kotlin.io.path.createTempDirectory("monorepo-test-$sanitizedTestName").toFile()
        currentTestDir = tempDir
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        currentTestProject = null
        currentTestDir?.let { dir ->
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            currentTestDir = null
        }
    }
}
