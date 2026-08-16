package com.dergoogler.mmrl.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkPolicyTest {
    @Test
    fun `github token is attached only to exact approved hosts`() {
        assertTrue(NetworkPolicy.shouldAttachGitHubToken("https://api.github.com/repos/o/r"))
        assertTrue(NetworkPolicy.shouldAttachGitHubToken("https://raw.githubusercontent.com/o/r/main/file"))
        assertFalse(NetworkPolicy.shouldAttachGitHubToken("https://github.com.evil.invalid/repos/o/r"))
        assertFalse(NetworkPolicy.shouldAttachGitHubToken("https://evil.invalid/github.com/repos/o/r"))
    }

    @Test
    fun `service refresh intervals are clamped to bounded values`() {
        assertEquals(1L, NetworkPolicy.clampRefreshIntervalHours(0L))
        assertEquals(24L * 14L, NetworkPolicy.clampRefreshIntervalHours(Long.MAX_VALUE))
        assertEquals(6L, NetworkPolicy.clampRefreshIntervalHours(6L))
    }

    @Test
    fun `download byte accounting rejects oversized streams`() {
        assertTrue(NetworkPolicy.declaredDownloadLengthAllowed(-1L))
        assertTrue(NetworkPolicy.declaredDownloadLengthAllowed(NetworkPolicy.MAX_DOWNLOAD_BYTES))
        assertFalse(NetworkPolicy.declaredDownloadLengthAllowed(NetworkPolicy.MAX_DOWNLOAD_BYTES + 1))
        assertEquals(5L, NetworkPolicy.addReceivedBytes(2L, 3))
        assertThrows(IllegalArgumentException::class.java) {
            NetworkPolicy.addReceivedBytes(NetworkPolicy.MAX_DOWNLOAD_BYTES, 1)
        }
    }

    @Test
    fun `http error snippets preserve status but redact html`() {
        val error = NetworkHttpException(
            statusCode = 403,
            requestUrl = "https://api.github.com/repos/o/r",
            responseSnippet = NetworkPolicy.sanitizeErrorBody("<!doctype html><body>secret</body>"),
        )

        assertEquals(403, error.statusCode)
        assertTrue(error.message!!.contains("HTTP 403"))
        assertTrue(error.message!!.contains("HTML response body redacted"))
    }
}
