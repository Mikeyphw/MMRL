package com.dergoogler.mmrl.database.activity

import io.github.mikeyphw.mmrl.shared.OperationEvent
import io.github.mikeyphw.mmrl.shared.OperationKind
import io.github.mikeyphw.mmrl.shared.OperationPhase
import io.github.mikeyphw.mmrl.shared.OperationRecord
import io.github.mikeyphw.mmrl.shared.OperationStatus

/** Never infer the result of an interrupted operation that could modify device state. */
internal object ActivityRecoveryPolicy {
    fun recover(record: OperationRecord, now: Long): OperationRecord {
        require(record.status == OperationStatus.QUEUED || record.status == OperationStatus.RUNNING)
        val mayHaveMutated = record.status == OperationStatus.RUNNING &&
            record.kind !in setOf(OperationKind.DOWNLOAD, OperationKind.UPDATE_CHECK)
        val status = if (mayHaveMutated) OperationStatus.RECONCILIATION_REQUIRED else OperationStatus.INTERRUPTED
        val phase = if (mayHaveMutated) OperationPhase.WAITING_FOR_USER else OperationPhase.FINISHED
        // An earlier wall-clock timestamp is allowed after reboot or a time correction.
        val at = maxOf(now, record.updatedAtMillis)
        return checkNotNull(record.reduce(checkNotNull(OperationEvent.of(
            record.id, record.status, status, phase, at))))
    }
}
