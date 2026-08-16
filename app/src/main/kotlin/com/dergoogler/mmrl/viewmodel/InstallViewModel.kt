package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.BuildConfig
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.app.Const.CLEAR_CMD
import com.dergoogler.mmrl.app.Event
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.database.entity.local.LocalModuleSource
import com.dergoogler.mmrl.database.entity.Repo.Companion.toRepo
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.ext.nullable
import com.dergoogler.mmrl.github.GitHubSourceSpec
import com.dergoogler.mmrl.installer.ArchiveInspection
import com.dergoogler.mmrl.installer.ArchiveInspector
import com.dergoogler.mmrl.installer.BatchInstallOutcomePolicy
import com.dergoogler.mmrl.installer.InstallExecutionAuthorizationPolicy
import com.dergoogler.mmrl.installer.InstallIdentityPolicy
import com.dergoogler.mmrl.installer.InstallReconciliationPolicy
import com.dergoogler.mmrl.installer.UpdateRollbackStore
import com.dergoogler.mmrl.installer.OperationStagingStore
import com.dergoogler.mmrl.installer.StagingOwnership
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
import com.dergoogler.mmrl.operation.OneShotOperationGate
import com.dergoogler.mmrl.operation.PrivilegedOperationCoordinator
import com.dergoogler.mmrl.operation.PrivilegedOperationCoordinator.OperationCompletion
import com.dergoogler.mmrl.operation.PrivilegedProcessExecutor
import com.dergoogler.mmrl.operation.VerifiedMutationFinalizationPolicy
import com.topjohnwu.superuser.CallbackList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val operationStagingStore: OperationStagingStore,
    private val operationCoordinator: PrivilegedOperationCoordinator,
    private val privilegedProcessExecutor: PrivilegedProcessExecutor,
) : TerminalViewModel(
        application,
        localRepository,
        modulesRepository,
        userPreferencesRepository,
        operationHistoryRepository,
    ) {
    val logfile get() = "Install_${LocalDateTime.now()}.log"

    data class ApprovalRequest(
        val modules: List<ApprovalModule>,
    ) {
        val summary: String
            get() = modules.joinToString("\n\n") { module ->
                buildString {
                    append(module.name).append(" · ").append(module.inspection.summary)
                    append("\nSHA-256 ").append(module.inspection.sha256.take(16)).append("…")
                    module.inspection.warnings.take(3).forEach { warning -> append("\n• ").append(warning) }
                }
            }
    }

    data class ApprovalModule(
        val id: String,
        val name: String,
        val inspection: ArchiveInspection,
    )

    var approvalRequest by mutableStateOf<ApprovalRequest?>(null)
        private set
    private var approvalDecision: CompletableDeferred<Boolean>? = null
    private val installLaunchGate = OneShotOperationGate()

    fun startInstall(
        uris: List<Uri>,
        parentOperationId: String? = null,
        rollbackMode: Boolean = false,
        expectedModuleIds: List<String> = emptyList(),
        expectedArchiveSha256: List<String> = emptyList(),
        requireApproval: Boolean = false,
    ) {
        if (!installLaunchGate.tryStart()) return
        viewModelScope.launch {
            installModules(
                uris = uris,
                parentOperationId = parentOperationId,
                rollbackMode = rollbackMode,
                expectedModuleIds = expectedModuleIds,
                expectedArchiveSha256 = expectedArchiveSha256,
                requireApproval = requireApproval,
            )
        }
    }

    fun respondToApproval(approved: Boolean) {
        approvalDecision?.complete(approved)
    }

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
        expectedModuleIds: List<String> = emptyList(),
        expectedArchiveSha256: List<String> = emptyList(),
        requireApproval: Boolean = false,
    ) {
        runCatching {
            InstallExecutionAuthorizationPolicy.requireAuthorizedLaunch(
                artifactCount = uris.size,
                requireApproval = requireApproval,
                reviewedSha256 = expectedArchiveSha256,
            )
        }.getOrElse { error ->
            val message = error.message ?: "Invalid install authorization"
            uris.forEach { recordRejectedInstall(it, message) }
            log(message)
            event = Event.FAILED
            return
        }
        val expectedIds =
            runCatching {
                InstallIdentityPolicy.expectedModuleIds(expectedModuleIds, uris.size)
            }.getOrElse { error ->
                val message = error.message ?: "Invalid expected module identity"
                uris.forEach { recordRejectedInstall(it, message) }
                log(message)
                event = Event.FAILED
                return
            }

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
        val batchOperationId = if (uris.size > 1) {
            operationHistoryRepository.start(
                kind = OperationKind.PREPARE_INSTALL,
                title = "Install batch",
                summary = "Preparing ${uris.size} module artifacts",
                sourceOperationId = parentOperationId,
                initialStatus = if (requireApproval) {
                    com.dergoogler.mmrl.database.entity.history.OperationStatus.WAITING_APPROVAL
                } else {
                    com.dergoogler.mmrl.database.entity.history.OperationStatus.RUNNING
                },
            )
        } else null

        val preparedModules = mutableListOf<PreparedInstall>()
        var blacklistedModuleFound = false

        try {
        withContext(Dispatchers.IO) {
            for ((index, uri) in uris.withIndex()) {
                val staged = runCatching { operationStagingStore.stage(uri) }.getOrElse { error ->
                    withContext(Dispatchers.Main) {
                        devLog(R.string.unable_to_find_path_for_uri, uri.toString())
                    }
                    recordRejectedInstall(uri, error.message ?: "Unable to create a verified staging copy")
                    allSucceeded = false
                    continue
                }
                val archive = staged.file
                val expectedReviewedHash = expectedArchiveSha256.getOrNull(index)
                if (expectedReviewedHash != null && !staged.provenance.sha256.equals(expectedReviewedHash, ignoreCase = true)) {
                    val message = "Archive changed after review; installation requires a new inspection"
                    recordRejectedInstall(uri, message)
                    operationStagingStore.release(staged.operationId)
                    allSucceeded = false
                    continue
                }
                val path = archive.path
                if (userPreferences.strictMode && !archive.name.endsWith(".zip", ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        log(
                            R.string.is_not_a_module_file_magisk_modules_must_be_zip_files_skipping,
                            path,
                        )
                    }
                    recordRejectedInstall(uri, "The selected file is not a ZIP module")
                    operationStagingStore.release(staged.operationId)
                    allSucceeded = false
                    continue
                }

                val inspection = runCatching { ArchiveInspector.inspect(archive) }.getOrElse { error ->
                    val message = error.message ?: "Unable to inspect staged module archive"
                    recordRejectedInstall(uri, message)
                    operationStagingStore.release(staged.operationId)
                    allSucceeded = false
                    continue
                }
                if (!inspection.canInstall) {
                    val message = inspection.blockedReasons.joinToString("; ").ifBlank { "Archive safety inspection blocked installation" }
                    recordRejectedInstall(uri, message)
                    operationStagingStore.release(staged.operationId)
                    allSucceeded = false
                    continue
                }
                if (expectedReviewedHash != null && !inspection.sha256.equals(expectedReviewedHash, ignoreCase = true)) {
                    val message = "Archive inspection hash no longer matches the reviewed authorization"
                    recordRejectedInstall(uri, message)
                    operationStagingStore.release(staged.operationId)
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
                    operationStagingStore.release(staged.operationId)
                    allSucceeded = false
                    continue
                }

                val identityResult =
                    runCatching {
                        InstallIdentityPolicy.verify(
                            actual = info.id,
                            expected = expectedIds[index],
                        )
                    }
                if (identityResult.isFailure) {
                    val message =
                        identityResult.exceptionOrNull()?.message
                            ?: "Archive module identity does not match the selected module"
                    withContext(Dispatchers.Main) { log(message) }
                    recordRejectedInstall(
                        uri = uri,
                        summary = message,
                        moduleId = info.id.id,
                        moduleName = info.name,
                    )
                    operationStagingStore.release(staged.operationId)
                    allSucceeded = false
                    continue
                }
                val identity = identityResult.getOrThrow()

                val blacklist = getBlacklistById(identity.moduleId.id)
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
                    operationStagingStore.release(staged.operationId)
                    allSucceeded = false
                    blacklistedModuleFound = true
                    break
                }

                preparedModules += PreparedInstall(
                    sourceUri = uri,
                    archive = archive,
                    module = info,
                    identity = identity,
                    staged = staged,
                    inspection = inspection,
                )
            }
        }

        if (blacklistedModuleFound) {
            preparedModules.forEach { operationStagingStore.release(it.staged.operationId) }
            batchOperationId?.let { operationHistoryRepository.fail(it, "Batch blocked by module blacklist") }
            return
        }

        if (preparedModules.size != uris.size) {
            preparedModules.forEach { operationStagingStore.release(it.staged.operationId) }
            batchOperationId?.let { operationHistoryRepository.fail(it, "Install preflight failed; no partial batch will execute") }
            event = Event.FAILED
            return
        }

        if (requireApproval) {
            val approved = awaitApproval(preparedModules)
            if (!approved) {
                preparedModules.forEach { operationStagingStore.release(it.staged.operationId) }
                batchOperationId?.let { operationHistoryRepository.cancel(it, "Installation cancelled after archive review") }
                event = Event.FAILED
                return
            }
            batchOperationId?.let { batchId ->
                operationHistoryRepository.transition(
                    id = batchId,
                    to = com.dergoogler.mmrl.database.entity.history.OperationStatus.RUNNING,
                    from = setOf(com.dergoogler.mmrl.database.entity.history.OperationStatus.WAITING_APPROVAL),
                    summary = "Archive review approved; installation starting",
                    phase = OperationPhase.STAGE,
                )
            }
        }

        val validBulkModules = preparedModules.map(PreparedInstall::bulkModule)
        var installedCount = 0
        var processedCount = 0

        for (prepared in preparedModules) {
            if (userPreferences.clearInstallTerminal && uris.size > 1) {
                log(CLEAR_CMD)
            }

            val result = loadAndInstallModule(
                prepared = prepared,
                allBulkModulesInBatch = validBulkModules,
                parentOperationId = batchOperationId ?: parentOperationId,
                rollbackMode = rollbackMode,
            )
            processedCount++
            if (result) installedCount++
            if (!result) {
                allSucceeded = false
                withContext(Dispatchers.Main) {
                    log(context.getString(R.string.installation_aborted_due_to_an_error))
                }
                break
            }
        }
        preparedModules.drop(processedCount).forEach { operationStagingStore.release(it.staged.operationId) }
        batchOperationId?.let { batchId ->
            val outcome = BatchInstallOutcomePolicy.classify(
                totalRequested = uris.size,
                installed = installedCount,
                hadFailure = !allSucceeded || installedCount != uris.size,
            )
            when (outcome.kind) {
                BatchInstallOutcomePolicy.Kind.SUCCEEDED -> operationHistoryRepository.succeed(
                    batchId,
                    outcome.summary,
                    requiresReboot = outcome.requiresReboot,
                )
                BatchInstallOutcomePolicy.Kind.FAILED,
                BatchInstallOutcomePolicy.Kind.PARTIAL_SUCCESS -> operationHistoryRepository.fail(
                    batchId,
                    outcome.summary,
                    requiresReboot = outcome.requiresReboot,
                )
            }
        }
        event = if (allSucceeded && installedCount == preparedModules.size) Event.SUCCEEDED else Event.FAILED
        } finally {
            preparedModules.forEach { prepared ->
                if (prepared.stagingOwnership.callerMayRelease()) {
                    operationStagingStore.release(prepared.staged.operationId)
                }
            }
        }
    }


    private suspend fun awaitApproval(prepared: List<PreparedInstall>): Boolean {
        val decision = CompletableDeferred<Boolean>()
        approvalDecision = decision
        withContext(Dispatchers.Main) {
            approvalRequest = ApprovalRequest(
                prepared.map { item ->
                    ApprovalModule(
                        id = item.module.id.id,
                        name = item.module.name,
                        inspection = item.inspection,
                    )
                },
            )
        }
        return try {
            decision.await()
        } finally {
            withContext(Dispatchers.Main) { approvalRequest = null }
            if (approvalDecision === decision) approvalDecision = null
        }
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
            )
        operationHistoryRepository.appendLog(historyId, "Source URI: $uri")
        operationHistoryRepository.fail(historyId, summary)
    }

    private data class PreparedInstall(
        val sourceUri: Uri,
        val archive: File,
        val module: LocalModule,
        val identity: com.dergoogler.mmrl.installer.ArtifactIdentity,
        val staged: OperationStagingStore.StagedArtifact,
        val inspection: ArchiveInspection,
        val stagingOwnership: StagingOwnership = StagingOwnership(),
    ) {
        val bulkModule = BulkModule(id = module.id.toString(), name = module.name)
    }

    private var datePattern = "d MMMM yyyy"

    init {
        viewModelScope.launch {
            userPreferencesRepository.data.collect { preferences ->
                datePattern = preferences.datePattern
            }
        }
    }

    private suspend fun loadAndInstallModule(
        prepared: PreparedInstall,
        allBulkModulesInBatch: List<BulkModule>,
        parentOperationId: String?,
        rollbackMode: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
            val moduleInfo = prepared.module
            val archive = prepared.archive

            if (!archive.isFile || !archive.canRead() || archive.length() <= 0L) {
                withContext(Dispatchers.Main) {
                    event = Event.FAILED
                    log(context.getString(R.string.copying_failed))
                }
                recordRejectedInstall(
                    uri = prepared.sourceUri,
                    summary = "The prepared module archive is no longer available",
                    moduleId = moduleInfo.id.id,
                    moduleName = moduleInfo.name,
                )
                return@withContext false
            }

            withContext<Unit>(Dispatchers.Main) {
                devLog(R.string.install_view_module_info)
                devLog("ID: ${moduleInfo.id.id}")
                devLog("Name: ${moduleInfo.name}")
                devLog("Version: ${moduleInfo.version}")
                devLog("Version Code: ${moduleInfo.versionCode}")
                devLog("Author: ${moduleInfo.author}")
                devLog("Description: ${moduleInfo.description}")
                devLog("Update JSON: ${moduleInfo.updateJson}")
                devLog("State: ${moduleInfo.state}")
                devLog("Size: ${moduleInfo.size.toFormattedFileSize()}")
                devLog(
                    "Last Updated: ${
                        moduleInfo.lastUpdated.toFormattedDateSafely(datePattern)
                    }",
                )
                devLog("::endgroup::")
            }

            install(
                zipPath = archive.path,
                allBulkModulesInBatch = allBulkModulesInBatch,
                module = moduleInfo,
                identity = prepared.identity,
                approvedInspection = prepared.inspection,
                sourceUri = prepared.sourceUri.toString(),
                sourceUrl = prepared.staged.provenance.sourceUrl,
                parentOperationId = parentOperationId,
                rollbackMode = rollbackMode,
                stagingOperationId = prepared.staged.operationId,
                onCoordinatorOwnership = prepared.stagingOwnership::handoffToCoordinator,
            )
            } finally {
                if (prepared.stagingOwnership.callerMayRelease()) {
                    operationStagingStore.release(prepared.staged.operationId)
                }
            }
        }

    private suspend fun install(
        zipPath: String,
        allBulkModulesInBatch: List<BulkModule>,
        module: LocalModule? = null,
        identity: com.dergoogler.mmrl.installer.ArtifactIdentity,
        approvedInspection: ArchiveInspection,
        sourceUri: String? = null,
        sourceUrl: String? = null,
        parentOperationId: String? = null,
        rollbackMode: Boolean = false,
        stagingOperationId: String? = null,
        onCoordinatorOwnership: () -> Unit = {},
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
                    sourceUrl = sourceUrl,
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
            if (!inspection.canInstall || !inspection.sha256.equals(approvedInspection.sha256, ignoreCase = true)) {
                val message = "Staged archive no longer matches the approved safety inspection"
                operationHistoryRepository.fail(historyId, message)
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

            val inspectedModule =
                PlatformManager.moduleManager.getModuleInfo(zipPath)
                    ?: run {
                        val message = "Unable to re-read module identity from inspected archive"
                        operationHistoryRepository.fail(historyId, message)
                        activeOperationId = null
                        return@withContext false
                    }
            runCatching {
                InstallIdentityPolicy.verifyInspectedModule(
                    identity = identity,
                    actual = inspectedModule.id,
                )
            }.getOrElse { error ->
                val message = error.message ?: "Inspected archive module identity changed"
                operationHistoryRepository.appendLog(historyId, "Blocked: $message")
                operationHistoryRepository.fail(historyId, message, error)
                activeOperationId = null
                return@withContext false
            }

            val reviewedIdentity =
                runCatching {
                    InstallIdentityPolicy.bindInspection(
                        identity = identity,
                        file = zipFile,
                        sha256 = inspection.sha256,
                    )
                }.getOrElse { error ->
                    val message = error.message ?: "Unable to bind the reviewed archive identity"
                    operationHistoryRepository.fail(historyId, message, error)
                    activeOperationId = null
                    return@withContext false
                }

            val idempotencyKey = "${kind.name.lowercase()}:${identity.moduleId.id}:${reviewedIdentity.sha256}"
            if (!operationHistoryRepository.claimIdempotencyKey(historyId, idempotencyKey)) {
                val message = "An identical module mutation is already active"
                operationHistoryRepository.fail(historyId, message)
                activeOperationId = null
                return@withContext false
            }

            operationHistoryRepository.phase(historyId, OperationPhase.STAGE, "Preparing rollback and staging files")
            val rollbackArchive =
                if (previous != null && !rollbackMode) {
                    updateRollbackStore.create(previous, historyId).getOrNull()
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

            runCatching {
                InstallIdentityPolicy.verifyUnchanged(reviewedIdentity, zipFile)
            }.getOrElse { error ->
                val message = error.message ?: "Reviewed archive changed before installation"
                operationHistoryRepository.appendLog(historyId, "Blocked: $message")
                operationHistoryRepository.fail(historyId, message, error)
                activeOperationId = null
                return@withContext false
            }
            operationHistoryRepository.appendLog(
                historyId,
                "Archive SHA-256 and size reverified immediately before privileged command construction",
            )

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

            operationHistoryRepository.phase(
                historyId,
                OperationPhase.INSTALL,
                if (rollbackMode) "Restoring previous module version" else "Installing module",
            )
            onCoordinatorOwnership()
            val completion = operationCoordinator.execute<Boolean>(
                historyId = historyId,
                cleanup = {
                    stagingOperationId?.let { operationStagingStore.release(it) }
                },
            ) {
                runCatching { InstallIdentityPolicy.verifyUnchanged(reviewedIdentity, zipFile) }.getOrElse { error ->
                    return@execute OperationCompletion.Failure(
                        error.message ?: "Reviewed archive changed before privileged execution",
                        error,
                    )
                }
                markMutationStarted()
                log("Privileged mutation started for SHA-256 ${reviewedIdentity.sha256}")
                val rootResult = privilegedProcessExecutor.execute(installCommand, env) { line -> log(line) }
                phase(OperationPhase.RECONCILE, "Reconciling installed state from the root backend")

                suspend fun backendModule(): LocalModule? =
                    runCatching { PlatformManager.moduleManager.getModuleById(identity.moduleId) }.getOrNull()

                if (rootResult.isSuccess) {
                    val actual = backendModule()
                    if (
                        actual == null ||
                        !InstallReconciliationPolicy.matchesExpected(
                            actualId = actual.id.id,
                            actualVersionCode = actual.versionCode,
                            expectedId = identity.moduleId.id,
                            expectedVersionCode = module?.versionCode,
                        )
                    ) {
                        return@execute OperationCompletion.OutcomeUnknown(
                            summary = "Installer exited successfully but backend state does not match the reviewed module; reconcile before retrying",
                            requiresReboot = true,
                            rollbackAction = rollbackArchive?.let { OperationAction.INSTALL },
                        )
                    }
                    val rollbackAction = when {
                        rollbackMode -> null
                        rollbackArchive != null -> OperationAction.INSTALL
                        previous == null -> OperationAction.REMOVE
                        else -> null
                    }
                    val finalizationError = runCatching {
                        localRepository.insertLocal(actual)
                        recordGitHubInstallSource(actual, parentOperationId, sourceUrl)
                    }.exceptionOrNull()
                    when (
                        VerifiedMutationFinalizationPolicy.classify(
                            backendVerified = true,
                            finalizationSucceeded = finalizationError == null,
                        )
                    ) {
                        VerifiedMutationFinalizationPolicy.Outcome.SUCCESS -> {
                            if (userPreferences.deleteZipFile && !updateRollbackStore.isManagedBackup(zipPath)) {
                                runCatching { PlatformManager.fileManager.deleteOnExit(zipPath) }
                                    .onFailure { log("Warning: unable to schedule staged ZIP deletion: ${it.message}") }
                            }
                            OperationCompletion.Success(
                                value = true,
                                summary = when (kind) {
                                    OperationKind.UPDATE -> "Update installed and backend state verified"
                                    OperationKind.ROLLBACK -> "Previous module version restored and verified"
                                    else -> "Installed and backend state verified"
                                },
                                requiresReboot = true,
                                rollbackAction = rollbackAction,
                            )
                        }
                        VerifiedMutationFinalizationPolicy.Outcome.KNOWN_APPLIED_FINALIZATION_FAILED ->
                            OperationCompletion.Failure(
                                summary = "Backend state confirms the module was applied, but local/provenance finalization failed; refresh local state before another mutation",
                                error = finalizationError,
                                requiresReboot = true,
                                rollbackAction = rollbackAction,
                                retryable = false,
                            )
                        VerifiedMutationFinalizationPolicy.Outcome.OUTCOME_UNKNOWN ->
                            OperationCompletion.OutcomeUnknown(
                                summary = "Installed backend state could not be verified; reconcile before retrying",
                                requiresReboot = true,
                                rollbackAction = rollbackAction,
                            )
                    }
                } else {
                    log("Installer exited with code ${rootResult.exitCode}")
                    if (rollbackArchive != null && !rollbackMode) {
                        phase(OperationPhase.ROLLBACK, "Install failed; restoring previous version")
                        val rollbackCommand = PlatformManager.moduleManager.getInstallCommand(rollbackArchive.absolutePath)
                        if (!rollbackCommand.isNullOrBlank()) {
                            val rollbackResult = privilegedProcessExecutor.execute(rollbackCommand, env) { line -> log(line) }
                            val restored = backendModule()
                            if (rollbackResult.isSuccess && restored != null && previous != null && restored.versionCode == previous.versionCode) {
                                val finalizationError = runCatching { localRepository.insertLocal(restored) }.exceptionOrNull()
                                return@execute OperationCompletion.Failure(
                                    summary = if (finalizationError == null) {
                                        "Installation failed; previous version was restored and verified"
                                    } else {
                                        "Installation failed and the previous version was restored in the backend, but local state finalization failed"
                                    },
                                    error = finalizationError,
                                    requiresReboot = true,
                                    retryable = false,
                                )
                            }
                        }
                        return@execute OperationCompletion.OutcomeUnknown(
                            summary = "Installation failed and rollback could not be verified; reconcile before retrying",
                            requiresReboot = true,
                            rollbackAction = OperationAction.INSTALL,
                        )
                    }
                    val observed = backendModule()
                    val unchanged = when {
                        previous == null -> observed == null
                        observed == null -> false
                        else -> observed.versionCode == previous.versionCode
                    }
                    if (unchanged) {
                        OperationCompletion.Failure(
                            summary = "Installation failed and backend state is unchanged",
                            retryable = false,
                        )
                    } else {
                        OperationCompletion.OutcomeUnknown(
                            summary = "Installation failed after mutation began and backend state changed; reconcile before retrying",
                            requiresReboot = true,
                            rollbackAction = rollbackArchive?.let { OperationAction.INSTALL },
                        )
                    }
                }
            }
            val success = completion is OperationCompletion.Success<*>
            activeOperationId = null
            return@withContext success
        }

    private suspend fun recordGitHubInstallSource(
        module: LocalModule,
        parentOperationId: String?,
        sourceUrlOverride: String?,
    ) {
        val parentId = parentOperationId
        val sourceUrl = sourceUrlOverride?.takeIf(String::isNotBlank)
            ?: parentId?.let { operationHistoryRepository.getById(it)?.sourceUrl }
                ?.takeIf(String::isNotBlank)
            ?: return
        val source = GitHubSourceSpec.fromDownloadUrl(sourceUrl) ?: return
        val repoUrl = source.sourceUrl
        localRepository.insertRepo(repoUrl.toRepo())
        modulesRepository.getRepo(repoUrl.toRepo())

        val versions = localRepository.getVersionByIdAndUrl(module.id.id, repoUrl)
        val installedVersion =
            versions.firstOrNull { it.zipUrl == sourceUrl }
                ?: versions.maxByOrNull { it.versionCode }

        localRepository.insertLocalSource(
            LocalModuleSource(
                id = module.id.id,
                repoUrl = repoUrl,
                mode = source.mode.name,
                installedVersion = installedVersion?.version ?: module.version,
                installedVersionCode = installedVersion?.versionCode ?: module.versionCode,
                sourceUrl = sourceUrl,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        parentId?.let {
            operationHistoryRepository.appendLog(
                it,
                "Linked ${module.id.id} to GitHub ${source.mode.name.lowercase()} source: $repoUrl",
            )
        }
    }


}
