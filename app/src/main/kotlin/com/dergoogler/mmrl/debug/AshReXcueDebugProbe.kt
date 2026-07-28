package com.dergoogler.mmrl.debug

import com.dergoogler.mmrl.model.ModuleIdentity
import java.io.File

class AshReXcueDebugProbe(
    private val activeModuleRoot: File = File("/data/adb/modules"),
    private val stagedModuleRoot: File = File("/data/adb/modules_update"),
) {
    fun run(): DebugProbeResult {
        val candidates = listOf(activeModuleRoot to true, stagedModuleRoot to false)
            .flatMap { (root, active) -> inspectRoot(root, active) }
        val recognized = candidates.filter { it.recognized }
        val activeRecognized = recognized.filter { it.active }
        return DebugProbeResult(
            id = "ashrexcue-module-identity",
            title = "AshReXcue module identity",
            group = DebugProbeGroup.ASH_REXCUE,
            status = when {
                activeRecognized.isNotEmpty() -> DebugProbeStatus.PASS
                recognized.isNotEmpty() -> DebugProbeStatus.WARN
                candidates.isNotEmpty() -> DebugProbeStatus.WARN
                else -> DebugProbeStatus.FAIL
            },
            summary = when {
                activeRecognized.isNotEmpty() -> "Active AshReXcue-compatible module found."
                recognized.isNotEmpty() -> "Only staged AshReXcue-compatible module found; reboot may be required."
                candidates.isNotEmpty() -> "Potential AshReXcue folder found, but aliases did not match."
                else -> "No AshReXcue-compatible module folder was found."
            },
            evidence = if (candidates.isEmpty()) {
                listOf(
                    DebugEvidence("aliases", ASH_ALIASES.joinToString()),
                    DebugEvidence("active root", activeModuleRoot.absolutePath),
                    DebugEvidence("staged root", stagedModuleRoot.absolutePath),
                )
            } else {
                candidates.flatMap { it.toEvidence() }
            },
            remedies = if (recognized.isEmpty()) {
                listOf("Share this report if your module.prop uses a new AshReXcue id or name alias.")
            } else {
                emptyList()
            },
        )
    }

    private fun inspectRoot(root: File, active: Boolean): List<AshCandidateRow> {
        if (!root.isDirectory) return emptyList()
        val preferred = ASH_ALIASES.map { File(root, it) }
        val discovered = runCatching { root.listFiles().orEmpty().filter(File::isDirectory) }.getOrDefault(emptyList())
        return (preferred + discovered)
            .distinctBy { it.absolutePath }
            .mapNotNull { directory -> inspectDirectory(directory, active) }
    }

    private fun inspectDirectory(directory: File, active: Boolean): AshCandidateRow? {
        if (!directory.isDirectory) return null
        val properties = readModuleProperties(directory.resolve("module.prop"))
        val id = properties["id"].orEmpty()
        val name = properties["name"].orEmpty()
        val folder = directory.name
        val recognized = sequenceOf(id, name, folder).any(ModuleIdentity::isAshReXcue)
        val plausible = recognized || sequenceOf(id, name, folder)
            .joinToString(" ")
            .lowercase()
            .contains("ash")
        if (!plausible) return null
        return AshCandidateRow(
            active = active,
            folder = folder,
            moduleId = id.ifBlank { folder },
            name = name.ifBlank { id.ifBlank { folder } },
            recognized = recognized,
            canonical = ModuleIdentity.canonical(id.ifBlank { folder }),
            disabled = directory.resolve("disable").isFile,
            removalPending = directory.resolve("remove").isFile,
            actionSh = directory.resolve("action.sh").isFile,
            serviceSh = directory.resolve("service.sh").isFile,
            controlScript = directory.resolve("ashrexcuectl").isFile,
            bundledJq = directory.resolve("bin/jq").isFile || directory.resolve("jq").isFile,
        )
    }

    private fun readModuleProperties(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            file.useLines { lines ->
                lines.map(String::trim)
                    .filter { line -> line.isNotEmpty() && !line.startsWith('#') && '=' in line }
                    .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
            }
        }.getOrDefault(emptyMap())
    }

    private data class AshCandidateRow(
        val active: Boolean,
        val folder: String,
        val moduleId: String,
        val name: String,
        val recognized: Boolean,
        val canonical: String,
        val disabled: Boolean,
        val removalPending: Boolean,
        val actionSh: Boolean,
        val serviceSh: Boolean,
        val controlScript: Boolean,
        val bundledJq: Boolean,
    ) {
        fun toEvidence(): List<DebugEvidence> = listOf(
            DebugEvidence("${if (active) "active" else "staged"}:$folder", "id=$moduleId, name=$name, canonical=$canonical, recognized=$recognized"),
            DebugEvidence("$folder scripts", "action.sh=$actionSh, service.sh=$serviceSh, ashrexcuectl=$controlScript, bundledJq=$bundledJq"),
            DebugEvidence("$folder markers", "disabled=$disabled, remove=$removalPending"),
        )
    }

    private companion object {
        val ASH_ALIASES = listOf(
            "ashlooper",
            "ashrexcue",
            "AshReXcue",
            "AshReXcue_Bootloop_Protector",
            "ashrexcuebootloopprotector",
        )
    }
}
