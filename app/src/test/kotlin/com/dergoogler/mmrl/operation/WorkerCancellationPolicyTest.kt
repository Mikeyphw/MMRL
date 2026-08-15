package com.dergoogler.mmrl.operation

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkerCancellationPolicyTest {
    @Test fun `queued worker cancellation becomes a clean cancellation when no coordinator owns mutation`() {
        assertEquals(
            WorkerCancellationPolicy.Disposition.CANCEL_QUEUED_OPERATION,
            WorkerCancellationPolicy.disposition(coordinatorActive = false),
        )
    }

    @Test fun `worker cancellation only detaches when process coordinator already owns execution`() {
        assertEquals(
            WorkerCancellationPolicy.Disposition.DETACH_FROM_ACTIVE_COORDINATOR,
            WorkerCancellationPolicy.disposition(coordinatorActive = true),
        )
    }
}
