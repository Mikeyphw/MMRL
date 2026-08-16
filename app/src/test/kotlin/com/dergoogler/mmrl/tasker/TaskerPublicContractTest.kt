package com.dergoogler.mmrl.tasker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

class TaskerPublicContractTest {
    @Test
    fun `public result output exposes stable contract metadata`() {
        val output = taskerResultOutput(status = "OK", message = "ready")

        assertEquals(2, output.contractVersion)
        assertEquals("mmrl.tasker.output.v2", output.contractSchema)
        assertEquals(TaskerFreshness.FRESH.name, output.freshness)
        assertFalse(output.partial)
        assertFalse(output.stale)
        assertEquals("INLINE", output.deliveryStatus)
        assertEquals("MMRL", output.source)
        assertNotEquals(0L, output.generatedAt)
    }

    @Test
    fun `tasker output variable names use documented mmrl prefix`() {
        val annotation = TaskerResultOutput::class.java
            .getMethod("getOperationId")
            .getAnnotation(TaskerOutputVariable::class.java)
        assertEquals("mmrl_operation_id", annotation.name)
    }
}
