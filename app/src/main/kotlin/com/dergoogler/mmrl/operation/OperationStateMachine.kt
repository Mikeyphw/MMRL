package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationStatus

/** Pure transition/cancellation policy shared by the durable privileged-operation coordinator and tests. */
object OperationStateMachine {
    private val allowed =
        mapOf(
            OperationStatus.QUEUED to setOf(
                OperationStatus.RUNNING,
                OperationStatus.WAITING_APPROVAL,
                OperationStatus.CANCEL_REQUESTED,
                OperationStatus.FAILED,
                OperationStatus.CANCELLED,
            ),
            OperationStatus.RUNNING to setOf(
                OperationStatus.WAITING_APPROVAL,
                OperationStatus.CANCEL_REQUESTED,
                OperationStatus.SUCCEEDED,
                OperationStatus.FAILED,
                OperationStatus.CANCELLED,
                OperationStatus.OUTCOME_UNKNOWN,
            ),
            OperationStatus.WAITING_APPROVAL to setOf(
                OperationStatus.QUEUED,
                OperationStatus.RUNNING,
                OperationStatus.CANCEL_REQUESTED,
                OperationStatus.CANCELLED,
                OperationStatus.FAILED,
                OperationStatus.OUTCOME_UNKNOWN,
            ),
            OperationStatus.CANCEL_REQUESTED to setOf(
                OperationStatus.CANCELLED,
                OperationStatus.OUTCOME_UNKNOWN,
                OperationStatus.FAILED,
                OperationStatus.SUCCEEDED,
            ),
        )

    fun canTransition(from: OperationStatus, to: OperationStatus): Boolean =
        (from == to && from in setOf(OperationStatus.RUNNING, OperationStatus.WAITING_APPROVAL)) ||
            to in allowed[from].orEmpty()

    fun interruptedOutcome(mutationStarted: Boolean): OperationStatus =
        if (mutationStarted) OperationStatus.OUTCOME_UNKNOWN else OperationStatus.CANCELLED

    fun retryRequiresReconciliation(status: OperationStatus, mutationStarted: Boolean): Boolean =
        status == OperationStatus.OUTCOME_UNKNOWN || mutationStarted && status != OperationStatus.SUCCEEDED
}
