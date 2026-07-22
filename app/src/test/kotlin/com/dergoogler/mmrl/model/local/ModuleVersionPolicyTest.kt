package com.dergoogler.mmrl.model.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleVersionPolicyTest {
    @Test
    fun pinCurrentBlocksNewerVersionsOnly() {
        val policy = ModuleVersionPolicy.pinCurrent(
            moduleId = "PlayIntegrityFix",
            version = "v18.7",
            versionCode = 18700,
        )

        assertFalse(policy.blocks(18700))
        assertTrue(policy.blocks(18701))
    }

    @Test
    fun ignoreUpdatesBlocksEveryCandidate() {
        val policy = ModuleVersionPolicy.ignore("zygisk_next")

        assertTrue(policy.blocks(1))
        assertTrue(policy.blocks(999999))
    }

    @Test
    fun snapshotPlannerBuildsReviewFirstDiffs() {
        val snapshot = ModuleSnapshot(
            id = "known-good",
            label = "Known good",
            createdAt = 1L,
            manager = "RKSU",
            androidSdk = 36,
            modules = listOf(
                ModuleSnapshotItem(
                    id = "stable",
                    name = "Stable Module",
                    version = "1.0",
                    versionCode = 10,
                    author = "A",
                    description = "safe",
                    enabled = true,
                    state = "ENABLE",
                    size = 1L,
                    lastUpdated = 1L,
                ),
                ModuleSnapshotItem(
                    id = "missing",
                    name = "Missing Module",
                    version = "1.0",
                    versionCode = 10,
                    author = "A",
                    description = "gone",
                    enabled = true,
                    state = "ENABLE",
                    size = 1L,
                    lastUpdated = 1L,
                ),
            ),
        )
        val plan = ModuleSnapshotPlanner.compare(
            snapshot = snapshot,
            current = listOf(
                ModuleSnapshotItem(
                    id = "stable",
                    name = "Stable Module",
                    version = "1.1",
                    versionCode = 11,
                    author = "A",
                    description = "safe",
                    enabled = true,
                    state = "ENABLE",
                    size = 1L,
                    lastUpdated = 2L,
                ),
                ModuleSnapshotItem(
                    id = "extra",
                    name = "Extra Module",
                    version = "1.0",
                    versionCode = 10,
                    author = "B",
                    description = "new",
                    enabled = true,
                    state = "ENABLE",
                    size = 1L,
                    lastUpdated = 2L,
                ),
            ),
        )

        assertEquals(ModuleSnapshotPlanStatus.VERSION_CHANGED, plan.first { it.moduleId == "stable" }.status)
        assertEquals(ModuleSnapshotPlanStatus.MISSING, plan.first { it.moduleId == "missing" }.status)
        assertEquals(ModuleSnapshotPlanStatus.EXTRA, plan.first { it.moduleId == "extra" }.status)
    }
}
