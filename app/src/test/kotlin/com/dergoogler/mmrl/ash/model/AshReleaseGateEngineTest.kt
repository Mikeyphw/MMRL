package com.dergoogler.mmrl.ash.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AshReleaseGateEngineTest {
    @Test
    fun healthyCurrentLiveStateIsReady() {
        val report = assess()
        assertEquals(AshReleaseGateStatus.Ready, report.status)
        assertEquals(0, report.blockerCount)
        assertEquals(0, report.warningCount)
    }

    @Test
    fun cachedSnapshotBlocksRelease() {
        val report = assess(source = AshSnapshotSource.Cache)
        assertEquals(AshReleaseGateStatus.Blocked, report.status)
        assertTrue(report.checks.any { it.id == "live-snapshot" && it.state == AshReleaseCheckState.Blocker })
    }

    @Test
    fun missingCapabilityBlocksRelease() {
        val snapshot = snapshot().copy(
            capabilities = snapshot().capabilities.copy(
                features = AshReleaseGateEngine.REQUIRED_FEATURES - "one-shot-tokens",
            ),
        )
        val report = assess(snapshot = snapshot)
        assertTrue(report.checks.any { it.id == "feature-parity" && it.state == AshReleaseCheckState.Blocker })
    }

    @Test
    fun activeRestorationTrialBlocksRelease() {
        val report = assess(snapshot = snapshot().copy(dashboard = Dashboard(restoreState = "testing", restoreCount = 1)))
        assertTrue(report.checks.any { it.id == "restoration-idle" && it.state == AshReleaseCheckState.Blocker })
    }

    @Test
    fun staleQuarantineBlocksRelease() {
        val report = assess(
            snapshot = snapshot().copy(
                quarantine = listOf(
                    QuarantineItem("gone", "gone", "Gone", "normal", "r1", 1_000, false, false),
                ),
            ),
        )
        assertTrue(report.checks.any { it.id == "quarantine-integrity" && it.state == AshReleaseCheckState.Blocker })
    }

    @Test
    fun queuedSettingsProduceWarningOnly() {
        val report = assess(
            snapshot = snapshot().copy(
                pendingSettings = listOf(PendingSetting("threshold", "4", "3")),
            ),
        )
        assertEquals(AshReleaseGateStatus.ReadyWithWarnings, report.status)
        assertEquals(0, report.blockerCount)
    }

    @Test
    fun largeSnapshotProducesPerformanceWarning() {
        val modules = (1..513).map { index -> module("m$index") }
        val report = assess(snapshot = snapshot().copy(modules = modules))
        assertTrue(report.checks.any { it.id == "performance-budget" && it.state == AshReleaseCheckState.Warning })
    }

    @Test
    fun repairRequiredHealthBlocksRelease() {
        val report = assess(
            health = AshStateHealth(
                level = AshStateHealthLevel.RepairRequired,
                issues = listOf(AshStateIssue("broken", "Broken", "Repair required", true)),
            ),
        )
        assertTrue(report.checks.any { it.id == "state-health" && it.state == AshReleaseCheckState.Blocker })
    }

    @Test
    fun missingModuleSelfTestBlocksRelease() {
        val report = assess(moduleGate = null)
        assertTrue(report.checks.any { it.id == "module-self-test" && it.state == AshReleaseCheckState.Blocker })
    }

    @Test
    fun moduleWarningPropagatesWithoutBlocking() {
        val report = assess(
            moduleGate = moduleGate().copy(status = AshReleaseGateStatus.ReadyWithWarnings),
        )
        assertEquals(AshReleaseGateStatus.ReadyWithWarnings, report.status)
    }

    private fun assess(
        snapshot: AshSnapshot? = snapshot(),
        source: AshSnapshotSource = AshSnapshotSource.Live,
        health: AshStateHealth = AshStateHealth(level = AshStateHealthLevel.Healthy),
        moduleGate: AshModuleReleaseGate? = moduleGate(),
    ) = AshReleaseGateEngine.assess(
        rootAvailable = true,
        lifecycle = lifecycle(),
        snapshot = snapshot,
        source = source,
        health = health,
        moduleGate = moduleGate,
        nowSeconds = 1_100,
    )

    private fun lifecycle() = AshModuleLifecycle(
        state = AshModuleLifecycleState.Current,
        installation = AshModuleInstallation(
            installed = true,
            active = true,
            version = "11.6.0",
            versionCode = 260,
            controlAvailable = true,
        ),
        bundled = AshBundledModuleMetadata(version = "11.6.0", versionCode = 260),
        compatible = true,
        compatibilityMessage = "Compatible API 2",
    )

    private fun snapshot() = AshSnapshot(
        schemaVersion = 2,
        generatedAt = 1_000,
        recoveryRevision = "rev-1",
        capabilities = AshCapabilities(
            apiVersion = 2,
            minimumClientApi = 2,
            moduleVersion = "11.6.0",
            moduleVersionCode = 260,
            features = AshReleaseGateEngine.REQUIRED_FEATURES,
        ),
        dashboard = Dashboard(restoreState = "idle"),
        modules = listOf(module("one")),
        health = AshModuleHealth(schemaVersion = 2, status = "healthy"),
    )

    private fun module(folder: String) = ModuleItem(
        folder = folder,
        id = folder,
        name = folder,
        version = "1",
        versionCode = "1",
        enabled = true,
        quarantined = false,
        trust = "normal",
    )

    private fun moduleGate() = AshModuleReleaseGate(
        protocolVersion = AshReleaseGateEngine.MODULE_PROTOCOL_VERSION,
        generatedAt = 1_000,
        moduleVersion = "11.6.0",
        moduleVersionCode = 260,
        status = AshReleaseGateStatus.Ready,
        checks = listOf(AshReleaseCheck("runtime", "Runtime", AshReleaseCheckState.Pass, "OK")),
    )
}
