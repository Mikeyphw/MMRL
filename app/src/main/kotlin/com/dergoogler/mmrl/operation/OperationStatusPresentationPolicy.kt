package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationStatus

enum class OperationStatusPresentation {
    QUEUED, RUNNING, WAITING_APPROVAL, CANCEL_REQUESTED, SUCCEEDED, FAILED, CANCELLED, OUTCOME_UNKNOWN, UNKNOWN;

    val isActive: Boolean get() = this in setOf(QUEUED, RUNNING, WAITING_APPROVAL, CANCEL_REQUESTED)
    val isError: Boolean get() = this == FAILED || this == OUTCOME_UNKNOWN
}

object OperationStatusPresentationPolicy {
    fun classify(raw: String): OperationStatusPresentation = when (raw) {
        OperationStatus.QUEUED.name -> OperationStatusPresentation.QUEUED
        OperationStatus.RUNNING.name -> OperationStatusPresentation.RUNNING
        OperationStatus.WAITING_APPROVAL.name -> OperationStatusPresentation.WAITING_APPROVAL
        OperationStatus.CANCEL_REQUESTED.name -> OperationStatusPresentation.CANCEL_REQUESTED
        OperationStatus.SUCCEEDED.name -> OperationStatusPresentation.SUCCEEDED
        OperationStatus.FAILED.name -> OperationStatusPresentation.FAILED
        OperationStatus.CANCELLED.name -> OperationStatusPresentation.CANCELLED
        OperationStatus.OUTCOME_UNKNOWN.name -> OperationStatusPresentation.OUTCOME_UNKNOWN
        else -> OperationStatusPresentation.UNKNOWN
    }
}
