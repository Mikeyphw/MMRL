package dev.mmrlx.terminal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Small public replacement for the private MMRL terminal artifact.
 *
 * MMRL only needs a non-interactive root command runner plus an observable terminal buffer.
 * This class deliberately keeps that API surface and does not pretend to implement a full PTY.
 */
class TerminalEmulator(
    private val maxLines: Int = DEFAULT_MAX_LINES,
) {
    private val mutableLines = MutableStateFlow<List<String>>(emptyList())

    val lines: StateFlow<List<String>> = mutableLines.asStateFlow()

    @Volatile
    private var cursorStyle: Int = TERMINAL_CURSOR_STYLE_NONE

    fun setCursorStyle(style: Int) {
        cursorStyle = style
    }

    fun appendLine(line: String) {
        val normalized = stripTerminalControls(line)
        val additions = normalized.split('\n')
        mutableLines.update { current ->
            val combined = current + additions
            if (combined.size > maxLines) combined.takeLast(maxLines) else combined
        }
    }

    fun clear() {
        mutableLines.value = emptyList()
    }

    companion object {
        const val TERMINAL_CURSOR_STYLE_NONE: Int = 0
        private const val DEFAULT_MAX_LINES: Int = 8_000

        private val ansiPattern = Regex(
            "(?:\\u001B\\[[0-?]*[ -/]*[@-~])|(?:\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\))|[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"
        )

        private fun stripTerminalControls(value: String): String =
            value.replace(ansiPattern, "").trimEnd('\r')
    }
}

fun TerminalEmulator.appendLineOnMain(message: String) {
    appendLine(message)
}

/**
 * Execute a root command through the root manager's standard `su -c` interface.
 * Stdout and stderr are consumed concurrently so installers cannot deadlock on a full pipe.
 */
suspend fun TerminalEmulator.newSuperUserPty(
    command: String,
    environment: Map<String, String> = emptyMap(),
): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        require(command.isNotBlank()) { "Root command must not be blank" }

        val shellCommand = buildString {
            environment.forEach { (name, value) ->
                require(ENV_NAME.matches(name)) { "Invalid environment variable name: $name" }
                append("export ")
                append(name)
                append('=')
                append(shellQuote(value))
                append(';')
            }
            append(command)
        }

        val process = ProcessBuilder("su", "-c", shellCommand)
            .redirectErrorStream(false)
            .start()

        try {
            coroutineScope {
                val stdout = async { process.inputStream.consumeLines(this@newSuperUserPty) }
                val stderr = async { process.errorStream.consumeLines(this@newSuperUserPty) }
                val exitCode = runInterruptible { process.waitFor() }
                stdout.await()
                stderr.await()

                check(exitCode == 0) { "Root command exited with code $exitCode" }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            process.inputStream.closeQuietly()
            process.errorStream.closeQuietly()
            process.outputStream.closeQuietly()
            if (process.isAlive) {
                process.destroy()
                if (process.isAlive) process.destroyForcibly()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

private fun java.io.InputStream.consumeLines(emulator: TerminalEmulator) {
    BufferedReader(InputStreamReader(this)).use { reader ->
        while (true) {
            val line = reader.readLine() ?: break
            emulator.appendLine(line)
        }
    }
}

private fun AutoCloseable.closeQuietly() {
    runCatching { close() }
}

private fun shellQuote(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"

private val ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
