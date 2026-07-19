package com.dergoogler.mmrl.ash.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshGuidanceEngineTest {
    @Test
    fun suspectChangedModuleRanksAboveTrustedModule() {
        val snapshot = AshSnapshot(
            dashboard = Dashboard(latestRescueId = "r1", latestRescueStatus = "quarantined"),
            modules = listOf(
                module("suspect", trust = "suspect", changed = true),
                module("trusted", trust = "trusted", changed = false),
            ),
            activity = listOf(
                ActivityItem("r1", 10, "rescue", "Rescue", "boot failure", "quarantined", "suspect [suspect]"),
            ),
        )

        val plan = AshGuidanceEngine.build(snapshot, nowSeconds = 100)

        assertEquals("suspect", plan.candidates.first().folder)
        assertTrue(plan.candidates.first().score >= 70)
    }

    @Test
    fun protectedModuleIsPenalizedAndExcludedFromMutation() {
        val snapshot = AshSnapshot(
            dashboard = Dashboard(latestRescueId = "r1", latestRescueStatus = "active"),
            modules = listOf(
                module("protected", trust = "protected", changed = true, quarantined = true),
                module("normal", trust = "normal", changed = true, quarantined = true),
            ),
            quarantine = listOf(
                quarantine("protected"),
                quarantine("normal"),
            ),
        )

        val plan = AshGuidanceEngine.build(snapshot, nowSeconds = 100)
        val protected = plan.candidates.first { it.folder == "protected" }

        assertTrue(protected.protected)
        assertFalse(plan.recommendations.flatMap { it.affectedFolders }.contains("protected"))
    }

    @Test
    fun guidedBatchPreviewsSafestHalfExactly() {
        val snapshot = AshSnapshot(
            capabilities = AshCapabilities(features = setOf("guided-recovery")),
            modules = listOf(
                module("a", trust = "suspect", changed = true, quarantined = true),
                module("b", trust = "normal", changed = false, quarantined = true),
                module("c", trust = "normal", changed = true, quarantined = true),
                module("d", trust = "trusted", changed = false, quarantined = true),
            ),
            quarantine = listOf(quarantine("a"), quarantine("b"), quarantine("c"), quarantine("d")),
        )

        val recommendation = AshGuidanceEngine.build(snapshot, nowSeconds = 100)
            .recommendations.first { it.kind == AshRecoveryRecommendationKind.RestoreSelected }

        assertEquals(2, recommendation.affectedFolders.size)
        assertEquals(listOf("d", "b"), recommendation.affectedFolders)
    }

    @Test
    fun latestGuidanceFeedbackIsRetained() {
        val snapshot = AshSnapshot(
            activity = listOf(
                guidance("rec-1", "module", "failed", 10),
                guidance("rec-1", "module", "helped", 20),
            ),
        )

        val plan = AshGuidanceEngine.build(snapshot, nowSeconds = 100)

        assertEquals(AshGuidanceOutcome.Helped, plan.feedback.getValue("rec-1").outcome)
    }

    @Test
    fun alreadySuspectModuleDoesNotReceiveRedundantClassificationRecommendation() {
        val snapshot = AshSnapshot(
            dashboard = Dashboard(latestRescueId = "r1", latestRescueStatus = "active"),
            modules = listOf(module("candidate", trust = "suspect", changed = true)),
        )

        val plan = AshGuidanceEngine.build(snapshot, nowSeconds = 100)

        assertFalse(
            plan.recommendations.any { it.kind == AshRecoveryRecommendationKind.MarkSuspect },
        )
    }

    @Test
    fun allMutatingRecommendationsRequireConfirmation() {
        val snapshot = AshSnapshot(
            dashboard = Dashboard(latestRescueId = "r1", latestRescueStatus = "active"),
            modules = listOf(module("candidate", trust = "suspect", changed = true)),
        )

        val plan = AshGuidanceEngine.build(snapshot, nowSeconds = 100)

        assertTrue(
            plan.recommendations
                .filter { it.kind != AshRecoveryRecommendationKind.Observe }
                .all(AshRecoveryRecommendation::requiresConfirmation),
        )
    }

    private fun module(
        folder: String,
        trust: String,
        changed: Boolean,
        quarantined: Boolean = false,
    ) = ModuleItem(
        folder = folder,
        id = folder,
        name = folder,
        version = "1",
        versionCode = "1",
        enabled = !quarantined,
        quarantined = quarantined,
        trust = if (quarantined) "quarantined" else trust,
        baseTrust = trust,
        changedSinceStable = changed,
    )

    private fun quarantine(folder: String) = QuarantineItem(
        folder = folder,
        id = folder,
        name = folder,
        trust = "normal",
        rescueId = "r1",
        disabledAt = 90,
        exists = true,
        disablePresent = true,
        reason = "new or updated since stable boot",
    )

    private fun guidance(
        recommendation: String,
        module: String,
        outcome: String,
        timestamp: Long,
    ) = ActivityItem(
        id = "guidance-$timestamp",
        timestamp = timestamp,
        type = "guidance",
        title = "Recovery guidance outcome",
        subtitle = outcome,
        status = outcome,
        details = "recommendation=$recommendation\nmodule=$module\noutcome=$outcome",
    )
}
