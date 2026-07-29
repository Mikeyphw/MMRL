package com.dergoogler.mmrl.ash.root

import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.platform.file.SuFile
import com.dergoogler.mmrl.platform.file.useLines
import java.io.File

/** Resolves the installed AshReXcue module across active and staged module roots. */
internal class AshModuleLocator(
    private val activeRoot: File = DEFAULT_ACTIVE_ROOT,
    private val updateRoot: File = DEFAULT_UPDATE_ROOT,
) {
    internal data class Inspection(
        val installed: Boolean,
        val active: Boolean,
        val directory: File?,
        val folder: String,
        val properties: Map<String, String>,
        val controlScript: File?,
        val disabled: Boolean,
        val removalPending: Boolean,
        val updatePending: Boolean,
        val source: String,
    )

    fun locateControlScript(): File? = inspect().controlScript

    fun inspect(): Inspection {
        val active = findMatchingModule(activeRoot)
        val staged = findMatchingModule(updateRoot)
        val selected = active ?: staged

        return Inspection(
            installed = selected != null,
            active = active != null,
            directory = selected?.directory,
            folder = selected?.directory?.name.orEmpty(),
            properties = selected?.properties.orEmpty(),
            controlScript = active?.controlScript,
            disabled = active?.directory?.let { safeIsFile(suChild(it, DISABLE_MARKER)) } == true,
            removalPending = active?.directory?.let { safeIsFile(suChild(it, REMOVE_MARKER)) } == true,
            updatePending = staged != null,
            source = when {
                active != null -> "active"
                staged != null -> "staged"
                else -> "none"
            },
        )
    }

    private fun findMatchingModule(root: File): Candidate? {
        val rootFile = rootAccessFile(root)
        if (!safeIsDirectory(rootFile)) return null

        MODULE_ID_ALIASES.asSequence()
            .map { alias -> suChild(rootFile, alias) }
            .mapNotNull(::candidateFromDirectory)
            .firstOrNull()
            ?.let { return it }

        return runCatching { rootFile.list()?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .asSequence()
            .map { name -> suChild(rootFile, name) }
            .filter(::safeIsDirectory)
            .sortedBy(File::getAbsolutePath)
            .mapNotNull(::candidateFromDirectory)
            .firstOrNull()
    }

    private fun candidateFromDirectory(moduleDirectory: File): Candidate? {
        if (!safeIsDirectory(moduleDirectory)) return null
        val properties = readModuleProperties(suChild(moduleDirectory, MODULE_PROP))
        val moduleId = properties["id"].orEmpty()
        val moduleName = properties["name"].orEmpty()
        val folderName = moduleDirectory.name
        val matches = sequenceOf(moduleId, folderName, moduleName)
            .any(ModuleIdentity::isAshReXcue)
        if (!matches) return null

        val controlScript = suChild(moduleDirectory, CONTROL_SCRIPT).takeIf(::safeIsFile)
        return Candidate(moduleDirectory, properties, controlScript)
    }

    private fun readModuleProperties(file: File): Map<String, String> {
        if (!safeIsFile(file)) return emptyMap()
        return runCatching {
            val readLines: ((Sequence<String>) -> Map<String, String>) = { lines ->
                lines.map(String::trim)
                    .filter { line -> line.isNotEmpty() && !line.startsWith('#') && '=' in line }
                    .map { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
                    .filter { (key, _) -> key.isNotEmpty() }
                    .toMap()
            }
            if (file is SuFile) {
                file.useLines(block = readLines)
            } else {
                file.useLines(block = readLines)
            }
        }.getOrDefault(emptyMap())
    }

    private fun rootAccessFile(root: File): File = if (root.absolutePath.startsWith(DATA_ADB_ROOT)) {
        SuFile(root.absolutePath)
    } else {
        root
    }

    private fun suChild(parent: File, child: String): File = if (parent is SuFile || parent.absolutePath.startsWith(DATA_ADB_ROOT)) {
        SuFile(parent.absolutePath.trimEnd('/') + "/" + child)
    } else {
        File(parent, child)
    }

    private fun safeIsDirectory(file: File): Boolean = runCatching { file.isDirectory }.getOrDefault(false)

    private fun safeIsFile(file: File): Boolean = runCatching { file.isFile }.getOrDefault(false)

    private data class Candidate(
        val directory: File,
        val properties: Map<String, String>,
        val controlScript: File?,
    )

    private companion object {
        const val CONTROL_SCRIPT = "ashrexcuectl"
        const val MODULE_PROP = "module.prop"
        const val DISABLE_MARKER = "disable"
        const val REMOVE_MARKER = "remove"
        const val DATA_ADB_ROOT = "/data/adb/"

        val MODULE_ID_ALIASES = listOf(
            "AshLooper",
            "AshReXcue",
            "ashrexcue",
            "AshReXcue_BootLoop_Protector",
            "AshReXcue_Bootloop_Protector",
            "ashrexcue_bootloop_protector",
        )
        val DEFAULT_ACTIVE_ROOT = File("/data/adb/modules")
        val DEFAULT_UPDATE_ROOT = File("/data/adb/modules_update")
    }
}
