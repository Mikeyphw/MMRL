package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.content.ModuleCompatibility
import com.dergoogler.mmrl.platform.content.NullableBoolean
import com.dergoogler.mmrl.platform.ksu.KernelVersion
import com.dergoogler.mmrl.platform.ksu.KsuNative
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.model.ModId.Companion.disableFile
import com.dergoogler.mmrl.platform.model.ModId.Companion.moduleDir
import com.dergoogler.mmrl.platform.model.ModId.Companion.removeFile
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.platform.util.Shell.submit
import com.dergoogler.mmrl.platform.util.ShellCommand

open class KernelSUModuleManager(protected val rootPlatform: Platform = Platform.KernelSU) : BaseModuleManager() {
    override fun getManagerName(): String = rootPlatform.name

    override fun getVersion(): String = mVersion

    override fun getVersionCode(): Int {
        if (!RootManagerCapabilityPolicy.mayQueryNative(rootPlatform)) return mVersionCode
        val ksuVersion = KsuNative.nativeGetVersion()

        return if (ksuVersion != -1) {
            ksuVersion
        } else {
            mVersionCode
        }
    }

    override fun setSuEnabled(enabled: Boolean): Boolean =
        supportedCapabilities().supported && KsuNative.nativeSetSuEnabled(enabled)

    override fun isSuEnabled(): Boolean =
        supportedCapabilities().supported && KsuNative.nativeIsSuEnabled()

    private fun supportedCapabilities() =
        RootManagerCapabilityPolicy.capabilities(
            rootPlatform,
            versionCode,
            KernelVersion.getKernelVersion().isGKI(),
        )

    override fun isLkmMode(): NullableBoolean {
        val capabilities = supportedCapabilities()
        return NullableBoolean(if (capabilities.canQueryLkmMode) KsuNative.nativeIsLkmMode() else null)
    }

    override fun getSuperUserCount(): Int =
        if (supportedCapabilities().supported) KsuNative.nativeGetAllowList().size else -1

    override fun isSafeMode(): Boolean =
        supportedCapabilities().supported && KsuNative.nativeIsSafeMode()

    override fun uidShouldUmount(uid: Int): Boolean =
        supportedCapabilities().supported && KsuNative.nativeUidShouldUmount(uid)

    override fun getModuleCompatibility(): ModuleCompatibility {
        val capabilities = supportedCapabilities()
        return ModuleCompatibility(
            hasMagicMount = capabilities.hasMagicMount,
            canRestoreModules = capabilities.canRestoreModules,
        )
    }

    override fun enable(
        id: ModId,
        useShell: Boolean,
        callback: IModuleOpsCallback,
    ) {
        val terminal = singleTerminal(callback)
        val dir = id.moduleDir
        if (!dir.exists()) return terminal.onFailure(id, null)

        if (useShell) {
            ShellCommand.of("ksud", "module", "enable", id.id).submit {
                if (isSuccess) {
                    terminal.onSuccess(id)
                } else {
                    terminal.onFailure(id, failureMessage())
                }
            }
        } else {
            runCatching {
                requireMutation(ensureMarkerAbsent(dir.resolve("remove")), "Failed to remove module remove marker")
                requireMutation(ensureMarkerAbsent(dir.resolve("disable")), "Failed to remove module disable marker")
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
            ShellCommand.of("ksud", "module", "disable", id.id).submit {
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
            ShellCommand.of("ksud", "module", "uninstall", id.id).submit {
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

    override fun getInstallCommand(path: String): String = ShellCommand.of("ksud", "module", "install", path)

    override fun getActionCommand(id: ModId): String {
        id.requireOperational()
        return ShellCommand.of("ksud", "module", "action", id.id)
    }

    override fun getActionEnvironment(): List<String> =
        listOf(
            "export ASH_STANDALONE=1",
            "export KSU=true",
            "export KSU_VER=$version",
            "export KSU_VER_CODE=$versionCode",
        )
}
