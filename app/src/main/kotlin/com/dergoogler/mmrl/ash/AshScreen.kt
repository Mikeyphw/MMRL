package com.dergoogler.mmrl.ash

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.dergoogler.mmrl.ash.ui.AshReXcueApp
import com.dergoogler.mmrl.ui.component.LocalScreenProvider
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph

@Destination<RootGraph>
@Composable
fun AshScreen() =
    LocalScreenProvider {
        AshReXcueApp(viewModel = hiltViewModel())
    }
