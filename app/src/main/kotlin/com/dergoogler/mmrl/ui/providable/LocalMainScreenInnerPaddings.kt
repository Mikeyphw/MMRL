package com.dergoogler.mmrl.ui.providable

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalMainScreenInnerPaddings =
    staticCompositionLocalOf<PaddingValues> {
        error("CompositionLocal LocalMainScreenInnerPaddings not present")
    }

/**
 * Bottom padding for scrollable main-screen content.
 *
 * The main navigation bar is intentionally drawn over screen content so blur remains visible.
 * Lists therefore need the bar height plus a small breathing area; using only the bar height
 * leaves the final row and its chips visually pinned to the navigation surface.
 */
fun PaddingValues.mainContentBottomPadding(extra: Dp = 24.dp): Dp =
    calculateBottomPadding() + extra
