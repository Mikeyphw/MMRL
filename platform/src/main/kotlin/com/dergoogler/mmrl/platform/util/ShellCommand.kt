package com.dergoogler.mmrl.platform.util

/** Builds POSIX-shell command strings while keeping every argument data-only. */
object ShellCommand {
    fun quote(argument: String): String =
        "'" + argument.replace("'", "'\"'\"'") + "'"

    fun of(vararg arguments: String): String =
        arguments.joinToString(" ") { quote(it) }
}
