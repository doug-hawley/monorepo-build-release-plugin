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
        return runCommand(directory, silent = false, command = command)
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
        return runCommand(directory, silent = true, command = command)
    }

    /**
     * Executes a git command and returns the output lines on success.
     * Throws [RuntimeException] if the command fails, since callers of this method
     * do not expect failure — use [executeSilently] for commands where non-zero exit
     * codes are expected.
     *
     * @param directory The directory to execute the command in
     * @param command The git command and arguments
     * @return List of output lines on success
     * @throws RuntimeException if the command exits with a non-zero code
     */
    fun executeForOutput(directory: File, vararg command: String): List<String> {
        val result = execute(directory, *command)
        if (!result.success) {
            throw RuntimeException(
                "Git command failed (exit code ${result.exitCode}): git ${command.joinToString(" ")}\n${result.errorOutput}"
            )
        }
        return result.output
    }

    private fun runCommand(directory: File, silent: Boolean, vararg command: String): CommandResult {
        val fullCommand = arrayOf("git") + command

        val process = try {
            ProcessBuilder(*fullCommand)
                .directory(directory)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            if (silent) {
                logger.debug("Exception starting git command: ${fullCommand.joinToString(" ")}", e)
            } else {
                logger.error("Exception starting git command: ${fullCommand.joinToString(" ")}", e)
            }
            return CommandResult(
                success = false,
                output = emptyList(),
                exitCode = -1,
                errorOutput = e.message ?: "Unknown error"
            )
        }

        return try {
            // Read the output stream before waitFor() to avoid deadlock when
            // the process produces more output than the OS pipe buffer (~64KB).
            val output = process.inputStream.bufferedReader().use { reader ->
                reader.readLines().filter { it.isNotBlank() }
            }
            val exitCode = process.waitFor()

            val commandString = fullCommand.joinToString(" ")
            if (exitCode == 0) {
                if (silent) {
                    logger.debug("$ $commandString → exit 0 (${output.size} lines)")
                } else {
                    logger.info("$ $commandString → exit 0 (${output.size} lines)")
                }
                output.forEach { line -> logger.debug("  $line") }
                CommandResult(
                    success = true,
                    output = output,
                    exitCode = exitCode
                )
            } else {
                val errorOutput = output.joinToString("\n")
                if (silent) {
                    logger.debug("$ $commandString → exit $exitCode")
                    logger.debug("  $errorOutput")
                } else {
                    logger.info("$ $commandString → exit $exitCode")
                    logger.warn("Git command failed: $commandString")
                    logger.warn("Error output: $errorOutput")
                }

                CommandResult(
                    success = false,
                    output = emptyList(),
                    exitCode = exitCode,
                    errorOutput = errorOutput
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            if (silent) {
                logger.debug("Interrupted waiting for git command: ${fullCommand.joinToString(" ")}", e)
            } else {
                logger.error("Interrupted waiting for git command: ${fullCommand.joinToString(" ")}", e)
            }
            CommandResult(
                success = false,
                output = emptyList(),
                exitCode = -1,
                errorOutput = e.message ?: "Interrupted"
            )
        } catch (e: Exception) {
            if (silent) {
                logger.debug("Exception executing git command: ${fullCommand.joinToString(" ")}", e)
            } else {
                logger.error("Exception executing git command: ${fullCommand.joinToString(" ")}", e)
            }
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
}
