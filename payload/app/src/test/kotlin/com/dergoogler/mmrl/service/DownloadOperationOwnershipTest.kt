package com.dergoogler.mmrl.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class DownloadOperationOwnershipTest {
    @Test
    fun `only one concurrent claim owns a logical operation`() {
        val ownership = DownloadOperationOwnership()
        val ready = CountDownLatch(16)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(16)
        try {
            val futures = (1..16).map {
                pool.submit<DownloadOperationOwnership.ClaimResult> {
                    ready.countDown()
                    start.await()
                    ownership.claim("operation-1")
                }
            }
            ready.await()
            start.countDown()
            val results = futures.map { it.get() }
            assertEquals(1, results.count { it is DownloadOperationOwnership.ClaimResult.Claimed })
            assertEquals(15, results.count { it is DownloadOperationOwnership.ClaimResult.AlreadyOwned })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `different logical operations receive different attempt identities`() {
        val ownership = DownloadOperationOwnership()
        val first = ownership.claim("one") as DownloadOperationOwnership.ClaimResult.Claimed
        val second = ownership.claim("two") as DownloadOperationOwnership.ClaimResult.Claimed
        assertNotEquals(first.lease.attemptId, second.lease.attemptId)
    }

    @Test
    fun `release is compare and remove and stale lease cannot release current owner`() {
        var sequence = 0
        val ownership = DownloadOperationOwnership { "attempt-${++sequence}" }
        val first = (ownership.claim("operation") as DownloadOperationOwnership.ClaimResult.Claimed).lease
        assertTrue(ownership.release(first))
        val second = (ownership.claim("operation") as DownloadOperationOwnership.ClaimResult.Claimed).lease
        assertFalse(ownership.release(first))
        assertEquals(second.attemptId, ownership.currentAttempt("operation"))
        assertTrue(ownership.release(second))
        assertNull(ownership.currentAttempt("operation"))
    }

    @Test
    fun `duplicate claim reports current owner without replacing it`() {
        val ownership = DownloadOperationOwnership { "attempt-fixed" }
        val first = ownership.claim("operation") as DownloadOperationOwnership.ClaimResult.Claimed
        val duplicate = ownership.claim("operation") as DownloadOperationOwnership.ClaimResult.AlreadyOwned
        assertEquals(first.lease.attemptId, duplicate.activeAttemptId)
        assertEquals(first.lease.attemptId, ownership.currentAttempt("operation"))
    }

    @Test
    fun `blank operation identity fails closed`() {
        val ownership = DownloadOperationOwnership()
        assertThrows(IllegalArgumentException::class.java) { ownership.claim("   ") }
    }
    @Test
    fun `terminal logical operation id may be claimed again only after prior lease releases`() {
        val ids = ArrayDeque(listOf("attempt-one", "attempt-two"))
        val ownership = DownloadOperationOwnership { ids.removeFirst() }
        val first = (ownership.claim("history-1") as DownloadOperationOwnership.ClaimResult.Claimed).lease
        assertTrue(ownership.release(first))
        val second = (ownership.claim("history-1") as DownloadOperationOwnership.ClaimResult.Claimed).lease
        assertNotEquals(first.attemptId, second.attemptId)
        assertEquals("attempt-two", ownership.currentAttempt("history-1"))
    }

}
