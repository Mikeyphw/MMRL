package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LsposedIdentitySourceContractTest {
    private val repo = File(System.getProperty("user.dir") ?: ".")
    private fun source(path: String) = File(repo, path).readText()

    @Test
    fun repository_matching_uses_normalized_identity() {
        val text = source("src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")
        assertTrue(text.contains("index.associateBy { LsposedIdentity.normalize(it.packageName) }"))
        assertTrue(text.contains("repoByPackage[LsposedIdentity.normalize(packageName)]"))
        assertFalse(text.contains("index.associateBy { it.packageName }"))
    }

    @Test
    fun repository_ui_membership_and_compose_keys_use_normalized_identity() {
        val text = source("src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")
        assertTrue(text.contains("mapTo(mutableSetOf()) { LsposedIdentity.normalize(it.packageName) }"))
        assertTrue(text.contains("key = { LsposedIdentity.normalize(it.packageName) }"))
        assertFalse(text.contains("module.packageName in installedPackages"))
        assertFalse(text.contains("key = { it.packageName }"))
        assertTrue(text.contains("state.installingPackage?.let { LsposedIdentity.same(it, module.packageName) } == true"))
        assertTrue(text.contains("state.applyingScopePackage?.let { LsposedIdentity.same(it, module.packageName) } == true"))
        assertFalse(text.contains("state.installingPackage == module.packageName"))
        assertFalse(text.contains("state.applyingScopePackage == module.packageName"))
    }

    @Test
    fun scope_identity_does_not_build_raw_package_keys() {
        val models = source("src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        val screens = source("src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")
        assertTrue(models.contains("LsposedIdentity.scopeTargetKey(it)"))
        assertTrue(screens.contains("LsposedIdentity.scopeTargetKey(target)"))
        assertTrue(screens.contains("remember(LsposedIdentity.normalize(module.packageName))"))
        assertTrue(screens.contains("scope.targets.map { LsposedIdentity.scopeTargetKey(it) }.toSet()"))
        assertFalse(screens.contains("remember(module.packageName)"))
        assertFalse(screens.contains("\"${it.userId}:${it.packageName}\""))
    }
}
