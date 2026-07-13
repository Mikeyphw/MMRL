package com.dergoogler.mmrl.tasker

import com.dergoogler.mmrl.installer.ArchiveInspection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskerReviewPolicyTest {
    private fun inspection(
        scripts: List<String> = emptyList(),
        apks: List<String> = emptyList(),
        sePolicy: List<String> = emptyList(),
        remote: List<String> = emptyList(),
        properties: List<String> = emptyList(),
        blocked: List<String> = emptyList(),
    ) = ArchiveInspection(
        sha256 = "abc",
        entryCount = 1,
        compressedBytes = 1,
        uncompressedBytes = 1,
        scripts = scripts,
        nativeBinaries = emptyList(),
        apks = apks,
        sePolicyFiles = sePolicy,
        propertyFiles = properties,
        remoteExecutionFiles = remote,
        warnings = emptyList(),
        blockedReasons = blocked,
    )

    @Test
    fun `routine automation requires verified low-risk archive`() {
        assertTrue(inspection().isRoutineAutomation(verified = true))
        assertFalse(inspection().isRoutineAutomation(verified = false))
    }

    @Test
    fun `boot scripts and sensitive changes require review`() {
        assertFalse(inspection(scripts = listOf("service.sh")).isRoutineAutomation(verified = true))
        assertFalse(inspection(apks = listOf("app.apk")).isRoutineAutomation(verified = true))
        assertFalse(inspection(sePolicy = listOf("sepolicy.rule")).isRoutineAutomation(verified = true))
        assertFalse(inspection(remote = listOf("customize.sh")).isRoutineAutomation(verified = true))
        assertFalse(inspection(properties = listOf("system.prop")).isRoutineAutomation(verified = true))
    }

    @Test
    fun `blocked archive is never routine`() {
        assertFalse(inspection(blocked = listOf("unsafe path")).isRoutineAutomation(verified = true))
    }
}
