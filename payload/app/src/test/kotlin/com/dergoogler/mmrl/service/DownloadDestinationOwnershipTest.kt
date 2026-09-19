package com.dergoogler.mmrl.service

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadDestinationOwnershipTest {
    @Test
    fun `concurrent claims for same filename receive distinct destinations`() {
        val root = Files.createTempDirectory("mmrl-destination-owner").toFile()
        try {
            val requested = root.resolve("module.zip")
            val ownership = DownloadDestinationOwnership()
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            val first = pool.submit<DownloadDestinationOwnership.Lease> { start.await(); ownership.claim(requested, "attempt-a") }
            val second = pool.submit<DownloadDestinationOwnership.Lease> { start.await(); ownership.claim(requested, "attempt-b") }
            start.countDown()
            val a = first.get()
            val b = second.get()
            pool.shutdownNow()

            assertNotEquals(a.destination.canonicalFile, b.destination.canonicalFile)
            assertEquals(setOf("module.zip", "module (1).zip"), setOf(a.destination.name, b.destination.name))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `existing destination is never selected for a new publication`() {
        val root = Files.createTempDirectory("mmrl-destination-existing").toFile()
        try {
            val requested = root.resolve("module.zip").apply { writeText("existing") }
            val lease = DownloadDestinationOwnership().claim(requested, "attempt")
            assertEquals("module (1).zip", lease.destination.name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `verified reuse may reserve the existing requested destination`() {
        val root = Files.createTempDirectory("mmrl-destination-reuse").toFile()
        try {
            val requested = root.resolve("module.zip").apply { writeText("verified") }
            val lease = DownloadDestinationOwnership().claim(requested, "attempt", allowExisting = true)
            assertEquals(requested.canonicalFile, lease.destination.canonicalFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `stale lease cannot release a newer reservation`() {
        val root = Files.createTempDirectory("mmrl-destination-stale").toFile()
        try {
            val requested = root.resolve("module.zip")
            val ownership = DownloadDestinationOwnership()
            val old = ownership.claim(requested, "old")
            assertTrue(ownership.release(old))
            val current = ownership.claim(requested, "new")
            assertFalse(ownership.release(old))
            assertEquals("new", ownership.currentAttempt(current.destination))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `many simultaneous claims allocate unique destinations`() {
        val root = Files.createTempDirectory("mmrl-destination-many").toFile()
        try {
            val requested = root.resolve("module.zip")
            val ownership = DownloadDestinationOwnership()
            val contenders = 8
            val ready = CountDownLatch(contenders)
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(contenders)
            try {
                val futures = (0 until contenders).map { index ->
                    pool.submit<DownloadDestinationOwnership.Lease> {
                        ready.countDown()
                        start.await()
                        ownership.claim(requested, "attempt-$index")
                    }
                }
                ready.await()
                start.countDown()
                val leases = futures.map { it.get() }
                assertEquals(contenders, leases.map { it.destination.canonicalPath }.toSet().size)
                assertEquals(contenders, leases.map { it.attemptId }.toSet().size)
                leases.forEach { assertTrue(ownership.release(it)) }
            } finally {
                pool.shutdownNow()
            }
        } finally {
            root.deleteRecursively()
        }
    }

}
