package com.dergoogler.mmrl.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class ArtifactDigestTest {
    @Test fun `digest counts emitted bytes and is stable`() {
        val first = ArtifactDigest.of(ByteArrayInputStream("reviewed bytes".toByteArray()))
        val second = ArtifactDigest.of(ByteArrayInputStream("reviewed bytes".toByteArray()))
        assertEquals(14, first.size)
        assertEquals(first, second)
    }

    @Test fun `one byte change changes artifact authority`() {
        val first = ArtifactDigest.of(ByteArrayInputStream("abc".toByteArray()))
        val second = ArtifactDigest.of(ByteArrayInputStream("abd".toByteArray()))
        assertNotEquals(first.sha256, second.sha256)
    }
}
