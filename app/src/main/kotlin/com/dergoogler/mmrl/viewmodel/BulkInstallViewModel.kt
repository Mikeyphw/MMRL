package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.local.BulkModule
import com.dergoogler.mmrl.installer.DependencyPlanPolicy
import com.dergoogler.mmrl.installer.BulkDownloadCompletionPolicy
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.repository.LocalRepository
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.service.DownloadService
import com.dergoogler.mmrl.service.DownloadReceiptStore
import com.dergoogler.mmrl.utils.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class BulkInstallViewModel
    @Inject
    constructor(
        application: Application,
        localRepository: LocalRepository,
        modulesRepository: ModulesRepository,
        userPreferencesRepository: UserPreferencesRepository,
        private val downloadReceiptStore: DownloadReceiptStore,
    ) : MMRLViewModel(application, localRepository, modulesRepository, userPreferencesRepository) {
        private val bulkModulesFlow = MutableStateFlow(listOf<BulkModule>())
        val bulkModules get() = bulkModulesFlow.asStateFlow()

        private val downloadingFlow = MutableStateFlow(false)
        val isDownloading get() = downloadingFlow.asStateFlow()
        private val activeOperations = ConcurrentHashMap<String, String>()

        fun addBulkModule(
            module: BulkModule,
            onSuccess: () -> Unit,
            onFailure: (error: String) -> Unit,
        ) {
            val currentModules = bulkModulesFlow.value
            if (currentModules.contains(module)) {
                onFailure(context.getString(R.string.bulk_install_module_already_added))
            } else {
                bulkModulesFlow.value = currentModules + module
                onSuccess()
            }
        }

        fun removeBulkModule(module: BulkModule) {
            bulkModulesFlow.value = bulkModulesFlow.value - module
        }

        fun clearBulkModules() {
            bulkModulesFlow.value = listOf()
        }

        fun removeBulkModules(ids: Set<String>) {
            bulkModulesFlow.value = bulkModulesFlow.value.filterNot { it.id in ids }
        }

        fun cancelDownloads() {
            activeOperations.values.toSet().forEach { DownloadService.cancel(context, it) }
        }

        fun downloadMultiple(
            items: List<BulkModule>,
            onAllSuccess: (List<BulkDownloadSuccess>) -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            if (items.isEmpty() || downloadingFlow.value) return

            viewModelScope.launch {
                downloadingFlow.value = true
                try {
                    val plannedItems = resolveInstallPlan(items)
                    val results = coroutineScope {
                        plannedItems.map { bulkModule ->
                            async {
                                val result = runCatching {
                                    val item = bulkModule.versionItem
                                    val filename = Utils.getFilename(
                                        name = bulkModule.name,
                                        version = item.version,
                                        versionCode = item.versionCode,
                                        extension = "zip",
                                    )
                                    val task = DownloadService.TaskItem(
                                        key = downloadKey(bulkModule),
                                        url = item.zipUrl,
                                        filename = filename,
                                        title = bulkModule.name,
                                        desc = item.versionDisplay,
                                    )
                                    val deferred = CompletableDeferred<BulkDownloadedArtifact>()
                                    val listener = object : DownloadService.IDownloadListener {
                                        override fun onStarted(operationId: String) {
                                            activeOperations[bulkModule.id] = operationId
                                        }

                                        override fun onSuccess(uri: Uri) {
                                            activeOperations.remove(bulkModule.id)
                                            val provenance = downloadReceiptStore.load(uri)
                                            if (provenance == null || provenance.sourceUrl != item.zipUrl) {
                                                deferred.completeExceptionally(
                                                    IllegalStateException("Downloaded artifact provenance verification failed"),
                                                )
                                            } else {
                                                deferred.complete(
                                                    BulkDownloadedArtifact(
                                                        uri = uri,
                                                        sha256 = provenance.sha256,
                                                        size = provenance.size,
                                                        sourceUrl = provenance.sourceUrl,
                                                    ),
                                                )
                                            }
                                        }

                                        override fun onFailure(e: Throwable) {
                                            activeOperations.remove(bulkModule.id)
                                            deferred.completeExceptionally(e)
                                        }
                                    }
                                    DownloadService.start(context, task, listener)
                                    deferred.await()
                                }.mapError { error ->
                                    IllegalStateException(
                                        "${bulkModule.name}: ${error.message ?: error.javaClass.simpleName}",
                                        error,
                                    )
                                }
                                bulkModule to result
                            }
                        }.awaitAll()
                    }

                    val successes = results.mapNotNull { (module, result) ->
                        result.getOrNull()?.let { artifact ->
                            BulkDownloadSuccess(
                                module = module,
                                uri = artifact.uri,
                                sha256 = artifact.sha256,
                                size = artifact.size,
                                sourceUrl = artifact.sourceUrl,
                            )
                        }
                    }
                    val failures = results.mapNotNull { (module, result) ->
                        result.exceptionOrNull()?.let { BulkDownloadFailure(module, it) }
                    }

                    if (BulkDownloadCompletionPolicy.mayInstall(successes.size, failures.size, plannedItems.size)) {
                        onAllSuccess(successes)
                    } else {
                        failures.forEach { Timber.d(it.error) }
                        onFailure(BulkDownloadException(successes, failures))
                    }
                } finally {
                    activeOperations.clear()
                    downloadingFlow.value = false
                }
            }
        }

        private suspend fun resolveInstallPlan(requested: List<BulkModule>): List<BulkModule> {
            val rootVersion = PlatformManager.get(-1) { moduleManager.versionCode }
            require(rootVersion >= 0) { "Root manager is unavailable for dependency preflight" }
            val installed = localRepository.getLocalAll().map { ModuleIdentity.canonical(it.id) }.toSet()
            val requestedById = requested.associateBy { ModuleIdentity.canonical(it.id) }
            require(requestedById.size == requested.size) { "Duplicate module identity in install batch" }
            val selected = linkedMapOf<String, BulkModule>()
            val nodes = linkedMapOf<String, DependencyPlanPolicy.Node>()
            val visiting = linkedSetOf<String>()

            suspend fun resolve(rawId: String) {
                val id = ModuleIdentity.canonical(rawId)
                if (id in nodes) return
                require(id !in visiting) { "Dependency cycle encountered while resolving $id" }
                visiting += id
                try {
                    val requestedItem = requestedById[id]
                    val candidates = localRepository.getOnlineAllById(rawId).ifEmpty {
                        if (rawId != id) localRepository.getOnlineAllById(id) else emptyList()
                    }
                    val compatible = candidates.filter { module ->
                        module.manager(platform).isCompatible(rootVersion) &&
                            (module.minApi == null || Build.VERSION.SDK_INT >= module.minApi) &&
                            (module.maxApi == null || Build.VERSION.SDK_INT <= module.maxApi)
                    }
                    val chosen = requestedItem?.let { root ->
                        compatible.firstOrNull { module ->
                            module.versions.any { version ->
                                version.zipUrl == root.versionItem.zipUrl && version.versionCode == root.versionItem.versionCode
                            }
                        }
                    } ?: compatible.maxByOrNull { it.versionCode }
                    requireNotNull(chosen) { "Missing or incompatible required module: $id" }

                    val alreadyInstalled = requestedItem == null && id in installed
                    val item = requestedItem?.versionItem ?: if (!alreadyInstalled) {
                        chosen.versions.filter { it.zipUrl.isNotBlank() }.maxByOrNull { it.versionCode }
                            ?: error("No downloadable compatible version for dependency: $id")
                    } else null
                    if (item != null) selected[id] = requestedItem ?: BulkModule(chosen.id, chosen.name, item)

                    val deps = chosen.manager(platform).require.orEmpty().map(ModuleIdentity::canonical).toSortedSet()
                    nodes[id] = DependencyPlanPolicy.Node(
                        id = id,
                        dependencies = deps,
                        compatible = true,
                        alreadyInstalled = alreadyInstalled,
                    )
                    deps.forEach { dependency -> resolve(dependency) }
                } finally {
                    visiting -= id
                }
            }

            requested.forEach { resolve(it.id) }
            val roots = requested.map { ModuleIdentity.canonical(it.id) }
            val plan = DependencyPlanPolicy.plan(roots, nodes)
            return plan.orderedIds.map { id ->
                selected[id] ?: error("Dependency planner lost downloadable artifact for $id")
            }
        }

        @Composable
        fun getProgress(module: BulkModule): Float {
            val progress by DownloadService
                .getProgressByKey(downloadKey(module))
                .collectAsStateWithLifecycle(initialValue = 0f)

            return progress
        }
    }

private fun downloadKey(module: BulkModule): Int =
    31 * module.versionItem.hashCode() + module.id.hashCode()

private inline fun <T> Result<T>.mapError(transform: (Throwable) -> Throwable): Result<T> =
    fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(transform(it)) })

private data class BulkDownloadedArtifact(
    val uri: Uri,
    val sha256: String,
    val size: Long,
    val sourceUrl: String?,
)

data class BulkDownloadSuccess(
    val module: BulkModule,
    val uri: Uri,
    val sha256: String,
    val size: Long,
    val sourceUrl: String?,
)

data class BulkDownloadFailure(
    val module: BulkModule,
    val error: Throwable,
)

class BulkDownloadException(
    val successes: List<BulkDownloadSuccess>,
    val failures: List<BulkDownloadFailure>,
) : IllegalStateException(
    buildString {
        append(failures.size)
        append(" batch download(s) failed")
        if (successes.isNotEmpty()) append("; ${successes.size} completed")
        failures.take(3).forEach { append("\n• ${it.module.name}: ${it.error.message}") }
    },
)
