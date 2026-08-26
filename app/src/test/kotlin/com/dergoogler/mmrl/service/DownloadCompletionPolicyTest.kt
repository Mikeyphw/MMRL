package com.dergoogler.mmrl.service

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadCompletionPolicyTest {
    @Test
    fun `success is externally visible only after URI and terminal history are durable`() {
        assertThrows(IllegalStateException::class.java) {
            DownloadCompletionPolicy.requireDurableSuccess(false, true)
        }
        assertThrows(IllegalStateException::class.java) {
            DownloadCompletionPolicy.requireDurableSuccess(true, false)
        }
        DownloadCompletionPolicy.requireDurableSuccess(true, true)
    }

    @Test
    fun `post commit callback failure is isolated from durable download success`() {
        assertNotNull(DownloadCompletionPolicy.runPostCommitBestEffort { error("listener failed") })
        assertNull(DownloadCompletionPolicy.runPostCommitBestEffort { })
    }
}
