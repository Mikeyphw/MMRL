package com.dergoogler.mmrl.platform.file

/** Decision helper used by SuFile so privileged mutations never silently fall back to local I/O. */
internal object PrivilegeRouting {
    enum class Backend { ROOT, LOCAL, UNAVAILABLE }

    fun select(
        privilegedPlatformSelected: Boolean,
        rootServiceReady: Boolean,
    ): Backend = when {
        privilegedPlatformSelected && rootServiceReady -> Backend.ROOT
        privilegedPlatformSelected -> Backend.UNAVAILABLE
        else -> Backend.LOCAL
    }
}
