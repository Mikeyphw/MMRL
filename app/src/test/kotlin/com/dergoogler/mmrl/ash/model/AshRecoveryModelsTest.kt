package com.dergoogler.mmrl.ash.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshRecoveryModelsTest {
    @Test
    fun recoveryOverviewExposesActiveTrialAndIncident() {
        val state = AshManagerState(
            snapshot = AshSnapshot(
                dashboard = Dashboard(
                    bootState = "monitoring",
                    loops = 1,
                    threshold = 2,
                    rescueStageLabel = "targeted",
                    nextRescue = "Changed modules",
                    quarantined = 3,
                    restoreState = "testing",
                    restoreCount = 2,
                    latestRescueId = "rescue-42",
                    latestRescueStatus = "active",
                    latestRescueReason = "SystemUI did not stabilize",
                ),
            ),
        )

        val overview = state.recoveryOverview()

        assertTrue(overview.restorationTrialActive)
        assertEquals(2, overview.restorationTrialCount)
        assertEquals("rescue-42", overview.activeIncidentId)
        assertEquals(3, overview.quarantineCount)
    }

    @Test
    fun sessionsExcludeConfigurationNoiseAndSortNewestFirst() {
        val snapshot = AshSnapshot(
            dashboard = Dashboard(latestRescueId = "rescue-new"),
            activity = listOf(
                ActivityItem("setting", 30, "settings", "Settings", "", "success", ""),
                ActivityItem("restore-old", 10, "restoration", "Restore", "", "completed", ""),
                ActivityItem("rescue-new", 20, "rescue", "Rescue", "", "active", "module-a"),
            ),
        )

        val sessions = snapshot.recoverySessions()

        assertEquals(listOf("rescue-new", "restore-old"), sessions.map(AshRecoverySession::id))
        assertEquals(AshRecoverySessionKind.Rescue, sessions.first().kind)
        assertTrue(sessions.first().active)
    }

    @Test
    fun staleQuarantineRequiresBothModuleAndDisableMarker() {
        assertFalse(
            QuarantineItem("folder", "id", "Name", "normal", "r", 0, true, true).isStale,
        )
        assertTrue(
            QuarantineItem("folder", "id", "Name", "normal", "r", 0, false, true).isStale,
        )
        assertTrue(
            QuarantineItem("folder", "id", "Name", "normal", "r", 0, true, false).isStale,
        )
    }
}
