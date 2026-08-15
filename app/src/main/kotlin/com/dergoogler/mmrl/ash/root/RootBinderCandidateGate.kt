package com.dergoogler.mmrl.ash.root

/**
 * Serializes publication of a single Binder candidate against death/cancellation.
 * The cleanup callback runs at most once after a candidate was published.
 */
internal class RootBinderCandidateGate {
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

    fun lose(cleanup: () -> Unit): Boolean = terminate(isLoss = true, cleanup = cleanup)

    fun cancel(cleanup: () -> Unit): Boolean = terminate(isLoss = false, cleanup = cleanup)

    private fun terminate(isLoss: Boolean, cleanup: () -> Unit): Boolean = synchronized(lock) {
        if (isLoss) lost = true else cancelled = true
        if (!published || cleaned) return@synchronized false
        cleaned = true
        cleanup()
        true
    }
}
