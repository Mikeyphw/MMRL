package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.Platform
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootManagerCapabilityPolicyTest {
    @Test
    fun `magic mount mapping follows detected manager semantics`() {
        assertTrue(RootManagerCapabilityPolicy.capabilities(Platform.KsuNext, 13000, true).hasMagicMount)
        assertTrue(RootManagerCapabilityPolicy.capabilities(Platform.SukiSU, 13000, true).hasMagicMount)
        assertFalse(RootManagerCapabilityPolicy.capabilities(Platform.MKSU, 13000, true).hasMagicMount)
        assertFalse(RootManagerCapabilityPolicy.capabilities(Platform.KernelSU, 13000, true).hasMagicMount)
        assertFalse(RootManagerCapabilityPolicy.capabilities(Platform.RKSU, 13000, true).hasMagicMount)
    }

    @Test
    fun `lkm query is gated by family minimum and GKI`() {
        assertFalse(RootManagerCapabilityPolicy.capabilities(Platform.KernelSU, 11647, true).canQueryLkmMode)
        assertFalse(RootManagerCapabilityPolicy.capabilities(Platform.KernelSU, 12000, false).canQueryLkmMode)
        assertTrue(RootManagerCapabilityPolicy.capabilities(Platform.KernelSU, 12000, true).canQueryLkmMode)
    }

    @Test
    fun `next is the only family manager with restore capability`() {
        assertTrue(RootManagerCapabilityPolicy.capabilities(Platform.KsuNext, 13000, true).canRestoreModules)
        assertFalse(RootManagerCapabilityPolicy.capabilities(Platform.SukiSU, 13000, true).canRestoreModules)
    }


    @Test
    fun `unsupported versions and non KSU platforms fail closed`() {
        assertFalse(RootManagerCapabilityPolicy.capabilities(Platform.KernelSU, 11070, true).supported)
        assertFalse(RootManagerCapabilityPolicy.capabilities(Platform.KsuNext, 12796, true).supported)
        assertFalse(RootManagerCapabilityPolicy.capabilities(Platform.Magisk, Int.MAX_VALUE, true).supported)
        assertFalse(RootManagerCapabilityPolicy.mayQueryNative(Platform.Magisk))
    }
}
