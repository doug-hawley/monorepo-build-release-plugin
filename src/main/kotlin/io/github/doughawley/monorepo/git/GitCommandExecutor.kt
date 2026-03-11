package io.github.doughawley.monorepo.git

import org.gradle.api.logging.Logger
import java.io.File

/**
 * Responsible for executing git commands and handling their output.
 * Provides a consistent interface for running git operations with proper error handling.
 */
class GitCommandExecutor(private val logger: Logger) {

    /**
     * Result of a git command execution.
     *
     * @property success Whether the command completed successfully (exit code 0)
     * @property output The output lines from the command (excluding blank lines)
     * @property exitCode The exit code from the command
     * @property errorOutput The error output if the command failed
     */
    data class CommandResult(
        val success: Boolean,
        val output: List<String>,
        val exitCode: Int,
        val errorOutput: String = ""
    )

    /**
     * Executes a git command in the specified directory.
     *
     * @param directory The directory to execute the command in
     * @param command The git command and arguments (e.g., "diff", "--name-only")
     * @return CommandResult containing success status, output, and error information
     */
    fun execute(directory: File, vararg command: String): CommandResult {
        val fullCommand = arrayOf("git") + command

        val process = try {
            ProcessBuilder(*fullCommand)
                .directory(directory)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            logger.error("Exception starting git command: ${fullCommand.joinToString(" ")}", e)
            return CommandResult(
                success = false,
                output = emptyList(),
                exitCode = -1,
                errorOutput = e.message ?: "Unknown error"
            )
        }

        return try {
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().use { reader ->
                reader.readLines().filter { it.isNotBlank() }
            }

            if (exitCode == 0) {
                CommandResult(
                    success = true,
                    output = output,
                    exitCode = exitCode
                )
            } else {
                val errorOutput = output.joinToString("\n")
                logger.warn("Git command failed with exit code $exitCode: ${fullCommand.joinToString(" ")}")
                logger.warn("Error output: $errorOutput")

                CommandResult(
                    success = false,
                    output = emptyList(),
                    exitCode = exitCode,
                    errorOutput = errorOutput
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error("Interrupted waiting for git command: ${fullCommand.joinToString(" ")}", e)
            CommandResult(
                success = false,
                output = emptyList(),
                exitCode = -1,
                errorOutput = e.message ?: "Interrupted"
            )
        } catch (e: Exception) {
            logger.error("Exception executing git command: ${fullCommand.joinToString(" ")}", e)
            CommandResult(
                success = false,
                output = emptyList(),
                exitCode = -1,
                errorOutput = e.message ?: "Unknown error"
            )
        } finally {
            process.destroy()
        }
    }

    /**
     * Executes a git command without logging warnings on failure.
     * Use this for commands where a non-zero exit code is expected and not an error
     * (e.g., checking if a ref exists, fetching a tag that may not exist on the remote).
     * Failures are logged at debug level instead of warn.
     *
     * @param directory The directory to execute the command in
     * @param command The git command and arguments
     * @return CommandResult containing success status, output, and error information
     */
    fun executeSilently(directory: File, vararg command: String): CommandResult {
        val fullCommand = arrayOf("git") + command

        val process = try {
            ProcessBuilder(*fullCommand)
                .directory(directory)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            logger.debug("Exception starting git command: ${fullCommand.joinToString(" ")}", e)
            return CommandResult(
                success = false,
                output = emptyList(),
                exitCode = -1,
                errorOutput = e.message ?: "Unknown error"
            )
        }

        return try {
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().use { reader ->
                reader.readLines().filter { it.isNotBlank() }
            }

            if (exitCode == 0) {
                CommandResult(
                    success = true,
                    output = output,
                    exitCode = exitCode
                )
            } else {
                val errorOutput = output.joinToString("\n")
                logger.debug("Git command exited with code $exitCode: ${fullCommand.joinToString(" ")}")
                logger.debug("Output: $errorOutput")

                CommandResult(
                    success = false,
                    output = emptyList(),
                    exitCode = exitCode,
                    errorOutput = errorOutput
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.debug("Interrupted waiting for git command: ${fullCommand.joinToString(" ")}", e)
            CommandResult(
                success = false,
                output = emptyList(),
                exitCode = -1,
                errorOutput = e.message ?: "Interrupted"
            )
        } catch (e: Exception) {
            logger.debug("Exception executing git command: ${fullCommand.joinToString(" ")}", e)
            CommandResult(
                success = false,
                output = emptyList(),
                exitCode = -1,
                errorOutput = e.message ?: "Unknown error"
            )
        } finally {
            process.destroy()
        }
    }

    /**
     * Executes a git command and returns only the output lines if successful, empty list otherwise.
     * This is a convenience method for cases where you only care about the output on success.
     *
     * @param directory The directory to execute the command in
     * @param command The git command and arguments
     * @return List of output lines if successful, empty list otherwise
     */
    fun executeForOutput(directory: File, vararg command: String): List<String> {
        val result = execute(directory, *command)
        return if (result.success) result.output else emptyList()
    }
}
