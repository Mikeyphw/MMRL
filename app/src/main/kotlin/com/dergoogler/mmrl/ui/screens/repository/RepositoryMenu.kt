package com.dergoogler.mmrl.ui.screens.repository

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.datastore.model.Option
import com.dergoogler.mmrl.datastore.model.RepoListMode
import com.dergoogler.mmrl.datastore.model.RepositoryMenu
import com.dergoogler.mmrl.ui.component.BottomSheet
import com.dergoogler.mmrl.ui.component.MenuChip
import com.dergoogler.mmrl.ui.component.Segment
import com.dergoogler.mmrl.ui.component.SegmentedButtons
import com.dergoogler.mmrl.ui.component.SegmentedButtonsDefaults
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences

@Composable
fun RepositoryMenu(
    activeFilterCount: Int = 0,
    onResetFilters: () -> Unit = {},
    setMenu: (RepositoryMenu) -> Unit,
) {
    val menu = LocalUserPreferences.current.repositoryMenu
    var open by rememberSaveable { mutableStateOf(false) }

    val sortLabel =
        stringResource(
            when (menu.option) {
                Option.Name -> R.string.menu_sort_option_name
                Option.UpdatedTime -> R.string.menu_sort_option_updated
                Option.Size -> R.string.menu_sort_option_size
            },
        )
    val summary =
        buildList {
            add("$sortLabel ${if (menu.descending) "↓" else "↑"}")
            if (activeFilterCount > 0) {
                add(stringResource(R.string.repository_active_filters, activeFilterCount))
            }
            if (menu.pinInstalled || menu.pinUpdatable) {
                add(stringResource(R.string.repository_pinning_active))
            }
        }.joinToString(" · ")

    val hasActiveState =
        activeFilterCount > 0 ||
            menu.option != Option.UpdatedTime ||
            !menu.descending ||
            menu.pinInstalled ||
            menu.pinUpdatable

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.sort_outline),
                contentDescription = null,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(start = 7.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (hasActiveState) {
            IconButton(
                onClick = {
                    setMenu(menu.resetOrdering())
                    onResetFilters()
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.rotate),
                    contentDescription = stringResource(R.string.repository_reset_state),
                )
            }
        }
    }

    if (open) {
        MenuBottomSheet(
            onClose = { open = false },
            menu = menu,
            activeFilterCount = activeFilterCount,
            onResetFilters = onResetFilters,
            setMenu = setMenu,
        )
    }
}

@Composable
private fun MenuBottomSheet(
    onClose: () -> Unit,
    menu: RepositoryMenu,
    activeFilterCount: Int,
    onResetFilters: () -> Unit,
    setMenu: (RepositoryMenu) -> Unit,
) = BottomSheet(onDismissRequest = onClose) {
    val options =
        listOf(
            Option.Name to R.string.menu_sort_option_name,
            Option.UpdatedTime to R.string.menu_sort_option_updated,
            Option.Size to R.string.menu_sort_option_size,
        )

    val optionsRepoListMode =
        listOf(
            RepoListMode.Detailed to R.string.menu_sort_repolistmode_detailed,
            RepoListMode.Compact to R.string.menu_sort_repolistmode_compact,
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(id = R.string.menu_advanced_menu),
            style = MaterialTheme.typography.headlineSmall,
        )
        TextButton(
            onClick = {
                setMenu(menu.resetOrdering())
                if (activeFilterCount > 0) onResetFilters()
            },
        ) {
            Text(stringResource(R.string.repository_reset_state))
        }
    }

    Column(
        modifier = Modifier.padding(all = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.menu_sort_mode),
            style = MaterialTheme.typography.titleSmall,
        )

        SegmentedButtons(
            border = SegmentedButtonsDefaults.border(color = Color.Transparent),
        ) {
            options.forEach { (option, label) ->
                Segment(
                    selected = option == menu.option,
                    onClick = { setMenu(menu.copy(option = option)) },
                    colors =
                        SegmentedButtonsDefaults.buttonColor(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    icon = null,
                ) {
                    Text(text = stringResource(id = label))
                }
            }
        }

        Text(
            text = stringResource(id = R.string.menu_sort_order),
            style = MaterialTheme.typography.titleSmall,
        )

        SegmentedButtons(
            border = SegmentedButtonsDefaults.border(color = Color.Transparent),
        ) {
            listOf(
                false to R.string.menu_ascending,
                true to R.string.menu_descending,
            ).forEach { (descending, label) ->
                Segment(
                    selected = descending == menu.descending,
                    onClick = { setMenu(menu.copy(descending = descending)) },
                    colors =
                        SegmentedButtonsDefaults.buttonColor(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    icon = null,
                ) {
                    Text(text = stringResource(id = label))
                }
            }
        }

        Text(
            text = stringResource(R.string.repository_pinning),
            style = MaterialTheme.typography.titleSmall,
        )

        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuChip(
                selected = menu.pinInstalled,
                onClick = { setMenu(menu.copy(pinInstalled = !menu.pinInstalled)) },
                label = { Text(text = stringResource(id = R.string.menu_pin_installed)) },
            )

            MenuChip(
                selected = menu.pinUpdatable,
                onClick = { setMenu(menu.copy(pinUpdatable = !menu.pinUpdatable)) },
                label = { Text(text = stringResource(id = R.string.menu_pin_updatable)) },
            )
        }

        Text(
            text = stringResource(R.string.repository_row_content),
            style = MaterialTheme.typography.titleSmall,
        )

        SegmentedButtons(
            border = SegmentedButtonsDefaults.border(color = Color.Transparent),
        ) {
            optionsRepoListMode.forEach { (repoListMode, label) ->
                Segment(
                    selected = repoListMode == menu.repoListMode,
                    onClick = { setMenu(menu.copy(repoListMode = repoListMode)) },
                    colors =
                        SegmentedButtonsDefaults.buttonColor(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    icon = null,
                ) {
                    Text(text = stringResource(id = label))
                }
            }
        }

        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuChip(
                selected = menu.showIcon,
                onClick = { setMenu(menu.copy(showIcon = !menu.showIcon)) },
                label = { Text(text = stringResource(id = R.string.menu_show_icon)) },
            )
            MenuChip(
                selected = menu.showCover,
                onClick = { setMenu(menu.copy(showCover = !menu.showCover)) },
                label = { Text(text = stringResource(id = R.string.menu_show_cover)) },
            )
            MenuChip(
                selected = menu.showVerified,
                onClick = { setMenu(menu.copy(showVerified = !menu.showVerified)) },
                label = { Text(text = stringResource(id = R.string.menu_show_verified)) },
            )
            MenuChip(
                selected = menu.showAntiFeatures,
                onClick = { setMenu(menu.copy(showAntiFeatures = !menu.showAntiFeatures)) },
                label = { Text(text = stringResource(id = R.string.menu_show_antifeatures)) },
            )
            MenuChip(
                selected = menu.showCategory,
                onClick = { setMenu(menu.copy(showCategory = !menu.showCategory)) },
                label = { Text(text = stringResource(id = R.string.menu_show_category)) },
            )
            MenuChip(
                selected = menu.showUpdatedTime,
                onClick = { setMenu(menu.copy(showUpdatedTime = !menu.showUpdatedTime)) },
                label = { Text(text = stringResource(id = R.string.menu_show_updated)) },
            )
        }
    }
}


private fun RepositoryMenu.resetOrdering() =
    copy(
        option = Option.UpdatedTime,
        descending = true,
        pinInstalled = false,
        pinUpdatable = false,
    )
