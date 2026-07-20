package com.dergoogler.mmrl.ash.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshRecoveryPlanEngineTest {
    @Test
    fun conservativePlanChoosesLowestRiskEligibleModule() {
        val snapshot = snapshot(
            modules = listOf(
                module("changed", trust = "suspect", changed = true),
                module("safe", trust = "normal", changed = false),
                module("protected", trust = "protected", changed = false),
            ),
            quarantine = listOf(quarantine("changed"), quarantine("safe"), quarantine("protected", trust = "protected")),
        )

        val plan = AshRecoveryPlanEngine.presets(snapshot, nowSeconds = 1_050)
            .first { it.preset == AshRecoveryPlanPreset.Conservative }

        assertEquals(listOf("safe"), plan.affectedFolders)
        assertTrue(plan.canExecute)
        assertEquals(AshRecoveryPlanRisk.Low, plan.risk)
    }

    @Test
    fun activeTrialBlocksEveryNewPlan() {
        val snapshot = snapshot(
            dashboard = Dashboard(restoreState = "testing"),
            modules = listOf(module("one")),
            quarantine = listOf(quarantine("one")),
        )

        val plan = AshRecoveryPlanEngine.custom(snapshot, listOf("one"), nowSeconds = 1_050)

        assertFalse(plan.canExecute)
        assertTrue(plan.guards.any { it.code == "trial-active" && it.severity == AshRecoveryGuardSeverity.Blocker })
    }

    @Test
    fun staleSnapshotIsBlocked() {
        val snapshot = snapshot(
            generatedAt = 100,
            modules = listOf(module("one")),
            quarantine = listOf(quarantine("one")),
        )

        val plan = AshRecoveryPlanEngine.custom(snapshot, listOf("one"), nowSeconds = 1_000)

        assertFalse(plan.canExecute)
        assertTrue(plan.guards.any { it.code == "snapshot-stale" })
    }

    @Test
    fun oversizedPlanIsBlockedAndNeverSilentlyTruncated() {
        val folders = (1..9).map { "m$it" }
        val snapshot = snapshot(
            modules = folders.map(::module),
            quarantine = folders.map(::quarantine),
        )

        val plan = AshRecoveryPlanEngine.custom(snapshot, folders, nowSeconds = 1_050)

        assertEquals(folders, plan.affectedFolders)
        assertFalse(plan.canExecute)
        assertTrue(plan.guards.any { it.code == "batch-limit" })
    }

    @Test
    fun changedRecoveryRevisionBlocksExecution() {
        val original = snapshot(
            recoveryRevision = "100-20",
            modules = listOf(module("one")),
            quarantine = listOf(quarantine("one")),
        )
        val plan = AshRecoveryPlanEngine.custom(original, listOf("one"), nowSeconds = 1_050)
        val refreshed = original.copy(recoveryRevision = "101-20", generatedAt = 1_040)

        val guards = AshRecoveryPlanEngine.executionGuards(plan, refreshed, nowSeconds = 1_050)

        assertTrue(guards.any { it.code == "revision-changed" && it.severity == AshRecoveryGuardSeverity.Blocker })
    }

    @Test
    fun protectedAndStaleQuarantineEntriesAreBlocked() {
        val snapshot = snapshot(
            modules = listOf(module("protected", trust = "protected"), module("stale")),
            quarantine = listOf(
                quarantine("protected", trust = "protected"),
                quarantine("stale", exists = false, disablePresent = false),
            ),
        )

        val plan = AshRecoveryPlanEngine.custom(snapshot, listOf("protected", "stale"), nowSeconds = 1_050)

        assertFalse(plan.canExecute)
        assertTrue(plan.guards.any { it.code == "protected:protected" })
        assertTrue(plan.guards.any { it.code == "stale:stale" })
    }

    @Test
    fun highRiskPlanRequiresExactTypedPhrase() {
        val folders = (1..5).map { "m$it" }
        val snapshot = snapshot(
            modules = folders.map(::module),
            quarantine = folders.map(::quarantine),
        )

        val plan = AshRecoveryPlanEngine.custom(snapshot, folders, nowSeconds = 1_050)

        assertEquals(AshRecoveryPlanRisk.High, plan.risk)
        assertEquals("RESTORE 5 MODULES", plan.confirmationPhrase)
        assertTrue(plan.canExecute)
    }

    private fun snapshot(
        generatedAt: Long = 1_000,
        recoveryRevision: String = "100-20",
        dashboard: Dashboard = Dashboard(latestRescueId = "r1", latestRescueStatus = "quarantined"),
        modules: List<ModuleItem>,
        quarantine: List<QuarantineItem>,
    ) = AshSnapshot(
        generatedAt = generatedAt,
        recoveryRevision = recoveryRevision,
        capabilities = AshCapabilities(features = setOf("snapshot", "capabilities", "guided-recovery", "recovery-plans")),
        dashboard = dashboard,
        modules = modules,
        quarantine = quarantine,
    )

    private fun module(
        folder: String,
        trust: String = "normal",
        changed: Boolean = false,
    ) = ModuleItem(
        folder = folder,
        id = folder,
        name = folder,
        version = "1",
        versionCode = "1",
        enabled = false,
        quarantined = true,
        trust = "quarantined",
        baseTrust = trust,
        changedSinceStable = changed,
    )

    private fun quarantine(
        folder: String,
        trust: String = "normal",
        exists: Boolean = true,
        disablePresent: Boolean = true,
    ) = QuarantineItem(
        folder = folder,
        id = folder,
        name = folder,
        trust = trust,
        rescueId = "r1",
        disabledAt = 900,
        exists = exists,
        disablePresent = disablePresent,
        reason = "recovery",
    )
}
