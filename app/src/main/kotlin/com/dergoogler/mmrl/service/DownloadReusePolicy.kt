package com.dergoogler.mmrl.service

import com.dergoogler.mmrl.installer.ArtifactDigest
import com.dergoogler.mmrl.installer.ArtifactProvenance

/** Pure receipt/digest authority check used before any existing download may be reused. */
object DownloadReusePolicy {
    fun matches(
        receipt: ArtifactProvenance,
        digest: ArtifactDigest.Digest,
        expectedSourceUrl: String? = null,
    ): Boolean =
        (expectedSourceUrl == null || receipt.sourceUrl == expectedSourceUrl) &&
            receipt.size == digest.size &&
            receipt.sha256.equals(digest.sha256, ignoreCase = true)
}
