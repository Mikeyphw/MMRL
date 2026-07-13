package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.BuildConfig
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.app.Event
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.model.terminal.ScriptError
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.hasAction
import com.dergoogler.mmrl.platform.content.State
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.repository.LocalRepository
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import com.topjohnwu.superuser.CallbackList
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mmrlx.terminal.newSuperUserPty
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
) : TerminalViewModel(
        application,
        localRepository,
        modulesRepository,
        userPreferencesRepository,
        operationHistoryRepository,
    ) {
    val logfile get() = "Action_${LocalDateTime.now()}.log"

    init {
        Timber.d("ActionViewModel initialized")
    }

    private val stdoutCallbackList =
        object : CallbackList<String?>() {
            override fun onAddElement(msg: String?) {
                msg?.let {
                    viewModelScope.launch { log(it) }
                }
            }
        }

    private val stderrCallbackList =
        object : CallbackList<String?>() {
            override fun onAddElement(msg: String?) {
                msg?.let {
                    viewModelScope.launch {
                        val error = ScriptError.fromString(it)

                        if (error != null) {
                            devLog("::error title=${error.title}::At Line ${error.lineNumber}: ${error.message}")
                            return@launch
                        }

                        devLog(it)
                    }
                }
            }
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
                    summary = "Running module action",
                    moduleId = module.id.id,
                    moduleName = module.name,
                    retryAction = OperationAction.RUN_ACTION,
                    useShell = userPreferences.useShellForModuleAction,
                )
            activeOperationId = historyId
            val success = executeAction(modId, userPreferences.useShellForModuleAction)

            if (success) {
                operationHistoryRepository.succeed(historyId, "Module action completed")
            } else {
                operationHistoryRepository.fail(historyId, "Module action failed")
            }
            activeOperationId = null
            event = if (success) Event.SUCCEEDED else Event.FAILED
        }

    private suspend fun executeAction(
        modId: ModId,
        legacy: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val shellEnv =
                listOf(
                    "export PATH=/data/adb/ap/bin:/data/adb/ksu/bin:/data/adb/magisk:\$PATH",
                    "export MMRL=true",
                    "export MMRL_VER=${BuildConfig.VERSION_NAME}",
                    "export MMRL_VER_CODE=${BuildConfig.VERSION_CODE}",
                    "export BOOTMODE=true",
                    "export ARCH=${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}",
                    "export API=${Build.VERSION.SDK_INT}",
                    "export IS64BIT=${Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()}",
                )

            val env = mapOf(
                "ASH_STANDALONE" to "1",
                "MMRL" to "true",
                "MMRL_VER" to BuildConfig.VERSION_NAME,
                "MMRL_VER_CODE" to BuildConfig.VERSION_CODE.toString(),
                "BOOTMODE" to "true",
                "ARCH" to Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                "API" to Build.VERSION.SDK_INT.toString(),
                "IS64BIT" to Build.SUPPORTED_64_BIT_ABIS.isNotEmpty().toString(),
            )

            val command =
                if (legacy || platform.isMagisk) {
                    "busybox sh /data/adb/modules/$modId/action.sh"
                } else {
                    PlatformManager.moduleManager.getActionCommand(modId)
                }

            val emu = emulatorReady.await()
            val result = emu.newSuperUserPty(command, env)
            val success = result.isSuccess
            if (success) {
                return@withContext true
            } else {
                withContext(Dispatchers.Main) {
                    log(R.string.execution_failed_try_to_use_shell_for_the_action_execution_settings_module_use_shell_for_module_action)
                    result.onFailure { devLog("Shell Error: $it") }
                }
            }
            return@withContext false
        }
}
