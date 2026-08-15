package com.dergoogler.mmrl.installer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkDownloadCompletionPolicyTest {
    @Test fun `complete planned batch may install`() {
        assertTrue(BulkDownloadCompletionPolicy.mayInstall(3, 0, 3))
    }

    @Test fun `partial download subset never auto installs`() {
        assertFalse(BulkDownloadCompletionPolicy.mayInstall(2, 1, 3))
        assertFalse(BulkDownloadCompletionPolicy.mayInstall(2, 0, 3))
    }

    @Test fun `empty batch does not cross install boundary`() {
        assertFalse(BulkDownloadCompletionPolicy.mayInstall(0, 0, 0))
    }
}
