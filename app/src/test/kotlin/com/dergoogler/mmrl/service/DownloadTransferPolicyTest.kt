package com.dergoogler.mmrl.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTransferPolicyTest {
    @Test fun `oversized declared response is rejected before streaming`() {
        assertFalse(DownloadTransferPolicy.declaredLengthAllowed(DownloadTransferPolicy.MAX_DOWNLOAD_BYTES + 1))
        assertTrue(DownloadTransferPolicy.declaredLengthAllowed(-1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown length response is still bounded by bytes actually received`() {
        DownloadTransferPolicy.addReceived(DownloadTransferPolicy.MAX_DOWNLOAD_BYTES, 1)
    }
}
