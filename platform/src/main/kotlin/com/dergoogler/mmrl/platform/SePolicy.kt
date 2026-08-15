package com.dergoogler.mmrl.platform

import android.util.Log
import com.dergoogler.mmrl.platform.file.SuFile
import com.dergoogler.mmrl.platform.file.readText
import com.dergoogler.mmrl.platform.file.writeText
import com.dergoogler.mmrl.platform.ksu.KsuInputPolicy
import com.dergoogler.mmrl.platform.ksu.KsuNative

object SePolicy {
    private const val TAG = "SePolicy"
    private const val POLICY_DIR = "/data/adb/ksu/profile/selinux"

    fun isSepolicyValid(rules: String?): Boolean {
        if (rules == null) return true
        return runCatching { SePolicyParser.parseSepolicy(rules, strict = true).getOrThrow() }
            .onFailure { Log.w(TAG, "SEPolicy validation failed: ${it.message}") }
            .isSuccess
    }

    fun getSepolicy(pkg: String): String {
        if (!KsuInputPolicy.validPackage(pkg)) return ""
        return try {
            val file = policyFile(pkg)
            if (file.exists()) file.readText() else ""
        } catch (error: Exception) {
            Log.e(TAG, "Failed to read sepolicy for $pkg", error)
            ""
        }
    }

    /**
     * Applies live rules first and persists only after live application succeeds. If persistence
     * fails, the previous persisted file is restored best-effort. KernelSU's live allow-rule API
     * is additive, so a failed transaction never claims that an already-applied rule was removed.
     */
    fun setSePolicy(pkg: String, rules: String): Boolean {
        if (!KsuInputPolicy.validPackage(pkg) || rules.isBlank()) return clearSePolicy(pkg)
        if (!isSepolicyValid(rules)) return false

        val previous = getSepolicy(pkg)
        if (!applyPolicyRules(pkg, rules)) return false

        return try {
            val dir = SuFile(POLICY_DIR)
            if (!dir.exists() && !dir.mkdirs()) return false
            policyFile(pkg).writeText(rules)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Failed to persist sepolicy for $pkg; restoring previous persisted state", error)
            restorePersisted(pkg, previous)
            false
        }
    }

    /** Removes stale custom policy persistence for default/revoked/empty profiles. */
    fun clearSePolicy(pkg: String): Boolean {
        if (!KsuInputPolicy.validPackage(pkg)) return false
        return try {
            val file = policyFile(pkg)
            !file.exists() || file.delete()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to clear persisted sepolicy for $pkg", error)
            false
        }
    }

    private fun restorePersisted(pkg: String, previous: String) {
        runCatching {
            if (previous.isBlank()) {
                policyFile(pkg).apply { if (exists()) delete() }
            } else {
                val dir = SuFile(POLICY_DIR)
                if (!dir.exists()) dir.mkdirs()
                policyFile(pkg).writeText(previous)
            }
        }.onFailure { Log.e(TAG, "Failed to restore previous persisted sepolicy for $pkg", it) }
    }

    private fun policyFile(pkg: String) = SuFile(POLICY_DIR, pkg)

    private fun applyPolicyRules(pkg: String, rules: String): Boolean =
        try {
            val statements = SePolicyParser.parseSepolicy(rules, strict = true).getOrThrow()
            val atomic = statements.flatMap { it.toAtomicStatements() }.toTypedArray()
            KsuNative.applyPolicyRules(atomic, strict = true).also { success ->
                Log.i(TAG, "Live sepolicy apply for $pkg (${atomic.size} statements): $success")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to apply live sepolicy for $pkg", error)
            false
        }
}
