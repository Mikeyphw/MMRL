package com.dergoogler.mmrl.debug

import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.platform.file.SuFile
import com.dergoogler.mmrl.platform.file.useLines
import java.io.File

class AshReXcueDebugProbe(
    private val activeModuleRoot: File = File("/data/adb/modules"),
    private val stagedModuleRoot: File = File("/data/adb/modules_update"),
) {
    fun run(): DebugProbeResult {
        val rootSnapshots = listOf(
            rootSnapshot(rootAccessFile(activeModuleRoot), "active root"),
            rootSnapshot(rootAccessFile(stagedModuleRoot), "staged root"),
        )
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
                else -> "No AshReXcue-compatible module folder was found. Root directory visibility is included below."
            },
            evidence = listOf(DebugEvidence("aliases", ASH_ALIASES.joinToString())) +
                rootSnapshots.flatMap { it.toEvidence() } +
                if (candidates.isEmpty()) emptyList() else candidates.flatMap { it.toEvidence() },
            remedies = if (recognized.isEmpty()) {
                listOf("Check the root previews and module.prop identity fields below; share the report if your module uses a new AshReXcue id or name alias.")
            } else {
                emptyList()
            },
        )
    }

    private fun inspectRoot(root: File, active: Boolean): List<AshCandidateRow> {
        val rootFile = rootAccessFile(root)
        if (!safeIsDirectory(rootFile)) return emptyList()
        val preferred = ASH_ALIASES.map { alias -> suChild(rootFile, alias) }
        val discovered = listRootNames(rootFile).names
            .map { name -> suChild(rootFile, name) }
            .filter(::safeIsDirectory)
        return (preferred + discovered)
            .distinctBy { it.absolutePath }
            .mapNotNull { directory -> inspectDirectory(directory, active) }
    }

    private fun inspectDirectory(directory: File, active: Boolean): AshCandidateRow? {
        if (!safeIsDirectory(directory)) return null
        val propFile = suChild(directory, "module.prop")
        val properties = readModuleProperties(propFile)
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
            modulePropReadable = safeIsFile(propFile) && properties.isNotEmpty(),
            disabled = safeIsFile(suChild(directory, "disable")),
            removalPending = safeIsFile(suChild(directory, "remove")),
            actionSh = safeIsFile(suChild(directory, "action.sh")),
            serviceSh = safeIsFile(suChild(directory, "service.sh")),
            controlScript = safeIsFile(suChild(directory, "ashrexcuectl")),
            bundledJq = safeIsFile(suChild(directory, "bin/jq")) || safeIsFile(suChild(directory, "jq")),
        )
    }

    private fun rootSnapshot(root: File, label: String): ModuleRootSnapshot {
        val listing = listRootNames(root)
        return ModuleRootSnapshot(
            label = label,
            path = root.absolutePath,
            exists = safeExists(root),
            directory = safeIsDirectory(root),
            readable = safeCanRead(root),
            childCount = listing.names.size,
            childrenPreview = listing.names.sorted().take(ROOT_PREVIEW_LIMIT),
            listingError = listing.error.orEmpty(),
        )
    }

    private fun listRootNames(root: File): RootListing = runCatching {
        RootListing(root.list()?.toList().orEmpty(), null)
    }.getOrElse { error -> RootListing(emptyList(), error.message ?: error::class.java.simpleName) }

    private fun readModuleProperties(file: File): Map<String, String> {
        if (!safeIsFile(file)) return emptyMap()
        return runCatching {
            val readLines: ((Sequence<String>) -> Map<String, String>) = { lines ->
                lines.map(String::trim)
                    .filter { line -> line.isNotEmpty() && !line.startsWith('#') && '=' in line }
                    .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
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

    private fun safeExists(file: File): Boolean = runCatching { file.exists() }.getOrDefault(false)

    private fun safeIsDirectory(file: File): Boolean = runCatching { file.isDirectory }.getOrDefault(false)

    private fun safeIsFile(file: File): Boolean = runCatching { file.isFile }.getOrDefault(false)

    private fun safeCanRead(file: File): Boolean = runCatching { file.canRead() }.getOrDefault(false)

    private data class AshCandidateRow(
        val active: Boolean,
        val folder: String,
        val moduleId: String,
        val name: String,
        val recognized: Boolean,
        val canonical: String,
        val modulePropReadable: Boolean,
        val disabled: Boolean,
        val removalPending: Boolean,
        val actionSh: Boolean,
        val serviceSh: Boolean,
        val controlScript: Boolean,
        val bundledJq: Boolean,
    ) {
        fun toEvidence(): List<DebugEvidence> = listOf(
            DebugEvidence("${if (active) "active" else "staged"}:$folder", "id=$moduleId, name=$name, canonical=$canonical, recognized=$recognized, module.prop=$modulePropReadable"),
            DebugEvidence("$folder scripts", "action.sh=$actionSh, service.sh=$serviceSh, ashrexcuectl=$controlScript, bundledJq=$bundledJq"),
            DebugEvidence("$folder markers", "disabled=$disabled, remove=$removalPending"),
        )
    }

    private data class RootListing(
        val names: List<String>,
        val error: String?,
    )

    private data class ModuleRootSnapshot(
        val label: String,
        val path: String,
        val exists: Boolean,
        val directory: Boolean,
        val readable: Boolean,
        val childCount: Int,
        val childrenPreview: List<String>,
        val listingError: String,
    ) {
        fun toEvidence(): List<DebugEvidence> = listOf(
            DebugEvidence(label, "path=$path, exists=$exists, directory=$directory, readable=$readable, children=$childCount"),
            DebugEvidence("$label preview", childrenPreview.joinToString().ifBlank { "none" }),
            DebugEvidence("$label list error", listingError.ifBlank { "none" }),
        )
    }

    private companion object {
        const val ROOT_PREVIEW_LIMIT = 30
        const val DATA_ADB_ROOT = "/data/adb/"

        val ASH_ALIASES = listOf(
            "ashlooper",
            "ashrexcue",
            "AshReXcue",
            "AshLooper",
            "AshReXcue_BootLoop_Protector",
            "AshReXcue_Bootloop_Protector",
            "ashrexcue_bootloop_protector",
            "ashrexcuebootloopprotector",
        )
    }
}
