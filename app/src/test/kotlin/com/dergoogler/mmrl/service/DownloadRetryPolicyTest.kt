package com.dergoogler.mmrl.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class DownloadRetryPolicyTest {
    @Test fun `transient HTTP and transport errors are retryable`() {
        assertTrue(DownloadRetryPolicy.isRetryable(DownloadHttpException(429, "rate limited")))
        assertTrue(DownloadRetryPolicy.isRetryable(DownloadHttpException(503, "unavailable")))
        assertTrue(DownloadRetryPolicy.isRetryable(SocketTimeoutException("timeout")))
    }

    @Test fun `permanent HTTP and local safety failures are not retryable`() {
        assertFalse(DownloadRetryPolicy.isRetryable(DownloadHttpException(401, "unauthorized")))
        assertFalse(DownloadRetryPolicy.isRetryable(DownloadHttpException(404, "missing")))
        assertFalse(DownloadRetryPolicy.isRetryable(IllegalArgumentException("artifact too large")))
        assertFalse(DownloadRetryPolicy.isRetryable(java.io.IOException("cannot publish destination")))
    }
}
