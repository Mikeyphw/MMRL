package com.dergoogler.mmrl.ash.ui

import androidx.compose.runtime.Composable
import com.dergoogler.mmrl.ash.AshScreen
import com.dergoogler.mmrl.ash.AshViewModel

/**
 * Compatibility entry point retained for callers built against the former embedded companion UI.
 * The destination now renders MMRL's native Recovery Center and no longer owns a second shell.
 */
@Deprecated("Use the native Recovery Center destination")
@Composable
fun AshReXcueApp(viewModel: AshViewModel) {
    AshScreen(viewModel = viewModel)
}
