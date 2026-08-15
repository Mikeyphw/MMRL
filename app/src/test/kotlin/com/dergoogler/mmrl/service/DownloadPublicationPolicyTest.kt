package com.dergoogler.mmrl.service

import com.dergoogler.mmrl.service.DownloadPublicationPolicy.PublishMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPublicationPolicyTest {
    @Test fun `android ten public download requires pending MediaStore row`() {
        assertEquals(PublishMode.MEDIASTORE_PENDING, DownloadPublicationPolicy.forDestination(29, true))
    }

    @Test fun `legacy public and all private downloads use atomic file publication`() {
        assertEquals(PublishMode.ATOMIC_FILE, DownloadPublicationPolicy.forDestination(28, true))
        assertEquals(PublishMode.ATOMIC_FILE, DownloadPublicationPolicy.forDestination(35, false))
    }
}
