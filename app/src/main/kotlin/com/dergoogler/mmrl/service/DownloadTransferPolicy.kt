package com.dergoogler.mmrl.service

import com.dergoogler.mmrl.network.NetworkPolicy

/** Bounds the temporary network artifact using bytes actually received, not only Content-Length. */
object DownloadTransferPolicy {
    const val MAX_DOWNLOAD_BYTES = NetworkPolicy.MAX_DOWNLOAD_BYTES

    /** Negative lengths mean unknown and are allowed only because streaming is still hard-bounded. */
    fun declaredLengthAllowed(contentLength: Long): Boolean =
        NetworkPolicy.declaredDownloadLengthAllowed(contentLength)

    fun addReceived(received: Long, emitted: Int): Long =
        NetworkPolicy.addReceivedBytes(received, emitted)
}
