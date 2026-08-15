package com.dergoogler.mmrl.platform.manager

import java.util.concurrent.atomic.AtomicBoolean

/** Claims exactly one terminal callback regardless of racing/error paths. */
internal class TerminalSignalGate {
    private val completed = AtomicBoolean(false)
    fun claim(): Boolean = completed.compareAndSet(false, true)
}
