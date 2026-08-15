package com.dergoogler.mmrl.platform.ksu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMutationTransactionTest {
    private class FakeBackend : ProfileMutationTransaction.Backend {
        val events = mutableListOf<String>()
        var profileResult = true
        var policyResult = true
        var clearResult = true
        override fun setProfile(profile: Profile): Boolean { events += "profile:${profile.allowSu}:${profile.rootUseDefault}"; return profileResult }
        override fun setPolicy(packageName: String, rules: String): Boolean { events += "policy:$rules"; return policyResult }
        override fun clearPolicy(packageName: String): Boolean { events += "clear"; return clearResult }
    }

    @Test
    fun `custom policy is applied before activating custom root profile`() {
        val backend = FakeBackend()
        val old = Profile("pkg")
        val target = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow a b file read")
        val result = ProfileMutationTransaction.execute(old, target, backend)
        assertTrue(result.success)
        assertFalse(result.reconciliationRequired)
        assertEquals(listOf("policy:allow a b file read", "profile:true:false"), backend.events)
    }

    @Test
    fun `revoke applies profile before deleting stale policy`() {
        val backend = FakeBackend()
        val old = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow a b file read")
        val target = Profile("pkg", allowSu = false)
        val result = ProfileMutationTransaction.execute(old, target, backend)
        assertTrue(result.success)
        assertFalse(result.reconciliationRequired)
        assertEquals("profile:false:true", backend.events[0])
        assertEquals("clear", backend.events[1])
    }

    @Test
    fun `failed stale-policy cleanup rolls profile back`() {
        val backend = FakeBackend().apply { clearResult = false }
        val old = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow a b file read")
        val target = Profile("pkg", allowSu = false)
        val result = ProfileMutationTransaction.execute(old, target, backend)
        assertFalse(result.success)
        assertTrue(result.rolledBack)
        assertTrue(backend.events.count { it.startsWith("profile:") } >= 2)
    }


    @Test
    fun `failed live policy apply never activates target root profile`() {
        val backend = FakeBackend().apply { policyResult = false }
        val old = Profile("pkg")
        val target = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow a b file read")
        val result = ProfileMutationTransaction.execute(old, target, backend)
        assertFalse(result.success)
        assertFalse(result.rolledBack)
        assertEquals(listOf("policy:allow a b file read"), backend.events)
    }

    @Test
    fun `rollback is reported false when either restoration step fails`() {
        val backend = object : ProfileMutationTransaction.Backend {
            var profileCalls = 0
            override fun setProfile(profile: Profile): Boolean {
                profileCalls++
                return profileCalls == 1
            }
            override fun setPolicy(packageName: String, rules: String) = true
            override fun clearPolicy(packageName: String) = false
        }
        val old = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "old")
        val target = Profile("pkg", allowSu = false)
        val result = ProfileMutationTransaction.execute(old, target, backend)
        assertFalse(result.success)
        assertFalse(result.rolledBack)
    }
    @Test
    fun `profile failure after live custom policy apply is never reported fully rolled back`() {
        val backend = object : ProfileMutationTransaction.Backend {
            var profileCalls = 0
            override fun setProfile(profile: Profile): Boolean {
                profileCalls++
                return profileCalls > 1 // target fails, previous profile restoration succeeds
            }
            override fun setPolicy(packageName: String, rules: String) = true
            override fun clearPolicy(packageName: String) = true
        }
        val old = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow old domain file read")
        val target = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow new domain file write")
        val result = ProfileMutationTransaction.execute(old, target, backend)
        assertFalse(result.success)
        assertFalse(result.rolledBack)
        assertTrue(result.reconciliationRequired)
    }

    @Test
    fun `changing live custom rules commits durable target but requires reconciliation`() {
        val backend = FakeBackend()
        val old = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow old domain file read")
        val target = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow new domain file write")
        val result = ProfileMutationTransaction.execute(old, target, backend)
        assertTrue(result.success)
        assertFalse(result.rolledBack)
        assertTrue(result.reconciliationRequired)
        assertEquals(listOf("policy:allow new domain file write", "profile:true:false"), backend.events)
    }

    @Test
    fun `switching from custom to default while retaining su requires live reconciliation`() {
        val backend = FakeBackend()
        val old = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow old domain file read")
        val target = Profile("pkg", allowSu = true, rootUseDefault = true, rules = "")
        val result = ProfileMutationTransaction.execute(old, target, backend)
        assertTrue(result.success)
        assertFalse(result.rolledBack)
        assertTrue(result.reconciliationRequired)
        assertEquals(listOf("profile:true:true", "clear"), backend.events)
    }

    @Test
    fun `same custom policy does not manufacture reconciliation requirement`() {
        val backend = FakeBackend()
        val old = Profile("pkg", allowSu = true, rootUseDefault = false, rules = "allow same domain file read")
        val target = old.copy(uid = 0)
        val result = ProfileMutationTransaction.execute(old, target, backend)
        assertTrue(result.success)
        assertFalse(result.reconciliationRequired)
    }
}
