package com.dergoogler.mmrl.ui.screens.repository

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.model.state.Permissions
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.ui.component.Logo
import com.dergoogler.mmrl.ui.providable.LocalOnlineModule
import com.dergoogler.mmrl.ui.providable.LocalOnlineModuleState
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.ui.theme.LocalSemanticColors
import com.dergoogler.mmrl.ui.theme.LocalMMRLSurfaces
import com.dergoogler.mmrl.utils.toFormattedDateSafely

@Composable
internal fun RepositoryModuleRow(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
    alpha: Float = 1f,
    decoration: TextDecoration = TextDecoration.None,
    sourceProvider: String? = null,
    showLabels: Boolean,
    showDescription: Boolean,
    showLastUpdated: Boolean = true,
) {
    val module = LocalOnlineModule.current
    val state = LocalOnlineModuleState.current
    val menu = LocalUserPreferences.current.repositoryMenu
    val semantic = LocalSemanticColors.current
    val surfaces = LocalMMRLSurfaces.current

    val platform = PlatformManager.platform
    val rootVersion =
        PlatformManager.get(0) {
            with(moduleManager) { versionCode }
        }
    val compatible =
        remember(module, platform, rootVersion) {
            val managerCompatible = module.manager(platform).isCompatible(rootVersion)
            val androidCompatible =
                (module.minApi == null || Build.VERSION.SDK_INT >= module.minApi) &&
                    (module.maxApi == null || Build.VERSION.SDK_INT <= module.maxApi)
            managerCompatible && androidCompatible
        }
    val permissions = module.permissions.orEmpty()
    val hasWebUi =
        remember(module) {
            module.features?.webroot == true ||
                Permissions.KERNELSU_WEBUI in permissions ||
                Permissions.MMRL_WEBUI in permissions ||
                Permissions.MMRL_WEBUI_CONFIG in permissions
        }
    val category = module.categories?.firstOrNull().takeIf { menu.showCategory }
    val preview =
        when {
            menu.showCover && !module.cover.isNullOrBlank() -> module.cover
            menu.showIcon && !module.icon.isNullOrBlank() -> module.icon
            else -> null
        }
    val previewIsCover = menu.showCover && preview == module.cover

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        color = surfaces.row,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .alpha(alpha)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (preview != null) {
                AsyncImage(
                    model = preview,
                    modifier =
                        Modifier
                            .size(
                                width = if (previewIsCover) 72.dp else 44.dp,
                                height = if (previewIsCover) 58.dp else 44.dp,
                            ).clip(RoundedCornerShape(if (previewIsCover) 8.dp else 12.dp)),
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else if (menu.showIcon) {
                Logo(
                    icon = R.drawable.box,
                    modifier = Modifier.size(44.dp),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = module.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        textDecoration = decoration,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (module.isVerified && menu.showVerified) {
                        Icon(
                            painter = painterResource(R.drawable.rosette_discount_check),
                            contentDescription = stringResource(R.string.repository_verified),
                            modifier = Modifier.size(18.dp),
                            tint = semantic.verified,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.module_version_author, module.versionDisplay, module.author),
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = decoration,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val sourceLine = listOfNotNull(sourceProvider, category).joinToString(" · ")
                if (sourceLine.isNotBlank()) {
                    Text(
                        text = sourceLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (showDescription) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = module.description ?: stringResource(R.string.view_module_no_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (showLabels) {
                    FlowRow(
                        modifier = Modifier.padding(top = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        RepositoryStatusLabel(
                            text =
                                stringResource(
                                    if (compatible) {
                                        R.string.repo_compatible
                                    } else {
                                        R.string.repo_incompatible
                                    },
                                ),
                            color =
                                if (compatible) {
                                    semantic.success
                                } else {
                                    semantic.incompatible
                                },
                        )

                        when {
                            state.updatable ->
                                RepositoryStatusLabel(
                                    text = stringResource(R.string.module_update_available),
                                    color = semantic.updateAvailable,
                                )

                            state.installed ->
                                RepositoryStatusLabel(
                                    text = stringResource(R.string.module_installed),
                                    color = semantic.disabled,
                                )
                        }

                        if (hasWebUi) {
                            RepositoryStatusLabel(
                                text = stringResource(R.string.view_module_features_webui),
                                color = semantic.info,
                            )
                        }

                        if (module.track.hasAntifeatures && menu.showAntiFeatures) {
                            RepositoryStatusLabel(
                                text = stringResource(R.string.view_module_antifeatures),
                                color = semantic.warning,
                            )
                        }
                    }
                }

                if (menu.showUpdatedTime && showLastUpdated) {
                    Text(
                        text = stringResource(R.string.module_update_at, state.lastUpdated.toFormattedDateSafely),
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoryStatusLabel(
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
