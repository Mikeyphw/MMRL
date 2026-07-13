package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.model.local.BulkModule
import com.dergoogler.mmrl.repository.LocalRepository
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.service.DownloadService
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
import java.io.File
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
            onAllSuccess: (List<Uri>) -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            if (items.isEmpty() || downloadingFlow.value) return

            viewModelScope.launch {
                downloadingFlow.value = true
                val downloadPath = File(userPreferencesRepository.data.first().downloadPath)

                try {
                    val results = coroutineScope {
                        items.map { bulkModule ->
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
                                    val deferred = CompletableDeferred<Uri>()
                                    val listener = object : DownloadService.IDownloadListener {
                                        override fun onStarted(operationId: String) {
                                            activeOperations[bulkModule.id] = operationId
                                        }

                                        override fun onSuccess() {
                                            activeOperations.remove(bulkModule.id)
                                            deferred.complete(downloadPath.resolve(filename).toUri())
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
                        result.getOrNull()?.let { BulkDownloadSuccess(module, it) }
                    }
                    val failures = results.mapNotNull { (module, result) ->
                        result.exceptionOrNull()?.let { BulkDownloadFailure(module, it) }
                    }

                    if (failures.isEmpty()) {
                        onAllSuccess(successes.map { it.uri })
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

data class BulkDownloadSuccess(
    val module: BulkModule,
    val uri: Uri,
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
