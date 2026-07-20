package com.dergoogler.mmrl.ash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshOperationStateTest {
    @Test
    fun operationTargetsAreTrackedIndependently() {
        val state = AshUiState(
            activeOperations = setOf(
                AshOperation(AshOperationKind.ExecuteRecoveryPlan, "balanced-plan"),
                AshOperation(AshOperationKind.ExportDiagnostics),
            ),
        )

        assertTrue(state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan))
        assertTrue(state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan, "balanced-plan"))
        assertFalse(state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan, "rapid-plan"))
        assertTrue(state.isOperationRunning(AshOperationKind.ExportDiagnostics))
        assertFalse(state.isOperationRunning(AshOperationKind.RepairState))
    }

    @Test
    fun refreshStateDoesNotImplyAnotherOperationIsRunning() {
        val state = AshUiState(
            activeOperations = setOf(AshOperation(AshOperationKind.Refresh)),
        )

        assertTrue(state.refreshing)
        assertFalse(state.isOperationRunning(AshOperationKind.CompleteTrial))
        assertFalse(state.isOperationRunning(AshOperationKind.PrepareModuleInstall))
    }
}
