package com.dergoogler.mmrl.ash.model

enum class AshRecoverySessionKind {
    Rescue,
    Restoration,
    Diagnostics,
    Configuration,
    Other,
}

data class AshRecoverySession(
    val id: String,
    val timestamp: Long,
    val kind: AshRecoverySessionKind,
    val title: String,
    val summary: String,
    val status: String,
    val details: String,
    val active: Boolean = false,
)

data class AshRecoveryOverview(
    val activeIncidentId: String = "",
    val activeIncidentStatus: String = "",
    val activeIncidentReason: String = "",
    val bootState: String = "unknown",
    val bootReason: String = "",
    val failedBoots: Int = 0,
    val failureThreshold: Int = 0,
    val rescueStage: String = "unknown",
    val nextRescue: String = "Unknown",
    val quarantineCount: Int = 0,
    val restorationTrialActive: Boolean = false,
    val restorationTrialCount: Int = 0,
    val recentSessions: List<AshRecoverySession> = emptyList(),
)

fun AshManagerState.recoveryOverview(): AshRecoveryOverview {
    val dashboard = snapshot?.dashboard ?: Dashboard()
    val sessions = snapshot?.recoverySessions().orEmpty()
    return AshRecoveryOverview(
        activeIncidentId = dashboard.latestRescueId,
        activeIncidentStatus = dashboard.latestRescueStatus,
        activeIncidentReason = dashboard.latestRescueReason,
        bootState = dashboard.bootState,
        bootReason = dashboard.bootReason,
        failedBoots = dashboard.loops,
        failureThreshold = dashboard.threshold,
        rescueStage = dashboard.rescueStageLabel,
        nextRescue = dashboard.nextRescue,
        quarantineCount = dashboard.quarantined,
        restorationTrialActive = dashboard.restoreState.equals("testing", ignoreCase = true),
        restorationTrialCount = dashboard.restoreCount,
        recentSessions = sessions.take(6),
    )
}

fun AshSnapshot.recoverySessions(): List<AshRecoverySession> {
    val latestRescueId = dashboard.latestRescueId
    return activity
        .map { item ->
            val kind = when (item.type.lowercase()) {
                "rescue" -> AshRecoverySessionKind.Rescue
                "restoration", "restore", "trial" -> AshRecoverySessionKind.Restoration
                "diagnostics" -> AshRecoverySessionKind.Diagnostics
                "settings", "setting", "trust" -> AshRecoverySessionKind.Configuration
                else -> AshRecoverySessionKind.Other
            }
            AshRecoverySession(
                id = item.id,
                timestamp = item.timestamp,
                kind = kind,
                title = item.title,
                summary = item.subtitle,
                status = item.status,
                details = item.details,
                active = latestRescueId.isNotBlank() && item.id == latestRescueId &&
                    !item.status.equals("stable", ignoreCase = true) &&
                    !item.status.equals("completed", ignoreCase = true),
            )
        }
        .filter { session -> session.kind != AshRecoverySessionKind.Configuration }
        .distinctBy { session -> "${session.kind}:${session.id}:${session.timestamp}" }
        .sortedByDescending(AshRecoverySession::timestamp)
}

val QuarantineItem.isStale: Boolean
    get() = !exists || !disablePresent
