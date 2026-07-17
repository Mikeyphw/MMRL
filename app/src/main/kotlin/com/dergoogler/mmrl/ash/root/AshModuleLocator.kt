package com.dergoogler.mmrl.ash.root

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
            disabled = active?.directory?.resolve(DISABLE_MARKER)?.isFile == true,
            removalPending = active?.directory?.resolve(REMOVE_MARKER)?.isFile == true,
            updatePending = staged != null,
            source = when {
                active != null -> "active"
                staged != null -> "staged"
                else -> "none"
            },
        )
    }

    private fun findMatchingModule(root: File): Candidate? {
        if (!root.isDirectory) return null

        MODULE_ID_ALIASES.asSequence()
            .map { alias -> File(root, alias) }
            .mapNotNull(::candidateFromDirectory)
            .firstOrNull()
            ?.let { return it }

        return root.listFiles().orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .sortedBy(File::getAbsolutePath)
            .mapNotNull(::candidateFromDirectory)
            .firstOrNull()
    }

    private fun candidateFromDirectory(moduleDirectory: File): Candidate? {
        if (!moduleDirectory.isDirectory) return null
        val properties = readModuleProperties(File(moduleDirectory, MODULE_PROP))
        val moduleId = properties["id"].orEmpty()
        val moduleName = properties["name"].orEmpty()
        val folderName = moduleDirectory.name
        val matches = MODULE_ID_ALIASES.any { alias -> alias.equals(moduleId, ignoreCase = true) } ||
            MODULE_ID_ALIASES.any { alias -> alias.equals(folderName, ignoreCase = true) } ||
            moduleName.contains("AshReXcue", ignoreCase = true)
        if (!matches) return null

        val controlScript = File(moduleDirectory, CONTROL_SCRIPT).takeIf(File::isFile)
        return Candidate(moduleDirectory, properties, controlScript)
    }

    private fun readModuleProperties(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            file.useLines { lines ->
                lines.map(String::trim)
                    .filter { line -> line.isNotEmpty() && !line.startsWith('#') && '=' in line }
                    .map { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
                    .filter { (key, _) -> key.isNotEmpty() }
                    .toMap()
            }
        }.getOrDefault(emptyMap())
    }

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

        val MODULE_ID_ALIASES = listOf("AshLooper", "AshReXcue", "ashrexcue")
        val DEFAULT_ACTIVE_ROOT = File("/data/adb/modules")
        val DEFAULT_UPDATE_ROOT = File("/data/adb/modules_update")
    }
}
