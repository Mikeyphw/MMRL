package com.dergoogler.mmrl.installer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemainingStorageIdentitySourceContractTest {
    @Test fun rollbackArtifactsUseOpaqueCollisionResistantIdentity() {
        val text = File("src/main/kotlin/com/dergoogler/mmrl/installer/UpdateRollbackStore.kt").readText()
        assertTrue(text.contains("OperationStoragePolicy.safeOperationId"))
        assertTrue(text.contains("\\u0000"))
        assertFalse(text.contains("private fun safe(value: String)"))
    }
}
