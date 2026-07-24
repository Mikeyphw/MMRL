package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedGovernancePolicyTest {
    @Test
    fun pinnedApkPolicyBlocksNewerVersions() {
        val installed = installedModule(versionCode = 10L, versionName = "1.0.0")
        val policy = LsposedVersionPolicy.pinCurrent(installed)

        assertTrue(policy.blocks(11L))
        assertFalse(policy.blocks(10L))
        assertEquals("io.github.example", policy.normalizedPackageName)
    }

    @Test
    fun maxCurrentPolicyAllowsCurrentAndBlocksAboveMax() {
        val installed = installedModule(versionCode = 12L, versionName = "1.2.0")
        val policy = LsposedVersionPolicy.maxCurrent(installed)

        assertFalse(policy.blocks(12L))
        assertTrue(policy.blocks(13L))
    }

    @Test
    fun snapshotPlannerFindsVersionChangesAndExtras() {
        val saved = installedModule(versionCode = 10L, versionName = "1.0.0").toSnapshotItem()
        val snapshot = LsposedSnapshot(
            id = "snapshot-1",
            label = "Known good",
            createdAt = 1L,
            modules = listOf(saved),
        )
        val current = listOf(
            installedModule(versionCode = 11L, versionName = "1.1.0").toSnapshotItem(),
            installedModule(packageName = "io.github.extra", label = "Extra", versionCode = 1L).toSnapshotItem(),
        )

        val plan = LsposedSnapshotPlanner.compare(snapshot, current)

        assertEquals(LsposedSnapshotPlanStatus.EXTRA, plan[0].status)
        assertEquals(LsposedSnapshotPlanStatus.VERSION_CHANGED, plan[1].status)
    }

    @Test
    fun snapshotPlannerFindsMissingPackages() {
        val snapshot = LsposedSnapshot(
            id = "snapshot-1",
            label = "Known good",
            createdAt = 1L,
            modules = listOf(installedModule().toSnapshotItem()),
        )

        val plan = LsposedSnapshotPlanner.compare(snapshot, current = emptyList())

        assertEquals(LsposedSnapshotPlanStatus.MISSING, plan.single().status)
    }

    private fun installedModule(
        packageName: String = "io.github.example",
        label: String = "Example",
        versionCode: Long = 10L,
        versionName: String? = "1.0.0",
    ) = LsposedInstalledModule(
        packageName = packageName,
        label = label,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        repoModule = LsposedRepoModule(
            name = packageName,
            summary = label,
            latestRelease = "${versionCode + 1}-${versionName ?: "next"}",
        ),
        launchable = true,
        detectedByXposedMetadata = true,
    )
}
