package com.dergoogler.mmrl.service

/** Durable boundary for making a completed download externally observable. */
object DownloadCompletionPolicy {
    fun requireDurableSuccess(sourceUriCommitted: Boolean, terminalHistoryCommitted: Boolean) {
        check(sourceUriCommitted) { "Authoritative download URI is not durably recorded" }
        check(terminalHistoryCommitted) { "Download terminal history is not durably committed" }
    }

    inline fun runPostCommitBestEffort(block: () -> Unit): Throwable? =
        runCatching(block).exceptionOrNull()
}
