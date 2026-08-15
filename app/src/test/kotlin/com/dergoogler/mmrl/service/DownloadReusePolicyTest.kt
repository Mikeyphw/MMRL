package com.dergoogler.mmrl.service

import com.dergoogler.mmrl.installer.ArtifactDigest
import com.dergoogler.mmrl.installer.ArtifactProvenance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadReusePolicyTest {
    private val receipt = ArtifactProvenance("content://artifact/1", "https://example.test/a.zip", "aabb", 42)

    @Test fun `reuse requires source url size and hash`() {
        assertTrue(DownloadReusePolicy.matches(receipt, ArtifactDigest.Digest("AABB", 42), "https://example.test/a.zip"))
    }

    @Test fun `different source cannot reuse same destination`() {
        assertFalse(DownloadReusePolicy.matches(receipt, ArtifactDigest.Digest("aabb", 42), "https://example.test/b.zip"))
    }

    @Test fun `nonempty artifact with wrong bytes is not reusable`() {
        assertFalse(DownloadReusePolicy.matches(receipt, ArtifactDigest.Digest("deadbeef", 42), "https://example.test/a.zip"))
        assertFalse(DownloadReusePolicy.matches(receipt, ArtifactDigest.Digest("aabb", 41), "https://example.test/a.zip"))
    }
}
