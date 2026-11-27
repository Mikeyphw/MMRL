package com.dergoogler.mmrl.database.activity

import io.github.mikeyphw.mmrl.shared.OperationEvent
import io.github.mikeyphw.mmrl.shared.OperationFailure
import io.github.mikeyphw.mmrl.shared.OperationId
import io.github.mikeyphw.mmrl.shared.OperationKind
import io.github.mikeyphw.mmrl.shared.OperationOrigin
import io.github.mikeyphw.mmrl.shared.OperationPhase
import io.github.mikeyphw.mmrl.shared.OperationRecord
import io.github.mikeyphw.mmrl.shared.OperationStatus
import org.junit.Assert.*
import org.junit.Test

class M1BActivityContractTest {
    @Test fun failedDownloadIsTerminalAndDoesNotStoreUnboundedErrors() {
        val id = OperationId.parse("activity:download-test")!!
        val queued = OperationRecord.create(id, OperationKind.DOWNLOAD, OperationOrigin.USER, 10L)!!
        val running = queued.reduce(OperationEvent.of(id, OperationStatus.QUEUED,
            OperationStatus.RUNNING, OperationPhase.PREPARING, 11L)!!)!!
        val progress = running.reduce(OperationEvent.of(id, OperationStatus.RUNNING,
            OperationStatus.RUNNING, OperationPhase.TRANSFERRING, 12L,
            progressPercent = 80)!!)!!
        assertNull(progress.reduce(OperationEvent.of(id, OperationStatus.RUNNING,
            OperationStatus.RUNNING, OperationPhase.TRANSFERRING, 13L,
            progressPercent = 10)!!))
        val failure = progress.reduce(OperationEvent.of(id, OperationStatus.RUNNING,
            OperationStatus.FAILED, OperationPhase.FINISHED, 14L,
            failure = OperationFailure.parse("DOWNLOAD_FAILED"))!!)!!
        assertTrue(failure.status.terminal)
        assertEquals("DOWNLOAD_FAILED", failure.failure?.code)
        assertNull(failure.reduce(OperationEvent.of(id, OperationStatus.FAILED,
            OperationStatus.RUNNING, OperationPhase.PREPARING, 15L)!!))
        assertNull(OperationFailure.parse("Authorization: Bearer secret"))
    }
}
