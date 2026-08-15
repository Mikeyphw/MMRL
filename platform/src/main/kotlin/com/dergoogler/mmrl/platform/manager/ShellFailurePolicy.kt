package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.model.ShellResult

internal object ShellFailurePolicy {
    fun message(result: ShellResult): String =
        result.err.joinToString("\n")
            .ifBlank { result.out.joinToString("\n") }
            .ifBlank { "Command exited with code ${result.exitCode}" }
}
