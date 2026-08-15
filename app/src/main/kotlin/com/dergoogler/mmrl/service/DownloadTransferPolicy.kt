package com.dergoogler.mmrl.service

/** Bounds the temporary network artifact using bytes actually received, not only Content-Length. */
object DownloadTransferPolicy {
    const val MAX_DOWNLOAD_BYTES = 1_073_741_824L

    /** Negative lengths mean unknown and are allowed only because streaming is still hard-bounded. */
    fun declaredLengthAllowed(contentLength: Long): Boolean =
        contentLength < 0L || contentLength in 0L..MAX_DOWNLOAD_BYTES

    fun addReceived(received: Long, emitted: Int): Long {
        require(received >= 0L && emitted >= 0) { "Download byte counts must be non-negative" }
        val next = runCatching { Math.addExact(received, emitted.toLong()) }
            .getOrElse { throw IllegalArgumentException("Download byte count overflow", it) }
        require(next <= MAX_DOWNLOAD_BYTES) { "Download exceeds the temporary-file safety limit" }
        return next
    }
}
