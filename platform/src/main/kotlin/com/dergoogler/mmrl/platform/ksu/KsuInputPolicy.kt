package com.dergoogler.mmrl.platform.ksu

import java.nio.charset.StandardCharsets

/** Mirrors the fixed KernelSU ABI field capacities before crossing JNI. */
internal object KsuInputPolicy {
    const val PACKAGE_BYTES = 256
    const val DOMAIN_BYTES = 64
    const val MAX_GROUPS = 32

    fun fitsUtf8(value: String?, capacity: Int): Boolean =
        value != null && value.toByteArray(StandardCharsets.UTF_8).size < capacity

    fun validPackage(value: String?): Boolean = fitsUtf8(value, PACKAGE_BYTES)

    fun validProfile(profile: Profile?): Boolean {
        if (profile == null || !validPackage(profile.name)) return false
        if (profile.rootTemplate != null && !fitsUtf8(profile.rootTemplate, PACKAGE_BYTES)) return false
        if (!fitsUtf8(profile.context, DOMAIN_BYTES)) return false
        if (profile.groups.size > MAX_GROUPS) return false
        return profile.capabilities.all { it in 0..63 }
    }
}
