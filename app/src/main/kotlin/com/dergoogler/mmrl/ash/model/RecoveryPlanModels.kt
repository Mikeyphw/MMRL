package com.dergoogler.mmrl.ash.model

import java.util.Locale
import kotlin.math.ceil

enum class AshRecoveryPlanPreset {
    Conservative,
    Balanced,
    Rapid,
    Custom,
}

enum class AshRecoveryPlanRisk {
    Low,
    Moderate,
    High,
    Blocked,
}

enum class AshRecoveryGuardSeverity {
    Info,
    Warning,
    Blocker,
}

data class AshRecoveryGuard(
    val code: String,
    val title: String,
    val detail: String,
    val severity: AshRecoveryGuardSeverity,
)

data class AshRecoveryPlan(
    val id: String,
    val preset: AshRecoveryPlanPreset,
    val title: String,
    val summary: String,
    val affectedFolders: List<String>,
    val risk: AshRecoveryPlanRisk,
    val guards: List<AshRecoveryGuard>,
    val confirmationPhrase: String,
    val rollbackStrategy: String,
    val recoveryRevision: String,
) {
    val canExecute: Boolean
        get() = affectedFolders.isNotEmpty() && guards.none { it.severity == AshRecoveryGuardSeverity.Blocker }
}

object AshRecoveryPlanEngine {
    const val MAX_PLAN_MODULES = 8
    const val MAX_SNAPSHOT_AGE_SECONDS = 10L * 60L

    fun presets(
        snapshot: AshSnapshot,
        guidance: AshGuidancePlan = AshGuidanceEngine.build(snapshot),
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): List<AshRecoveryPlan> {
        val candidatesByFolder = guidance.candidates.associateBy(AshCulpritCandidate::folder)
        val eligible = snapshot.quarantine
            .filter { item ->
                val module = snapshot.modules.firstOrNull { it.folder == item.folder || it.id == item.id }
                item.exists && item.disablePresent &&
                    !module?.baseTrust.equals("protected", ignoreCase = true) &&
                    !item.trust.equals("protected", ignoreCase = true)
            }
            .sortedWith(
                compareBy<QuarantineItem> { item -> candidatesByFolder[item.folder]?.score ?: 0 }
                    .thenBy { item -> item.name.lowercase(Locale.ROOT) },
            )

        if (eligible.isEmpty()) {
            return listOf(
                buildPlan(
                    snapshot = snapshot,
                    preset = AshRecoveryPlanPreset.Conservative,
                    folders = emptyList(),
                    title = "Conservative trial",
                    summary = "No safe quarantined modules are currently available.",
                    nowSeconds = nowSeconds,
                ),
            )
        }

        val conservative = eligible.take(1).map(QuarantineItem::folder)
        val balancedCount = ceil(eligible.size / 2.0).toInt().coerceIn(1, 4)
        val balanced = eligible.take(balancedCount).map(QuarantineItem::folder)
        val rapid = eligible.map(QuarantineItem::folder)

        return listOf(
            buildPlan(
                snapshot = snapshot,
                preset = AshRecoveryPlanPreset.Conservative,
                folders = conservative,
                title = "Conservative trial",
                summary = "Restore one lowest-risk module and gather a clean boot result.",
                nowSeconds = nowSeconds,
            ),
            buildPlan(
                snapshot = snapshot,
                preset = AshRecoveryPlanPreset.Balanced,
                folders = balanced,
                title = "Balanced isolation trial",
                summary = "Restore up to half of the lowest-risk quarantine, capped at four modules.",
                nowSeconds = nowSeconds,
            ),
            buildPlan(
                snapshot = snapshot,
                preset = AshRecoveryPlanPreset.Rapid,
                folders = rapid,
                title = "Rapid restoration trial",
                summary = "Restore every eligible quarantined module in one high-risk trial.",
                nowSeconds = nowSeconds,
            ),
        ).distinctBy { plan -> plan.affectedFolders }
    }

    fun custom(
        snapshot: AshSnapshot,
        folders: List<String>,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): AshRecoveryPlan = buildPlan(
        snapshot = snapshot,
        preset = AshRecoveryPlanPreset.Custom,
        folders = folders,
        title = "Custom recovery plan",
        summary = "Restore only the modules selected in this plan.",
        nowSeconds = nowSeconds,
    )

    fun forRecommendation(
        snapshot: AshSnapshot,
        recommendation: AshRecoveryRecommendation,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): AshRecoveryPlan = buildPlan(
        snapshot = snapshot,
        preset = AshRecoveryPlanPreset.Custom,
        folders = recommendation.affectedFolders,
        title = recommendation.title,
        summary = recommendation.rationale,
        nowSeconds = nowSeconds,
    )

    fun executionGuards(
        plan: AshRecoveryPlan,
        snapshot: AshSnapshot,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): List<AshRecoveryGuard> = guards(
        snapshot = snapshot,
        folders = plan.affectedFolders,
        nowSeconds = nowSeconds,
        expectedRevision = plan.recoveryRevision,
    )

    private fun buildPlan(
        snapshot: AshSnapshot,
        preset: AshRecoveryPlanPreset,
        folders: List<String>,
        title: String,
        summary: String,
        nowSeconds: Long,
    ): AshRecoveryPlan {
        val normalized = folders.filter(String::isNotBlank).distinct()
        val guards = guards(snapshot, normalized, nowSeconds)
        val trustedCount = normalized.count { folder ->
            snapshot.modules.firstOrNull { it.folder == folder }?.baseTrust.equals("trusted", ignoreCase = true)
        }
        val risk = when {
            guards.any { it.severity == AshRecoveryGuardSeverity.Blocker } -> AshRecoveryPlanRisk.Blocked
            normalized.size == 1 && trustedCount == 0 -> AshRecoveryPlanRisk.Low
            normalized.size <= 4 && trustedCount == 0 -> AshRecoveryPlanRisk.Moderate
            else -> AshRecoveryPlanRisk.High
        }
        val confirmationPhrase = if (risk == AshRecoveryPlanRisk.High) {
            "RESTORE ${normalized.size} MODULES"
        } else {
            ""
        }
        return AshRecoveryPlan(
            id = planId(preset, normalized, snapshot.recoveryRevision),
            preset = preset,
            title = title,
            summary = summary,
            affectedFolders = normalized,
            risk = risk,
            guards = guards,
            confirmationPhrase = confirmationPhrase,
            rollbackStrategy = "A failed or unfinished boot automatically disables the tested modules and returns them to AshReXcue quarantine.",
            recoveryRevision = snapshot.recoveryRevision,
        )
    }

    private fun guards(
        snapshot: AshSnapshot,
        folders: List<String>,
        nowSeconds: Long,
        expectedRevision: String? = null,
    ): List<AshRecoveryGuard> = buildList {
        if (!snapshot.capabilities.supports("recovery-plans")) {
            add(blocker("capability", "Module update required", "Install the bundled AshReXcue update before executing guarded recovery plans."))
        }
        if (snapshot.recoveryRevision.isBlank()) {
            add(blocker("revision-missing", "Recovery state has no revision", "Refresh after updating AshReXcue so the plan can be bound to an exact quarantine state."))
        }
        if (!expectedRevision.isNullOrBlank() && snapshot.recoveryRevision != expectedRevision) {
            add(blocker("revision-changed", "Recovery state changed", "The quarantine or restoration state changed after this plan was created. Review a freshly generated plan."))
        }
        if (snapshot.generatedAt <= 0L) {
            add(blocker("timestamp-missing", "Snapshot age is unknown", "Refresh live AshReXcue state before changing module state."))
        } else if (nowSeconds - snapshot.generatedAt > MAX_SNAPSHOT_AGE_SECONDS) {
            add(blocker("snapshot-stale", "Snapshot is stale", "The recovery snapshot is more than ten minutes old. Refresh before executing this plan."))
        }
        if (snapshot.dashboard.restoreState.equals("testing", ignoreCase = true)) {
            add(blocker("trial-active", "A restoration trial is active", "Complete or roll back the current trial before starting another recovery plan."))
        }
        if (folders.isEmpty()) {
            add(blocker("empty", "No modules selected", "Select at least one live quarantined module."))
        }
        if (folders.size > MAX_PLAN_MODULES) {
            add(blocker("batch-limit", "Plan exceeds the safety limit", "Guarded plans are limited to $MAX_PLAN_MODULES modules per trial."))
        }

        folders.forEach { folder ->
            val quarantine = snapshot.quarantine.firstOrNull { it.folder == folder }
            val module = snapshot.modules.firstOrNull { it.folder == folder }
            when {
                quarantine == null -> add(blocker("not-quarantined:$folder", "$folder is not quarantined", "Only modules currently owned by AshReXcue quarantine can be restored."))
                !quarantine.exists || !quarantine.disablePresent -> add(blocker("stale:$folder", "$folder has stale quarantine state", "The module directory or disable marker no longer matches the quarantine record."))
            }
            if (module == null) {
                add(blocker("module-missing:$folder", "$folder is missing from the live module inventory", "Refresh and repair the quarantine record before restoring it."))
            } else {
                when (module.baseTrust.lowercase(Locale.ROOT)) {
                    "protected" -> add(blocker("protected:$folder", "${module.name} is protected", "Protected modules cannot be included in a recovery plan."))
                    "trusted" -> add(warning("trusted:$folder", "${module.name} is trusted", "Restoring a trusted module needs stronger confirmation because it may be infrastructure required for recovery."))
                }
            }
        }

        if (folders.isNotEmpty() && folders.size == snapshot.quarantine.count { it.exists && it.disablePresent }) {
            add(warning("all", "This plan restores the full live quarantine", "A full restoration gives less isolation evidence and carries the largest reboot risk."))
        }
        if (snapshot.dashboard.latestRescueId.isNotBlank()) {
            add(info("rollback", "Automatic rollback is armed", "A failed or unfinished test boot returns this exact batch to quarantine without escalating unrelated modules."))
        }
    }

    private fun planId(
        preset: AshRecoveryPlanPreset,
        folders: List<String>,
        revision: String,
    ): String {
        val input = buildString {
            append(preset.name.lowercase(Locale.ROOT)).append('|')
            append(revision).append('|')
            folders.forEach { append(it.length).append(':').append(it).append('|') }
        }
        return "plan-${preset.name.lowercase(Locale.ROOT)}-${input.hashCode().toUInt().toString(16)}"
    }

    private fun blocker(code: String, title: String, detail: String) =
        AshRecoveryGuard(code, title, detail, AshRecoveryGuardSeverity.Blocker)

    private fun warning(code: String, title: String, detail: String) =
        AshRecoveryGuard(code, title, detail, AshRecoveryGuardSeverity.Warning)

    private fun info(code: String, title: String, detail: String) =
        AshRecoveryGuard(code, title, detail, AshRecoveryGuardSeverity.Info)
}
