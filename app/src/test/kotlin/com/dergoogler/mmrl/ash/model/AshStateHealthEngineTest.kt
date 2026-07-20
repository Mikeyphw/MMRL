package com.dergoogler.mmrl.ash.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshStateHealthEngineTest {
    @Test
    fun snapshotChecksumIsDeterministicAndContentBound() {
        val original = AshSnapshotIntegrity.checksum(10, "{\"a\":1}", "{\"b\":2}")
        assertEquals(original, AshSnapshotIntegrity.checksum(10, "{\"a\":1}", "{\"b\":2}"))
        assertTrue(original.matches(Regex("^[0-9a-f]{64}$")))
        assertFalse(original == AshSnapshotIntegrity.checksum(10, "{\"a\":2}", "{\"b\":2}"))
        assertFalse(original == AshSnapshotIntegrity.checksum(10, "{\"a\":1}", "{\"b\":3}"))
        assertFalse(original == AshSnapshotIntegrity.checksum(11, "{\"a\":1}", "{\"b\":2}"))
    }

    @Test
    fun healthySnapshotHasNoIssues() {
        val health = AshStateHealthEngine.assess(snapshot(), AshSnapshotSource.Live, nowSeconds = 1_100)
        assertEquals(AshStateHealthLevel.Healthy, health.level)
        assertTrue(health.issues.isEmpty())
    }

    @Test
    fun missingRevisionRequiresRepair() {
        val health = AshStateHealthEngine.assess(snapshot().copy(recoveryRevision = ""), AshSnapshotSource.Live, nowSeconds = 1_100)
        assertEquals(AshStateHealthLevel.RepairRequired, health.level)
        assertTrue(health.issues.any { it.code == "revision-missing" })
    }

    @Test
    fun staleCacheIsDegradedButNotRepairable() {
        val health = AshStateHealthEngine.assess(
            snapshot().copy(generatedAt = 1),
            AshSnapshotSource.Cache,
            nowSeconds = 100_000,
        )
        assertEquals(AshStateHealthLevel.Degraded, health.level)
        assertFalse(health.repairRecommended)
    }

    @Test
    fun duplicateFoldersRequireRepair() {
        val duplicate = module("one")
        val health = AshStateHealthEngine.assess(
            snapshot(modules = listOf(duplicate, duplicate.copy(id = "two"))),
            AshSnapshotSource.Live,
            nowSeconds = 1_100,
        )
        assertTrue(health.issues.any { it.code == "duplicate-folder:one" })
        assertEquals(AshStateHealthLevel.RepairRequired, health.level)
    }

    @Test
    fun orphanQuarantineRequiresRepair() {
        val health = AshStateHealthEngine.assess(
            snapshot(quarantine = listOf(quarantine("missing", exists = false))),
            AshSnapshotSource.Live,
            nowSeconds = 1_100,
        )
        assertTrue(health.issues.any { it.code == "orphan-quarantine:missing" })
    }

    @Test
    fun enabledQuarantinedModuleRequiresRepair() {
        val health = AshStateHealthEngine.assess(
            snapshot(
                modules = listOf(module("one", enabled = true)),
                quarantine = listOf(quarantine("one", disablePresent = false)),
            ),
            AshSnapshotSource.Live,
            nowSeconds = 1_100,
        )
        assertTrue(health.issues.any { it.code == "quarantine-enabled:one" })
    }

    @Test
    fun emptyActiveTrialRequiresRepair() {
        val health = AshStateHealthEngine.assess(
            snapshot(dashboard = Dashboard(restoreState = "testing", restoreCount = 0)),
            AshSnapshotSource.Live,
            nowSeconds = 1_100,
        )
        assertTrue(health.issues.any { it.code == "trial-empty" })
    }

    @Test
    fun recoveredCacheIsVisibleWithoutForcingModuleRepair() {
        val health = AshStateHealthEngine.assess(
            snapshot(),
            AshSnapshotSource.Cache,
            cacheEvents = listOf("cache-recovered"),
            nowSeconds = 1_100,
        )
        assertEquals(AshStateHealthLevel.Degraded, health.level)
        assertFalse(health.repairRecommended)
        assertTrue(health.issues.any { it.code == "cache-recovered" })
    }

    @Test
    fun moduleReportedIssueRequiresRepair() {
        val health = AshStateHealthEngine.assess(
            snapshot().copy(health = AshModuleHealth(status = "repair-required", issueCount = 2)),
            AshSnapshotSource.Live,
            nowSeconds = 1_100,
        )
        assertTrue(health.issues.any { it.code == "module-health" })
    }

    private fun snapshot(
        modules: List<ModuleItem> = listOf(module("one")),
        quarantine: List<QuarantineItem> = emptyList(),
        dashboard: Dashboard = Dashboard(),
    ) = AshSnapshot(
        schemaVersion = 2,
        generatedAt = 1_000,
        recoveryRevision = "100-20",
        dashboard = dashboard,
        modules = modules,
        quarantine = quarantine,
        health = AshModuleHealth(schemaVersion = 2, status = "healthy"),
    )

    private fun module(folder: String, enabled: Boolean = false) = ModuleItem(
        folder = folder,
        id = folder,
        name = folder,
        version = "1",
        versionCode = "1",
        enabled = enabled,
        quarantined = false,
        trust = "normal",
    )

    private fun quarantine(
        folder: String,
        exists: Boolean = true,
        disablePresent: Boolean = true,
    ) = QuarantineItem(
        folder = folder,
        id = folder,
        name = folder,
        trust = "normal",
        rescueId = "r1",
        disabledAt = 900,
        exists = exists,
        disablePresent = disablePresent,
    )
}
