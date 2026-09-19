package com.dergoogler.mmrl.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadConcurrencySourceContractTest {
    @Test
    fun `verified existing destination is rechecked after reservation before reuse`() {
        val source = File("src/main/kotlin/com/dergoogler/mmrl/service/DownloadService.kt").readText()
        val firstCheck = source.indexOf("val reusableReceipt = downloadReceiptStore.verifyDestination")
        val reservation = source.indexOf("destinationOwnership.claim(", firstCheck)
        val secondCheck = source.indexOf("effectiveReusableReceipt = downloadReceiptStore.verifyDestination", reservation)

        assertTrue("receipt must be checked before reservation", firstCheck >= 0 && reservation > firstCheck)
        assertTrue("existing destination must be re-verified after reservation", secondCheck > reservation)
        assertTrue(
            "failed re-verification must fall back to a non-existing destination",
            source.indexOf("allowExisting = false", secondCheck) > secondCheck,
        )
    }

    @Test
    fun `attempt identity scopes temporary and github materialized artifacts`() {
        val source = File("src/main/kotlin/com/dergoogler/mmrl/service/DownloadService.kt").readText()
        assertTrue(source.contains("cacheDir.resolve(\"downloads/\$attemptId.part\")"))
        assertTrue(source.contains("outputNamePrefix = attemptId"))
    }
}
