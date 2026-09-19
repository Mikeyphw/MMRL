package com.dergoogler.mmrl.service

import java.io.File
import org.junit.Test

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class DownloadDurableLifecycleSourceContractTest {
    private val source = sequenceOf(
        File("app/src/main/kotlin/com/dergoogler/mmrl/service/DownloadService.kt"),
        File("src/main/kotlin/com/dergoogler/mmrl/service/DownloadService.kt"),
    ).first(File::isFile).readText()

    @Test fun `finalization is independent of service lifecycle scope and releases ownership after durable state`() {
        val failure = source.substringAfter("override fun onFailure(e: Throwable)").substringBefore("private suspend fun reconcileAbandonedAttempts")
        assertTrue(failure.contains("durableFinalizationScope.launch"))
        assertFalse(failure.contains("lifecycleScope.launch"))
        val durable = failure.substringAfter("durableFinalizationScope.launch")
        assertTrue(durable.indexOf("attemptJournal.transition") in 0 until durable.indexOf("cleanupTask("))
    }

    @Test fun `startup reconciles every durable non terminal phase`() {
        assertTrue(source.contains("durableFinalizationScope.launch { reconcileAbandonedAttempts() }"))
        assertTrue(source.contains("attemptJournal.recoverable()"))
        assertTrue(source.contains("DownloadAttemptJournal.Phase.QUEUED"))
        assertTrue(source.contains("DownloadAttemptJournal.Phase.DOWNLOADING"))
        assertTrue(source.contains("DownloadAttemptJournal.Phase.PUBLISHING"))
    }

    @Test fun `recovery derives attempt owned temporary path instead of deleting persisted path`() {
        val reconcile = source.substringAfter("private suspend fun reconcileAbandonedAttempts()").substringBefore("private fun temporaryFile")
        assertTrue(reconcile.contains("cleanupTemporaryFile(record.attemptId)"))
        assertFalse(reconcile.contains("File(record.temporaryPath)"))
    }

    @Test fun `service destruction cannot erase durable recovery journal`() {
        val destroy = source.substringAfter("override fun onDestroy()").substringBefore("override fun onStartCommand")
        assertFalse(destroy.contains("attemptJournal"))
    }
}
