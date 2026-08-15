package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationKind

/** Pure policy for resolving an OUTCOME_UNKNOWN row from authoritative backend observations. */
object OperationReconciliationPolicy {
    enum class Resolution { SUCCEEDED, FAILED_RETRYABLE, FAILED_NON_RETRYABLE, UNRESOLVED }

    fun classify(
        kind: OperationKind,
        targetVersion: String?,
        previousVersion: String?,
        modulePresent: Boolean,
        actualVersion: String?,
        actualState: String?,
    ): Resolution = when (kind) {
        OperationKind.INSTALL,
        OperationKind.UPDATE,
        OperationKind.ROLLBACK,
        OperationKind.RESTORE,
        -> when {
            !targetVersion.isNullOrBlank() && modulePresent && actualVersion == targetVersion -> Resolution.SUCCEEDED
            !previousVersion.isNullOrBlank() && modulePresent && actualVersion == previousVersion -> Resolution.FAILED_NON_RETRYABLE
            kind == OperationKind.INSTALL && previousVersion.isNullOrBlank() && !modulePresent -> Resolution.FAILED_NON_RETRYABLE
            else -> Resolution.UNRESOLVED
        }

        OperationKind.ENABLE -> stateResolution(modulePresent, actualState, "ENABLE")
        OperationKind.DISABLE -> stateResolution(modulePresent, actualState, "DISABLE")
        OperationKind.REMOVE -> when {
            !modulePresent -> Resolution.SUCCEEDED
            actualState == "REMOVE" -> Resolution.SUCCEEDED
            actualState != null -> Resolution.FAILED_RETRYABLE
            else -> Resolution.UNRESOLVED
        }
        else -> Resolution.UNRESOLVED
    }

    private fun stateResolution(modulePresent: Boolean, actualState: String?, expected: String): Resolution = when {
        !modulePresent -> Resolution.UNRESOLVED
        actualState == expected -> Resolution.SUCCEEDED
        actualState != null -> Resolution.FAILED_RETRYABLE
        else -> Resolution.UNRESOLVED
    }
}
