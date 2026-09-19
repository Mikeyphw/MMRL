package com.dergoogler.mmrl.service

/**
 * User-facing errors for download admission failures.
 *
 * Rejection messages identify the condition without echoing operation IDs, URLs, attempt IDs,
 * or filesystem paths. The original throwable is logged internally at the rejection site.
 */
internal object DownloadRejectionDiagnostics {
    fun duplicateActiveOperation(): IllegalStateException =
        IllegalStateException("Download rejected: operation is already active")

    fun destinationUnavailable(): IllegalStateException =
        IllegalStateException("Download rejected: destination is unavailable")
}
