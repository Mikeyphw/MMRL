package com.dergoogler.mmrl.ui.screens.repositories

import android.widget.Toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.datastore.model.RepositoriesMenu
import com.dergoogler.mmrl.ext.isScrollingUp
import com.dergoogler.mmrl.ext.none
import com.dergoogler.mmrl.ext.rememberSaveableLazyListState
import com.dergoogler.mmrl.ext.systemBarsPaddingEnd
import com.dergoogler.mmrl.github.GitHubSourceMode
import com.dergoogler.mmrl.github.GitHubTokenStore
import com.dergoogler.mmrl.model.local.BulkModule
import com.dergoogler.mmrl.repository.normalizeRepositoryUrlInput
import com.dergoogler.mmrl.ui.activity.terminal.install.InstallActivity
import com.dergoogler.mmrl.ui.animate.slideInTopToBottom
import com.dergoogler.mmrl.ui.animate.slideOutBottomToTop
import com.dergoogler.mmrl.ui.component.Loading
import com.dergoogler.mmrl.ui.component.LocalScreenProvider
import com.dergoogler.mmrl.ui.component.PageIndicator
import com.dergoogler.mmrl.ui.component.dialog.TextFieldDialog
import com.dergoogler.mmrl.ui.component.scaffold.Scaffold
import com.dergoogler.mmrl.ui.component.HomeNavigationButton
import com.dergoogler.mmrl.ui.component.toolbar.BlurToolbar
import com.dergoogler.mmrl.ui.component.toolbar.ToolbarTitle
import com.dergoogler.mmrl.ui.providable.LocalBulkInstall
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.dergoogler.mmrl.ui.providable.LocalMainScreenInnerPaddings
import com.dergoogler.mmrl.ui.providable.mainContentBottomPadding
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.ui.screens.repositories.items.BulkBottomSheet
import com.dergoogler.mmrl.ui.screens.lsposed.LsposedRepositoryTab
import com.dergoogler.mmrl.viewmodel.BulkDownloadException
import com.dergoogler.mmrl.viewmodel.RepositoriesViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SearchScreenDestination
import timber.log.Timber
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.reflect.KFunction1

@Destination<RootGraph>
@Composable
fun RepositoriesScreen() =
    LocalScreenProvider {
        val viewModel = hiltViewModel<RepositoriesViewModel>()

        val userPrefs = LocalUserPreferences.current
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        val list by viewModel.repos.collectAsStateWithLifecycle()
        val bulkInstallViewModel = LocalBulkInstall.current
        val bulkModules by bulkInstallViewModel.bulkModules.collectAsStateWithLifecycle()

        val state by viewModel.screenState.collectAsStateWithLifecycle()
        val progress by viewModel.progress.collectAsStateWithLifecycle()

        val pullToRefreshState = rememberPullToRefreshState()
        
        // Use rememberSaveable to persist scroll position across navigation
        val listState = rememberSaveableLazyListState(key = "repositories_list")

        val showFab by listState.isScrollingUp()

        var repoUrl by remember { mutableStateOf("") }
        var message: String by remember { mutableStateOf("") }

        var failure by remember { mutableStateOf(false) }
        if (failure) {
            FailureDialog(
                name = repoUrl,
                message = message,
                onClose = {
                    failure = false
                    message = ""
                },
            )
        }

        var add by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(RepositoryTab.RootModules) }
        if (add) {
            when (selectedTab) {
                RepositoryTab.RootModules -> {
                    AddDialog(
                        onClose = { add = false },
                        onAdd = {
                            repoUrl = it
                            viewModel.insert(it) { e ->
                                failure = true
                                message = e.stackTraceToString()
                            }
                        },
                    )
                }
                RepositoryTab.GitHubSources -> {
                    GitHubSourceAddDialog(
                        onClose = { add = false },
                        onAdd = {
                            repoUrl = it
                            viewModel.insert(it) { e ->
                                failure = true
                                message = e.stackTraceToString()
                            }
                        },
                    )
                }
                RepositoryTab.LSPosed -> add = false
            }
        }

        val context = LocalContext.current
        var bulkInstallBottomSheet by remember { mutableStateOf(false) }

        val bulkDownload: (List<BulkModule>, Boolean) -> Unit = { item, install ->
            bulkInstallViewModel.downloadMultiple(
                items = item,
                onAllSuccess = {
                    bulkInstallViewModel.clearBulkModules()
                    bulkInstallBottomSheet = false
                    if (install) {
                        InstallActivity.start(
                            context = context,
                            uri = it,
                        )
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.message_download_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onFailure = { error ->
                    Timber.e(error)
                    if (error is BulkDownloadException) {
                        val successfulIds = error.successes.map { it.module.id }.toSet()
                        bulkInstallViewModel.removeBulkModules(successfulIds)
                        if (install && error.successes.isNotEmpty()) {
                            bulkInstallBottomSheet = false
                            InstallActivity.start(context = context, uri = error.successes.map { it.uri })
                        }
                    }
                    Toast.makeText(
                        context,
                        error.message ?: context.getString(R.string.unknown_error),
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }

        if (bulkInstallBottomSheet) {
            BulkBottomSheet(
                onClose = {
                    bulkInstallBottomSheet = false
                },
                modules = bulkModules,
                onDownload = bulkDownload,
                bulkInstallViewModel = bulkInstallViewModel,
            )
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopBar(
                    setMenu = viewModel::setRepositoriesMenu,
                    onAdd = { add = true },
                    canAdd = selectedTab != RepositoryTab.LSPosed,
                    scrollBehavior = scrollBehavior,
                )
            },
            contentWindowInsets = WindowInsets.none,
            floatingActionButton = {
                AnimatedVisibility(
                    visible = showFab && selectedTab != RepositoryTab.LSPosed,
                    enter =
                        scaleIn(
                            animationSpec = tween(100),
                            initialScale = 0.8f,
                        ),
                    exit =
                        scaleOut(
                            animationSpec = tween(100),
                            targetScale = 0.8f,
                        ),
                ) {
                    FloatingButton(
                        onClick = {
                            bulkInstallBottomSheet = true
                        },
                    )
                }
            },
        ) { innerPadding ->

            if (viewModel.isLoading) {
                Loading()
            }

            if (selectedTab != RepositoryTab.LSPosed && list.isEmpty() && !viewModel.isLoading) {
                PageIndicator(
                    icon = R.drawable.git_pull_request,
                    text = R.string.repo_empty,
                )
            }

            if (selectedTab == RepositoryTab.LSPosed) {
                Column {
                    RepositoryTabs(
                        selectedTab = selectedTab,
                        onSelected = { selectedTab = it },
                        topPadding = innerPadding.calculateTopPadding(),
                    )
                    LsposedRepositoryTab(
                        innerPadding = innerPadding,
                        contentTopPadding = 0.dp,
                    )
                }
            } else {
                PullToRefreshBox(
                    state = pullToRefreshState,
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::getRepoAll,
                    indicator = {
                        Indicator(
                            modifier =
                                Modifier.align(Alignment.TopCenter).let {
                                    if (!userPrefs.enableBlur) {
                                        it.padding(top = innerPadding.calculateTopPadding())
                                    } else {
                                        it
                                    }
                                },
                            isRefreshing = state.isRefreshing,
                            state = pullToRefreshState,
                        )
                    },
                ) {
                    val visibleList =
                        when (selectedTab) {
                            RepositoryTab.RootModules -> list.filterNot { it.url.isGitHubSourceUrl() }
                            RepositoryTab.GitHubSources -> list.filter { it.url.isGitHubSourceUrl() }
                            RepositoryTab.LSPosed -> emptyList()
                        }
                    Column {
                        RepositoryTabs(
                            selectedTab = selectedTab,
                            onSelected = { selectedTab = it },
                            topPadding = innerPadding.calculateTopPadding(),
                        )
                        this@Scaffold.RepositoriesList(
                            innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                            list = visibleList,
                            state = listState,
                            delete = viewModel::delete,
                            getUpdate = viewModel::getUpdate,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = progress,
                enter = slideInTopToBottom(),
                exit = slideOutBottomToTop(),
            ) {
                LinearProgressIndicator(
                    modifier =
                        Modifier.fillMaxWidth().let {
                            if (!userPrefs.enableBlur) {
                                it.padding(top = innerPadding.calculateTopPadding())
                            } else {
                                it
                            }
                        },
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }

private enum class RepositoryTab {
    RootModules,
    GitHubSources,
    LSPosed,
}

@Composable
private fun RepositoryTabs(
    selectedTab: RepositoryTab,
    onSelected: (RepositoryTab) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.padding(top = topPadding),
    ) {
        Tab(
            selected = selectedTab == RepositoryTab.RootModules,
            onClick = { onSelected(RepositoryTab.RootModules) },
            text = { Text(stringResource(R.string.repository_tab_root_modules)) },
        )
        Tab(
            selected = selectedTab == RepositoryTab.GitHubSources,
            onClick = { onSelected(RepositoryTab.GitHubSources) },
            text = { Text(stringResource(R.string.repository_tab_github_sources)) },
        )
        Tab(
            selected = selectedTab == RepositoryTab.LSPosed,
            onClick = { onSelected(RepositoryTab.LSPosed) },
            text = { Text(stringResource(R.string.repository_tab_lsposed)) },
        )
    }
}

@Composable
private fun AddDialog(
    onClose: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var repositoryUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val onDone: () -> Unit = {
        runCatching { normalizeRepositoryUrlInput(repositoryUrl) }
            .onSuccess { normalizedUrl ->
                onAdd(normalizedUrl)
                onClose()
            }.onFailure {
                error = it.message ?: "Invalid repository URL"
            }
    }

    TextFieldDialog(
        onDismissRequest = onClose,
        title = { Text(text = stringResource(id = R.string.repo_add_dialog_title)) },
        confirmButton = {
            TextButton(
                onClick = onDone,
                enabled = repositoryUrl.isNotBlank(),
            ) {
                Text(text = stringResource(id = R.string.repo_add_dialog_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onClose,
            ) {
                Text(text = stringResource(id = R.string.dialog_cancel))
            }
        },
    ) { focusRequester ->
        OutlinedTextField(
            modifier = Modifier.focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge,
            value = repositoryUrl,
            onValueChange = {
                repositoryUrl = it
                error = null
            },
            label = { Text(text = stringResource(id = R.string.repo_add_dialog_url_label)) },
            placeholder = { Text(text = stringResource(id = R.string.repo_add_dialog_url_placeholder)) },
            singleLine = true,
            isError = error != null,
            supportingText =
                if (error == null) {
                    null
                } else {
                    { Text(text = error.orEmpty()) }
                },
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions {
                    if (repositoryUrl.isNotBlank()) onDone()
                },
            shape = RoundedCornerShape(15.dp),
        )
    }
}

@Composable
private fun GitHubSourceAddDialog(
    onClose: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val context = LocalContext.current
    val tokenStore = remember { GitHubTokenStore(context) }
    var repoUrl by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(GitHubSourceMode.RELEASE) }
    var includePreReleases by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(tokenStore.hasToken()) }
    var error by remember { mutableStateOf<String?>(null) }

    val add: () -> Unit = {
        runCatching {
            buildGitHubSourceUrl(repoUrl, mode, includePreReleases, regex)
        }.onSuccess { sourceUrl ->
            if (token.isNotBlank()) {
                tokenStore.saveToken(token)
                hasToken = tokenStore.hasToken()
            }
            onAdd(sourceUrl)
            onClose()
        }.onFailure {
            error = it.message ?: context.getString(R.string.github_source_error_invalid_url)
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.github_source_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = repoUrl,
                    onValueChange = {
                        repoUrl = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.github_source_repository_url)) },
                    placeholder = { Text(stringResource(R.string.github_source_repository_placeholder)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Uri,
                        ),
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == GitHubSourceMode.RELEASE,
                        onClick = { mode = GitHubSourceMode.RELEASE },
                        label = { Text(stringResource(R.string.github_source_mode_release)) },
                    )
                    FilterChip(
                        selected = mode == GitHubSourceMode.NIGHTLY,
                        onClick = { mode = GitHubSourceMode.NIGHTLY },
                        label = { Text(stringResource(R.string.github_source_mode_nightly)) },
                    )
                    FilterChip(
                        selected = mode == GitHubSourceMode.NIGHTLY_LINK,
                        onClick = { mode = GitHubSourceMode.NIGHTLY_LINK },
                        label = { Text(stringResource(R.string.github_source_mode_nightly_link)) },
                    )
                }
                if (mode == GitHubSourceMode.RELEASE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.github_source_include_prereleases))
                        Switch(
                            checked = includePreReleases,
                            onCheckedChange = { includePreReleases = it },
                        )
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = regex,
                    onValueChange = { regex = it },
                    label = { Text(stringResource(R.string.github_source_file_regex)) },
                    placeholder = { Text(stringResource(R.string.github_source_file_regex_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(if (hasToken) R.string.github_source_token_saved else R.string.github_source_token)) },
                    placeholder = { Text(stringResource(if (mode == GitHubSourceMode.NIGHTLY_LINK) R.string.github_source_token_placeholder_nightly_link else R.string.github_source_token_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
                if (hasToken) {
                    TextButton(
                        onClick = {
                            tokenStore.clearToken()
                            hasToken = false
                            token = ""
                        },
                    ) {
                        Text(stringResource(R.string.github_source_clear_token))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = add,
                enabled = repoUrl.isNotBlank(),
            ) {
                Text(stringResource(R.string.github_source_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(text = stringResource(id = R.string.dialog_cancel))
            }
        },
    )
}

@Composable
private fun TopBar(
    onAdd: () -> Unit,
    canAdd: Boolean,
    setMenu: KFunction1<RepositoriesMenu, Unit>,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val navigator = LocalDestinationsNavigator.current

    BlurToolbar(
        scrollBehavior = scrollBehavior,
        title = {
            ToolbarTitle(title = stringResource(R.string.page_repos))
        },
        navigationIcon = {
            HomeNavigationButton()
        },
        fadeDistance = 50f,
        fade = true,
        actions = {
            IconButton(
                onClick = {
                    navigator.navigate(SearchScreenDestination)
                },
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.search),
                    contentDescription = stringResource(R.string.accessibility_search),
                )
            }
            if (canAdd) {
                IconButton(
                    onClick = onAdd,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.plus),
                        contentDescription = stringResource(R.string.accessibility_add_repository),
                    )
                }
            }

            RepositoriesMenu(
                setMenu = setMenu,
            )
        },
    )
}

@Composable
private fun FloatingButton(onClick: () -> Unit) {
    val paddingValues = LocalMainScreenInnerPaddings.current

    SmallFloatingActionButton(
        modifier =
            Modifier
                .systemBarsPaddingEnd()
                .padding(
                    bottom = paddingValues.mainContentBottomPadding(16.dp),
                ),
        onClick = onClick,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.primary,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.package_import),
            contentDescription = stringResource(R.string.bulk_module_install),
        )
    }
}


private fun GitHubSourceMode.queryValue(): String =
    when (this) {
        GitHubSourceMode.RELEASE -> "release"
        GitHubSourceMode.NIGHTLY -> "nightly"
        GitHubSourceMode.NIGHTLY_LINK -> "nightlyLink"
    }

private fun String.isGitHubSourceUrl(): Boolean =
    runCatching {
        val uri = URI(this)
        uri.host.equals("github.com", ignoreCase = true) &&
            uri.rawQuery.orEmpty().contains("mmrlSource=")
    }.getOrDefault(false)

private fun buildGitHubSourceUrl(
    rawUrl: String,
    mode: GitHubSourceMode,
    includePreReleases: Boolean,
    regex: String,
): String {
    val uri = URI(rawUrl.trim().trimEnd('/').removeSuffix(".git"))
    val parts = uri.path.trim('/').split('/').filter(String::isNotBlank)
    val source =
        when {
            uri.host.equals("github.com", ignoreCase = true) -> {
                require(parts.size >= 2) { "GitHub URL must include owner and repository" }
                GitHubSourceInput(parts[0], parts[1], mode, null)
            }
            uri.host.equals("nightly.link", ignoreCase = true) -> {
                require(parts.size >= 2) { "nightly.link URL must include owner and repository" }
                val artifactName = parts.lastOrNull()?.removeSuffix(".zip")?.takeIf(String::isNotBlank)
                GitHubSourceInput(parts[0], parts[1], GitHubSourceMode.NIGHTLY_LINK, artifactName)
            }
            else -> error("Only github.com repositories and nightly.link artifact URLs are supported")
        }
    val effectiveRegex =
        regex.trim().takeIf(String::isNotBlank)
            ?: source.artifactName?.let { Regex.escape(it) }
    val query =
        buildList {
            add("mmrlSource=${source.mode.queryValue()}")
            if (source.mode == GitHubSourceMode.RELEASE && includePreReleases) add("includePreReleases=true")
            effectiveRegex?.let {
                add("regex=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}")
            }
        }.joinToString("&")
    return "https://github.com/${source.owner}/${source.repository}?$query"
}

private data class GitHubSourceInput(
    val owner: String,
    val repository: String,
    val mode: GitHubSourceMode,
    val artifactName: String?,
)
