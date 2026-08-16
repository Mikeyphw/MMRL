package com.dergoogler.mmrl.ash.model

import java.security.MessageDigest
import java.util.Locale

/**
 * Incident-safe identity and evidence binding for AshReXcue guidance.
 *
 * UI text can change and root activity lines are intentionally human-oriented. Recovery scoring,
 * feedback, and execution must therefore bind to typed module identities and a concrete recovery
 * revision/fingerprint instead of substring matches in labels or descriptions.
 */
object AshIncidentIdentityPolicy {
    private val TOKEN = Regex("^[A-Za-z0-9._:-]{1,160}$")

    data class ModuleRef(
        val folder: String,
        val moduleId: String,
        val fingerprint: String,
        val versionCode: String,
    ) {
        val canonicalKey: String = listOf(folder, moduleId, fingerprint, versionCode)
            .joinToString("|") { it.trim().lowercase(Locale.ROOT) }
    }

    data class IncidentScope(
        val incidentId: String,
        val recoveryRevision: String,
        val module: ModuleRef,
        val issuedAt: Long,
    ) {
        val binding: String = sha256("$incidentId\u0000$recoveryRevision\u0000${module.canonicalKey}\u0000$issuedAt")
    }

    fun moduleRef(module: ModuleItem): ModuleRef = ModuleRef(
        folder = requireToken(module.folder, "folder"),
        moduleId = module.id.takeIf(String::isNotBlank)?.let { requireToken(it, "moduleId") }.orEmpty(),
        fingerprint = module.fingerprint.takeIf(String::isNotBlank)?.let { requireToken(it, "fingerprint") }.orEmpty(),
        versionCode = module.versionCode.takeIf(String::isNotBlank)?.let { requireToken(it, "versionCode") }.orEmpty(),
    )

    fun incidentScope(snapshot: AshSnapshot, module: ModuleItem, nowSeconds: Long): IncidentScope = IncidentScope(
        incidentId = snapshot.dashboard.latestRescueId.takeIf(String::isNotBlank)
            ?: sha256("incident:${snapshot.recoveryRevision}:${snapshot.generatedAt}").take(24),
        recoveryRevision = requireToken(snapshot.recoveryRevision, "recoveryRevision"),
        module = moduleRef(module),
        issuedAt = nowSeconds,
    )

    fun validateFeedback(
        expected: IncidentScope,
        submittedRecoveryRevision: String,
        submittedModuleFolder: String,
        submittedBinding: String,
        nowSeconds: Long,
        maxAgeSeconds: Long = 7L * 24L * 60L * 60L,
    ): Boolean {
        if (nowSeconds < expected.issuedAt) return false
        if (nowSeconds - expected.issuedAt > maxAgeSeconds) return false
        if (submittedRecoveryRevision != expected.recoveryRevision) return false
        if (submittedModuleFolder != expected.module.folder) return false
        return submittedBinding == expected.binding
    }

    fun evidenceWeight(basePoints: Int, evidenceAgeSeconds: Long): Int {
        val divisor = when {
            evidenceAgeSeconds < 0L -> 4
            evidenceAgeSeconds <= 24L * 60L * 60L -> 1
            evidenceAgeSeconds <= 7L * 24L * 60L * 60L -> 2
            evidenceAgeSeconds <= 30L * 24L * 60L * 60L -> 4
            else -> 8
        }
        return basePoints / divisor
    }

    private fun requireToken(value: String, field: String): String {
        require(TOKEN.matches(value)) { "Invalid AshReXcue $field identity" }
        return value
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
