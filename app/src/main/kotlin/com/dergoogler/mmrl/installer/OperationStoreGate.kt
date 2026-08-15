package com.dergoogler.mmrl.installer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes quota calculation, pruning, publication, and deletion within an operation store. */
class OperationStoreGate {
    private val mutex = Mutex()

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
}
