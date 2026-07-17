package com.dergoogler.mmrl.ash.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshModuleLifecycleResolverTest {
    private val bundled = AshBundledModuleMetadata(version = "11.1.0", versionCode = 210)
    private val compatibleCapabilities = AshCapabilities(
        apiVersion = 2,
        moduleVersion = "11.1.0",
        moduleVersionCode = 210,
        features = setOf("snapshot", "capabilities"),
    )

    @Test
    fun `detects missing module`() {
        val result = AshModuleLifecycleResolver.resolve(
            installation = AshModuleInstallation(),
            bundled = bundled,
            capabilities = null,
        )
        assertEquals(AshModuleLifecycleState.Missing, result.state)
    }

    @Test
    fun `detects outdated module before failed capability negotiation`() {
        val result = AshModuleLifecycleResolver.resolve(
            installation = installed(versionCode = 204),
            bundled = bundled,
            capabilities = null,
            liveError = "Unsupported command",
        )
        assertEquals(AshModuleLifecycleState.Outdated, result.state)
        assertTrue(result.updateAvailable)
        assertFalse(result.compatible)
    }

    @Test
    fun `detects incompatible API`() {
        val result = AshModuleLifecycleResolver.resolve(
            installation = installed(versionCode = 210),
            bundled = bundled,
            capabilities = compatibleCapabilities.copy(apiVersion = 1),
        )
        assertEquals(AshModuleLifecycleState.Incompatible, result.state)
        assertFalse(result.compatible)
    }

    @Test
    fun `rejects module requiring a newer client API`() {
        val result = AshModuleLifecycleResolver.resolve(
            installation = installed(versionCode = 210),
            bundled = bundled,
            capabilities = compatibleCapabilities.copy(minimumClientApi = 3),
        )
        assertEquals(AshModuleLifecycleState.Incompatible, result.state)
        assertFalse(result.compatible)
    }

    @Test
    fun `detects disabled module`() {
        val result = AshModuleLifecycleResolver.resolve(
            installation = installed(versionCode = 210).copy(disabled = true),
            bundled = bundled,
            capabilities = compatibleCapabilities,
        )
        assertEquals(AshModuleLifecycleState.Disabled, result.state)
        assertTrue(result.compatible)
    }

    @Test
    fun `detects staged update requiring reboot`() {
        val result = AshModuleLifecycleResolver.resolve(
            installation = installed(versionCode = 204).copy(updatePending = true),
            bundled = bundled,
            capabilities = compatibleCapabilities.copy(moduleVersionCode = 204),
        )
        assertEquals(AshModuleLifecycleState.RebootPending, result.state)
        assertTrue(result.rebootRequired)
    }

    @Test
    fun `detects current compatible module`() {
        val result = AshModuleLifecycleResolver.resolve(
            installation = installed(versionCode = 210),
            bundled = bundled,
            capabilities = compatibleCapabilities,
        )
        assertEquals(AshModuleLifecycleState.Current, result.state)
        assertTrue(result.compatible)
        assertFalse(result.updateAvailable)
    }

    private fun installed(versionCode: Int) = AshModuleInstallation(
        installed = true,
        active = true,
        folder = "AshLooper",
        id = "AshLooper",
        name = "AshReXcue BootLoop Protector",
        version = if (versionCode == 210) "11.1.0" else "11.0.1",
        versionCode = versionCode,
        source = "active",
        controlAvailable = true,
    )
}
