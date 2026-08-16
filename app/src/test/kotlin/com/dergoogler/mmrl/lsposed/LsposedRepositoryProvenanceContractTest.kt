package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedRepositoryProvenanceContractTest {
    @Test
    fun `cache state marks stale fallback and partial provenance`() {
        val state = LsposedRepositoryCacheState(
            modules = emptyList(),
            fetchedAt = 123L,
            sourceUrl = "cache",
            freshness = LsposedCacheFreshness.STALE,
            errorMessage = "network failed",
        )

        assertTrue(state.stale)
        assertTrue(state.partial)
        assertEquals(123L, state.fetchedAt)
    }

    @Test
    fun `cache policy marks old metadata as stale`() {
        assertEquals(LsposedCacheFreshness.FRESH, LsposedRepositoryCachePolicy.freshnessFor(1_000L, 1_000L + 60_000L))
        assertEquals(
            LsposedCacheFreshness.STALE,
            LsposedRepositoryCachePolicy.freshnessFor(1_000L, 1_000L + LsposedRepositoryCachePolicy.CACHE_TTL_MS + 1L),
        )
        assertEquals(LsposedCacheFreshness.EMPTY, LsposedRepositoryCachePolicy.freshnessFor(0L, 1_000L))
    }

    @Test
    fun `apk identity policy rejects package substitution`() {
        val error = runCatching {
            LsposedApkIdentityPolicy.requireMatches(
                expectedPackageName = "com.example.module",
                actualPackageName = "com.evil.module",
                expectedVersionCode = null,
                actualVersionCode = 1L,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `apk identity policy accepts matching package and version`() {
        LsposedApkIdentityPolicy.requireMatches(
            expectedPackageName = "Com.Example.Module",
            actualPackageName = "com.example.module",
            expectedVersionCode = 42L,
            actualVersionCode = 42L,
        )
    }

    @Test
    fun `scope transaction policy requires integrity ok`() {
        assertTrue(LsposedScopeTransactionPolicy.integrityOk("ok"))
        assertFalse(LsposedScopeTransactionPolicy.integrityOk("malformed"))
        assertEquals(".mmrl.lock", LsposedScopeTransactionPolicy.ROOT_LOCK_SUFFIX)
    }
}
