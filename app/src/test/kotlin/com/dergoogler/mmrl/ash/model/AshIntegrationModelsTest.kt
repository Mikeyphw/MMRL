package com.dergoogler.mmrl.ash.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshIntegrationModelsTest {
    @Test
    fun quarantinedSnapshotTakesPriorityOverStableState() {
        val state =
            AshManagerState(
                rootAvailable = true,
                lifecycle = compatibleLifecycle(),
                snapshot =
                    AshSnapshot(
                        dashboard = Dashboard(bootState = "stable", quarantined = 2, threshold = 2),
                    ),
                source = AshSnapshotSource.Live,
                readOnly = false,
            )

        val summary = state.protectionSummary()

        assertEquals(AshProtectionStatus.Quarantined, summary.status)
        assertEquals(2, summary.quarantinedModules)
    }

    @Test
    fun moduleProtectionIndexesIdAndFolder() {
        val state =
            AshManagerState(
                snapshot =
                    AshSnapshot(
                        modules =
                            listOf(
                                ModuleItem(
                                    folder = "renamed-folder",
                                    id = "com.example.module",
                                    name = "Example",
                                    version = "1",
                                    versionCode = "1",
                                    enabled = true,
                                    quarantined = true,
                                    trust = "suspect",
                                ),
                            ),
                    ),
            )

        val protections = state.moduleProtections()

        assertEquals("renamed-folder", protections.getValue("com.example.module").folder)
        assertEquals("com.example.module", protections.getValue("renamed-folder").moduleId)
        assertTrue(protections.getValue("com.example.module").matches(AshModuleFilter.Quarantined))
        assertTrue(protections.getValue("com.example.module").matches(AshModuleFilter.Suspect))
        assertFalse(protections.getValue("com.example.module").matches(AshModuleFilter.Normal))
    }

    @Test
    fun missingProtectionStillMatchesAllFilter() {
        val protection: AshModuleProtection? = null
        assertTrue(protection.matches(AshModuleFilter.All))
        assertFalse(protection.matches(AshModuleFilter.Protected))
    }

    private fun compatibleLifecycle() =
        AshModuleLifecycle(
            state = AshModuleLifecycleState.Current,
            installation = AshModuleInstallation(installed = true, active = true, controlAvailable = true),
            bundled = AshBundledModuleMetadata(version = "11.1.0", versionCode = 210),
            compatible = true,
            compatibilityMessage = "Compatible API 2",
        )
}
