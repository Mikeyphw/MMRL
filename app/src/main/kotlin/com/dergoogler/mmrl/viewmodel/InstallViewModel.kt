package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.BuildConfig
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.app.Const.CLEAR_CMD
import com.dergoogler.mmrl.app.Event
import com.dergoogler.mmrl.compat.MediaStoreCompat.copyToDir
import com.dergoogler.mmrl.compat.MediaStoreCompat.getPathForUri
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.ext.nullable
import com.dergoogler.mmrl.installer.ArchiveInspector
import com.dergoogler.mmrl.installer.UpdateRollbackStore
import com.dergoogler.mmrl.ext.tmpDir
import com.dergoogler.mmrl.ext.toFormattedDateSafely
import com.dergoogler.mmrl.model.local.LocalModule
import com.dergoogler.mmrl.model.online.Blacklist
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.content.BulkModule
import com.dergoogler.mmrl.platform.file.SuFile
import com.dergoogler.mmrl.platform.file.SuFile.Companion.toFormattedFileSize
import com.dergoogler.mmrl.repository.LocalRepository
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import com.topjohnwu.superuser.CallbackList
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mmrlx.terminal.newSuperUserPty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class InstallViewModel
@Inject
constructor(
    application: Application,
    localRepository: LocalRepository,
    modulesRepository: ModulesRepository,
    userPreferencesRepository: UserPreferencesRepository,
    operationHistoryRepository: OperationHistoryRepository,
    private val updateRollbackStore: UpdateRollbackStore,
) : TerminalViewModel(
        application,
        localRepository,
        modulesRepository,
        userPreferencesRepository,
        operationHistoryRepository,
    ) {
    val logfile get() = "Install_${LocalDateTime.now()}.log"

    init {
        Timber.d("InstallViewModel initialized")
    }

    private val stdoutCallbackList =
        object : CallbackList<String?>() {
            override fun onAddElement(msg: String?) {
                if (msg == null) return

                viewModelScope.launch {
                    log(msg)
                }
            }
        }

    private val stderrCallbackList =
        object : CallbackList<String?>() {
            override fun onAddElement(msg: String?) {
                if (msg == null) return

                viewModelScope.launch {
                    devLog(msg)
                }
            }
        }

    suspend fun installModules(
        uris: List<Uri>,
        parentOperationId: String? = null,
        rollbackMode: Boolean = false,
    ) {
        if (!platformReadyDeferred.await()) {
            val message = context.getString(R.string.platform_initialization_failed_cannot_install)
            uris.forEach { recordRejectedInstall(it, message) }
            log(R.string.platform_initialization_failed_cannot_install)
            event = Event.FAILED
            return
        }

        if (!PlatformManager.isAlive) {
            val message = context.getString(R.string.platform_not_alive_cannot_install)
            uris.forEach { recordRejectedInstall(it, message) }
            log(R.string.platform_not_alive_cannot_install)
            event = Event.FAILED
            return
        }

        val userPreferences = userPreferencesRepository.data.first()
        event = Event.LOADING
        var allSucceeded = true

        val processedModules = mutableListOf<Pair<Uri, BulkModule?>>()
        var blacklistedModuleFound = false

        withContext(Dispatchers.IO) {
            for (uri in uris) {
                val path = context.getPathForUri(uri)
                if (path == null) {
                    withContext(Dispatchers.Main) {
                        devLog(
                            R.string.unable_to_find_path_for_uri,
                            uri.toString(),
                        )
                    }
                    recordRejectedInstall(uri, "Unable to resolve the selected file")
                    processedModules.add(uri to null)
                    allSucceeded = false
                    continue
                }

                if (userPreferences.strictMode && !path.endsWith(".zip")) {
                    withContext(Dispatchers.Main) {
                        log(
                            R.string.is_not_a_module_file_magisk_modules_must_be_zip_files_skipping,
                            path,
                        )
                    }
                    recordRejectedInstall(uri, "The selected file is not a ZIP module")
                    processedModules.add(uri to null)
                    allSucceeded = false
                    continue
                }

                val info = PlatformManager.moduleManager.getModuleInfo(path)
                if (info == null) {
                    withContext(Dispatchers.Main) {
                        devLog(
                            R.string.unable_to_gather_module_info_of_file,
                            path,
                        )
                    }
                    recordRejectedInstall(uri, "Unable to read module metadata")
                    processedModules.add(uri to null)
                    allSucceeded = false
                    continue
                }

                val blacklist = getBlacklistById(info.id.toString())
                if (Blacklist.isBlacklisted(userPreferences.blacklistAlerts, blacklist)) {
                    withContext(Dispatchers.Main) {
                        log(R.string.cannot_install_blacklisted_modules_settings_security_blacklist_alerts)
                        event = Event.FAILED
                    }
                    recordRejectedInstall(
                        uri = uri,
                        summary = "Installation blocked by the module blacklist",
                        moduleId = info.id.id,
                        moduleName = info.name,
                    )
                    allSucceeded = false
                    blacklistedModuleFound = true
                    break
                }
                processedModules.add(uri to BulkModule(id = info.id.toString(), name = info.name))
            }
        }

        if (blacklistedModuleFound) {
            return
        }

        val validBulkModules = processedModules.mapNotNull { it.second }

        for (item in processedModules) {
            val uri = item.first
            val bulkModuleInfo = item.second
            if (bulkModuleInfo == null) {
                continue
            }

            if (userPreferences.clearInstallTerminal && uris.size > 1) {
                log(CLEAR_CMD)
            }

            val result = loadAndInstallModule(uri, validBulkModules, parentOperationId, rollbackMode)
            if (!result) {
                allSucceeded = false
                withContext(Dispatchers.Main) {
                    log(context.getString(R.string.installation_aborted_due_to_an_error))
                }
                break
            }
        }
        event = if (allSucceeded) Event.SUCCEEDED else Event.FAILED
    }


    private suspend fun recordRejectedInstall(
        uri: Uri,
        summary: String,
        moduleId: String? = null,
        moduleName: String? = null,
    ) {
        val historyId =
            operationHistoryRepository.start(
                kind = OperationKind.INSTALL,
                title = moduleName ?: uri.lastPathSegment ?: "Module installation",
                summary = summary,
                moduleId = moduleId,
                moduleName = moduleName,
                sourceUri = uri.toString(),
                retryAction = OperationAction.INSTALL,
            )
        operationHistoryRepository.appendLog(historyId, "Source URI: $uri")
        operationHistoryRepository.fail(historyId, summary)
    }

    private val datePattern = runBlocking { userPreferencesRepository.data.first().datePattern }

    private suspend fun loadAndInstallModule(
        uri: Uri,
        allBulkModulesInBatch: List<BulkModule>,
        parentOperationId: String?,
        rollbackMode: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val path = context.getPathForUri(uri)

            if (path != null) {
                val moduleInfoFromPath = PlatformManager.moduleManager.getModuleInfo(path)
                if (moduleInfoFromPath != null) {
                    withContext<Unit>(Dispatchers.Main) {
                        moduleInfoFromPath.let { mod ->
                            devLog(R.string.install_view_module_info)
                            devLog("ID: ${mod.id.id}")
                            devLog("Name: ${mod.name}")
                            devLog("Version: ${mod.version}")
                            devLog("Version Code: ${mod.versionCode}")
                            devLog("Author: ${mod.author}")
                            devLog("Description: ${mod.description}")
                            devLog("Update JSON: ${mod.updateJson}")
                            devLog("State: ${mod.state}")
                            devLog("Size: ${mod.size.toFormattedFileSize()}")
                            devLog(
                                "Last Updated: ${
                                    mod.lastUpdated.toFormattedDateSafely(
                                        datePattern
                                    )
                                }"
                            )
                            devLog("::endgroup::")
                        }
                    }
                    return@withContext install(path, allBulkModulesInBatch, moduleInfoFromPath, uri.toString(), parentOperationId, rollbackMode)
                }
            }

            withContext(Dispatchers.Main) { log(R.string.copying_zip_to_temp_directory) }
            val tmpFile =
                context.copyToDir(uri, context.tmpDir) ?: run {
                    withContext(Dispatchers.Main) {
                        event = Event.FAILED
                        log(context.getString(R.string.copying_failed))
                    }
                    return@withContext false
                }

            val moduleInfoFromTmp = PlatformManager.moduleManager.getModuleInfo(tmpFile.path)
            if (moduleInfoFromTmp == null) {
                withContext(Dispatchers.Main) {
                    event = Event.FAILED
                    log(R.string.unable_to_gather_module_info)
                }
                tmpFile.delete()
                return@withContext false
            }

            withContext(Dispatchers.Main) {
                devLog(R.string.install_view_module_info, moduleInfoFromTmp.toString())
            }
            return@withContext install(tmpFile.path, allBulkModulesInBatch, moduleInfoFromTmp, uri.toString(), parentOperationId, rollbackMode)
        }

    private suspend fun install(
        zipPath: String,
        allBulkModulesInBatch: List<BulkModule>,
        module: LocalModule? = null,
        sourceUri: String? = null,
        parentOperationId: String? = null,
        rollbackMode: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.Default) {
            val zipFile = File(zipPath)
            val userPreferences = userPreferencesRepository.data.first()
            val previous = module?.let { localRepository.getLocalByIdOrNull(it.id.id) }
            val kind = when {
                rollbackMode -> OperationKind.ROLLBACK
                previous == null -> OperationKind.INSTALL
                else -> OperationKind.UPDATE
            }
            val historyId =
                operationHistoryRepository.start(
                    kind = kind,
                    title = module?.name ?: zipFile.name,
                    summary = module?.version?.let { "Version $it" }.orEmpty(),
                    moduleId = module?.id?.id,
                    moduleName = module?.name,
                    sourceUri = sourceUri,
                    destinationPath = zipPath,
                    retryAction = OperationAction.INSTALL,
                    parentId = parentOperationId,
                )
            activeOperationId = historyId
            operationHistoryRepository.phase(historyId, OperationPhase.REVIEW, "Reviewing module metadata and compatibility")
            operationHistoryRepository.appendLog(historyId, "Archive: $zipPath")
            sourceUri?.let { operationHistoryRepository.appendLog(historyId, "Source URI: $it") }

            operationHistoryRepository.phase(historyId, OperationPhase.VERIFY, "Calculating archive digest")
            operationHistoryRepository.appendLog(historyId, "Archive verification started")
            operationHistoryRepository.phase(historyId, OperationPhase.INSPECT, "Inspecting scripts, binaries, APKs, and policy changes")
            val inspection = runCatching { ArchiveInspector.inspect(zipFile) }.getOrElse { error ->
                val message = error.message ?: "Unable to inspect module archive"
                operationHistoryRepository.fail(historyId, message, error)
                activeOperationId = null
                return@withContext false
            }
            operationHistoryRepository.inspectionSummary(historyId, inspection.summary)
            operationHistoryRepository.appendLog(historyId, "SHA-256: ${inspection.sha256}")
            operationHistoryRepository.appendLog(historyId, "Inspection: ${inspection.summary}")
            inspection.warnings.forEach { operationHistoryRepository.appendLog(historyId, "Warning: $it") }
            inspection.blockedReasons.forEach { operationHistoryRepository.appendLog(historyId, "Blocked: $it") }
            if (!inspection.canInstall) {
                val message = "Archive inspection blocked installation"
                operationHistoryRepository.fail(historyId, message)
                activeOperationId = null
                return@withContext false
            }

            operationHistoryRepository.phase(historyId, OperationPhase.STAGE, "Preparing rollback and staging files")
            val rollbackArchive =
                if (previous != null && !rollbackMode) {
                    updateRollbackStore.create(previous).getOrNull()
                } else {
                    null
                }
            operationHistoryRepository.attachRollbackArchive(
                id = historyId,
                path = rollbackArchive?.absolutePath,
                previousVersion = previous?.version,
                targetVersion = module?.version,
            )
            if (previous != null) {
                operationHistoryRepository.appendLog(
                    historyId,
                    rollbackArchive?.let { "Rollback archive: ${it.absolutePath}" }
                        ?: "Warning: a rollback archive could not be created",
                )
            }

            val installCommand = PlatformManager.moduleManager.getInstallCommand(zipPath)
            if (installCommand.isNullOrBlank()) {
                val message = "Failed to get install command for ${zipFile.name}"
                withContext(Dispatchers.Main) {
                    log("Error: $message")
                }
                operationHistoryRepository.fail(historyId, message)
                activeOperationId = null
                return@withContext false
            }

            val env = mapOf(
                "ASH_STANDALONE" to "1",
                "MMRL" to "true",
                "MMRL_VER" to BuildConfig.VERSION_NAME,
                "MMRL_VER_CODE" to BuildConfig.VERSION_CODE.toString(),
                "BULK_MODULES" to allBulkModulesInBatch.joinToString(" ") { it.id },
            )

            withContext(Dispatchers.Main) {
                log(R.string.install_view_installing, zipFile.name)
            }

            /*if (!shell.isAlive) {
                withContext(Dispatchers.Main) {
                    log("Error: Shell is not alive. Cannot execute installation.")
                }
                return@withContext false
            }*/

            operationHistoryRepository.phase(historyId, OperationPhase.INSTALL, if (rollbackMode) "Restoring previous module version" else "Installing module")
            val emu = emulatorReady.await()
            val result = emu.newSuperUserPty(installCommand, env)
            val success = result.isSuccess
            if (success) {
                module.nullable(::insertLocal)
                operationHistoryRepository.succeed(
                    id = historyId,
                    summary = when (kind) {
                        OperationKind.UPDATE -> "Update installed successfully"
                        OperationKind.ROLLBACK -> "Previous module version restored"
                        else -> "Installed successfully"
                    },
                    requiresReboot = true,
                    rollbackAction = when {
                        rollbackMode -> null
                        rollbackArchive != null -> OperationAction.INSTALL
                        previous == null && module != null -> OperationAction.REMOVE
                        else -> null
                    },
                )

                if (userPreferences.deleteZipFile && !updateRollbackStore.isManagedBackup(zipPath)) {
                    deleteBySu(zipPath)
                }
            } else {
                val message = "Installation failed for ${zipFile.name}"
                withContext(Dispatchers.Main) {
                    log("Error: $message. Exit code: ${-999/*result.code*/}")
                    result.onFailure { devLog("Shell Error: $it") }
                }
                var restored = false
                if (rollbackArchive != null && !rollbackMode) {
                    operationHistoryRepository.phase(historyId, OperationPhase.ROLLBACK, "Update failed; restoring previous version")
                    operationHistoryRepository.appendLog(historyId, "Automatic rollback started")
                    val rollbackCommand = PlatformManager.moduleManager.getInstallCommand(rollbackArchive.absolutePath)
                    restored = !rollbackCommand.isNullOrBlank() && emu.newSuperUserPty(rollbackCommand, env).isSuccess
                    operationHistoryRepository.appendLog(
                        historyId,
                        if (restored) "Automatic rollback completed" else "Automatic rollback failed; manual restore remains available",
                    )
                }
                operationHistoryRepository.fail(
                    id = historyId,
                    summary = if (restored) "Update failed; previous version restored" else message,
                    requiresReboot = restored,
                    rollbackAction = if (!restored && rollbackArchive != null) OperationAction.INSTALL else null,
                )
                if (module != null /*&& !shell.isAlive*/) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            SuFile("/data/adb/modules_update/${module.id}").deleteRecursively()
                        }.onFailure {
                            Timber.e(
                                it,
                                "Failed to cleanup /data/adb/modules_update/${module.id}",
                            )
                        }
                    }
                }
            }
            activeOperationId = null
            return@withContext success
        }

    private suspend fun insertLocal(module: LocalModule) {
        withContext(Dispatchers.IO) {
            localRepository.insertLocal(module)
        }
    }

    private fun deleteBySu(zipPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                PlatformManager.fileManager.deleteOnExit(zipPath)
            }.onFailure {
                Timber.e(it, "Failed to delete $zipPath via su")
                withContext(Dispatchers.Main) {
                    log("Warning: Failed to delete $zipPath after installation.")
                }
            }.onSuccess {
                Timber.d("Deleted: $zipPath")
                withContext(Dispatchers.Main) {
                    devLog(R.string.deleted_zip_file, zipPath)
                }
            }
        }
    }
}
