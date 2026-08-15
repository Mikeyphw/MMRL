package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.Platform

/** Central capability/version semantics for the KernelSU family. */
internal object RootManagerCapabilityPolicy {
    data class Capabilities(
        val supported: Boolean,
        val hasMagicMount: Boolean,
        val canRestoreModules: Boolean,
        val canQueryLkmMode: Boolean,
    )

    fun capabilities(platform: Platform, versionCode: Int, isGki: Boolean): Capabilities {
        if (!platform.isKernelSuVariant) return Capabilities(false, false, false, false)
        val minimum = platform.type.MINIMAL_SUPPORTED_KERNEL
        val supported = minimum >= 0 && versionCode >= minimum
        val magicMount = supported && when (platform) {
            Platform.KsuNext, Platform.SukiSU -> true
            else -> false
        }
        val canRestore = supported && platform == Platform.KsuNext
        val lkmMinimum = platform.type.MINIMAL_SUPPORTED_KERNEL_LKM
        val canLkm = supported && isGki && lkmMinimum >= 0 && versionCode >= lkmMinimum
        return Capabilities(supported, magicMount, canRestore, canLkm)
    }

    fun mayQueryNative(platform: Platform): Boolean = platform.isKernelSuVariant
}
