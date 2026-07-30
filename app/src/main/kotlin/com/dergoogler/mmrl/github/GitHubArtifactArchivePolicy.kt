package com.dergoogler.mmrl.github

import java.io.File
import java.net.URI
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Shared policy for GitHub Actions artifact archives.
 *
 * GitHub's Actions artifact endpoint always looks like a ZIP download, but projects upload
 * different shapes: a direct module ZIP, nested ZIP files, root module files, or one wrapped
 * module directory. The resolver and the final DownloadService must classify the content shape,
 * not just the artifact filename.
 */
internal object GitHubArtifactArchivePolicy {
    fun isActionsArtifactArchive(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host.orEmpty()
        val parts = uri.path.trim('/').split('/').filter(String::isNotBlank)
        if (host.equals("api.github.com", ignoreCase = true)) {
            return parts.size >= 7 &&
                parts[0] == "repos" &&
                parts[3] == "actions" &&
                parts[4] == "artifacts" &&
                parts.last().equals("zip", ignoreCase = true)
        }
        return false
    }

    fun moduleRoot(entryNames: List<String>): String? {
        val normalized = normalizedEntryNames(entryNames)
        if (normalized.any { it.equals("module.prop", ignoreCase = true) }) return ""

        return normalized
            .mapNotNull { name ->
                val lower = name.lowercase(Locale.ROOT)
                when {
                    lower.endsWith("/module.prop") -> name.dropLast("/module.prop".length)
                    else -> null
                }
            }.filter(String::isNotBlank)
            .distinct()
            .maxByOrNull { root ->
                normalized.count { it == root || it.startsWith("$root/") }
            }?.let { root -> "$root/" }
    }

    fun analyze(
        entryNames: List<String>,
        preferredEntryName: String? = null,
        forcedStrategy: GitHubArtifactStrategy = GitHubArtifactStrategy.AUTO,
    ): GitHubArtifactArchiveAnalysis {
        val normalized = normalizedEntryNames(entryNames)
        val nestedZipNames = normalized.filter { it.endsWith(".zip", ignoreCase = true) }
        val root = moduleRoot(normalized)
        val preferredNestedZip =
            preferredEntryName
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { preferred -> nestedZipNames.firstOrNull { it == preferred } }
        val selectedNestedZip = preferredNestedZip ?: nestedZipNames.maxByOrNull(::candidateScore)
        val autoStrategy =
            when {
                root == "" -> GitHubArtifactStrategy.EXTRACTED_MODULE_LAYOUT
                selectedNestedZip != null -> GitHubArtifactStrategy.NESTED_ZIP
                root != null -> GitHubArtifactStrategy.SINGLE_FOLDER_MODULE_LAYOUT
                else -> GitHubArtifactStrategy.AUTO
            }
        val strategy = if (forcedStrategy == GitHubArtifactStrategy.AUTO) autoStrategy else forcedStrategy
        val selectedEntry =
            when (strategy) {
                GitHubArtifactStrategy.NESTED_ZIP -> selectedNestedZip
                GitHubArtifactStrategy.DIRECT_MODULE_ZIP -> root?.takeIf { it.isEmpty() }?.let { "module.prop" }
                GitHubArtifactStrategy.EXTRACTED_MODULE_LAYOUT -> root?.takeIf { it.isEmpty() }?.let { "module.prop" }
                GitHubArtifactStrategy.SINGLE_FOLDER_MODULE_LAYOUT -> root?.takeIf { it.isNotEmpty() }
                GitHubArtifactStrategy.AUTO -> null
            }
        val installable =
            when (strategy) {
                GitHubArtifactStrategy.DIRECT_MODULE_ZIP -> root == ""
                GitHubArtifactStrategy.NESTED_ZIP -> selectedNestedZip != null
                GitHubArtifactStrategy.EXTRACTED_MODULE_LAYOUT -> root == ""
                GitHubArtifactStrategy.SINGLE_FOLDER_MODULE_LAYOUT -> root != null && root.isNotEmpty()
                GitHubArtifactStrategy.AUTO -> false
            }
        val reason =
            when {
                installable && strategy == GitHubArtifactStrategy.DIRECT_MODULE_ZIP -> "module.prop at artifact root"
                installable && strategy == GitHubArtifactStrategy.NESTED_ZIP -> "nested module ZIP selected"
                installable && strategy == GitHubArtifactStrategy.EXTRACTED_MODULE_LAYOUT -> "module.prop at artifact root"
                installable && strategy == GitHubArtifactStrategy.SINGLE_FOLDER_MODULE_LAYOUT -> "module.prop inside ${root.orEmpty().trimEnd('/')}"
                forcedStrategy != GitHubArtifactStrategy.AUTO -> "forced strategy did not match artifact contents"
                else -> "no module.prop and no nested module ZIP"
            }
        return GitHubArtifactArchiveAnalysis(
            strategy = strategy,
            installable = installable,
            entryCount = normalized.size,
            nestedZipCount = nestedZipNames.size,
            modulePropLocations = normalized.filter { it.equals("module.prop", true) || it.endsWith("/module.prop", true) },
            moduleRoot = root,
            selectedEntryName = selectedEntry,
            reason = reason,
        )
    }

    fun materializeModuleZip(
        archive: File,
        targetDirectory: File,
        outputNamePrefix: String,
        preferredEntryName: String? = null,
        forcedStrategy: GitHubArtifactStrategy = GitHubArtifactStrategy.AUTO,
        score: (String) -> Int = ::candidateScore,
    ): GitHubArtifactMaterializedFile {
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            val analysis = analyze(
                entryNames = entries.map { it.name },
                preferredEntryName = preferredEntryName,
                forcedStrategy = forcedStrategy,
            )
            require(analysis.installable) {
                "GitHub artifact does not contain an installable module. " +
                    "Strategy=${analysis.strategy.queryValue}; entries=${analysis.entryCount}; " +
                    "nestedZip=${analysis.nestedZipCount}; moduleProps=${analysis.modulePropLocations.joinToString().ifBlank { "none" }}. " +
                    analysis.reason
            }

            return when (analysis.strategy) {
                GitHubArtifactStrategy.DIRECT_MODULE_ZIP,
                GitHubArtifactStrategy.EXTRACTED_MODULE_LAYOUT,
                -> GitHubArtifactMaterializedFile(archive, analysis)

                GitHubArtifactStrategy.NESTED_ZIP -> {
                    val entry =
                        analysis.selectedEntryName
                            ?.let { selected -> entries.firstOrNull { it.name == selected } }
                            ?: entries
                                .filter { it.name.endsWith(".zip", ignoreCase = true) }
                                .maxByOrNull { score(it.name) }
                            ?: error("GitHub Actions artifact does not contain a module ZIP")
                    val output = File(targetDirectory, safeFileName("$outputNamePrefix-${entry.name.substringAfterLast('/')}"))
                    output.parentFile?.mkdirs()
                    output.delete()
                    zip.getInputStream(entry).buffered().use { input ->
                        output.outputStream().buffered().use { outputStream ->
                            input.copyTo(outputStream)
                        }
                    }
                    require(output.isFile && output.length() > 0L) { "Extracted GitHub Actions module ZIP is empty" }
                    GitHubArtifactMaterializedFile(output, analysis.copy(selectedEntryName = entry.name))
                }

                GitHubArtifactStrategy.SINGLE_FOLDER_MODULE_LAYOUT -> {
                    val root = analysis.moduleRoot.orEmpty()
                    val output = File(targetDirectory, safeFileName("$outputNamePrefix-module.zip"))
                    repackageModuleDirectory(zip, entries, root, output)
                    GitHubArtifactMaterializedFile(output, analysis)
                }

                GitHubArtifactStrategy.AUTO -> error("GitHub artifact strategy auto did not resolve an installable module")
            }
        }
    }

    fun downloadFailureMessage(
        url: String,
        code: Int,
        hasToken: Boolean,
        bodySnippet: String?,
    ): String {
        val isArtifact = isActionsArtifactArchive(url)
        val detail = bodySnippet?.trim()?.takeIf(String::isNotBlank)?.take(220)
        val base =
            when {
                isArtifact -> "HTTP $code while downloading GitHub Actions artifact"
                else -> "HTTP $code while downloading GitHub file"
            }
        val guidance =
            when {
                isArtifact && code in setOf(401, 403) && !hasToken ->
                    "Add a GitHub token with Actions read access, then retry."
                isArtifact && code in setOf(401, 403) ->
                    "Check that the saved GitHub token can read Actions artifacts for this repository."
                isArtifact && code == 404 ->
                    "The nightly artifact may have expired or been deleted. Refresh the source and retry."
                isArtifact ->
                    "Refresh the nightly source, check the artifact regex, then retry."
                else ->
                    "Retry the download or check the source URL."
            }
        return buildString {
            append(base)
            append(". ")
            append(guidance)
            if (detail != null) {
                append(" Server said: ")
                append(detail)
            }
        }
    }

    private fun normalizedEntryNames(entryNames: List<String>): List<String> =
        entryNames
            .asSequence()
            .map { it.trim().trimStart('/') }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("__MACOSX/", ignoreCase = true) }
            .toList()

    private fun repackageModuleDirectory(
        zip: ZipFile,
        entries: List<ZipEntry>,
        moduleRoot: String,
        output: File,
    ) {
        output.delete()
        output.parentFile?.mkdirs()
        val written = mutableSetOf<String>()
        ZipOutputStream(output.outputStream().buffered()).use { target ->
            entries
                .filter { it.name.trimStart('/').startsWith(moduleRoot) }
                .forEach { entry ->
                    val relative = entry.name.trimStart('/').removePrefix(moduleRoot).trimStart('/')
                    if (relative.isBlank() || !written.add(relative)) return@forEach
                    val targetEntry = ZipEntry(relative).apply { time = entry.time }
                    target.putNextEntry(targetEntry)
                    zip.getInputStream(entry).buffered().use { input -> input.copyTo(target) }
                    target.closeEntry()
                }
        }
        require(output.isFile && output.length() > 0L) { "Repacked GitHub Actions module ZIP is empty" }
    }

    private fun safeFileName(value: String): String =
        value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "github-module.zip" }

    private fun candidateScore(name: String): Int {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.contains("release") -> 80
            lower.contains("zygisk") -> 60
            lower.contains("debug") -> -80
            lower.contains("symbols") || lower.contains("mapping") -> -120
            else -> 0
        } + if (lower.endsWith(".zip")) 40 else 0
    }
}

enum class GitHubArtifactStrategy(val queryValue: String) {
    AUTO("auto"),
    DIRECT_MODULE_ZIP("directZip"),
    NESTED_ZIP("nestedZip"),
    EXTRACTED_MODULE_LAYOUT("extractedModuleLayout"),
    SINGLE_FOLDER_MODULE_LAYOUT("singleFolderModuleLayout"),
    ;

    companion object {
        fun fromQuery(value: String?): GitHubArtifactStrategy =
            when (value?.trim()?.lowercase(Locale.ROOT)) {
                "directzip", "direct_zip", "direct-module-zip", "directmodulezip" -> DIRECT_MODULE_ZIP
                "nestedzip", "nested_zip", "nested-module-zip", "nestedmodulezip" -> NESTED_ZIP
                "extractedmodulelayout", "extracted_module_layout", "module-root", "module_root", "root" -> EXTRACTED_MODULE_LAYOUT
                "singlefoldermodulelayout", "single_folder_module_layout", "folder", "wrapped" -> SINGLE_FOLDER_MODULE_LAYOUT
                else -> AUTO
            }
    }
}

data class GitHubArtifactArchiveAnalysis(
    val strategy: GitHubArtifactStrategy,
    val installable: Boolean,
    val entryCount: Int,
    val nestedZipCount: Int,
    val modulePropLocations: List<String>,
    val moduleRoot: String?,
    val selectedEntryName: String?,
    val reason: String,
) {
    val summary: String
        get() =
            "strategy=${strategy.queryValue}; installable=$installable; entries=$entryCount; " +
                "nestedZip=$nestedZipCount; moduleRoot=${moduleRoot ?: "none"}; reason=$reason"
}

data class GitHubArtifactMaterializedFile(
    val file: File,
    val analysis: GitHubArtifactArchiveAnalysis,
)
