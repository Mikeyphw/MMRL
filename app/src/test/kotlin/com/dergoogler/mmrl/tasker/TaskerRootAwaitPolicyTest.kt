package com.dergoogler.mmrl.tasker

import org.junit.Assert.assertTrue
import org.junit.Test

class TaskerRootAwaitPolicyTest {
    @Test
    fun `module state callback wait is bounded and timeout is outcome unknown`() {
        assertTrue(TaskerRootAwaitPolicy.MODULE_STATE_CALLBACK_TIMEOUT_MS in 1L..60_000L)
        val message = TaskerRootAwaitPolicy.timeoutMessage()
        assertTrue(message.contains("outcome unknown"))
        assertTrue(message.contains("Reconcile"))
    }
}
