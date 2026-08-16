package com.dergoogler.mmrl.pathHandler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WebUiContentPolicyTest {
    @Test
    fun `readme urls reject unsafe schemes and credentials before loading`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebUiContentPolicy.requireReadmeUri("http://github.com/owner/repo/raw/README.md")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebUiContentPolicy.requireReadmeUri("https://token@github.com/owner/repo/raw/README.md")
        }
    }

    @Test
    fun `markdown sanitizer blocks scriptable html`() {
        val sanitized = WebUiContentPolicy.sanitizeMarkdown("<h1 onclick=evil()>Hi</h1><script>alert(1)</script>[x](javascript:evil)")
        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertEquals(512L * 1024L, WebUiContentPolicy.boundedReadLimitBytes())
    }
}
