package com.dergoogler.mmrl.operation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationStatusPresentationPolicyTest {
    @Test fun `unknown privileged outcome is never presented as cancelled`() {
        val unknown = OperationStatusPresentationPolicy.classify("OUTCOME_UNKNOWN")
        assertEquals(OperationStatusPresentation.OUTCOME_UNKNOWN, unknown)
        assertTrue(unknown.isError)
        assertFalse(unknown == OperationStatusPresentation.CANCELLED)
    }

    @Test fun `durable active states remain distinct`() {
        assertEquals(OperationStatusPresentation.QUEUED, OperationStatusPresentationPolicy.classify("QUEUED"))
        assertEquals(OperationStatusPresentation.WAITING_APPROVAL, OperationStatusPresentationPolicy.classify("WAITING_APPROVAL"))
        assertEquals(OperationStatusPresentation.CANCEL_REQUESTED, OperationStatusPresentationPolicy.classify("CANCEL_REQUESTED"))
        assertTrue(OperationStatusPresentationPolicy.classify("RUNNING").isActive)
    }
}
