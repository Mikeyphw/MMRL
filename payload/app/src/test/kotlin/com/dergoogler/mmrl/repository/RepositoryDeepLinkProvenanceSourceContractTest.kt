package com.dergoogler.mmrl.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepositoryDeepLinkProvenanceSourceContractTest {
    private val source = File("src/main/kotlin/com/dergoogler/mmrl/repository/RepositoryDeepLinkProvenanceStore.kt").readText()
    @Test fun approvalProvenanceIsDurableAndExplicit() {
        assertTrue(source.contains("repository_deeplink_provenance_v1"))
        assertTrue(source.contains("userApproved"))
        assertTrue(source.contains("source: String"))
        assertTrue(source.contains("approvedAtEpochMs"))
        assertTrue(source.contains("SHA-256"))
        assertTrue(source.contains(".commit()"))
        assertTrue(source.contains("check(committed)"))
    }
}
