package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationStateMachineTest {
    @Test fun `queued operation may wait for approval then resume queued`() {
        assertTrue(OperationStateMachine.canTransition(OperationStatus.QUEUED, OperationStatus.WAITING_APPROVAL))
        assertTrue(OperationStateMachine.canTransition(OperationStatus.WAITING_APPROVAL, OperationStatus.QUEUED))
    }

    @Test fun `terminal state cannot be rewritten`() {
        assertFalse(OperationStateMachine.canTransition(OperationStatus.SUCCEEDED, OperationStatus.FAILED))
        assertFalse(OperationStateMachine.canTransition(OperationStatus.FAILED, OperationStatus.SUCCEEDED))
    }

    @Test fun `interruption before mutation is a clean cancellation`() {
        assertEquals(OperationStatus.CANCELLED, OperationStateMachine.interruptedOutcome(false))
    }

    @Test fun `interruption after mutation is outcome unknown`() {
        assertEquals(OperationStatus.OUTCOME_UNKNOWN, OperationStateMachine.interruptedOutcome(true))
    }

    @Test fun `unknown outcome requires reconciliation before retry`() {
        assertTrue(OperationStateMachine.retryRequiresReconciliation(OperationStatus.OUTCOME_UNKNOWN, true))
        assertFalse(OperationStateMachine.retryRequiresReconciliation(OperationStatus.SUCCEEDED, true))
    }
    @Test fun `queued operation may enter cancellation requested before mutation`() {
        assertTrue(OperationStateMachine.canTransition(OperationStatus.QUEUED, OperationStatus.CANCEL_REQUESTED))
        assertTrue(OperationStateMachine.canTransition(OperationStatus.CANCEL_REQUESTED, OperationStatus.CANCELLED))
    }

    @Test fun `waiting approval may be cancelled without starting mutation`() {
        assertTrue(OperationStateMachine.canTransition(OperationStatus.WAITING_APPROVAL, OperationStatus.CANCEL_REQUESTED))
        assertEquals(OperationStatus.CANCELLED, OperationStateMachine.interruptedOutcome(false))
    }

    @Test fun `waiting approval with mutation evidence may resolve unknown`() {
        assertTrue(OperationStateMachine.canTransition(OperationStatus.WAITING_APPROVAL, OperationStatus.OUTCOME_UNKNOWN))
    }

}
