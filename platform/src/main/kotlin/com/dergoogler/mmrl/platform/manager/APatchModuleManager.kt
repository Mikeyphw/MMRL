package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.content.ModuleCompatibility
import com.dergoogler.mmrl.platform.content.NullableBoolean
import com.dergoogler.mmrl.platform.file.SuFile
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.model.ModId.Companion.disableFile
import com.dergoogler.mmrl.platform.model.ModId.Companion.moduleDir
import com.dergoogler.mmrl.platform.model.ModId.Companion.removeFile
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.platform.util.Shell.submit
import com.dergoogler.mmrl.platform.util.ShellCommand

open class APatchModuleManager : BaseModuleManager() {
    override fun getManagerName(): String = "APatch"

    override fun getVersion(): String = mVersion

    override fun getVersionCode(): Int = mVersionCode

    override fun getModuleCompatibility() =
        ModuleCompatibility(
            hasMagicMount =
                SuFile("/data/adb/.bind_mount_enable").exists() &&
                    (
                        versionCode >= 11011 &&
                            !SuFile(
                                "/data/adb/.overlay_enable",
                            ).exists()
                    ),
            canRestoreModules = false,
        )

    override fun enable(
        id: ModId,
        useShell: Boolean,
        callback: IModuleOpsCallback,
    ) {
        val terminal = singleTerminal(callback)
        val dir = id.moduleDir
        if (!dir.exists()) return terminal.onFailure(id, null)

        if (useShell) {
            ShellCommand.of("apd", "module", "enable", id.id).submit {
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

    override fun disable(
        id: ModId,
        useShell: Boolean,
        callback: IModuleOpsCallback,
    ) {
        val terminal = singleTerminal(callback)
        val dir = id.moduleDir
        if (!dir.exists()) return terminal.onFailure(id, null)

        if (useShell) {
            ShellCommand.of("apd", "module", "disable", id.id).submit {
                if (isSuccess) {
                    terminal.onSuccess(id)
                } else {
                    terminal.onFailure(id, failureMessage())
                }
            }
        } else {
            runCatching {
                requireMutation(ensureMarkerAbsent(id.removeFile), "Failed to remove module remove marker")
                requireMutation(ensureMarkerPresent(id.disableFile), "Failed to create module disable marker")
            }.onSuccess {
                terminal.onSuccess(id)
            }.onFailure {
                terminal.onFailure(id, it.message)
            }
        }
    }

    override fun remove(
        id: ModId,
        useShell: Boolean,
        callback: IModuleOpsCallback,
    ) {
        val terminal = singleTerminal(callback)
        val dir = id.moduleDir
        if (!dir.exists()) return terminal.onFailure(id, null)

        if (useShell) {
            ShellCommand.of("apd", "module", "uninstall", id.id).submit {
                if (isSuccess) {
                    terminal.onSuccess(id)
                } else {
                    terminal.onFailure(id, failureMessage())
                }
            }
        } else {
            runCatching {
                requireMutation(ensureMarkerAbsent(id.disableFile), "Failed to remove module disable marker")
                requireMutation(ensureMarkerPresent(id.removeFile), "Failed to create module remove marker")
            }.onSuccess {
                terminal.onSuccess(id)
            }.onFailure {
                terminal.onFailure(id, it.message)
            }
        }
    }

    override fun getInstallCommand(path: String): String = ShellCommand.of("apd", "module", "install", path)

    override fun getActionCommand(id: ModId): String {
        id.requireOperational()
        return ShellCommand.of("apd", "module", "action", id.id)
    }

    override fun getActionEnvironment(): List<String> =
        listOf(
            "export APATCH=true",
            "export APATCH_VER=$version",
            "export APATCH_VER_CODE=$versionCode",
        )

    // KernelSU only
    override fun isSafeMode(): Boolean = false

    override fun isLkmMode(): NullableBoolean = NullableBoolean(null)

    override fun setSuEnabled(enabled: Boolean): Boolean = true

    override fun isSuEnabled(): Boolean = true

    override fun getSuperUserCount(): Int = -1

    override fun uidShouldUmount(uid: Int): Boolean = false
}
