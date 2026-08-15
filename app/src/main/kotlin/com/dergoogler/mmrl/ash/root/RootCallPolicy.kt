package com.dergoogler.mmrl.ash.root

internal enum class RootCallKind { READ_ONLY, MUTATION }

internal object RootCallPolicy {
    fun maxAttempts(kind: RootCallKind): Int = if (kind == RootCallKind.READ_ONLY) 2 else 1

    fun transportFailure(kind: RootCallKind, message: String): String =
        if (kind == RootCallKind.MUTATION) {
            """{"ok":false,"outcome":"UNKNOWN","message":"${escape(message)}; reconcile state before retrying"}"""
        } else {
            """{"ok":false,"message":"${escape(message)}"}"""
        }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
