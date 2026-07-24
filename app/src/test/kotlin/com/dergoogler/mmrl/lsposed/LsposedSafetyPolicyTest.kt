package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedSafetyPolicyTest {
    @Test
    fun repositoryNoticesFlagMissingSourceAndRequireScopeReview() {
        val module = LsposedRepoModule(
            name = "com.example.xposed",
            description = "Example module",
            latestRelease = "10-1.0",
        )

        val notices = LsposedSafetyClassifier.repositoryNotices(module)

        assertTrue(notices.any { it.title == "APK install only" })
        assertTrue(notices.any { it.title == "No source link" })
        assertEquals(LsposedSafetyLevel.WARNING, LsposedSafetyClassifier.highestLevel(notices))
    }

    @Test
    fun installedNoticesFlagManagerAndRepoMismatch() {
        val installed = LsposedInstalledModule(
            packageName = "com.example.local",
            label = "Local module",
            installedVersionName = "1.0",
            installedVersionCode = 1,
            repoModule = null,
            launchable = false,
            detectedByXposedMetadata = true,
        )

        val notices = LsposedSafetyClassifier.installedNotices(
            module = installed,
            managerAvailable = false,
            updateBlocked = false,
        )

        assertTrue(notices.any { it.title == "Scope review needed" })
        assertTrue(notices.any { it.title == "LSPosed Manager not detected" })
        assertTrue(notices.any { it.title == "Not in LSPosed repo" })
        assertEquals(LsposedSafetyLevel.ACTION, LsposedSafetyClassifier.highestLevel(notices))
    }
}
