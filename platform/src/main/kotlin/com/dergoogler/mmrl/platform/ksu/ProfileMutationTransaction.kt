package com.dergoogler.mmrl.platform.ksu

/**
 * Coordinates profile + SELinux persistence with ordering chosen to fail closed:
 * custom policy is live/applied before root profile activation; revocation/default is applied
 * to the profile before stale policy persistence is removed.
 *
 * KernelSU policy injection is additive in the running kernel. A durable replacement can retire
 * old rules for the next boot but cannot subtract already-live rules; such successful transitions
 * are explicitly marked [Result.reconciliationRequired] rather than pretending the live state is
 * identical to the persisted target.
 */
object ProfileMutationTransaction {
    interface Backend {
        fun setProfile(profile: Profile): Boolean
        fun setPolicy(packageName: String, rules: String): Boolean
        fun clearPolicy(packageName: String): Boolean
    }

    data class Result(
        val success: Boolean,
        val rolledBack: Boolean,
        val reconciliationRequired: Boolean = false,
    )

    fun execute(previous: Profile, target: Profile, backend: Backend): Result {
        val targetNeedsPolicy = target.allowSu && !target.rootUseDefault && target.rules.isNotBlank()
        val previousNeedsPolicy = previous.allowSu && !previous.rootUseDefault && previous.rules.isNotBlank()
        val previousRulesDiffer =
            previousNeedsPolicy && (!targetNeedsPolicy || previous.rules != target.rules || previous.name != target.name)

        if (targetNeedsPolicy) {
            if (!backend.setPolicy(target.name, target.rules)) return Result(false, false)
            if (backend.setProfile(target)) {
                // New target is committed, but an older live policy cannot be subtracted from the
                // running kernel. Persisted state is correct; a reboot/reconcile retires old rules.
                return Result(true, false, reconciliationRequired = previousRulesDiffer)
            }

            if (previousNeedsPolicy) {
                backend.setPolicy(previous.name, previous.rules)
            } else {
                backend.clearPolicy(previous.name)
            }
            backend.setProfile(previous)
            // The target live rules were already injected and cannot be removed by this API.
            return Result(false, false, reconciliationRequired = true)
        }

        if (!backend.setProfile(target)) return Result(false, false)
        if (backend.clearPolicy(previous.name)) {
            // Revocation removes the privilege that could exercise stale live rules. If SU remains
            // enabled under default/empty policy, the old live custom rules persist until reboot.
            return Result(
                success = true,
                rolledBack = false,
                reconciliationRequired = previousNeedsPolicy && target.allowSu,
            )
        }

        // A stale policy file must not be paired with a successfully changed profile.
        val profileRestored = backend.setProfile(previous)
        val policyRestored = if (previousNeedsPolicy) {
            backend.setPolicy(previous.name, previous.rules)
        } else {
            backend.clearPolicy(previous.name)
        }
        return Result(false, profileRestored && policyRestored)
    }
}
