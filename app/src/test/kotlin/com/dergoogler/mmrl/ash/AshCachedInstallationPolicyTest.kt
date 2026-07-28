package com.dergoogler.mmrl.ash

import com.dergoogler.mmrl.ash.model.AshModuleInstallation
import org.junit.Assert.assertEquals
import org.junit.Test

class AshCachedInstallationPolicyTest {
    @Test
    fun `missing live inspection does not erase cached installed module state`() {
        val cached = installed(folder = "AshLooper", versionCode = 261)
        val liveMissing = AshModuleInstallation(source = "none")

        val selected = cachedInstallationForLifecycle(liveMissing, cached)

        assertEquals(cached, selected)
    }

    @Test
    fun `installed live inspection wins over cache`() {
        val cached = installed(folder = "AshLooper", versionCode = 250)
        val live = installed(folder = "renamed-by-manager", versionCode = 261)

        val selected = cachedInstallationForLifecycle(live, cached)

        assertEquals(live, selected)
    }

    @Test
    fun `staged live install wins over cache so reboot pending is preserved`() {
        val cached = installed(folder = "AshLooper", versionCode = 250)
        val staged = AshModuleInstallation(
            installed = true,
            active = false,
            folder = "AshLooper",
            id = "AshLooper",
            versionCode = 261,
            source = "staged",
            updatePending = true,
        )

        val selected = cachedInstallationForLifecycle(staged, cached)

        assertEquals(staged, selected)
    }

    private fun installed(folder: String, versionCode: Int) = AshModuleInstallation(
        installed = true,
        active = true,
        folder = folder,
        id = "AshLooper",
        name = "AshReXcue BootLoop Protector",
        version = "11.6.1",
        versionCode = versionCode,
        source = "active",
        controlAvailable = true,
    )
}
