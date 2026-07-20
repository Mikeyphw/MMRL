package com.dergoogler.mmrl.ash.model

import com.dergoogler.mmrl.model.ModuleIdentity
import java.util.Locale

enum class AshModuleRiskBand {
    Unknown,
    Low,
    Elevated,
    High,
    Critical,
}

data class AshModuleIntelligenceFactor(
    val code: String,
    val title: String,
    val detail: String,
    val weight: Int,
)

data class AshModuleIntelligence(
    val folder: String,
    val moduleId: String,
    val name: String,
    val trust: String,
    val quarantined: Boolean,
    val enabled: Boolean,
    val changedSinceStable: Boolean,
    val riskScore: Int,
    val riskBand: AshModuleRiskBand,
    val summary: String,
    val recommendedAction: String,
    val factors: List<AshModuleIntelligenceFactor>,
    val source: AshSnapshotSource,
    val readOnly: Boolean,
) {
    val needsReview: Boolean
        get() = quarantined || changedSinceStable || riskBand >= AshModuleRiskBand.High || trust == "suspect"
}

data class AshUpdateSafetyInput(
    val installed: Boolean,
    val updateAvailable: Boolean,
    val hasBootScripts: Boolean = false,
    val hasNativeCode: Boolean = false,
    val changesSePolicy: Boolean = false,
    val changesSystemProperties: Boolean = false,
    val bundlesApks: Boolean = false,
    val intelligence: AshModuleIntelligence? = null,
)

data class AshUpdateSafety(
    val riskScore: Int,
    val riskBand: AshModuleRiskBand,
    val title: String,
    val summary: String,
    val reasons: List<String>,
    val shouldReviewBeforeInstall: Boolean,
)

object AshModuleIntelligenceEngine {
    fun build(
        snapshot: AshSnapshot,
        source: AshSnapshotSource = AshSnapshotSource.Live,
        readOnly: Boolean = false,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): List<AshModuleIntelligence> {
        val guidance = AshGuidanceEngine.build(snapshot, nowSeconds)
        val candidateByIdentity = guidance.candidates
            .flatMap { candidate ->
                listOf(candidate.folder, candidate.moduleId)
                    .map(ModuleIdentity::normalize)
                    .filter(String::isNotBlank)
                    .map { it to candidate }
            }
            .toMap()

        return snapshot.modules.map { module ->
            val candidate = sequenceOf(module.id, module.folder)
                .map(ModuleIdentity::normalize)
                .mapNotNull(candidateByIdentity::get)
                .firstOrNull()
            val factors = if (candidate != null) {
                candidate.evidence.map {
                    AshModuleIntelligenceFactor(
                        code = it.code,
                        title = it.title,
                        detail = it.detail,
                        weight = it.points,
                    )
                }
            } else {
                fallbackFactors(module)
            }
            val score = candidate?.score ?: fallbackScore(module, factors)
            val band = score.toRiskBand(module.quarantined)
            AshModuleIntelligence(
                folder = module.folder,
                moduleId = module.id,
                name = module.name,
                trust = module.baseTrust.ifBlank { module.trust.ifBlank { "normal" } }.lowercase(Locale.ROOT),
                quarantined = module.quarantined,
                enabled = module.enabled,
                changedSinceStable = module.changedSinceStable,
                riskScore = score,
                riskBand = band,
                summary = summaryFor(module, band, score),
                recommendedAction = recommendationFor(module, band),
                factors = factors.sortedByDescending(AshModuleIntelligenceFactor::weight),
                source = source,
                readOnly = readOnly,
            )
        }.sortedWith(
            compareByDescending<AshModuleIntelligence> { it.riskScore }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )
    }

    fun index(
        snapshot: AshSnapshot,
        source: AshSnapshotSource = AshSnapshotSource.Live,
        readOnly: Boolean = false,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): Map<String, AshModuleIntelligence> = build(snapshot, source, readOnly, nowSeconds)
        .flatMap { intelligence ->
            listOf(intelligence.moduleId, intelligence.folder)
                .map(ModuleIdentity::normalize)
                .filter(String::isNotBlank)
                .map { it to intelligence }
        }
        .toMap()

    fun assessUpdate(input: AshUpdateSafetyInput): AshUpdateSafety {
        if (!input.installed) {
            return AshUpdateSafety(
                riskScore = 0,
                riskBand = AshModuleRiskBand.Unknown,
                title = "No installed-state evidence",
                summary = "AshReXcue can assess this module after it has been installed and indexed.",
                reasons = emptyList(),
                shouldReviewBeforeInstall = false,
            )
        }

        val intelligence = input.intelligence
        val reasons = buildList {
            if (intelligence?.quarantined == true) add("The installed module is currently quarantined.")
            if (intelligence?.changedSinceStable == true) add("The installed module already differs from the last stable snapshot.")
            if (intelligence?.trust == "suspect") add("AshReXcue classifies the installed module as suspect.")
            if (intelligence?.trust == "protected") add("The module is protected and should be changed deliberately.")
            if (input.hasBootScripts) add("The update declares boot-time scripts.")
            if (input.hasNativeCode) add("The update declares native or Zygisk code.")
            if (input.changesSePolicy) add("The update declares SELinux policy changes.")
            if (input.changesSystemProperties) add("The update declares system property changes.")
            if (input.bundlesApks) add("The update bundles APK payloads.")
        }

        var score = (intelligence?.riskScore ?: 0) / 2
        if (intelligence?.quarantined == true) score += 45
        if (intelligence?.changedSinceStable == true) score += 18
        if (intelligence?.trust == "suspect") score += 18
        if (intelligence?.trust == "protected") score += 8
        if (input.hasBootScripts) score += 14
        if (input.hasNativeCode) score += 18
        if (input.changesSePolicy) score += 16
        if (input.changesSystemProperties) score += 12
        if (input.bundlesApks) score += 8
        if (!input.updateAvailable) score = (score - 12).coerceAtLeast(0)
        score = score.coerceIn(0, 100)

        val band = score.toRiskBand(intelligence?.quarantined == true)
        val title = when {
            intelligence?.quarantined == true -> "Resolve quarantine before updating"
            band == AshModuleRiskBand.Critical -> "Critical update review"
            band == AshModuleRiskBand.High -> "Review this update carefully"
            band == AshModuleRiskBand.Elevated -> "Update deserves attention"
            else -> "No elevated AshReXcue concern"
        }
        val summary = when {
            intelligence?.quarantined == true ->
                "Updating an isolated module can erase useful recovery evidence. Review or complete its recovery plan first."
            band >= AshModuleRiskBand.High ->
                "The installed evidence and declared update surface combine into a high-risk change."
            band == AshModuleRiskBand.Elevated ->
                "The update touches recovery-sensitive areas or the installed module has recent warning signals."
            input.updateAvailable ->
                "AshReXcue has no strong evidence against this update, but a reboot-safe backup remains prudent."
            else -> "The selected version does not introduce an additional update transition."
        }

        return AshUpdateSafety(
            riskScore = score,
            riskBand = band,
            title = title,
            summary = summary,
            reasons = reasons,
            shouldReviewBeforeInstall = intelligence?.quarantined == true || band >= AshModuleRiskBand.Elevated,
        )
    }

    private fun fallbackFactors(module: ModuleItem): List<AshModuleIntelligenceFactor> = buildList {
        if (module.quarantined) {
            add(
                AshModuleIntelligenceFactor(
                    code = "quarantined",
                    title = "Quarantined by recovery",
                    detail = "AshReXcue currently keeps this module isolated.",
                    weight = 48,
                ),
            )
        }
        if (module.changedSinceStable) {
            add(
                AshModuleIntelligenceFactor(
                    code = "changed",
                    title = "Changed since stable boot",
                    detail = "Its fingerprint differs from the last stable module snapshot.",
                    weight = 28,
                ),
            )
        }
        when (module.baseTrust.lowercase(Locale.ROOT)) {
            "suspect" -> add(
                AshModuleIntelligenceFactor(
                    code = "suspect",
                    title = "Suspect classification",
                    detail = "The module is explicitly classified as suspect.",
                    weight = 26,
                ),
            )
            "trusted" -> add(
                AshModuleIntelligenceFactor(
                    code = "trusted",
                    title = "Trusted classification",
                    detail = "Trusted modules require stronger evidence before intervention.",
                    weight = -16,
                ),
            )
            "protected" -> add(
                AshModuleIntelligenceFactor(
                    code = "protected",
                    title = "Protected classification",
                    detail = "Protected modules are excluded from automatic or guided destructive action.",
                    weight = -45,
                ),
            )
        }
    }

    private fun fallbackScore(
        module: ModuleItem,
        factors: List<AshModuleIntelligenceFactor>,
    ): Int {
        val base = factors.sumOf(AshModuleIntelligenceFactor::weight)
        val enabledDuringIncident = if (module.enabled && module.changedSinceStable) 6 else 0
        return (base + enabledDuringIncident).coerceIn(0, 100)
    }

    private fun summaryFor(
        module: ModuleItem,
        band: AshModuleRiskBand,
        score: Int,
    ): String = when {
        module.quarantined -> "Isolated by AshReXcue with a $score/100 evidence score."
        module.baseTrust.equals("protected", ignoreCase = true) ->
            "Protected by policy; evidence is shown for review without suggesting destructive action."
        band == AshModuleRiskBand.Critical -> "Multiple recovery signals strongly point to this module."
        band == AshModuleRiskBand.High -> "Recovery evidence makes this module a high-priority review candidate."
        band == AshModuleRiskBand.Elevated -> "The module has meaningful recovery or stability signals."
        module.changedSinceStable -> "The module changed after the last stable snapshot."
        else -> "No elevated AshReXcue evidence is currently associated with this module."
    }

    private fun recommendationFor(
        module: ModuleItem,
        band: AshModuleRiskBand,
    ): String = when {
        module.quarantined -> "Keep isolated until a guarded recovery plan is reviewed."
        module.baseTrust.equals("protected", ignoreCase = true) ->
            "Keep protected; inspect evidence manually before changing its state."
        band >= AshModuleRiskBand.High -> "Review its evidence before updating, enabling, or restoring it."
        module.changedSinceStable -> "Compare the module update with the last known stable version."
        module.baseTrust.equals("suspect", ignoreCase = true) -> "Include it in the next recovery investigation."
        module.baseTrust.equals("trusted", ignoreCase = true) -> "Trusted status is intact; review sensitive updates normally."
        else -> "No recovery action is suggested."
    }

    private fun Int.toRiskBand(quarantined: Boolean): AshModuleRiskBand = when {
        quarantined && this >= 70 -> AshModuleRiskBand.Critical
        this >= 75 -> AshModuleRiskBand.Critical
        this >= 55 -> AshModuleRiskBand.High
        this >= 25 -> AshModuleRiskBand.Elevated
        this > 0 -> AshModuleRiskBand.Low
        else -> AshModuleRiskBand.Low
    }
}

fun AshManagerState.moduleIntelligence(
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
): Map<String, AshModuleIntelligence> = snapshot?.let {
    AshModuleIntelligenceEngine.index(
        snapshot = it,
        source = source,
        readOnly = readOnly,
        nowSeconds = nowSeconds,
    )
}.orEmpty()
