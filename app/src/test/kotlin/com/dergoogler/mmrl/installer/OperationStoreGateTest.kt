package com.dergoogler.mmrl.installer

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OperationStoreGateTest {
    @Test fun `exclusive gate serializes concurrent store mutations`() = runBlocking {
        val gate = OperationStoreGate()
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        (1..12).map {
            async {
                gate.exclusive {
                    val now = active.incrementAndGet()
                    maxActive.updateAndGet { previous -> maxOf(previous, now) }
                    delay(2)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()
        assertEquals(1, maxActive.get())
    }
}
