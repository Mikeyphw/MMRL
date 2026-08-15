package com.dergoogler.mmrl.operation

/** Pure callback/backend reconciliation rule for callback-based module mutations. */
object ModuleMutationReconciliationPolicy {
    enum class Outcome { SUCCESS, FAILURE, OUTCOME_UNKNOWN }

    fun classify(
        callbackSucceeded: Boolean,
        callbackIdentityMatches: Boolean,
        backendMatchesExpected: Boolean,
        backendIsUnchanged: Boolean,
    ): Outcome = when {
        callbackSucceeded && callbackIdentityMatches && backendMatchesExpected -> Outcome.SUCCESS
        !callbackSucceeded && backendIsUnchanged -> Outcome.FAILURE
        else -> Outcome.OUTCOME_UNKNOWN
    }
}
