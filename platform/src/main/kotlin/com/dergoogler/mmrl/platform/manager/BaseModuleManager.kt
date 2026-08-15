package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.content.LocalModule
import com.dergoogler.mmrl.platform.content.State
import com.dergoogler.mmrl.platform.file.ExtFile
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.model.ModId.Companion.disableFile
import com.dergoogler.mmrl.platform.model.ModId.Companion.files
import com.dergoogler.mmrl.platform.model.ModId.Companion.moduleDir
import com.dergoogler.mmrl.platform.model.ModId.Companion.propFile
import com.dergoogler.mmrl.platform.model.ModId.Companion.removeFile
import com.dergoogler.mmrl.platform.model.ModId.Companion.updateFile
import com.dergoogler.mmrl.platform.stub.IModuleManager
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.platform.model.ShellResult
import com.dergoogler.mmrl.platform.util.Shell.exec
import com.dergoogler.mmrl.platform.util.ShellCommand
import org.apache.commons.compress.archivers.zip.ZipFile

abstract class BaseModuleManager : IModuleManager.Stub() {
    protected val mVersion by lazy {
        "su -v".exec().getOrDefault("unknown")
    }

    protected val mVersionCode by lazy {
        "su -V".exec().getOrDefault("").toIntOr(-1)
    }

    override fun reboot(reason: String) {
        if (reason == "recovery") {
            "/system/bin/input keyevent 26".exec()
        }

        val serviceReboot = ShellCommand.of("/system/bin/svc", "power", "reboot", reason)
        val directReboot = ShellCommand.of("/system/bin/reboot", reason)
        "$serviceReboot || $directReboot".exec()
    }

    override fun getModules() =
        ExtFile(ModId.ADB_DIR, ModId.MODULES_DIR)
            .listFiles()
            .orEmpty()
            .mapNotNull { dir ->
                val id = ModId.parseOrNull(dir.name) ?: return@mapNotNull null
                id.readProps?.toModule(expectedId = id)
            }

    override fun getModuleById(id: ModId): LocalModule? =
        id.readProps?.toModule(expectedId = id)

    override fun getModuleInfo(zipPath: String): LocalModule? =
        ZipFile.Builder().setFile(zipPath).get().use { zipFile ->
            val entry = zipFile.getEntry(ModId.PROP_FILE) ?: return@use null
            zipFile.getInputStream(entry).use { input ->
                input
                    .bufferedReader()
                    .readText()
                    .let(::readProps)
                    .toModule()
            }
        }


    protected fun singleTerminal(delegate: IModuleOpsCallback): IModuleOpsCallback {
        val gate = TerminalSignalGate()
        return object : IModuleOpsCallback.Stub() {
            override fun onSuccess(id: ModId) {
                if (gate.claim()) delegate.onSuccess(id)
            }

            override fun onFailure(id: ModId, msg: String?) {
                if (gate.claim()) delegate.onFailure(id, msg)
            }
        }
    }

    protected fun ShellResult.failureMessage(): String = ShellFailurePolicy.message(this)

    /** Idempotent marker operations whose Boolean result is part of mutation success. */
    protected fun ensureMarkerAbsent(file: ExtFile): Boolean =
        ModuleMarkerMutation.ensureAbsent(file.exists(), file::delete)

    protected fun ensureMarkerPresent(file: ExtFile): Boolean =
        ModuleMarkerMutation.ensurePresent(file.exists(), file::createNewFile)

    protected fun requireMutation(success: Boolean, description: String) {
        check(success) { description }
    }

    protected fun readProps(props: String) =
        props
            .lines()
            .associate { line ->
                val items = line.split("=", limit = 2).map { it.trim() }
                if (items.size != 2) {
                    "" to ""
                } else {
                    items[0] to items[1]
                }
            }

    protected val ModId.readProps
        get() =
            propFile.let {
                when {
                    it.exists() -> readProps(it.readText())
                    else -> null
                }
            }

    protected val ModId.readState
        get(): State {
            removeFile.apply {
                if (exists()) return State.REMOVE
            }

            disableFile.apply {
                if (exists()) return State.DISABLE
            }

            updateFile.apply {
                if (exists()) return State.UPDATE
            }

            return State.ENABLE
        }

    protected fun readLastUpdated(id: ModId): Long {
        id.files.forEach {
            if (it.exists()) {
                return it.lastModified()
            }
        }

        return 0L
    }

    protected fun Map<String, String>.toModule(
        baseDir: String = ModId.ADB_DIR,
        expectedId: ModId? = null,
    ): LocalModule? {
        val declaredId = ModId.parseOrNull(get("id"), baseDir) ?: return null
        if (expectedId != null && declaredId != expectedId) return null
        val id = expectedId ?: declaredId

        val size =
            id.moduleDir.length(
                recursive = true,
                skipSymLinks = true,
            )

        return LocalModule(
            id = id,
            name = getOrDefault("name", id.id),
            version = getOrDefault("version", ""),
            versionCode = getOrDefault("versionCode", "-1").toIntOr(-1),
            author = getOrDefault("author", ""),
            description = getOrDefault("description", ""),
            updateJson = getOrDefault("updateJson", ""),
            state = id.readState,
            size = size,
            lastUpdated = readLastUpdated(id),
        )
    }

    protected fun String.toIntOr(defaultValue: Int) =
        runCatching {
            toInt()
        }.getOrDefault(defaultValue)
}
