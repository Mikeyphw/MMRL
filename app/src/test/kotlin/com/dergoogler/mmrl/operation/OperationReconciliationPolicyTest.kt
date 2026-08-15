package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationKind
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationReconciliationPolicyTest {
    @Test fun `reviewed install resolves success only when target version is observed`() {
        assertEquals(
            OperationReconciliationPolicy.Resolution.SUCCEEDED,
            OperationReconciliationPolicy.classify(OperationKind.UPDATE, "2.0", "1.0", true, "2.0", "ENABLE"),
        )
        assertEquals(
            OperationReconciliationPolicy.Resolution.FAILED_NON_RETRYABLE,
            OperationReconciliationPolicy.classify(OperationKind.UPDATE, "2.0", "1.0", true, "1.0", "ENABLE"),
        )
        assertEquals(
            OperationReconciliationPolicy.Resolution.UNRESOLVED,
            OperationReconciliationPolicy.classify(OperationKind.UPDATE, "2.0", "1.0", true, "1.5", "ENABLE"),
        )
    }

    @Test fun `fresh install absence is a known failure but cannot bypass fresh review`() {
        assertEquals(
            OperationReconciliationPolicy.Resolution.FAILED_NON_RETRYABLE,
            OperationReconciliationPolicy.classify(OperationKind.INSTALL, "1.0", null, false, null, null),
        )
    }

    @Test fun `idempotent module state can become retryable only after backend reconciliation`() {
        assertEquals(
            OperationReconciliationPolicy.Resolution.SUCCEEDED,
            OperationReconciliationPolicy.classify(OperationKind.DISABLE, null, null, true, "1", "DISABLE"),
        )
        assertEquals(
            OperationReconciliationPolicy.Resolution.FAILED_RETRYABLE,
            OperationReconciliationPolicy.classify(OperationKind.DISABLE, null, null, true, "1", "ENABLE"),
        )
    }

    @Test fun `arbitrary module actions cannot be auto reconciled`() {
        assertEquals(
            OperationReconciliationPolicy.Resolution.UNRESOLVED,
            OperationReconciliationPolicy.classify(OperationKind.MODULE_ACTION, null, null, true, "1", "ENABLE"),
        )
    }
}
