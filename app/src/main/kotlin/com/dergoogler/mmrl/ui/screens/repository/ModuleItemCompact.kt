package com.dergoogler.mmrl.ui.screens.repository

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ModuleItemCompact(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    showLabels: Boolean = true,
    showLastUpdated: Boolean = true,
    sourceProvider: String? = null,
) = RepositoryModuleRow(
    modifier = modifier,
    onClick = onClick,
    enabled = enabled,
    sourceProvider = sourceProvider,
    showLabels = showLabels,
    showDescription = false,
    showLastUpdated = showLastUpdated,
)
