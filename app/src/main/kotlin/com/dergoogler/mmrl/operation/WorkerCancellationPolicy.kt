package com.dergoogler.mmrl.operation

/** Distinguishes cancellation of a queued worker from detachment from a process-owned mutation. */
object WorkerCancellationPolicy {
    enum class Disposition { CANCEL_QUEUED_OPERATION, DETACH_FROM_ACTIVE_COORDINATOR }

    fun disposition(coordinatorActive: Boolean): Disposition =
        if (coordinatorActive) Disposition.DETACH_FROM_ACTIVE_COORDINATOR else Disposition.CANCEL_QUEUED_OPERATION
}
