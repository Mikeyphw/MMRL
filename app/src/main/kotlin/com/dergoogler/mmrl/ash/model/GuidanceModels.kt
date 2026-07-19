package com.dergoogler.mmrl.ash.model

import java.util.Locale
import kotlin.math.ceil

enum class AshGuidanceConfidence {
    Low,
    Medium,
    High,
    VeryHigh,
}

enum class AshGuidanceOutcome(val wireValue: String) {
    Helped("helped"),
    Failed("failed"),
    Inconclusive("inconclusive"),
}

enum class AshRecoveryRecommendationKind {
    RestoreSelected,
    MarkSuspect,
    ReviewTrial,
    Observe,
}

data class AshCulpritEvidence(
    val code: String,
    val title: String,
    val detail: String,
    val points: Int,
)

data class AshCulpritCandidate(
    val folder: String,
    val moduleId: String,
    val name: String,
    val score: Int,
    val confidence: AshGuidanceConfidence,
    val quarantined: Boolean,
    val protected: Boolean,
    val evidence: List<AshCulpritEvidence>,
)

data class AshRecoveryRecommendation(
    val id: String,
    val kind: AshRecoveryRecommendationKind,
    val title: String,
    val summary: String,
    val rationale: String,
    val affectedFolders: List<String> = emptyList(),
    val riskLabel: String,
    val requiresConfirmation: Boolean = true,
)

data class AshGuidanceFeedback(
    val recommendationId: String,
    val moduleFolder: String,
    val outcome: AshGuidanceOutcome,
    val timestamp: Long,
)

data class AshGuidancePlan(
    val activeIncident: Boolean,
    val candidates: List<AshCulpritCandidate>,
    val recommendations: List<AshRecoveryRecommendation>,
    val feedback: Map<String, AshGuidanceFeedback>,
)

object AshGuidanceEngine {
    private const val RECENT_WINDOW_SECONDS = 7L * 24L * 60L * 60L

    fun build(snapshot: AshSnapshot, nowSeconds: Long = System.currentTimeMillis() / 1000L): AshGuidancePlan {
        val activity = snapshot.activity
        val feedback = activity.guidanceFeedback()
        val latestRescue = snapshot.dashboard.latestRescueId
        val activeIncident = latestRescue.isNotBlank() &&
            snapshot.dashboard.latestRescueStatus.lowercase(Locale.ROOT) !in STABLE_STATUSES

        val candidates = snapshot.modules.mapNotNull { module ->
            val quarantine = snapshot.quarantine.firstOrNull {
                it.folder == module.folder || it.id == module.id
            }
            val aliases = listOf(module.folder, module.id, module.name)
                .filter(String::isNotBlank)
                .map { it.lowercase(Locale.ROOT) }
            val evidence = buildList {
                if (module.quarantined || quarantine != null) {
                    add(AshCulpritEvidence("quarantined", "Quarantined by rescue", quarantine?.reason?.ifBlank {
                        "AshReXcue isolated this module during recovery."
                    } ?: "AshReXcue isolated this module during recovery.", 32))
                }
                if (module.baseTrust.equals("suspect", ignoreCase = true) || module.trust.equals("suspect", ignoreCase = true)) {
                    add(AshCulpritEvidence("suspect", "Explicitly classified suspect", "The module is in AshReXcue's suspect set.", 28))
                }
                if (module.changedSinceStable) {
                    add(AshCulpritEvidence("changed", "Changed since stable boot", "Its fingerprint differs from the last stable module snapshot.", 26))
                }

                val rescueMentions = activity.count { item ->
                    item.type.equals("rescue", ignoreCase = true) && item.containsAny(aliases)
                }
                if (rescueMentions > 0) {
                    val points = (rescueMentions * 7).coerceAtMost(21)
                    add(AshCulpritEvidence("history", "Appears in rescue history", "Mentioned in $rescueMentions rescue session(s).", points))
                }

                if (latestRescue.isNotBlank() && activity.any { it.id == latestRescue && it.containsAny(aliases) }) {
                    add(AshCulpritEvidence("latest", "Part of the latest rescue", "This module appears in the active rescue manifest.", 18))
                }

                val failedTrialMentions = activity.count { item ->
                    item.type.equals("restoration", ignoreCase = true) &&
                        item.status.lowercase(Locale.ROOT) in FAILED_STATUSES &&
                        item.containsAny(aliases)
                }
                if (failedTrialMentions > 0) {
                    add(AshCulpritEvidence("trial_failure", "Failed restoration evidence", "A restoration attempt involving this module failed.", 16))
                }

                if (quarantine != null && quarantine.disabledAt > 0 && nowSeconds - quarantine.disabledAt <= RECENT_WINDOW_SECONDS) {
                    add(AshCulpritEvidence("recent", "Recently isolated", "The quarantine record was created within the last seven days.", 9))
                }
                if (activeIncident && module.enabled && !module.quarantined) {
                    add(AshCulpritEvidence("active", "Enabled during active incident", "It remains enabled while recovery is still active.", 7))
                }
                if (!module.enabled && !module.quarantined) {
                    add(AshCulpritEvidence("disabled", "Already disabled", "The module is disabled outside AshReXcue quarantine.", -8))
                }
                when (module.baseTrust.lowercase(Locale.ROOT)) {
                    "trusted" -> add(AshCulpritEvidence("trusted", "Trusted classification", "Trusted modules require stronger evidence before intervention.", -18))
                    "protected" -> add(AshCulpritEvidence("protected", "Protected classification", "Protected modules are excluded from guided destructive actions.", -55))
                }
                feedback.values
                    .filter { it.moduleFolder == module.folder }
                    .maxByOrNull(AshGuidanceFeedback::timestamp)
                    ?.let { prior ->
                        when (prior.outcome) {
                            AshGuidanceOutcome.Helped -> add(AshCulpritEvidence("feedback_helped", "Previous guidance helped", "A prior guided action involving this module improved recovery.", 8))
                            AshGuidanceOutcome.Failed -> add(AshCulpritEvidence("feedback_failed", "Previous guidance failed", "A prior guided action involving this module did not improve recovery.", -10))
                            AshGuidanceOutcome.Inconclusive -> Unit
                        }
                    }
            }
            val score = evidence.sumOf(AshCulpritEvidence::points).coerceIn(0, 100)
            if (score < 12) return@mapNotNull null
            AshCulpritCandidate(
                folder = module.folder,
                moduleId = module.id,
                name = module.name,
                score = score,
                confidence = score.toConfidence(),
                quarantined = module.quarantined || quarantine != null,
                protected = module.baseTrust.equals("protected", ignoreCase = true),
                evidence = evidence.sortedByDescending(AshCulpritEvidence::points),
            )
        }.sortedWith(compareByDescending<AshCulpritCandidate> { it.score }.thenBy { it.name.lowercase(Locale.ROOT) })

        return AshGuidancePlan(
            activeIncident = activeIncident,
            candidates = candidates.take(10),
            recommendations = recommendations(snapshot, candidates, activeIncident),
            feedback = feedback,
        )
    }

    private fun recommendations(
        snapshot: AshSnapshot,
        candidates: List<AshCulpritCandidate>,
        activeIncident: Boolean,
    ): List<AshRecoveryRecommendation> = buildList {
        if (snapshot.dashboard.restoreState.equals("testing", ignoreCase = true)) {
            add(
                AshRecoveryRecommendation(
                    id = "review-active-trial",
                    kind = AshRecoveryRecommendationKind.ReviewTrial,
                    title = "Review the active restoration trial",
                    summary = "Decide whether the current batch produced a stable boot.",
                    rationale = "AshReXcue cannot infer stability from app state alone. Complete the trial only after confirming the device is stable; otherwise roll it back.",
                    riskLabel = "Decision required",
                ),
            )
            return@buildList
        }

        val quarantined = candidates.filter { it.quarantined && !it.protected }
        if (quarantined.isNotEmpty()) {
            val safestFirst = quarantined.sortedWith(compareBy<AshCulpritCandidate> { it.score }.thenBy { it.folder })
            val guidedBatchSupported = snapshot.capabilities.supports("guided-recovery")
            val take = if (guidedBatchSupported && safestFirst.size >= 4) {
                ceil(safestFirst.size / 2.0).toInt()
            } else {
                1
            }
            val selected = safestFirst.take(take)
            add(
                AshRecoveryRecommendation(
                    id = recommendationId("restore", selected.map(AshCulpritCandidate::folder)),
                    kind = AshRecoveryRecommendationKind.RestoreSelected,
                    title = if (selected.size == 1) "Test the safest quarantined module" else "Test a low-risk half batch",
                    summary = selected.joinToString { it.name },
                    rationale = if (selected.size == 1) {
                        "Restoring the lowest-scoring candidate first minimizes risk while gathering useful evidence."
                    } else {
                        "A controlled low-risk half batch narrows the culprit set faster than restoring modules alphabetically."
                    },
                    affectedFolders = selected.map(AshCulpritCandidate::folder),
                    riskLabel = if (selected.size == 1) "Low-risk trial" else "Controlled batch",
                ),
            )
        }

        if (activeIncident) {
            candidates.firstOrNull { candidate ->
                !candidate.quarantined &&
                    !candidate.protected &&
                    candidate.score >= 45 &&
                    candidate.evidence.none { it.code == "suspect" }
            }?.let { candidate ->
                add(
                    AshRecoveryRecommendation(
                        id = "mark-suspect-${candidate.folder}",
                        kind = AshRecoveryRecommendationKind.MarkSuspect,
                        title = "Mark ${candidate.name} as suspect",
                        summary = "Include it in targeted rescue before escalating to normal modules.",
                        rationale = candidate.evidence.take(3).joinToString(" ") { it.title + "." },
                        affectedFolders = listOf(candidate.folder),
                        riskLabel = "Classification only",
                    ),
                )
            }
        }

        if (isEmpty()) {
            add(
                AshRecoveryRecommendation(
                    id = "observe-current-state",
                    kind = AshRecoveryRecommendationKind.Observe,
                    title = "Keep observing",
                    summary = "There is not enough evidence for a guided mutation yet.",
                    rationale = "Collect another boot result or recovery session before changing module state.",
                    riskLabel = "No mutation",
                    requiresConfirmation = false,
                ),
            )
        }
    }

    private fun recommendationId(prefix: String, folders: List<String>): String {
        val fingerprint = folders.joinToString("|") { "${it.length}:$it" }.hashCode().toUInt().toString(16)
        return "$prefix-${folders.size}-$fingerprint"
    }

    private fun ActivityItem.containsAny(aliases: List<String>): Boolean {
        val haystack = "$title\n$subtitle\n$details".lowercase(Locale.ROOT)
        return aliases.any(haystack::contains)
    }

    private fun List<ActivityItem>.guidanceFeedback(): Map<String, AshGuidanceFeedback> =
        asSequence()
            .filter { it.type.equals("guidance", ignoreCase = true) }
            .mapNotNull { item ->
                val values = item.details.lineSequence()
                    .mapNotNull { line -> line.substringBefore('=', "").takeIf(String::isNotBlank)?.let { it to line.substringAfter('=', "") } }
                    .toMap()
                val recommendationId = values["recommendation"].orEmpty()
                val outcome = AshGuidanceOutcome.entries.firstOrNull { it.wireValue == values["outcome"] }
                if (recommendationId.isBlank() || outcome == null) null else AshGuidanceFeedback(
                    recommendationId = recommendationId,
                    moduleFolder = values["module"].orEmpty(),
                    outcome = outcome,
                    timestamp = item.timestamp,
                )
            }
            .groupBy(AshGuidanceFeedback::recommendationId)
            .mapValues { (_, entries) -> entries.maxBy(AshGuidanceFeedback::timestamp) }

    private fun Int.toConfidence(): AshGuidanceConfidence = when {
        this >= 80 -> AshGuidanceConfidence.VeryHigh
        this >= 60 -> AshGuidanceConfidence.High
        this >= 35 -> AshGuidanceConfidence.Medium
        else -> AshGuidanceConfidence.Low
    }

    private val STABLE_STATUSES = setOf("stable", "completed", "complete", "success", "resolved")
    private val FAILED_STATUSES = setOf("failed", "error", "rolled-back", "rollback")
}
