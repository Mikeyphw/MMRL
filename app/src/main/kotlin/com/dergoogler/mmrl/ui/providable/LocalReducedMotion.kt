package com.dergoogler.mmrl.ui.providable

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.compositionLocalOf

val LocalReducedMotion = compositionLocalOf { false }

fun Context.prefersReducedMotion(): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
    }.getOrDefault(1f)
    return scale == 0f
}
