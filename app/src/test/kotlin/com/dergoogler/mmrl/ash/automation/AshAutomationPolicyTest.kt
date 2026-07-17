package com.dergoogler.mmrl.ash.automation

import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshModuleInstallation
import com.dergoogler.mmrl.ash.model.AshModuleLifecycle
import com.dergoogler.mmrl.ash.model.AshModuleLifecycleState
import com.dergoogler.mmrl.ash.model.AshSnapshot
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.Dashboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshAutomationPolicyTest {
    @Test
    fun activeIncidentProducesUrgentSignal() {
        val state = liveState(
            dashboard = Dashboard(
                latestRescueId = "rescue-7",
                latestRescueStatus = "quarantining",
                rescueStage = 2,
                rescueStageLabel = "isolate suspects",
                quarantined = 3,
                loops = 2,
                threshold = 2,
            ),
        )

        val signal = state.automationSignals(AshAutomationConfig()).single()
        assertEquals(AshAlertKind.Incident, signal.kind)
        assertTrue(signal.urgent)
        assertTrue(signal.message.contains("3 quarantined"))
    }

    @Test
    fun terminalIncidentDoesNotNotify() {
        val state = liveState(
            dashboard = Dashboard(
                latestRescueId = "rescue-7",
                latestRescueStatus = "stable",
            ),
        )

        assertTrue(state.automationSignals(AshAutomationConfig()).isEmpty())
    }

    @Test
    fun rebootReminderDoesNotRequireLiveSnapshot() {
        val state = AshManagerState(
            lifecycle = AshModuleLifecycle(
                state = AshModuleLifecycleState.RebootPending,
                installation = AshModuleInstallation(installed = true, updatePending = true),
                rebootRequired = true,
            ),
            source = AshSnapshotSource.None,
        )

        val signals = state.automationSignals(AshAutomationConfig())
        assertEquals(listOf(AshAlertKind.RebootRequired), signals.map(AshAlertSignal::kind))
    }

    @Test
    fun restorationReminderCanBeDisabledIndependently() {
        val state = liveState(dashboard = Dashboard(restoreState = "testing", restoreCount = 2))

        val enabled = state.automationSignals(AshAutomationConfig())
        val disabled = state.automationSignals(AshAutomationConfig(restorationReminders = false))

        assertTrue(enabled.any { it.kind == AshAlertKind.RestorationTrial })
        assertFalse(disabled.any { it.kind == AshAlertKind.RestorationTrial })
    }

    private fun liveState(dashboard: Dashboard): AshManagerState =
        AshManagerState(
            snapshot = AshSnapshot(dashboard = dashboard),
            source = AshSnapshotSource.Live,
        )
}
