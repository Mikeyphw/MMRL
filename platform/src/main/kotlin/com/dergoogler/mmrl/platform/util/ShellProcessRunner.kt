package com.dergoogler.mmrl.platform.util

/** Drains stdout and stderr concurrently so a full pipe can never deadlock the child. */
internal object ShellProcessRunner {
    data class Capture(val exitCode: Int, val stdout: List<String>, val stderr: List<String>)

    fun run(command: String): Capture {
        val process = ProcessBuilder("sh", "-c", command).start()
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val outThread = Thread({ process.inputStream.bufferedReader().useLines { stdout.addAll(it.toList()) } }, "mmrl-shell-stdout")
        val errThread = Thread({ process.errorStream.bufferedReader().useLines { stderr.addAll(it.toList()) } }, "mmrl-shell-stderr")
        outThread.start()
        errThread.start()
        val exit = process.waitFor()
        outThread.join()
        errThread.join()
        return Capture(exit, stdout, stderr)
    }
}
