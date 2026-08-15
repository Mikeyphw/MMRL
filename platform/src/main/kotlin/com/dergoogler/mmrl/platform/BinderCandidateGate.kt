package com.dergoogler.mmrl.platform

/**
 * Serializes publication of one Binder generation against death or caller cancellation.
 * A lost/cancelled candidate can never be published, and cleanup after publication runs once.
 */
internal class BinderCandidateGate {
    private val lock = Any()
    private var lost = false
    private var cancelled = false
    private var published = false
    private var cleaned = false

    fun publish(eligible: () -> Boolean, action: () -> Unit): Boolean = synchronized(lock) {
        if (lost || cancelled || published || !eligible()) return@synchronized false
        action()
        published = true
        true
    }

    fun lose(cleanup: () -> Unit): Boolean = terminate(loss = true, cleanup)

    fun cancel(cleanup: () -> Unit): Boolean = terminate(loss = false, cleanup)

    private fun terminate(loss: Boolean, cleanup: () -> Unit): Boolean = synchronized(lock) {
        if (loss) lost = true else cancelled = true
        if (!published || cleaned) return@synchronized false
        cleaned = true
        cleanup()
        true
    }
}
