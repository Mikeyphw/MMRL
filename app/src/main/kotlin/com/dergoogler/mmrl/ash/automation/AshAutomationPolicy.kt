package com.dergoogler.mmrl.ash.automation

import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshSnapshotSource

private const val HOUR_MILLIS = 60L * 60L * 1000L

enum class AshAlertKind {
    Incident,
    RebootRequired,
    RestorationTrial,
}

data class AshAutomationConfig(
    val incidentAlerts: Boolean = true,
    val rebootReminders: Boolean = true,
    val restorationReminders: Boolean = true,
)

data class AshAlertSignal(
    val kind: AshAlertKind,
    val key: String,
    val title: String,
    val message: String,
    val urgent: Boolean = false,
    val repeatAfterMillis: Long = 24L * HOUR_MILLIS,
)

fun AshManagerState.automationSignals(config: AshAutomationConfig): List<AshAlertSignal> {
    val dashboard = snapshot?.dashboard
    val signals = mutableListOf<AshAlertSignal>()

    if (config.incidentAlerts && source == AshSnapshotSource.Live && dashboard != null) {
        val status = dashboard.latestRescueStatus.trim()
        val incidentActive = dashboard.latestRescueId.isNotBlank() && !status.isTerminalRecoveryStatus()
        if (incidentActive) {
            val stage = dashboard.rescueStageLabel.takeUnless { it.isBlank() || it == "unknown" }
            val detail = buildList {
                add(status.ifBlank { "Recovery is active" })
                stage?.let { add("stage $it") }
                if (dashboard.quarantined > 0) add("${dashboard.quarantined} quarantined")
                if (dashboard.loops > 0) add("${dashboard.loops}/${dashboard.threshold} failed boots")
            }.joinToString(" · ")
            signals += AshAlertSignal(
                kind = AshAlertKind.Incident,
                key = listOf(
                    dashboard.latestRescueId,
                    status.lowercase(),
                    dashboard.rescueStage,
                    dashboard.quarantined,
                    dashboard.loops,
                ).joinToString(":"),
                title = "AshReXcue recovery incident",
                message = detail.ifBlank { dashboard.latestRescueReason.ifBlank { "Open Recovery Center to review the incident." } },
                urgent = true,
                repeatAfterMillis = 6L * HOUR_MILLIS,
            )
        }
    }

    if (config.rebootReminders && lifecycle.rebootRequired) {
        val installation = lifecycle.installation
        signals += AshAlertSignal(
            kind = AshAlertKind.RebootRequired,
            key = "${lifecycle.state}:${installation.versionCode}:${installation.updatePending}:${installation.removalPending}",
            title = "Reboot required for boot protection",
            message = "AshReXcue has a staged module change. Reboot before relying on live recovery controls.",
            repeatAfterMillis = 24L * HOUR_MILLIS,
        )
    }

    if (
        config.restorationReminders &&
        source == AshSnapshotSource.Live &&
        dashboard?.restoreState.equals("testing", ignoreCase = true)
    ) {
        val count = dashboard?.restoreCount ?: 0
        signals += AshAlertSignal(
            kind = AshAlertKind.RestorationTrial,
            key = "trial:$count:${dashboard?.quarantined ?: 0}:${dashboard?.latestRescueId.orEmpty()}",
            title = "Restoration trial needs review",
            message = if (count > 0) {
                "$count restored module${if (count == 1) " is" else "s are"} being tested. Complete or roll back the trial after checking stability."
            } else {
                "A restoration trial is active. Review device stability in Recovery Center."
            },
            repeatAfterMillis = 12L * HOUR_MILLIS,
        )
    }

    return signals
}

private fun String.isTerminalRecoveryStatus(): Boolean =
    lowercase() in setOf("stable", "completed", "complete", "resolved", "closed", "success", "idle")
