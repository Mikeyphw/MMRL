package com.dergoogler.mmrl.platform.ksu

import com.dergoogler.mmrl.platform.Platform

/** Keeps app-side KernelSU feature queries behind an explicit manager authorization. */
internal object KsuNativeQueryPolicy {
    fun canAuthorize(platformAlive: Boolean, platform: Platform): Boolean =
        platformAlive && platform.isKernelSuVariant

    fun canQuery(
        platformAlive: Boolean,
        platform: Platform,
        currentGeneration: Long,
        authorizedPlatform: Platform?,
        authorizedGeneration: Long,
    ): Boolean =
        canAuthorize(platformAlive, platform) &&
            authorizedPlatform == platform &&
            authorizedGeneration > 0L &&
            authorizedGeneration == currentGeneration
}
