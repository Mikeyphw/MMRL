package com.dergoogler.mmrl.ash.automation

import com.dergoogler.mmrl.ash.model.AshGuidanceEngine
import com.dergoogler.mmrl.ash.model.AshGuidanceOutcome
import com.dergoogler.mmrl.ash.model.AshModuleIntelligence
import com.dergoogler.mmrl.ash.model.AshModuleIntelligenceEngine
import com.dergoogler.mmrl.ash.model.AshRecoveryPlan
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanEngine
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanPreset
import com.dergoogler.mmrl.ash.model.AshSnapshot
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import java.security.MessageDigest
import java.util.Locale

const val ASH_EXTERNAL_CONTROL_API_VERSION = 1
const val ASH_EXTERNAL_CONTROL_SCHEMA = "mmrl.ash.external.v1"
const val ASH_EXTERNAL_TOKEN_TTL_MILLIS = 30L * 60L * 1000L

enum class AshExternalEvidenceFilter(val wireValue: String) {
    All("all"),
    Quarantined("quarantined"),
    Suspect("suspect"),
    Changed("changed"),
    NeedsReview("needs-review"),
    ;

    companion object {
        fun parse(value: String?): AshExternalEvidenceFilter = entries.firstOrNull {
            it.wireValue == value?.trim()?.lowercase(Locale.ROOT)
        } ?: All
    }
}

enum class AshExternalMutationKind(val wireValue: String) {
    PreparePlan("prepare-plan"),
    ExecutePlan("execute-plan"),
    RecordOutcome("record-outcome"),
}

data class AshExternalPreparedPlan(
    val plan: AshRecoveryPlan,
    val idempotencyKey: String,
    val dryRun: Boolean,
    val createdAt: Long,
    val expiresAt: Long,
    val binding: String,
)

data class AshExternalRateLimit(
    val allowed: Boolean,
    val retryAfterMillis: Long = 0L,
)

object AshExternalControlPolicy {
    const val MAX_IDEMPOTENCY_KEY_LENGTH = 96
    const val MAX_FOLDER_INPUT_LENGTH = 1_024
    const val MAX_READS_PER_MINUTE = 60
    const val MAX_PREVIEWS_PER_MINUTE = 12
    const val MAX_EXECUTIONS_PER_TEN_MINUTES = 6
    const val MAX_OUTCOMES_PER_MINUTE = 20

    private val tokenPattern = Regex("^[A-Za-z0-9._:-]+$")

    fun requireIdempotencyKey(value: String?): String {
        val normalized = value?.trim().orEmpty()
        require(normalized.length in 8..MAX_IDEMPOTENCY_KEY_LENGTH) {
            "Idempotency key must contain 8 to $MAX_IDEMPOTENCY_KEY_LENGTH characters"
        }
        require(normalized.all { it.isLetterOrDigit() || it in "._:-" }) {
            "Idempotency key contains unsupported characters"
        }
        return normalized
    }

    fun requireToken(value: String?): String {
        val normalized = value?.trim().orEmpty()
        require(normalized.length in 16..160 && tokenPattern.matches(normalized)) {
            "Invalid automation token"
        }
        return normalized
    }

    fun parseFolders(value: String?): List<String> {
        val raw = value.orEmpty()
        require(raw.length <= MAX_FOLDER_INPUT_LENGTH) { "Module folder input is too long" }
        return raw
            .split(',', '\n', '\r', '\t', ' ')
            .map(String::trim)
            .filter(String::isNotBlank)
            .onEach(::requireFolder)
            .distinct()
    }

    fun requireFolder(value: String): String {
        require(value.length in 1..128 && value.all { it.isLetterOrDigit() || it in "._-" }) {
            "Invalid module folder: $value"
        }
        return value
    }

    fun requireRecommendationId(value: String?): String {
        val normalized = value?.trim().orEmpty()
        require(normalized.length in 1..128 && tokenPattern.matches(normalized)) {
            "Invalid guidance recommendation"
        }
        return normalized
    }

    fun parseOutcome(value: String?): AshGuidanceOutcome {
        val normalized = value?.trim()?.lowercase(Locale.ROOT)
        return AshGuidanceOutcome.entries.firstOrNull { it.wireValue == normalized }
            ?: throw IllegalArgumentException("Outcome must be helped, failed, or inconclusive")
    }

    fun preparePlan(
        snapshot: AshSnapshot,
        presetValue: String?,
        foldersValue: String?,
        idempotencyKey: String,
        dryRun: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): AshExternalPreparedPlan {
        val preset = parsePreset(presetValue)
        val plan = when (preset) {
            AshRecoveryPlanPreset.Custom -> AshRecoveryPlanEngine.custom(
                snapshot = snapshot,
                folders = parseFolders(foldersValue),
                nowSeconds = nowMillis / 1_000L,
            )
            else -> AshRecoveryPlanEngine.presets(
                snapshot = snapshot,
                guidance = AshGuidanceEngine.build(snapshot, nowMillis / 1_000L),
                nowSeconds = nowMillis / 1_000L,
            ).firstOrNull { it.preset == preset }
                ?: throw IllegalArgumentException("The requested recovery preset is unavailable")
        }
        val expiresAt = nowMillis + ASH_EXTERNAL_TOKEN_TTL_MILLIS
        return AshExternalPreparedPlan(
            plan = plan,
            idempotencyKey = requireIdempotencyKey(idempotencyKey),
            dryRun = dryRun,
            createdAt = nowMillis,
            expiresAt = expiresAt,
            binding = binding(plan, idempotencyKey, nowMillis, expiresAt),
        )
    }

    fun filterEvidence(
        snapshot: AshSnapshot,
        filter: AshExternalEvidenceFilter,
        source: AshSnapshotSource,
        readOnly: Boolean,
        nowSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): List<AshModuleIntelligence> = AshModuleIntelligenceEngine.build(
        snapshot = snapshot,
        source = source,
        readOnly = readOnly,
        nowSeconds = nowSeconds,
    ).filter { intelligence ->
        when (filter) {
            AshExternalEvidenceFilter.All -> true
            AshExternalEvidenceFilter.Quarantined -> intelligence.quarantined
            AshExternalEvidenceFilter.Suspect -> intelligence.trust == "suspect"
            AshExternalEvidenceFilter.Changed -> intelligence.changedSinceStable
            AshExternalEvidenceFilter.NeedsReview -> intelligence.needsReview
        }
    }

    fun binding(
        plan: AshRecoveryPlan,
        idempotencyKey: String,
        createdAt: Long,
        expiresAt: Long,
    ): String = sha256(
        listOf(
            ASH_EXTERNAL_CONTROL_SCHEMA,
            plan.id,
            plan.preset.name,
            plan.recoveryRevision,
            plan.affectedFolders.joinToString(","),
            plan.risk.name,
            requireIdempotencyKey(idempotencyKey),
            createdAt.toString(),
            expiresAt.toString(),
        ).joinToString("\n"),
    )

    fun rateLimit(
        timestamps: List<Long>,
        nowMillis: Long,
        limit: Int,
        windowMillis: Long,
    ): AshExternalRateLimit {
        require(limit > 0 && windowMillis > 0L)
        val active = timestamps.filter { nowMillis - it in 0 until windowMillis }.sorted()
        if (active.size < limit) return AshExternalRateLimit(allowed = true)
        val retryAfter = (active.first() + windowMillis - nowMillis).coerceAtLeast(1L)
        return AshExternalRateLimit(allowed = false, retryAfterMillis = retryAfter)
    }

    private fun parsePreset(value: String?): AshRecoveryPlanPreset {
        val normalized = value?.trim()?.lowercase(Locale.ROOT).orEmpty().ifBlank { "conservative" }
        return when (normalized) {
            "conservative" -> AshRecoveryPlanPreset.Conservative
            "balanced" -> AshRecoveryPlanPreset.Balanced
            "rapid" -> AshRecoveryPlanPreset.Rapid
            "custom" -> AshRecoveryPlanPreset.Custom
            else -> throw IllegalArgumentException("Preset must be conservative, balanced, rapid, or custom")
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
