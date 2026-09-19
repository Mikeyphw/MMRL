package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedIdentityConvergenceTest {
    @Test
    fun normalization_is_locale_stable_and_case_whitespace_insensitive() {
        assertEquals("com.example.module", LsposedIdentity.normalize("  COM.Example.Module  "))
        assertTrue(LsposedIdentity.same("COM.Example.Module", " com.example.module "))
        assertFalse(LsposedIdentity.same("com.example.one", "com.example.two"))
    }

    @Test
    fun scope_target_identity_normalizes_package_but_preserves_user() {
        val upper = LsposedScopeTarget(packageName = " COM.Example.App ", userId = 10)
        val lower = LsposedScopeTarget(packageName = "com.example.app", userId = 10)
        val otherUser = lower.copy(userId = 0)
        assertEquals(LsposedIdentity.scopeTargetKey(upper), LsposedIdentity.scopeTargetKey(lower))
        assertFalse(LsposedIdentity.scopeTargetKey(lower) == LsposedIdentity.scopeTargetKey(otherUser))
    }

    @Test
    fun scope_planner_deduplicates_normalized_target_identity() {
        val scope = LsposedModuleScope("com.example.module", 1, "/module.apk", true, false)
        val module = LsposedInstalledModule("com.example.module", "Module", "1", 1, null, false, true, scope)
        val plan = LsposedScopePlanner.plan(
            module = module,
            enabled = true,
            autoInclude = false,
            targets = listOf(
                LsposedScopeTarget("COM.Example.App", userId = 0),
                LsposedScopeTarget("com.example.app", userId = 0),
                LsposedScopeTarget("com.example.app", userId = 10),
            ),
        )
        assertEquals(2, plan.targetCount)
        assertEquals(2, plan.targets.size)
    }
}
