package com.dergoogler.mmrl.ui.screens.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ext.none
import com.dergoogler.mmrl.ui.component.TopAppBar
import com.dergoogler.mmrl.ui.component.HomeNavigationButton
import com.dergoogler.mmrl.ui.component.scaffold.ResponsiveScaffold
import com.dergoogler.mmrl.ui.component.scaffold.Scaffold
import com.dergoogler.mmrl.ui.component.toolbar.BlurBottomToolbar
import com.dergoogler.mmrl.ui.navigation.MainDestination
import com.dergoogler.mmrl.ui.navigation.isAccessible
import com.dergoogler.mmrl.ui.providable.LocalBulkInstall
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.dergoogler.mmrl.ui.providable.LocalHazeState
import com.dergoogler.mmrl.ui.providable.LocalMainScreenInnerPaddings
import com.dergoogler.mmrl.ui.providable.LocalNavController
import com.dergoogler.mmrl.ui.providable.LocalReducedMotion
import com.dergoogler.mmrl.ui.providable.LocalSnackbarHost
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.ui.providable.LocalWindowSizeClass
import com.dergoogler.mmrl.ui.remember.rememberUpdatableModuleCount
import com.dergoogler.mmrl.utils.initPlatform
import com.dergoogler.mmrl.viewmodel.ActivityViewModel
import com.dergoogler.mmrl.viewmodel.BulkInstallViewModel
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.ActivityScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AshScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleUpdatesScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val compactDestinations =
    listOf(
        MainDestination.Repository,
        MainDestination.Modules,
        MainDestination.Ash,
        MainDestination.Activity,
    )

private val overflowDestinations =
    listOf(
        MainDestination.SuperUser,
        MainDestination.Settings,
    )

private val expandedDestinations =
    compactDestinations + overflowDestinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    openActivityOnLaunch: Boolean = false,
    openUpdatesOnLaunch: Boolean = false,
    openRecoveryOnLaunch: Boolean = false,
    onActivityOpened: () -> Unit = {},
    onUpdatesOpened: () -> Unit = {},
    onRecoveryOpened: () -> Unit = {},
) {
    val userPrefs = LocalUserPreferences.current
    val navigator = LocalDestinationsNavigator.current
    val context = LocalContext.current
    val windowWidth = LocalWindowSizeClass.current.widthSizeClass
    val updates by rememberUpdatableModuleCount()
    var moreDestinationsOpen by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val bulkInstallViewModel: BulkInstallViewModel = hiltViewModel()
    val activityViewModel: ActivityViewModel = hiltViewModel()
    val pendingReboots by activityViewModel.pendingRebootCount.collectAsStateWithLifecycle()

    LaunchedEffect(openActivityOnLaunch) {
        if (openActivityOnLaunch) {
            navigator.navigate(ActivityScreenDestination) {
                launchSingleTop = true
            }
            onActivityOpened()
        }
    }

    LaunchedEffect(openUpdatesOnLaunch) {
        if (openUpdatesOnLaunch) {
            navigator.navigate(ModuleUpdatesScreenDestination) {
                launchSingleTop = true
            }
            onUpdatesOpened()
        }
    }

    LaunchedEffect(openRecoveryOnLaunch) {
        if (openRecoveryOnLaunch) {
            navigator.navigate(AshScreenDestination) {
                launchSingleTop = true
            }
            onRecoveryOpened()
        }
    }

    LaunchedEffect(Unit) {
        initPlatform(context, userPrefs.workingMode.toPlatform())
    }

    val hazeState = rememberHazeState()

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalSnackbarHost provides snackbarHostState,
        LocalBulkInstall provides bulkInstallViewModel,
    ) {
        if (moreDestinationsOpen) {
            MoreDestinationsSheet(
                updates = updates,
                pendingReboots = pendingReboots,
                onDismiss = { moreDestinationsOpen = false },
            )
        }

        when (windowWidth) {
            WindowWidthSizeClass.Compact -> {
                ResponsiveScaffold(
                    bottomBar = {
                        BottomNav(
                            updates = updates,
                            pendingReboots = pendingReboots,
                            onMoreClick = { moreDestinationsOpen = true },
                        )
                    },
                    contentWindowInsets = WindowInsets.none,
                ) { paddingValues ->
                    CompositionLocalProvider(
                        LocalMainScreenInnerPaddings provides paddingValues,
                    ) {
                        CurrentNavHost(
                            modifier = Modifier.hazeSource(hazeState),
                        )
                    }
                }
            }

            WindowWidthSizeClass.Medium -> {
                ResponsiveScaffold(
                    railBar = {
                        RailNav(updates = updates, pendingReboots = pendingReboots)
                    },
                    contentWindowInsets = WindowInsets.none,
                ) { paddingValues ->
                    CompositionLocalProvider(
                        LocalMainScreenInnerPaddings provides paddingValues,
                    ) {
                        CurrentNavHost(
                            modifier = Modifier.hazeSource(hazeState),
                        )
                    }
                }
            }

            else -> {
                ExpandedMainLayout(
                    updates = updates,
                    pendingReboots = pendingReboots,
                    contentModifier = Modifier.hazeSource(hazeState),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedMainLayout(
    updates: Int,
    pendingReboots: Int,
    contentModifier: Modifier,
) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(
                modifier = Modifier.width(280.dp),
            ) {
                TopAppBar(
                    navigationIcon = {
                        HomeNavigationButton()
                    },
                    title = {
                        Text(
                            text = stringResource(R.string.navigation_home_button_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                )
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = expandedDestinations,
                        key = { it.name },
                    ) { screen ->
                        DrawerDestinationItem(
                            screen = screen,
                            updates = updates,
                            pendingReboots = pendingReboots,
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(contentWindowInsets = WindowInsets.none) { paddingValues ->
            CompositionLocalProvider(
                LocalMainScreenInnerPaddings provides paddingValues,
            ) {
                CurrentNavHost(modifier = contentModifier)
            }
        }
    }
}

@Composable
private fun CurrentNavHost(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val reducedMotion = LocalReducedMotion.current
    DestinationsNavHost(
        modifier = modifier,
        navGraph = NavGraphs.root,
        navController = navController,
        defaultTransitions =
            object : NavHostAnimatedDestinationStyle() {
                override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
                    get() = { if (reducedMotion) EnterTransition.None else fadeIn(animationSpec = tween(340)) }
                override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition
                    get() = { if (reducedMotion) ExitTransition.None else fadeOut(animationSpec = tween(340)) }
            },
    )
}

@Composable
private fun BottomNav(
    updates: Int,
    pendingReboots: Int,
    onMoreClick: () -> Unit,
) {
    val prefs = LocalUserPreferences.current
    val navigator = LocalDestinationsNavigator.current
    val navController = LocalNavController.current
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val superUserSelected by navController.isRouteOnBackStackAsState(MainDestination.SuperUser.direction)
    val settingsSelected by navController.isRouteOnBackStackAsState(MainDestination.Settings.direction)
    val moreSelected = superUserSelected || settingsSelected

    BlurBottomToolbar(
        modifier = Modifier.imePadding(),
    ) {
        compactDestinations.forEach { screen ->
            val isSelected by navController.isRouteOnBackStackAsState(screen.direction)
            if (!screen.isAccessible) return@forEach

            NavigationBarItem(
                icon = {
                    BaseNavIcon(screen, isSelected, updates, pendingReboots)
                },
                label = {
                    Text(
                        text = stringResource(id = screen.label),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                alwaysShowLabel = !prefs.hideBottomBarLabels && !largeText,
                selected = isSelected,
                onClick = {
                    navigator.navigateMainDestination(screen, isSelected)
                },
            )
        }

        NavigationBarItem(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.dots_vertical),
                    contentDescription = stringResource(R.string.page_more),
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.page_more),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            alwaysShowLabel = !prefs.hideBottomBarLabels && !largeText,
            selected = moreSelected,
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun RailNav(
    updates: Int,
    pendingReboots: Int,
) {
    val prefs = LocalUserPreferences.current
    val navigator = LocalDestinationsNavigator.current
    val navController = LocalNavController.current

    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        header = {
            HomeNavigationButton()
        },
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = expandedDestinations,
                key = { it.name },
            ) { screen ->
                val isSelected by navController.isRouteOnBackStackAsState(screen.direction)
                if (!screen.isAccessible) return@items

                NavigationRailItem(
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        BaseNavIcon(screen, isSelected, updates, pendingReboots)
                    },
                    label = {
                        Text(
                            text = stringResource(id = screen.label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    alwaysShowLabel = !prefs.hideBottomBarLabels,
                    selected = isSelected,
                    onClick = {
                        navigator.navigateMainDestination(screen, isSelected)
                    },
                )
            }
        }
    }
}

@Composable
private fun DrawerDestinationItem(
    screen: MainDestination,
    updates: Int,
    pendingReboots: Int,
    onNavigate: (() -> Unit)? = null,
) {
    val navigator = LocalDestinationsNavigator.current
    val navController = LocalNavController.current
    val isSelected by navController.isRouteOnBackStackAsState(screen.direction)
    if (!screen.isAccessible) return

    NavigationDrawerItem(
        icon = {
            BaseNavIcon(screen, isSelected, updates, pendingReboots)
        },
        label = {
            Text(
                text = stringResource(id = screen.label),
                style = MaterialTheme.typography.labelLarge,
            )
        },
        selected = isSelected,
        onClick = {
            onNavigate?.invoke()
            navigator.navigateMainDestination(screen, isSelected)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreDestinationsSheet(
    updates: Int,
    pendingReboots: Int,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.page_more),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            overflowDestinations.forEach { screen ->
                DrawerDestinationItem(
                    screen = screen,
                    updates = updates,
                    pendingReboots = pendingReboots,
                    onNavigate = onDismiss,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun DestinationsNavigator.navigateMainDestination(
    screen: MainDestination,
    isSelected: Boolean,
) {
    if (isSelected) {
        popBackStack(screen.direction, false)
    }
    navigate(screen.direction) {
        popUpTo(NavGraphs.root) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BaseNavIcon(
    screen: MainDestination,
    selected: Boolean,
    updates: Int,
    pendingReboots: Int,
) {
    if (screen == MainDestination.Activity && pendingReboots > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(text = pendingReboots.toString())
                }
            },
        ) {
            Icon(
                painter = painterResource(id = if (selected) screen.iconFilled else screen.icon),
                contentDescription = stringResource(screen.label),
            )
        }
        return
    }

    if (screen == MainDestination.Modules && updates > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(text = updates.toString())
                }
            },
        ) {
            Icon(
                painter =
                    painterResource(
                        id =
                            if (selected) {
                                screen.iconFilled
                            } else {
                                screen.icon
                            },
                    ),
                contentDescription = stringResource(screen.label),
            )
        }

        return
    }

    Icon(
        painter =
            painterResource(
                id =
                    if (selected) {
                        screen.iconFilled
                    } else {
                        screen.icon
                    },
            ),
        contentDescription = stringResource(screen.label),
    )
}
