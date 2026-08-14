package com.dergoogler.mmrl.ui.activity.terminal.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalInstallRequestPolicyTest {
    @Test
    fun acceptsOnlyViewContentZipRequests() {
        assertTrue(ExternalInstallRequestPolicy.accepts("android.intent.action.VIEW", "content", "application/zip"))
        assertTrue(ExternalInstallRequestPolicy.accepts("android.intent.action.VIEW", "CONTENT", "APPLICATION/ZIP"))
        assertFalse(ExternalInstallRequestPolicy.accepts("android.intent.action.VIEW", "file", "application/zip"))
        assertFalse(ExternalInstallRequestPolicy.accepts("android.intent.action.VIEW", "content", "application/octet-stream"))
        assertFalse(ExternalInstallRequestPolicy.accepts("android.intent.action.VIEW", "https", "application/zip"))
        assertFalse(ExternalInstallRequestPolicy.accepts("other", "content", "application/zip"))
        assertFalse(ExternalInstallRequestPolicy.accepts(null, "content", "application/zip"))
    }
}
