package com.dergoogler.mmrl.ash

import com.dergoogler.mmrl.ash.model.AshIncidentIdentityPolicy
import com.dergoogler.mmrl.ash.model.AshSnapshot
import com.dergoogler.mmrl.ash.model.Dashboard
import com.dergoogler.mmrl.ash.model.ModuleItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshIncidentIdentityPolicyTest {
    @Test
    fun `feedback must match issued revision module and binding`() {
        val module = ModuleItem(
            folder = "module.folder",
            id = "com.example.module",
            name = "Example",
            version = "1",
            versionCode = "1",
            enabled = false,
            quarantined = true,
            trust = "normal",
            fingerprint = "abc123",
        )
        val snapshot = AshSnapshot(
            generatedAt = 1000L,
            recoveryRevision = "rev-1",
            dashboard = Dashboard(latestRescueId = "incident-1"),
            modules = listOf(module),
        )
        val scope = AshIncidentIdentityPolicy.incidentScope(snapshot, module, nowSeconds = 1001L)

        assertTrue(
            AshIncidentIdentityPolicy.validateFeedback(
                expected = scope,
                submittedRecoveryRevision = "rev-1",
                submittedModuleFolder = "module.folder",
                submittedBinding = scope.binding,
                nowSeconds = 1002L,
            ),
        )
        assertFalse(
            AshIncidentIdentityPolicy.validateFeedback(
                expected = scope,
                submittedRecoveryRevision = "rev-2",
                submittedModuleFolder = "module.folder",
                submittedBinding = scope.binding,
                nowSeconds = 1002L,
            ),
        )
    }

    @Test
    fun `old evidence decays instead of overriding current incidents`() {
        assertTrue(AshIncidentIdentityPolicy.evidenceWeight(32, 60L) > AshIncidentIdentityPolicy.evidenceWeight(32, 40L * 24L * 60L * 60L))
    }
}
