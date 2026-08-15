package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationStatus

/** Distinguishes resumable durable queue/approval states from abandoned in-process execution. */
object OperationRecoveryPolicy {
    val interruptibleStatuses: Set<OperationStatus> = setOf(
        OperationStatus.QUEUED,
        OperationStatus.RUNNING,
        OperationStatus.WAITING_APPROVAL,
        OperationStatus.CANCEL_REQUESTED,
    )
    val interruptibleStatusNames: List<String> get() = interruptibleStatuses.map { it.name }

    fun recoverImmediately(status: OperationStatus, origin: String?): Boolean = when {
        status == OperationStatus.WAITING_APPROVAL -> !isDurableWorkerOrigin(origin)
        status == OperationStatus.QUEUED && isDurableWorkerOrigin(origin) -> false
        status in interruptibleStatuses -> true
        else -> false
    }

    fun recoverWhenStale(status: OperationStatus): Boolean = status in interruptibleStatuses

    private fun isDurableWorkerOrigin(origin: String?): Boolean =
        origin == "TASKER" || origin == "TASKER_ASH"
}
