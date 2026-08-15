package com.dergoogler.mmrl.operation

import java.util.concurrent.atomic.AtomicBoolean

/** Retained UI launch gate: configuration recreation observes the existing orchestration instead of starting another one. */
class OneShotOperationGate {
    private val started = AtomicBoolean(false)

    fun tryStart(): Boolean = started.compareAndSet(false, true)
    fun hasStarted(): Boolean = started.get()
}
