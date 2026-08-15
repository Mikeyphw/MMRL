package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.BuildConfig
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.app.Event
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.hasAction
import com.dergoogler.mmrl.platform.content.State
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.model.ModId.Companion.actionFile
import com.dergoogler.mmrl.platform.util.ShellCommand
import com.dergoogler.mmrl.operation.OneShotOperationGate
import com.dergoogler.mmrl.operation.PrivilegedOperationCoordinator
import com.dergoogler.mmrl.operation.PrivilegedProcessExecutor
import com.dergoogler.mmrl.repository.LocalRepository
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ActionViewModel
@Inject
constructor(
    application: Application,
    localRepository: LocalRepository,
    modulesRepository: ModulesRepository,
    userPreferencesRepository: UserPreferencesRepository,
    operationHistoryRepository: OperationHistoryRepository,
    private val operationCoordinator: PrivilegedOperationCoordinator,
    private val privilegedProcessExecutor: PrivilegedProcessExecutor,
) : TerminalViewModel(
        application,
        localRepository,
        modulesRepository,
        userPreferencesRepository,
        operationHistoryRepository,
    ) {
    val logfile get() = "Action_${LocalDateTime.now()}.log"

    private val actionLaunchGate = OneShotOperationGate()

    init {
        Timber.d("ActionViewModel initialized")
    }

    fun startAction(modId: ModId) {
        if (!actionLaunchGate.tryStart()) return
        viewModelScope.launch(Dispatchers.IO) { runAction(modId) }
    }

    suspend fun runAction(modId: ModId) =
        withContext(Dispatchers.IO) {
            val module = localModule(modId.toString())
            val userPreferences = userPreferencesRepository.data.first()
            event = Event.LOADING

            if (module == null) {
                withContext(Dispatchers.Main) {
                    event = Event.FAILED
                    log(R.string.module_not_found)
                }
                return@withContext
            }

            if (!module.hasAction) {
                withContext(Dispatchers.Main) {
                    event = Event.FAILED
                    log(R.string.this_module_don_t_have_an_action)
                }
                return@withContext
            }

            if (module.state == State.DISABLE || module.state == State.REMOVE) {
                withContext(Dispatchers.Main) {
                    event = Event.FAILED
                    log(R.string.module_is_disabled_or_removed_unable_to_execute_action)
                }
                return@withContext
            }

            val historyId =
                operationHistoryRepository.start(
                    kind = OperationKind.MODULE_ACTION,
                    title = module.name,
                    summary = "Queued module action",
                    moduleId = module.id.id,
                    moduleName = module.name,
                    retryAction = null,
                    useShell = userPreferences.useShellForModuleAction,
                    initialStatus = OperationStatus.QUEUED,
                )
            val idempotencyKey = "module-action:${module.id.id}"
            if (!operationHistoryRepository.claimIdempotencyKey(historyId, idempotencyKey)) {
                operationHistoryRepository.fail(historyId, "An identical module action is already active")
                withContext(Dispatchers.Main) {
                    event = Event.FAILED
                    log("An identical module action is already active")
                }
                return@withContext
            }
            activeOperationId = historyId

            val environment =
                mapOf(
                    "ASH_STANDALONE" to "1",
                    "MMRL" to "true",
                    "MMRL_VER" to BuildConfig.VERSION_NAME,
                    "MMRL_VER_CODE" to BuildConfig.VERSION_CODE.toString(),
                    "BOOTMODE" to "true",
                    "ARCH" to Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                    "API" to Build.VERSION.SDK_INT.toString(),
                    "IS64BIT" to Build.SUPPORTED_64_BIT_ABIS.isNotEmpty().toString(),
                )

            val completion = operationCoordinator.execute<Unit>(historyId) {
                phase(OperationPhase.STAGE, "Preparing module action")
                val command =
                    if (userPreferences.useShellForModuleAction || platform.isMagisk) {
                        ShellCommand.of(
                            "busybox",
                            "sh",
                            modId.requireOperational().actionFile.path,
                        )
                    } else {
                        PlatformManager.moduleManager.getActionCommand(modId)
                    }
                check(command.isNotBlank()) { "Module action command is empty" }
                phase(OperationPhase.INSTALL, "Executing module action")
                markMutationStarted()
                val result = privilegedProcessExecutor.execute(
                    command = command,
                    environment = environment,
                    onLine = { line ->
                        log(line)
                        this@ActionViewModel.log(line)
                    },
                )
                if (result.isSuccess) {
                    PrivilegedOperationCoordinator.OperationCompletion.Success(
                        value = Unit,
                        summary = "Module action completed",
                    )
                } else {
                    PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                        summary = "Module action exited with code ${result.exitCode} after privileged execution began; reconcile before retrying",
                    )
                }
            }
            activeOperationId = null
            withContext(Dispatchers.Main) {
                when (completion) {
                    is PrivilegedOperationCoordinator.OperationCompletion.Success -> event = Event.SUCCEEDED
                    is PrivilegedOperationCoordinator.OperationCompletion.Failure -> {
                        event = Event.FAILED
                        log(R.string.execution_failed_try_to_use_shell_for_the_action_execution_settings_module_use_shell_for_module_action)
                    }
                    is PrivilegedOperationCoordinator.OperationCompletion.Cancelled -> event = Event.FAILED
                    is PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown -> {
                        event = Event.FAILED
                        log("Action outcome is unknown; reconcile before retrying")
                    }
                }
            }
        }

}
