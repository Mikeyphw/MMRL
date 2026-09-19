package com.dergoogler.mmrl.service

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull

class DownloadCallbackIsolationTest {
    private class ThrowingObserver {
        fun fail(label: String): Nothing = throw IllegalStateException("$label boom")
    }

    @Test
    fun `every download callback category is exception isolated`() {
        val observer = ThrowingObserver()
        val labels = listOf(
            "started",
            "progress",
            "file-exists",
            "success",
            "failure",
            "permission-denied",
            "service-start failure",
            "duplicate-operation failure",
            "destination-claim failure",
            "destination-reclaim failure",
        )

        labels.forEach { label ->
            val error = DownloadCallbackIsolation.bestEffort(label, observer) { fail(label) }
            assertTrue(error is IllegalStateException)
            assertEquals("$label boom", error?.message)
        }
    }

    @Test
    fun `null listener is a no-op`() {
        val observer: ThrowingObserver? = null
        assertNull(DownloadCallbackIsolation.bestEffort("test", observer) { fail("test") })
    }

    @Test
    fun `successful callback remains observable`() {
        var calls = 0
        val error = DownloadCallbackIsolation.bestEffort("test", Unit) { calls++ }
        assertNull(error)
        assertEquals(1, calls)
    }
}
