package com.dergoogler.mmrl.operation

/**
 * Keeps privileged outcome truth separate from app-local finalization.
 * Once authoritative backend state is verified, a later database/provenance failure is a known-applied
 * operation, not an ambiguous privileged outcome.
 */
object VerifiedMutationFinalizationPolicy {
    enum class Outcome {
        SUCCESS,
        KNOWN_APPLIED_FINALIZATION_FAILED,
        OUTCOME_UNKNOWN,
    }

    fun classify(backendVerified: Boolean, finalizationSucceeded: Boolean): Outcome = when {
        !backendVerified -> Outcome.OUTCOME_UNKNOWN
        finalizationSucceeded -> Outcome.SUCCESS
        else -> Outcome.KNOWN_APPLIED_FINALIZATION_FAILED
    }
}
