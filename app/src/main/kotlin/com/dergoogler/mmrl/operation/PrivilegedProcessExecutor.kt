package com.dergoogler.mmrl.operation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Application-owned root command runner. It does not depend on an Activity, ViewModel or terminal emulator.
 * stdout/stderr are drained concurrently and cancellation terminates the child process.
 */
@Singleton
class PrivilegedProcessExecutor @Inject constructor() {
    suspend fun execute(
        command: String,
        environment: Map<String, String> = emptyMap(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onLine: suspend (String) -> Unit = {},
    ): ProcessResult = withContext(Dispatchers.IO) {
        require(command.isNotBlank()) { "Privileged command must not be blank" }
        val process = ProcessBuilder("su", "-c", command).apply {
            environment().putAll(environment)
        }.start()
        try {
            coroutineScope {
                val stdout = async(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            onLine(line)
                        }
                    }
                }
                val stderr = async(Dispatchers.IO) {
                    process.errorStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            onLine(line)
                        }
                    }
                }
                val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
                var finished = false
                while (!finished) {
                    coroutineContext.ensureActive()
                    val remainingNanos = deadlineNanos - System.nanoTime()
                    if (remainingNanos <= 0L) {
                        terminate(process)
                        throw PrivilegedProcessTimeoutException(timeoutMs)
                    }
                    val waitMs = minOf(POLL_INTERVAL_MS, TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L))
                    finished = process.waitFor(waitMs, TimeUnit.MILLISECONDS)
                }
                stdout.await()
                stderr.await()
                coroutineContext.ensureActive()
                ProcessResult(process.exitValue())
            }
        } finally {
            if (process.isAlive) terminate(process)
        }
    }

    private fun terminate(process: Process) {
        process.destroy()
        if (process.isAlive && !process.waitFor(750, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor(750, TimeUnit.MILLISECONDS)
        }
    }

    data class ProcessResult(val exitCode: Int) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    class PrivilegedProcessTimeoutException(timeoutMs: Long) :
        IllegalStateException("Privileged process exceeded ${timeoutMs}ms deadline")

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15L * 60L * 1000L
        private const val POLL_INTERVAL_MS = 250L
    }
}
