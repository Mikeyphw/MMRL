package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedModulePolicyTest {
    @Test
    fun parsesRepositoryVersionTag() {
        val version = LsposedVersion.parse("12-1.2.0")
        assertNotNull(version)
        assertEquals(12L, version!!.versionCode)
        assertEquals("1.2.0", version.versionName)
    }

    @Test
    fun rejectsInvalidRepositoryVersionTag() {
        assertNull(LsposedVersion.parse("1.2.0"))
        assertNull(LsposedVersion.parse("abc-1.2.0"))
    }

    @Test
    fun selectsApkAssetFromRelease() {
        val module = LsposedRepoModule(
            name = "io.github.example",
            latestRelease = "10-1.0.0",
            releases = listOf(
                LsposedRelease(
                    name = "10-1.0.0",
                    releaseAssets = listOf(
                        LsposedReleaseAsset(name = "source.zip", downloadUrl = "https://example.invalid/source.zip"),
                        LsposedReleaseAsset(name = "example.apk", downloadUrl = "https://example.invalid/example.apk"),
                    ),
                ),
            ),
        )

        val selected = LsposedModulePolicy.bestInstallAsset(module)
        assertNotNull(selected)
        assertEquals("example.apk", selected!!.second.name)
    }

    @Test
    fun matchesQueryAgainstPackageAndDescription() {
        val module = LsposedRepoModule(
            name = "io.github.example",
            summary = "Example Tweaks",
            description = "Changes system UI behavior",
        )

        assertTrue(LsposedModulePolicy.matchesQuery(module, "system ui"))
        assertTrue(LsposedModulePolicy.matchesQuery(module, "io.github"))
    }
}
