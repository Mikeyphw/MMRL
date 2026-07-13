package com.dergoogler.mmrl.tasker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskerAutomationPolicyTest {
    @Test
    fun `accepts HTTP and HTTPS module URLs`() {
        assertEquals(
            "https://example.org/module.zip",
            TaskerAutomationPolicy.requireSupportedDownloadUrl(" https://example.org/module.zip "),
        )
        assertEquals(
            "http://localhost:8080/module.zip",
            TaskerAutomationPolicy.requireSupportedDownloadUrl("http://localhost:8080/module.zip"),
        )
    }

    @Test
    fun `rejects non-network and hostless URLs`() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskerAutomationPolicy.requireSupportedDownloadUrl("file:///sdcard/module.zip")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskerAutomationPolicy.requireSupportedDownloadUrl("https:///module.zip")
        }
    }

    @Test
    fun `sanitizes requested filenames and forces ZIP extension`() {
        assertEquals("module_name.zip", TaskerAutomationPolicy.sanitizeFilename("../module name"))
        assertEquals("release.ZIP", TaskerAutomationPolicy.sanitizeFilename("folder/release.ZIP"))
    }

    @Test
    fun `rejects unusable filenames`() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskerAutomationPolicy.sanitizeFilename("...")
        }
    }
    @Test
    fun `accepts only shell safe module identifiers`() {
        assertEquals("example.module-1", TaskerAutomationPolicy.requireSafeModuleId(" example.module-1 "))
        assertThrows(IllegalArgumentException::class.java) {
            TaskerAutomationPolicy.requireSafeModuleId("module; reboot")
        }
    }

}
