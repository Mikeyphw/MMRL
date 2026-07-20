package com.dergoogler.mmrl.ash.automation

import android.content.Context
import com.dergoogler.mmrl.R
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

fun AshManagerState.automationSignals(config: AshAutomationConfig): List<AshAlertSignal> =
    automationSignals(DefaultAshAlertStrings, config)

fun AshManagerState.automationSignals(context: Context, config: AshAutomationConfig): List<AshAlertSignal> =
    automationSignals(AndroidAshAlertStrings(context), config)

private fun AshManagerState.automationSignals(strings: AshAlertStrings, config: AshAutomationConfig): List<AshAlertSignal> {
    val dashboard = snapshot?.dashboard
    val signals = mutableListOf<AshAlertSignal>()

    if (config.incidentAlerts && source == AshSnapshotSource.Live && dashboard != null) {
        val status = dashboard.latestRescueStatus.trim()
        val incidentActive = dashboard.latestRescueId.isNotBlank() && !status.isTerminalRecoveryStatus()
        if (incidentActive) {
            val stage = dashboard.rescueStageLabel.takeUnless { it.isBlank() || it == "unknown" }
            val detail = buildList {
                add(status.ifBlank { strings.recoveryActive() })
                stage?.let { add(strings.stage(it)) }
                if (dashboard.quarantined > 0) add(strings.quarantined(dashboard.quarantined))
                if (dashboard.loops > 0) add(strings.failedBoots(dashboard.loops, dashboard.threshold))
            }.joinToString(strings.separator())
            signals += AshAlertSignal(
                kind = AshAlertKind.Incident,
                key = listOf(
                    dashboard.latestRescueId,
                    status.lowercase(),
                    dashboard.rescueStage,
                    dashboard.quarantined,
                    dashboard.loops,
                ).joinToString(":"),
                title = strings.incidentTitle(),
                message = detail.ifBlank { dashboard.latestRescueReason.ifBlank { strings.openRecoveryCenter() } },
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
            title = strings.rebootTitle(),
            message = strings.rebootMessage(),
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
            title = strings.trialTitle(),
            message = if (count > 0) {
                strings.trialModulesMessage(count)
            } else {
                strings.trialActiveMessage()
            },
            repeatAfterMillis = 12L * HOUR_MILLIS,
        )
    }

    return signals
}

private fun String.isTerminalRecoveryStatus(): Boolean =
    lowercase() in setOf("stable", "completed", "complete", "resolved", "closed", "success", "idle")

private interface AshAlertStrings {
    fun separator(): String
    fun incidentTitle(): String
    fun recoveryActive(): String
    fun stage(stage: String): String
    fun quarantined(count: Int): String
    fun failedBoots(loops: Int, threshold: Int): String
    fun openRecoveryCenter(): String
    fun rebootTitle(): String
    fun rebootMessage(): String
    fun trialTitle(): String
    fun trialModulesMessage(count: Int): String
    fun trialActiveMessage(): String
}

private class AndroidAshAlertStrings(private val context: Context) : AshAlertStrings {
    override fun separator() = context.getString(R.string.list_separator_mid_dot)
    override fun incidentTitle() = context.getString(R.string.ash_alert_incident_title)
    override fun recoveryActive() = context.getString(R.string.ash_alert_recovery_active)
    override fun stage(stage: String) = context.getString(R.string.ash_alert_stage_value, stage)
    override fun quarantined(count: Int) = context.resources.getQuantityString(R.plurals.ash_alert_quarantined_count, count, count)
    override fun failedBoots(loops: Int, threshold: Int) = context.getString(R.string.ash_alert_failed_boots_value, loops, threshold)
    override fun openRecoveryCenter() = context.getString(R.string.ash_alert_open_recovery_center)
    override fun rebootTitle() = context.getString(R.string.ash_alert_reboot_title)
    override fun rebootMessage() = context.getString(R.string.ash_alert_reboot_message)
    override fun trialTitle() = context.getString(R.string.ash_alert_trial_title)
    override fun trialModulesMessage(count: Int) = context.resources.getQuantityString(R.plurals.ash_alert_trial_modules_message, count, count)
    override fun trialActiveMessage() = context.getString(R.string.ash_alert_trial_active_message)
}

private object DefaultAshAlertStrings : AshAlertStrings {
    override fun separator() = " · "
    override fun incidentTitle() = "AshReXcue recovery incident"
    override fun recoveryActive() = "Recovery is active"
    override fun stage(stage: String) = "stage $stage"
    override fun quarantined(count: Int) = "$count quarantined"
    override fun failedBoots(loops: Int, threshold: Int) = "$loops/$threshold failed boots"
    override fun openRecoveryCenter() = "Open Recovery Center to review the incident."
    override fun rebootTitle() = "Reboot required for boot protection"
    override fun rebootMessage() = "AshReXcue has a staged module change. Reboot before relying on live recovery controls."
    override fun trialTitle() = "Restoration trial needs review"
    override fun trialModulesMessage(count: Int) =
        "$count restored module${if (count == 1) " is" else "s are"} being tested. Complete or roll back the trial after checking stability."
    override fun trialActiveMessage() = "A restoration trial is active. Review device stability in Recovery Center."
}
