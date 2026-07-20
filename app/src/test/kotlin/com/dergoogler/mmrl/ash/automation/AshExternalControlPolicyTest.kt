package com.dergoogler.mmrl.ash.automation

import com.dergoogler.mmrl.ash.model.AshCapabilities
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanRisk
import com.dergoogler.mmrl.ash.model.AshSnapshot
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.Dashboard
import com.dergoogler.mmrl.ash.model.ModuleItem
import com.dergoogler.mmrl.ash.model.QuarantineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AshExternalControlPolicyTest {
    private fun snapshot(
        generatedAt: Long = 1_000L,
        revision: String = "rev-1",
        restoreState: String = "idle",
    ) = AshSnapshot(
        generatedAt = generatedAt,
        recoveryRevision = revision,
        capabilities = AshCapabilities(features = setOf("recovery-plans")),
        dashboard = Dashboard(restoreState = restoreState),
        modules = listOf(
            ModuleItem("alpha", "alpha", "Alpha", "1", "1", false, true, "normal"),
            ModuleItem("beta", "beta", "Beta", "1", "1", false, true, "suspect", changedSinceStable = true),
            ModuleItem("protected", "protected", "Protected", "1", "1", false, true, "protected"),
        ),
        quarantine = listOf(
            QuarantineItem("alpha", "alpha", "Alpha", "normal", "r1", 900, true, true),
            QuarantineItem("beta", "beta", "Beta", "suspect", "r1", 900, true, true),
            QuarantineItem("protected", "protected", "Protected", "protected", "r1", 900, true, true),
        ),
    )

    @Test
    fun `idempotency key is strict and stable`() {
        assertEquals("task:recovery-001", AshExternalControlPolicy.requireIdempotencyKey(" task:recovery-001 "))
        assertThrows(IllegalArgumentException::class.java) {
            AshExternalControlPolicy.requireIdempotencyKey("short")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AshExternalControlPolicy.requireIdempotencyKey("recovery;reboot")
        }
    }

    @Test
    fun `custom folders are normalized and deduplicated`() {
        assertEquals(
            listOf("alpha", "beta"),
            AshExternalControlPolicy.parseFolders(" alpha, beta\nalpha "),
        )
    }

    @Test
    fun `custom plan keeps exact selected folders`() {
        val prepared = AshExternalControlPolicy.preparePlan(
            snapshot = snapshot(),
            presetValue = "custom",
            foldersValue = "beta,alpha",
            idempotencyKey = "task:custom-001",
            dryRun = false,
            nowMillis = 1_000_000L,
        )
        assertEquals(listOf("beta", "alpha"), prepared.plan.affectedFolders)
        assertTrue(prepared.plan.canExecute)
    }

    @Test
    fun `rapid preset excludes protected modules`() {
        val prepared = AshExternalControlPolicy.preparePlan(
            snapshot = snapshot(),
            presetValue = "rapid",
            foldersValue = null,
            idempotencyKey = "task:rapid-001",
            dryRun = true,
            nowMillis = 1_000_000L,
        )
        assertEquals(listOf("alpha", "beta"), prepared.plan.affectedFolders.toSet().sorted())
        assertEquals(AshRecoveryPlanRisk.Moderate, prepared.plan.risk)
    }

    @Test
    fun `binding changes with idempotency key`() {
        val first = AshExternalControlPolicy.preparePlan(snapshot(), "conservative", null, "task:first-001", false, 1_000_000L)
        val second = AshExternalControlPolicy.preparePlan(snapshot(), "conservative", null, "task:second-001", false, 1_000_000L)
        assertNotEquals(first.binding, second.binding)
    }

    @Test
    fun `dry run is carried without weakening plan guards`() {
        val prepared = AshExternalControlPolicy.preparePlan(snapshot(restoreState = "testing"), "conservative", null, "task:dryrun-001", true, 1_000_000L)
        assertTrue(prepared.dryRun)
        assertFalse(prepared.plan.canExecute)
    }

    @Test
    fun `evidence filters isolate changed modules`() {
        val items = AshExternalControlPolicy.filterEvidence(
            snapshot = snapshot(),
            filter = AshExternalEvidenceFilter.Changed,
            source = AshSnapshotSource.Live,
            readOnly = false,
            nowSeconds = 1_000L,
        )
        assertEquals(listOf("beta"), items.map { it.folder })
    }

    @Test
    fun `rate limit allows requests below threshold`() {
        assertTrue(
            AshExternalControlPolicy.rateLimit(
                timestamps = listOf(100L, 200L),
                nowMillis = 500L,
                limit = 3,
                windowMillis = 1_000L,
            ).allowed,
        )
    }

    @Test
    fun `rate limit reports retry delay at threshold`() {
        val result = AshExternalControlPolicy.rateLimit(
            timestamps = listOf(100L, 200L, 300L),
            nowMillis = 500L,
            limit = 3,
            windowMillis = 1_000L,
        )
        assertFalse(result.allowed)
        assertEquals(600L, result.retryAfterMillis)
    }

    @Test
    fun `outcome parser rejects unknown values`() {
        assertEquals("helped", AshExternalControlPolicy.parseOutcome("HELPED").wireValue)
        assertThrows(IllegalArgumentException::class.java) {
            AshExternalControlPolicy.parseOutcome("maybe")
        }
    }
}
