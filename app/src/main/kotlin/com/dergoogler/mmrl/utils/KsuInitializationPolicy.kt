package com.dergoogler.mmrl.utils

import com.dergoogler.mmrl.platform.Platform

internal object KsuInitializationPolicy {
    fun shouldAttemptManagerAuthorization(initialized: Boolean, detectedPlatform: Platform): Boolean =
        initialized && detectedPlatform.isKernelSuVariant
}
