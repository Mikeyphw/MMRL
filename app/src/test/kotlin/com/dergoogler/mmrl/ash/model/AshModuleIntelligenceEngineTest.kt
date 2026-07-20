package com.dergoogler.mmrl.ash.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshModuleIntelligenceEngineTest {
    @Test
    fun quarantinedChangedSuspectModuleBecomesCritical() {
        val snapshot = AshSnapshot(
            dashboard = Dashboard(latestRescueId = "r1", latestRescueStatus = "active"),
            modules = listOf(module("danger", trust = "suspect", changed = true, quarantined = true)),
            quarantine = listOf(quarantine("danger")),
            activity = listOf(
                ActivityItem("r1", 90, "rescue", "Rescue", "danger", "quarantined", "danger"),
            ),
        )

        val intelligence = AshModuleIntelligenceEngine.build(snapshot, nowSeconds = 100).single()

        assertEquals(AshModuleRiskBand.Critical, intelligence.riskBand)
        assertTrue(intelligence.riskScore >= 75)
        assertTrue(intelligence.needsReview)
        assertTrue(intelligence.recommendedAction.contains("guarded recovery plan"))
    }

    @Test
    fun changedModuleIsVisibleEvenWhenOutsideGuidanceTopTen() {
        val modules = (0 until 12).map { index ->
            module(
                folder = "module-$index",
                trust = if (index < 11) "suspect" else "normal",
                changed = true,
            )
        }
        val intelligence = AshModuleIntelligenceEngine.build(AshSnapshot(modules = modules), nowSeconds = 100)
        val last = intelligence.first { it.folder == "module-11" }

        assertTrue(last.changedSinceStable)
        assertTrue(last.riskBand >= AshModuleRiskBand.Elevated)
        assertTrue(last.factors.any { it.code == "changed" })
    }

    @Test
    fun protectedModuleKeepsPolicyContextWithoutDestructiveRecommendation() {
        val intelligence = AshModuleIntelligenceEngine.build(
            AshSnapshot(modules = listOf(module("core", trust = "protected", changed = true))),
            nowSeconds = 100,
        ).single()

        assertEquals("protected", intelligence.trust)
        assertTrue(intelligence.summary.contains("Protected by policy"))
        assertTrue(intelligence.recommendedAction.contains("inspect evidence manually"))
    }

    @Test
    fun indexMatchesBothDeclaredIdAndFolder() {
        val snapshot = AshSnapshot(
            modules = listOf(
                ModuleItem(
                    folder = "renamed-folder",
                    id = "com.example.module",
                    name = "Example",
                    version = "1",
                    versionCode = "1",
                    enabled = true,
                    quarantined = false,
                    trust = "normal",
                    changedSinceStable = true,
                ),
            ),
        )

        val index = AshModuleIntelligenceEngine.index(snapshot, nowSeconds = 100)

        assertEquals("renamed-folder", index.getValue("com.example.module").folder)
        assertEquals("com.example.module", index.getValue("renamed-folder").moduleId)
    }

    @Test
    fun cachedManagerStateProducesReadOnlyIntelligence() {
        val state = AshManagerState(
            snapshot = AshSnapshot(modules = listOf(module("cached", trust = "normal", changed = false))),
            source = AshSnapshotSource.Cache,
            readOnly = true,
        )

        val intelligence = state.moduleIntelligence(nowSeconds = 100).getValue("cached")

        assertEquals(AshSnapshotSource.Cache, intelligence.source)
        assertTrue(intelligence.readOnly)
    }

    @Test
    fun quarantinedUpdateRequiresRecoveryReview() {
        val intelligence = AshModuleIntelligenceEngine.build(
            AshSnapshot(
                modules = listOf(module("isolated", trust = "suspect", changed = true, quarantined = true)),
                quarantine = listOf(quarantine("isolated")),
            ),
            nowSeconds = 100,
        ).single()

        val safety = AshModuleIntelligenceEngine.assessUpdate(
            AshUpdateSafetyInput(
                installed = true,
                updateAvailable = true,
                hasBootScripts = true,
                intelligence = intelligence,
            ),
        )

        assertEquals("Resolve quarantine before updating", safety.title)
        assertEquals(AshModuleRiskBand.Critical, safety.riskBand)
        assertTrue(safety.shouldReviewBeforeInstall)
    }

    @Test
    fun sensitiveUpdateEscalatesOtherwiseNormalModule() {
        val intelligence = AshModuleIntelligenceEngine.build(
            AshSnapshot(modules = listOf(module("normal", trust = "normal", changed = false))),
            nowSeconds = 100,
        ).single()

        val safety = AshModuleIntelligenceEngine.assessUpdate(
            AshUpdateSafetyInput(
                installed = true,
                updateAvailable = true,
                hasBootScripts = true,
                hasNativeCode = true,
                changesSePolicy = true,
                changesSystemProperties = true,
                intelligence = intelligence,
            ),
        )

        assertTrue(safety.riskBand >= AshModuleRiskBand.Elevated)
        assertTrue(safety.shouldReviewBeforeInstall)
        assertEquals(4, safety.reasons.size)
    }


    @Test
    fun changedProtectionMatchesIntelligenceFilters() {
        val state = AshManagerState(
            snapshot = AshSnapshot(modules = listOf(module("changed", trust = "normal", changed = true))),
            source = AshSnapshotSource.Live,
        )

        val protection = state.moduleProtections().getValue("changed")

        assertTrue(protection.matches(AshModuleFilter.Changed))
        assertTrue(protection.matches(AshModuleFilter.NeedsReview))
        assertFalse(protection.matches(AshModuleFilter.Normal))
    }

    @Test
    fun quietProtectionRemainsNormal() {
        val state = AshManagerState(
            snapshot = AshSnapshot(modules = listOf(module("quiet", trust = "normal", changed = false))),
            source = AshSnapshotSource.Live,
        )

        val protection = state.moduleProtections().getValue("quiet")

        assertTrue(protection.matches(AshModuleFilter.Normal))
        assertFalse(protection.matches(AshModuleFilter.NeedsReview))
    }

    @Test
    fun ordinaryUpdateRemainsLowRisk() {
        val intelligence = AshModuleIntelligenceEngine.build(
            AshSnapshot(modules = listOf(module("quiet", trust = "normal", changed = false))),
            nowSeconds = 100,
        ).single()

        val safety = AshModuleIntelligenceEngine.assessUpdate(
            AshUpdateSafetyInput(
                installed = true,
                updateAvailable = true,
                intelligence = intelligence,
            ),
        )

        assertEquals(AshModuleRiskBand.Low, safety.riskBand)
        assertFalse(safety.shouldReviewBeforeInstall)
        assertTrue(safety.reasons.isEmpty())
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
        trust = "suspect",
        rescueId = "r1",
        disabledAt = 90,
        exists = true,
        disablePresent = true,
        reason = "changed since stable boot",
    )
}
