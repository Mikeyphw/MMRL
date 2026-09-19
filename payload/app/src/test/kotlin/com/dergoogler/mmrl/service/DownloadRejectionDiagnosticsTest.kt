package com.dergoogler.mmrl.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRejectionDiagnosticsTest {
    @Test fun `duplicate rejection explains reason without operation identity`() {
        val message = DownloadRejectionDiagnostics.duplicateActiveOperation().message.orEmpty()
        assertEquals("Download rejected: operation is already active", message)
        assertFalse(message.contains("operation-123"))
        assertFalse(message.contains('/'))
    }

    @Test fun `destination rejection explains reason without filesystem path`() {
        val message = DownloadRejectionDiagnostics.destinationUnavailable().message.orEmpty()
        assertEquals("Download rejected: destination is unavailable", message)
        assertFalse(message.contains("/storage"))
        assertFalse(message.contains('\\'))
    }

    @Test fun `service logs raw reservation failures but reports sanitized rejection`() {
        val source = sequenceOf(
            File("src/main/kotlin/com/dergoogler/mmrl/service/DownloadService.kt"),
            File("app/src/main/kotlin/com/dergoogler/mmrl/service/DownloadService.kt"),
        ).first(File::isFile).readText()
        assertTrue(source.contains("Timber.w(error, \"Download destination reservation rejected\")"))
        assertTrue(source.contains("Timber.w(error, \"Download destination re-reservation rejected\")"))
        assertTrue(source.contains("DownloadRejectionDiagnostics.duplicateActiveOperation()"))
        assertTrue(source.contains("DownloadRejectionDiagnostics.destinationUnavailable()"))
        assertFalse(source.contains("Download operation is already active: \$historyId"))
    }
}
