package com.dergoogler.mmrl.service

import java.nio.file.Files
import java.util.UUID
import org.junit.Test
import org.junit.Assert.assertEquals

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class DownloadAttemptJournalTest {
    private fun attempt() = UUID.randomUUID().toString()

    @Test fun `queued downloading and publishing attempts survive journal recreation`() {
        val root = Files.createTempDirectory("attempt-journal").toFile()
        try {
            val phases = listOf(DownloadAttemptJournal.Phase.QUEUED, DownloadAttemptJournal.Phase.DOWNLOADING, DownloadAttemptJournal.Phase.PUBLISHING)
            phases.forEachIndexed { index, phase ->
                val id = attempt()
                DownloadAttemptJournal(root).write(DownloadAttemptJournal.Record("op-$index", id, phase, root.resolve("$id.part").path, root.resolve("out-$index.zip").path))
            }
            assertEquals(phases.toSet(), DownloadAttemptJournal(root).recoverable().map { it.phase }.toSet())
        } finally { root.deleteRecursively() }
    }

    @Test fun `terminal transition is idempotent and cannot be overwritten by a race`() {
        val root = Files.createTempDirectory("attempt-journal").toFile()
        try {
            val id = attempt()
            val journal = DownloadAttemptJournal(root)
            journal.write(DownloadAttemptJournal.Record("op", id, DownloadAttemptJournal.Phase.PUBLISHING, "tmp", "dest"))
            assertTrue(journal.transition(id, DownloadAttemptJournal.Phase.SUCCEEDED))
            assertTrue(journal.transition(id, DownloadAttemptJournal.Phase.SUCCEEDED))
            assertFalse(journal.transition(id, DownloadAttemptJournal.Phase.CANCELLED))
            assertEquals(DownloadAttemptJournal.Phase.SUCCEEDED, DownloadAttemptJournal(root).read(id)?.phase)
            assertTrue(DownloadAttemptJournal(root).recoverable().isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun `cancel wins once and completion cannot replace it`() {
        val root = Files.createTempDirectory("attempt-journal").toFile()
        try {
            val id = attempt()
            val journal = DownloadAttemptJournal(root)
            journal.write(DownloadAttemptJournal.Record("op", id, DownloadAttemptJournal.Phase.DOWNLOADING, "tmp", "dest"))
            assertTrue(journal.transition(id, DownloadAttemptJournal.Phase.CANCELLED))
            assertFalse(journal.transition(id, DownloadAttemptJournal.Phase.SUCCEEDED))
            assertEquals(DownloadAttemptJournal.Phase.CANCELLED, journal.read(id)?.phase)
        } finally { root.deleteRecursively() }
    }

    @Test fun `attempt journal rejects path shaped and non uuid identities`() {
        val root = Files.createTempDirectory("attempt-journal").toFile()
        try {
            val journal = DownloadAttemptJournal(root)
            listOf("../escape", ".", "..", "/absolute", "not-a-uuid").forEach { id ->
                try {
                    journal.write(DownloadAttemptJournal.Record("op", id, DownloadAttemptJournal.Phase.QUEUED, "tmp", "dest"))
                    throw AssertionError("Expected IllegalArgumentException for $id")
                } catch (_: IllegalArgumentException) {
                    // expected
                }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `concurrent terminal transitions preserve first terminal writer`() {
        val root = Files.createTempDirectory("attempt-journal-terminal-race").toFile()
        try {
            val id = attempt()
            val journal = DownloadAttemptJournal(root)
            journal.write(DownloadAttemptJournal.Record("op", id, DownloadAttemptJournal.Phase.PUBLISHING, "tmp", "dest"))
            val ready = java.util.concurrent.CountDownLatch(2)
            val start = java.util.concurrent.CountDownLatch(1)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val success = pool.submit<Boolean> {
                    ready.countDown()
                    start.await()
                    journal.transition(id, DownloadAttemptJournal.Phase.SUCCEEDED)
                }
                val cancel = pool.submit<Boolean> {
                    ready.countDown()
                    start.await()
                    journal.transition(id, DownloadAttemptJournal.Phase.CANCELLED)
                }
                ready.await()
                start.countDown()
                val results = listOf(success.get(), cancel.get())
                assertEquals(1, results.count { it })
                val terminal = journal.read(id)?.phase
                assertTrue(
                    terminal == DownloadAttemptJournal.Phase.SUCCEEDED ||
                        terminal == DownloadAttemptJournal.Phase.CANCELLED,
                )
                assertTrue(journal.recoverable().isEmpty())
            } finally {
                pool.shutdownNow()
            }
        } finally {
            root.deleteRecursively()
        }
    }

}
