package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.content.ModuleCompatibility
import com.dergoogler.mmrl.platform.content.NullableBoolean
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.model.ModId.Companion.disableFile
import com.dergoogler.mmrl.platform.model.ModId.Companion.moduleDir
import com.dergoogler.mmrl.platform.model.ModId.Companion.removeFile
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.platform.util.ShellCommand

open class MagiskModuleManager : BaseModuleManager() {
    override fun getManagerName(): String = "Magisk"

    override fun getModuleCompatibility() =
        ModuleCompatibility(
            hasMagicMount = true,
            canRestoreModules = true,
        )

    override fun getVersion(): String = mVersion

    override fun getVersionCode(): Int = mVersionCode

    override fun enable(
        id: ModId,
        useShell: Boolean,
        callback: IModuleOpsCallback,
    ) {
        val terminal = singleTerminal(callback)
        val dir = id.moduleDir
        if (!dir.exists()) return terminal.onFailure(id, null)

        runCatching {
            requireMutation(ensureMarkerAbsent(id.removeFile), "Failed to remove module remove marker")
            requireMutation(ensureMarkerAbsent(id.disableFile), "Failed to remove module disable marker")
        }.onSuccess {
            terminal.onSuccess(id)
        }.onFailure {
            terminal.onFailure(id, it.message)
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

        runCatching {
            requireMutation(ensureMarkerAbsent(id.removeFile), "Failed to remove module remove marker")
            requireMutation(ensureMarkerPresent(id.disableFile), "Failed to create module disable marker")
        }.onSuccess {
            terminal.onSuccess(id)
        }.onFailure {
            terminal.onFailure(id, it.message)
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

        runCatching {
            requireMutation(ensureMarkerAbsent(id.disableFile), "Failed to remove module disable marker")
            requireMutation(ensureMarkerPresent(id.removeFile), "Failed to create module remove marker")
        }.onSuccess {
            terminal.onSuccess(id)
        }.onFailure {
            terminal.onFailure(id, it.message)
        }
    }

    override fun getInstallCommand(path: String): String = ShellCommand.of("magisk", "--install-module", path)

    override fun getActionCommand(id: ModId): String = ""

    override fun getActionEnvironment(): List<String> =
        listOf(
            "export MAGISK=true",
            "export MAGISK_VER=$version",
            "export MAGISKTMP=$(magisk --path)",
            "export MAGISK_VER_CODE=$versionCode",
        )

    // KernelSU only
    override fun isSafeMode(): Boolean = false

    override fun isLkmMode(): NullableBoolean = NullableBoolean(null)

    override fun setSuEnabled(enabled: Boolean): Boolean = true

    override fun isSuEnabled(): Boolean = true

    override fun getSuperUserCount(): Int = -1

    override fun uidShouldUmount(uid: Int): Boolean = false
}
