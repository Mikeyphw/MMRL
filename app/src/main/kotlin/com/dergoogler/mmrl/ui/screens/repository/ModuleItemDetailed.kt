package com.dergoogler.mmrl.ui.screens.repository

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration

@Composable
fun ModuleItemDetailed(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    onClick: () -> Unit = {},
    decoration: TextDecoration = TextDecoration.None,
    enabled: Boolean = true,
    sourceProvider: String? = null,
) = RepositoryModuleRow(
    modifier = modifier,
    onClick = onClick,
    enabled = enabled,
    alpha = alpha,
    decoration = decoration,
    sourceProvider = sourceProvider,
    showLabels = true,
    showDescription = true,
)
