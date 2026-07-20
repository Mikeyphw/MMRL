package com.dergoogler.mmrl.ash.model

import com.dergoogler.mmrl.model.ModuleIdentity

enum class AshProtectionStatus {
    Unavailable,
    UpdateRequired,
    RebootPending,
    Cached,
    Monitoring,
    RestorationTrial,
    Quarantined,
    Stable,
}

data class AshProtectionSummary(
    val status: AshProtectionStatus = AshProtectionStatus.Unavailable,
    val title: String = "AshReXcue unavailable",
    val description: String = "Protection status is unavailable",
    val failedBoots: Int = 0,
    val failureThreshold: Int = 0,
    val quarantinedModules: Int = 0,
    val protectedModules: Int = 0,
    val lastSuccessfulAt: Long = 0,
    val readOnly: Boolean = true,
)

enum class AshModuleFilter {
    All,
    NeedsReview,
    Changed,
    Protected,
    Trusted,
    Normal,
    Suspect,
    Quarantined,
}

data class AshModuleProtection(
    val folder: String,
    val moduleId: String,
    val trust: String,
    val quarantined: Boolean,
    val enabled: Boolean,
    val changedSinceStable: Boolean = false,
    val riskScore: Int = 0,
    val riskBand: AshModuleRiskBand = AshModuleRiskBand.Low,
    val intelligenceSummary: String = "",
) {
    val needsReview: Boolean
        get() = quarantined || changedSinceStable || riskBand >= AshModuleRiskBand.High || trust == "suspect"
}

fun AshManagerState.protectionSummary(): AshProtectionSummary {
    val dashboard = snapshot?.dashboard
    val status = when {
        lifecycle.state == AshModuleLifecycleState.Missing ||
            lifecycle.state == AshModuleLifecycleState.Disabled ||
            lifecycle.state == AshModuleLifecycleState.Broken ||
            lifecycle.state == AshModuleLifecycleState.Incompatible ||
            !rootAvailable -> AshProtectionStatus.Unavailable
        lifecycle.state == AshModuleLifecycleState.Outdated -> AshProtectionStatus.UpdateRequired
        lifecycle.rebootRequired -> AshProtectionStatus.RebootPending
        source == AshSnapshotSource.Cache -> AshProtectionStatus.Cached
        dashboard?.restoreState == "testing" -> AshProtectionStatus.RestorationTrial
        (dashboard?.quarantined ?: 0) > 0 -> AshProtectionStatus.Quarantined
        dashboard?.bootState in setOf("booting", "monitoring") -> AshProtectionStatus.Monitoring
        else -> AshProtectionStatus.Stable
    }

    val title = when (status) {
        AshProtectionStatus.Unavailable -> when (lifecycle.state) {
            AshModuleLifecycleState.Missing -> "AshReXcue is not installed"
            AshModuleLifecycleState.Disabled -> "AshReXcue is disabled"
            else -> "AshReXcue unavailable"
        }
        AshProtectionStatus.UpdateRequired -> "AshReXcue update required"
        AshProtectionStatus.RebootPending -> "AshReXcue reboot pending"
        AshProtectionStatus.Cached -> "AshReXcue last known state"
        AshProtectionStatus.Monitoring -> "AshReXcue is monitoring boot"
        AshProtectionStatus.RestorationTrial -> "Restoration trial active"
        AshProtectionStatus.Quarantined -> "Modules quarantined"
        AshProtectionStatus.Stable -> "AshReXcue protection stable"
    }

    val description = when (status) {
        AshProtectionStatus.Unavailable -> liveError ?: lifecycle.compatibilityMessage
        AshProtectionStatus.UpdateRequired ->
            "Installed ${lifecycle.installation.version.ifBlank { "version unknown" }}; bundled ${lifecycle.bundled.version}"
        AshProtectionStatus.RebootPending -> "A module install, update, or state change is waiting for reboot"
        AshProtectionStatus.Cached -> liveError ?: "Showing the most recent successful protection snapshot"
        AshProtectionStatus.Monitoring -> dashboard?.bootReason?.ifBlank { "Waiting for boot stability signals" }
            ?: "Waiting for boot stability signals"
        AshProtectionStatus.RestorationTrial ->
            "${dashboard?.restoreCount ?: 0} module(s) are being tested after restoration"
        AshProtectionStatus.Quarantined ->
            "${dashboard?.quarantined ?: 0} module(s) are isolated after a rescue"
        AshProtectionStatus.Stable -> "No active rescue incident"
    }

    return AshProtectionSummary(
        status = status,
        title = title,
        description = description,
        failedBoots = dashboard?.loops ?: 0,
        failureThreshold = dashboard?.threshold ?: 0,
        quarantinedModules = dashboard?.quarantined ?: 0,
        protectedModules = dashboard?.protectedModules ?: 0,
        lastSuccessfulAt = lastSuccessfulAt,
        readOnly = readOnly,
    )
}

fun AshManagerState.moduleProtections(): Map<String, AshModuleProtection> {
    val intelligence = moduleIntelligence()
    return snapshot
        ?.modules
        .orEmpty()
        .flatMap { module ->
            val insight = sequenceOf(module.id, module.folder)
                .map(ModuleIdentity::normalize)
                .mapNotNull(intelligence::get)
                .firstOrNull()
            val protection =
                AshModuleProtection(
                    folder = module.folder,
                    moduleId = module.id,
                    trust = module.trust.ifBlank { "normal" },
                    quarantined = module.quarantined,
                    enabled = module.enabled,
                    changedSinceStable = module.changedSinceStable,
                    riskScore = insight?.riskScore ?: 0,
                    riskBand = insight?.riskBand ?: AshModuleRiskBand.Low,
                    intelligenceSummary = insight?.summary.orEmpty(),
                )
            buildList {
                ModuleIdentity.normalize(module.id).takeIf(String::isNotBlank)?.let { add(it to protection) }
                ModuleIdentity.normalize(module.folder).takeIf(String::isNotBlank)?.let { add(it to protection) }
            }
        }.toMap()
}

fun AshModuleProtection?.matches(filter: AshModuleFilter): Boolean =
    when (filter) {
        AshModuleFilter.All -> true
        AshModuleFilter.NeedsReview -> this?.needsReview == true
        AshModuleFilter.Changed -> this?.changedSinceStable == true
        AshModuleFilter.Protected -> this?.trust == "protected"
        AshModuleFilter.Trusted -> this?.trust == "trusted"
        AshModuleFilter.Normal -> this?.trust == "normal" && !this.quarantined && !this.needsReview
        AshModuleFilter.Suspect -> this?.trust == "suspect"
        AshModuleFilter.Quarantined -> this?.quarantined == true
    }
