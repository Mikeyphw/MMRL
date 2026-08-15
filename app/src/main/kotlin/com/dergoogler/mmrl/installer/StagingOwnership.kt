package com.dergoogler.mmrl.installer

/** Tracks the one-way handoff of an immutable staged artifact to the process-scoped coordinator. */
class StagingOwnership {
    @Volatile
    private var coordinatorOwned: Boolean = false

    fun handoffToCoordinator() {
        coordinatorOwned = true
    }

    fun callerMayRelease(): Boolean = !coordinatorOwned
}
