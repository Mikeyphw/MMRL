package com.dergoogler.mmrl.utils

import com.dergoogler.mmrl.platform.Platform
import java.util.Locale

/** Root-side platform detection. User preference is deliberately not an input. */
internal object RootPlatformDetector {
    fun fromSuVersion(version: String?): Platform {
        val value = version.orEmpty().lowercase(Locale.ROOT)
        return when {
            "sukisu" in value -> Platform.SukiSU
            "kernelsu next" in value || "kernelsu-next" in value || "ksunext" in value -> Platform.KsuNext
            "rksu" in value -> Platform.RKSU
            "mksu" in value -> Platform.MKSU
            "apatch" in value || "apd" in value -> Platform.APatch
            "kernelsu" in value -> Platform.KernelSU
            "magisk" in value -> Platform.Magisk
            else -> Platform.Unknown
        }
    }
}
