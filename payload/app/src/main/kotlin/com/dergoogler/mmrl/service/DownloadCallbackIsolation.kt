package com.dergoogler.mmrl.service

import timber.log.Timber

/**
 * Boundary for callbacks supplied by download callers.
 *
 * Callbacks are observers: they must never become part of the durable download transaction.
 * Every callback is therefore best-effort. An exception is logged and swallowed so history,
 * temporary-file cleanup, destination leases, and operation ownership continue independently.
 */
internal object DownloadCallbackIsolation {
    inline fun <T> bestEffort(
        label: String,
        listener: T?,
        callback: T.() -> Unit,
    ): Throwable? {
        if (listener == null) return null
        return runCatching { listener.callback() }
            .exceptionOrNull()
            ?.also { Timber.w(it, "Download callback failed: %s", label) }
    }
}
