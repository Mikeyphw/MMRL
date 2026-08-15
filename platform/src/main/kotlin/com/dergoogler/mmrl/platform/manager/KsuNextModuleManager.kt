package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.model.ModId.Companion.disableFile
import com.dergoogler.mmrl.platform.model.ModId.Companion.moduleDir
import com.dergoogler.mmrl.platform.model.ModId.Companion.removeFile
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.platform.util.Shell.submit
import com.dergoogler.mmrl.platform.util.ShellCommand

open class KsuNextModuleManager : KernelSUModuleManager(Platform.KsuNext) {
    override fun enable(
        id: ModId,
        useShell: Boolean,
        callback: IModuleOpsCallback,
    ) {
        val terminal = singleTerminal(callback)
        val dir = id.moduleDir
        if (!dir.exists()) return terminal.onFailure(id, null)

        if (useShell) {
            val restore = ShellCommand.of("ksud", "module", "restore", id.id)
            val enable = ShellCommand.of("ksud", "module", "enable", id.id)
            "$restore && $enable".submit {
                if (isSuccess) {
                    terminal.onSuccess(id)
                } else {
                    terminal.onFailure(id, failureMessage())
                }
            }
        } else {
            runCatching {
                requireMutation(ensureMarkerAbsent(id.removeFile), "Failed to remove module remove marker")
                requireMutation(ensureMarkerAbsent(id.disableFile), "Failed to remove module disable marker")
            }.onSuccess {
                terminal.onSuccess(id)
            }.onFailure {
                terminal.onFailure(id, it.message)
            }
        }
    }

    override fun getActionEnvironment(): List<String> =
        listOf(
            "export ASH_STANDALONE=1",
            "export KSU=true",
            "export KSU_NEXT=true",
            "export KSU_VER=$version",
            "export KSU_VER_CODE=$versionCode",
        )
}
