package com.dergoogler.mmrl.service

import java.io.File
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class DownloadCallbackIsolationSourceContractTest {
    private val source = sequenceOf(
        File("app/src/main/kotlin/com/dergoogler/mmrl/service/DownloadService.kt"),
        File("src/main/kotlin/com/dergoogler/mmrl/service/DownloadService.kt"),
    ).first(File::isFile).readText()

    @Test
    fun `all external callback kinds cross best effort boundary`() {
        listOf(
            "bestEffort(\"started\"",
            "bestEffort(\"progress\"",
            "bestEffort(\"file-exists\"",
            "bestEffort(\"success\"",
            "bestEffort(\"failure\"",
            "bestEffort(\"permission-denied\"",
            "bestEffort(\"service-start failure\"",
            "bestEffort(\"duplicate-operation failure\"",
            "bestEffort(\"destination-claim failure\"",
            "bestEffort(\"destination-reclaim failure\"",
        ).forEach { marker -> assertTrue("missing callback boundary: $marker", source.contains(marker)) }
    }

    @Test
    fun `failure bookkeeping and cleanup follow callback boundary`() {
        val failure = source.substringAfter("override fun onFailure(e: Throwable)").substringBefore("private fun temporaryFile")
        assertTrue(failure.indexOf("bestEffort(\"failure\"") < failure.indexOf("operationHistoryRepository.fail"))
        assertTrue(failure.contains("cleanupTask(original, tracked, historyId, lease, destinationLease)"))
        assertFalse(failure.contains("listeners[original]?.onFailure(e)"))
    }

    @Test
    fun `progress observer cannot prevent internal progress publication`() {
        val progress = source.substringAfter("override fun getProgress(value: Float)").substringBefore("override fun onSuccess")
        assertTrue(progress.indexOf("bestEffort(\"progress\"") < progress.indexOf("progressFlow.value = tracked to value"))
    }
}
