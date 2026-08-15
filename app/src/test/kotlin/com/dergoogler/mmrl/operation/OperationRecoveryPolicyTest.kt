package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationRecoveryPolicyTest {
    @Test fun `durable Tasker queued work survives process recreation`() {
        assertFalse(OperationRecoveryPolicy.recoverImmediately(OperationStatus.QUEUED, "TASKER"))
        assertFalse(OperationRecoveryPolicy.recoverImmediately(OperationStatus.QUEUED, "TASKER_ASH"))
        assertTrue(OperationRecoveryPolicy.recoverImmediately(OperationStatus.QUEUED, null))
    }

    @Test fun `only durable Tasker approval waits survive ordinary process restart`() {
        assertFalse(OperationRecoveryPolicy.recoverImmediately(OperationStatus.WAITING_APPROVAL, "TASKER"))
        assertFalse(OperationRecoveryPolicy.recoverImmediately(OperationStatus.WAITING_APPROVAL, "TASKER_ASH"))
        assertTrue(OperationRecoveryPolicy.recoverImmediately(OperationStatus.WAITING_APPROVAL, null))
        assertTrue(OperationRecoveryPolicy.recoverImmediately(OperationStatus.WAITING_APPROVAL, "APP"))
        assertTrue(OperationRecoveryPolicy.recoverWhenStale(OperationStatus.WAITING_APPROVAL))
    }

    @Test fun `recovery query status set includes every active state including approval waits`() {
        assertTrue(OperationStatus.QUEUED in OperationRecoveryPolicy.interruptibleStatuses)
        assertTrue(OperationStatus.RUNNING in OperationRecoveryPolicy.interruptibleStatuses)
        assertTrue(OperationStatus.WAITING_APPROVAL in OperationRecoveryPolicy.interruptibleStatuses)
        assertTrue(OperationStatus.CANCEL_REQUESTED in OperationRecoveryPolicy.interruptibleStatuses)
        assertFalse(OperationStatus.OUTCOME_UNKNOWN in OperationRecoveryPolicy.interruptibleStatuses)
    }

    @Test fun `abandoned running mutation is always recovered immediately`() {
        assertTrue(OperationRecoveryPolicy.recoverImmediately(OperationStatus.RUNNING, "TASKER"))
        assertTrue(OperationRecoveryPolicy.recoverImmediately(OperationStatus.CANCEL_REQUESTED, null))
    }
}
