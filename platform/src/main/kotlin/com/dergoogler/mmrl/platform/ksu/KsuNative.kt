@file:Suppress("unused")

package com.dergoogler.mmrl.platform.ksu

import com.dergoogler.mmrl.platform.AtomicStatement
import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.manager.RootManagerCapabilityPolicy
import com.dergoogler.mmrl.platform.PlatformType

object KsuNative {
    @Volatile
    private var authorizedPlatform: Platform? = null

    @Volatile
    private var authorizedGeneration: Long = -1L

    // minimal supported kernel version
    // 10915: allowlist breaking change, add app profile
    // 10931: app profile struct add 'version' field
    // 10946: add capabilities
    // 10977: change groups_count and groups to avoid overflow write
    // 11071: Fix the issue of failing to set a custom SELinux type.
    const val MINIMAL_SUPPORTED_KERNEL = 11071

    // 11640: Support query working mode, LKM or GKI
    // when MINIMAL_SUPPORTED_KERNEL > 11640, we can remove this constant.
    const val MINIMAL_SUPPORTED_KERNEL_LKM = 11648

    // 12404: Support disable sucompat mode
    const val MINIMAL_SUPPORTED_SU_COMPAT_NEXT = 12404
    const val MINIMAL_SUPPORTED_SU_COMPAT = 12040

    // 12569: support get hook mode
    const val MINIMAL_SUPPORTED_HOOK_MODE = 12569

    const val KERNEL_SU_DOMAIN = "u:r:su:s0"

    const val ROOT_UID = 0
    const val ROOT_GID = 0

    init {
        System.loadLibrary("mmrl-kernelsu")
    }

    private external fun nativeGrantRoot(): Boolean
    fun grantRoot(): Boolean = appSideKsuSupported() && nativeGrantRoot()

    private external fun nativeBecomeManager(pkg: String): Boolean

    fun becomeManager(pkg: String?): Boolean {
        val platform = PlatformManager.platform
        if (!KsuNativeQueryPolicy.canAuthorize(PlatformManager.isAlive, platform) ||
            !KsuInputPolicy.validPackage(pkg)
        ) {
            authorizedPlatform = null
            authorizedGeneration = -1L
            return false
        }
        val authorized = nativeBecomeManager(requireNotNull(pkg))
        authorizedPlatform = if (authorized) platform else null
        authorizedGeneration = if (authorized) PlatformManager.serviceGeneration else -1L
        return authorized
    }

    internal external fun nativeGetAllowList(): IntArray
    fun getAllowList(): IntArray = if (appSideKsuSupported()) nativeGetAllowList() else intArrayOf()

    internal external fun nativeIsSafeMode(): Boolean
    fun isSafeMode(): Boolean = appSideKsuSupported() && nativeIsSafeMode()

    internal external fun nativeGetVersion(): Int
    fun getVersion(): Int = if (appSideKsuAuthorized()) nativeGetVersion() else -1

    internal external fun nativeIsLkmMode(): Boolean
    fun isLkmMode(): Boolean = appSideCapabilities()?.canQueryLkmMode == true && nativeIsLkmMode()

    internal external fun nativeUidShouldUmount(uid: Int): Boolean
    fun uidShouldUmount(uid: Int): Boolean = appSideKsuSupported() && nativeUidShouldUmount(uid)

    /**
     * `su` compat mode can be disabled temporarily.
     *  0: disabled
     *  1: enabled
     *  negative : error
     */
    internal external fun nativeIsSuEnabled(): Boolean
    fun isSuEnabled(): Boolean = appSideKsuSupported() && nativeIsSuEnabled()

    internal external fun nativeSetSuEnabled(enabled: Boolean): Boolean
    fun setSuEnabled(enabled: Boolean): Boolean = appSideKsuSupported() && nativeSetSuEnabled(enabled)

    fun isDefaultUmountModules(): Boolean {
        getAppProfile(NON_ROOT_DEFAULT_PROFILE_KEY, NOBODY_UID).let {
            return it.umountModules
        }
    }

    /**
     * Get the profile of the given package.
     * @param key usually the package name
     * @return return null if failed.
     */
    private external fun nativeGetAppProfile(key: String, uid: Int): Profile?

    fun getAppProfile(key: String?, uid: Int): Profile =
        if (appSideKsuSupported() && KsuInputPolicy.validPackage(key)) {
            nativeGetAppProfile(requireNotNull(key), uid) ?: Profile(requireNotNull(key), currentUid = uid)
        } else {
            Profile(key.orEmpty(), currentUid = uid)
        }

    private external fun nativeSetAppProfile(profile: Profile): Boolean

    fun setAppProfile(profile: Profile?): Boolean =
        appSideKsuSupported() && KsuInputPolicy.validProfile(profile) && nativeSetAppProfile(requireNotNull(profile))

    private const val NON_ROOT_DEFAULT_PROFILE_KEY = "$"
    private const val NOBODY_UID = 9999

    @Throws(RuntimeException::class)
    private external fun nativeApplyPolicyRules(
        statements: Array<AtomicStatement>,
        strict: Boolean,
    ): Boolean

    fun applyPolicyRules(statements: Array<AtomicStatement>, strict: Boolean): Boolean =
        appSideKsuSupported() && nativeApplyPolicyRules(statements, strict)

    private fun appSideKsuReady(): Boolean =
        KsuNativeQueryPolicy.canAuthorize(PlatformManager.isAlive, PlatformManager.platform)

    private fun appSideKsuAuthorized(): Boolean =
        KsuNativeQueryPolicy.canQuery(
            PlatformManager.isAlive,
            PlatformManager.platform,
            PlatformManager.serviceGeneration,
            authorizedPlatform,
            authorizedGeneration,
        )

    private fun appSideCapabilities(): RootManagerCapabilityPolicy.Capabilities? {
        if (!appSideKsuAuthorized()) return null
        val version = nativeGetVersion()
        if (version < 0) return null
        return RootManagerCapabilityPolicy.capabilities(
            PlatformManager.platform,
            version,
            KernelVersion.getKernelVersion().isGKI(),
        )
    }

    private fun appSideKsuSupported(): Boolean = appSideCapabilities()?.supported == true

    fun requireNewKernel(): Boolean =
        !appSideKsuReady() || getVersion() < PlatformManager.type.MINIMAL_SUPPORTED_KERNEL

    fun hasFeature(type: Int): Boolean = hasFeature { type }

    fun hasFeature(feature: PlatformType.() -> Int): Boolean {
        if (!appSideKsuAuthorized()) return false
        val type = feature(PlatformManager.type)
        if (type == -1) return false
        return getVersion() >= type
    }

    /**
     * # KsuNext
     */
    private external fun nativeGetHookMode(): String?
    fun getHookMode(): String? =
        if (hasFeature { MINIMAL_SUPPORTED_HOOK_MODE }) nativeGetHookMode() else null

    /** # SukiSU */
    private external fun nativeIsKPMEnabled(): Boolean
    fun isKPMEnabled(): Boolean =
        PlatformManager.isAlive && PlatformManager.platform.isSukiSU &&
            hasFeature { MINIMAL_SUPPORTED_KPM } && nativeIsKPMEnabled()

    /** # SukiSU */
    private external fun nativeGetHookType(): String
    fun getHookType(): String =
        if (appSideKsuAuthorized() && PlatformManager.platform.isSukiSU &&
            getVersion() >= PlatformManager.type.MINIMAL_SUPPORTED_KERNEL
        ) nativeGetHookType() else "Unknown"
}
