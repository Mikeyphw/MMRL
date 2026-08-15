package com.dergoogler.mmrl.platform.util

import android.util.Log
import com.dergoogler.mmrl.platform.model.ShellResult

object Shell {
    private const val TAG = "Shell"

    fun String.exec(): Result<String> =
        runCatching {
            Log.d(TAG, "exec: $this")
            val capture = ShellProcessRunner.run(this)
            val error = capture.stderr.joinToString("\n")
            require(capture.exitCode.ok()) { error.ifBlank { "Command exited ${capture.exitCode}" } }
            capture.stdout.joinToString("\n").also { Log.d(TAG, "output: $it") }
        }.onFailure { Log.e(TAG, Log.getStackTraceString(it)) }

    fun String.exec(stdout: (String) -> Unit, stderr: (String) -> Unit) =
        runCatching {
            Log.d(TAG, "exec: $this")
            val capture = ShellProcessRunner.run(this)
            capture.stdout.forEach { stdout(it) }
            capture.stderr.forEach { stderr(it) }
            require(capture.exitCode.ok()) {
                capture.stderr.joinToString("\n").ifBlank { "Command exited ${capture.exitCode}" }
            }
        }.onFailure { Log.e(TAG, Log.getStackTraceString(it)) }

    fun Int.ok() = this == 0

    fun String.submit(callback: ShellResult.() -> Unit) {
        Thread {
            val result = runCatching {
                Log.d(TAG, "submit: $this")
                val capture = ShellProcessRunner.run(this)
                ShellResult(
                    isSuccess = capture.exitCode == 0,
                    out = capture.stdout,
                    err = capture.stderr,
                    exitCode = capture.exitCode,
                )
            }.getOrElse {
                ShellResult(false, emptyList(), listOf(it.message ?: "Unknown error"), -1)
            }
            callback(result)
        }.start()
    }
}
