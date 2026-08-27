@file:Suppress("unused")

package com.dergoogler.mmrl.platform.ksu

import android.util.Log
import com.dergoogler.mmrl.platform.AtomicStatement
import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.manager.RootManagerCapabilityPolicy
import com.dergoogler.mmrl.platform.PlatformType

object KsuNative {
    private const val TAG = "KsuNative"

    @Volatile
    private var nativeLoaded = false
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
        nativeLoaded = try {
            System.loadLibrary("mmrl-kernelsu")
            true
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "Unable to load KernelSU native bridge", error)
            false
        }
    }

    private inline fun <T> nativeOr(
        fallback: T,
        block: () -> T,
    ): T {
        if (!nativeLoaded) return fallback
        return try {
            block()
        } catch (error: LinkageError) {
            // A missing/mismatched JNI symbol must degrade KernelSU capabilities, never kill
            // the privileged Binder service process. Disable the bridge for this process
            // generation so subsequent calls return deterministic fallbacks.
            nativeLoaded = false
            Log.e(TAG, "KernelSU native bridge linkage failed; disabling native queries", error)
            fallback
        }
    }

    private external fun nativeGrantRoot(): Boolean
    fun grantRoot(): Boolean = appSideKsuSupported() && nativeOr(false) { nativeGrantRoot() }

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
        val authorized = nativeOr(false) { nativeBecomeManager(requireNotNull(pkg)) }
        authorizedPlatform = if (authorized) platform else null
        authorizedGeneration = if (authorized) PlatformManager.serviceGeneration else -1L
        return authorized
    }

    private external fun nativeGetAllowList(): IntArray
    internal fun rawGetAllowList(): IntArray = nativeOr(intArrayOf()) { nativeGetAllowList() }
    fun getAllowList(): IntArray = if (appSideKsuSupported()) rawGetAllowList() else intArrayOf()

    private external fun nativeIsSafeMode(): Boolean
    internal fun rawIsSafeMode(): Boolean = nativeOr(false) { nativeIsSafeMode() }
    fun isSafeMode(): Boolean = appSideKsuSupported() && rawIsSafeMode()

    private external fun nativeGetVersion(): Int
    internal fun rawGetVersion(): Int = nativeOr(-1) { nativeGetVersion() }
    fun getVersion(): Int = if (appSideKsuAuthorized()) rawGetVersion() else -1

    private external fun nativeIsLkmMode(): Boolean
    internal fun rawIsLkmMode(): Boolean = nativeOr(false) { nativeIsLkmMode() }
    fun isLkmMode(): Boolean = appSideCapabilities()?.canQueryLkmMode == true && rawIsLkmMode()

    private external fun nativeUidShouldUmount(uid: Int): Boolean
    internal fun rawUidShouldUmount(uid: Int): Boolean = nativeOr(false) { nativeUidShouldUmount(uid) }
    fun uidShouldUmount(uid: Int): Boolean = appSideKsuSupported() && rawUidShouldUmount(uid)

    /**
     * `su` compat mode can be disabled temporarily.
     *  0: disabled
     *  1: enabled
     *  negative : error
     */
    private external fun nativeIsSuEnabled(): Boolean
    internal fun rawIsSuEnabled(): Boolean = nativeOr(false) { nativeIsSuEnabled() }
    fun isSuEnabled(): Boolean = appSideKsuSupported() && rawIsSuEnabled()

    private external fun nativeSetSuEnabled(enabled: Boolean): Boolean
    internal fun rawSetSuEnabled(enabled: Boolean): Boolean = nativeOr(false) { nativeSetSuEnabled(enabled) }
    fun setSuEnabled(enabled: Boolean): Boolean = appSideKsuSupported() && rawSetSuEnabled(enabled)

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
            nativeOr<Profile?>(null) { nativeGetAppProfile(requireNotNull(key), uid) }
                ?: Profile(requireNotNull(key), currentUid = uid)
        } else {
            Profile(key.orEmpty(), currentUid = uid)
        }

    private external fun nativeSetAppProfile(profile: Profile): Boolean

    fun setAppProfile(profile: Profile?): Boolean =
        appSideKsuSupported() && KsuInputPolicy.validProfile(profile) &&
            nativeOr(false) { nativeSetAppProfile(requireNotNull(profile)) }

    private const val NON_ROOT_DEFAULT_PROFILE_KEY = "$"
    private const val NOBODY_UID = 9999

    @Throws(RuntimeException::class)
    private external fun nativeApplyPolicyRules(
        statements: Array<AtomicStatement>,
        strict: Boolean,
    ): Boolean

    fun applyPolicyRules(statements: Array<AtomicStatement>, strict: Boolean): Boolean =
        appSideKsuSupported() && nativeOr(false) { nativeApplyPolicyRules(statements, strict) }

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
        val version = rawGetVersion()
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
        if (hasFeature { MINIMAL_SUPPORTED_HOOK_MODE }) nativeOr<String?>(null) { nativeGetHookMode() } else null

    /** # SukiSU */
    private external fun nativeIsKPMEnabled(): Boolean
    fun isKPMEnabled(): Boolean =
        PlatformManager.isAlive && PlatformManager.platform.isSukiSU &&
            hasFeature { MINIMAL_SUPPORTED_KPM } && nativeOr(false) { nativeIsKPMEnabled() }

    /** # SukiSU */
    private external fun nativeGetHookType(): String
    fun getHookType(): String =
        if (appSideKsuAuthorized() && PlatformManager.platform.isSukiSU &&
            getVersion() >= PlatformManager.type.MINIMAL_SUPPORTED_KERNEL
        ) nativeOr("Unknown") { nativeGetHookType() } else "Unknown"
}
