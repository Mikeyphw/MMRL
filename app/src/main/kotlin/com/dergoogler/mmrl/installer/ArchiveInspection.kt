package com.dergoogler.mmrl.installer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Human-readable, deterministic inspection of a module archive before privileged installation.
 * Malformed paths and archive-bomb signals block installation, while executable/script
 * capabilities are surfaced as review warnings.
 */
data class ArchiveInspection(
    val sha256: String,
    val entryCount: Int,
    val compressedBytes: Long,
    val uncompressedBytes: Long,
    val scripts: List<String>,
    val nativeBinaries: List<String>,
    val apks: List<String>,
    val sePolicyFiles: List<String>,
    val propertyFiles: List<String>,
    val remoteExecutionFiles: List<String>,
    val warnings: List<String>,
    val blockedReasons: List<String>,
) {
    val canInstall: Boolean get() = blockedReasons.isEmpty()
    val hasBootScripts: Boolean
        get() = scripts.any { path ->
            path.substringAfterLast('/').lowercase(Locale.ROOT) in BOOT_SCRIPT_NAMES
        }
    val hasSensitiveChanges: Boolean
        get() = sePolicyFiles.isNotEmpty() || propertyFiles.isNotEmpty() || remoteExecutionFiles.isNotEmpty()

    val summary: String
        get() = buildList {
            add("$entryCount files")
            if (scripts.isNotEmpty()) add("${scripts.size} scripts")
            if (nativeBinaries.isNotEmpty()) add("${nativeBinaries.size} binaries")
            if (apks.isNotEmpty()) add("${apks.size} APKs")
            if (sePolicyFiles.isNotEmpty()) add("SELinux changes")
            if (propertyFiles.isNotEmpty()) add("system properties")
            if (remoteExecutionFiles.isNotEmpty()) add("remote code references")
            if (blockedReasons.isNotEmpty()) add("blocked")
        }.joinToString(" · ")

    companion object {
        internal val BOOT_SCRIPT_NAMES =
            setOf(
                "service.sh",
                "post-fs-data.sh",
                "post-mount.sh",
                "boot-completed.sh",
                "customize.sh",
            )
    }
}

object ArchiveInspector {
    private const val MAX_ENTRIES = 20_000
    private const val MAX_TOTAL_UNCOMPRESSED = 1_073_741_824L
    private const val MAX_SINGLE_ENTRY = 268_435_456L
    private const val MAX_COMPRESSION_RATIO = 250L
    private const val MAX_SCRIPT_SCAN_BYTES = 1_048_576L

    suspend fun inspect(file: File): ArchiveInspection =
        withContext(Dispatchers.IO) {
            require(file.isFile) { "Archive does not exist: ${file.absolutePath}" }

            val scripts = linkedSetOf<String>()
            val binaries = linkedSetOf<String>()
            val apks = linkedSetOf<String>()
            val sePolicy = linkedSetOf<String>()
            val properties = linkedSetOf<String>()
            val remoteExecution = linkedSetOf<String>()
            val warnings = linkedSetOf<String>()
            val blocked = linkedSetOf<String>()
            var entries = 0
            var compressed = 0L
            var uncompressed = 0L

            ZipFile(file).use { zip ->
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    val entry = iterator.nextElement()
                    entries += 1
                    if (entries > MAX_ENTRIES) {
                        blocked += "Archive contains more than $MAX_ENTRIES entries"
                        break
                    }

                    val normalized = normalizeEntry(entry)
                    if (normalized == null) {
                        blocked += "Unsafe archive path: ${entry.name}"
                        continue
                    }
                    if (entry.isDirectory) continue

                    val size = entry.size.coerceAtLeast(0L)
                    val packed = entry.compressedSize.coerceAtLeast(0L)
                    compressed += packed
                    uncompressed += size

                    if (size > MAX_SINGLE_ENTRY) blocked += "Oversized archive entry: $normalized"
                    if (uncompressed > MAX_TOTAL_UNCOMPRESSED) blocked += "Archive expands beyond 1 GiB"
                    if (packed > 0 && size / packed > MAX_COMPRESSION_RATIO) {
                        blocked += "Suspicious compression ratio: $normalized"
                    }

                    val lower = normalized.lowercase(Locale.ROOT)
                    val filename = lower.substringAfterLast('/')
                    when {
                        filename.endsWith(".apk") -> apks += normalized
                        filename == "sepolicy.rule" || filename.endsWith(".cil") || filename.endsWith(".te") -> sePolicy += normalized
                        filename == "system.prop" || filename.endsWith(".prop") -> properties += normalized
                    }

                    if (filename.endsWith(".sh") || filename in ArchiveInspection.BOOT_SCRIPT_NAMES) {
                        scripts += normalized
                        if (containsRemoteExecutionReference(zip.getInputStream(entry))) {
                            remoteExecution += normalized
                        }
                    } else if (looksLikeNativeBinary(lower, filename)) {
                        binaries += normalized
                    }
                }
            }

            if (scripts.isNotEmpty()) warnings += "Archive contains executable shell scripts"
            if (apks.isNotEmpty()) warnings += "Archive bundles Android application packages"
            if (sePolicy.isNotEmpty()) warnings += "Archive changes SELinux policy"
            if (properties.isNotEmpty()) warnings += "Archive changes system properties"
            if (remoteExecution.isNotEmpty()) warnings += "A script references remote downloads or execution"

            ArchiveInspection(
                sha256 = sha256(file),
                entryCount = entries,
                compressedBytes = compressed,
                uncompressedBytes = uncompressed,
                scripts = scripts.toList(),
                nativeBinaries = binaries.toList(),
                apks = apks.toList(),
                sePolicyFiles = sePolicy.toList(),
                propertyFiles = properties.toList(),
                remoteExecutionFiles = remoteExecution.toList(),
                warnings = warnings.toList(),
                blockedReasons = blocked.toList(),
            )
        }

    private fun normalizeEntry(entry: ZipEntry): String? {
        val raw = entry.name.replace('\\', '/')
        if (raw.startsWith('/') || raw.contains('\u0000')) return null
        val parts = raw.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun looksLikeNativeBinary(path: String, filename: String): Boolean {
        if (path.startsWith("bin/") || path.contains("/bin/") || path.startsWith("system/bin/")) return true
        if (filename.endsWith(".so")) return true
        return filename.isNotBlank() && '.' !in filename && (path.contains("arm64") || path.contains("armeabi") || path.contains("x86"))
    }

    private fun containsRemoteExecutionReference(input: InputStream): Boolean =
        input.use { stream ->
            val data = ByteArray(MAX_SCRIPT_SCAN_BYTES.toInt())
            val count = stream.read(data)
            if (count <= 0) return@use false
            val text = data.decodeToString(0, count).lowercase(Locale.ROOT)
            REMOTE_PATTERNS.any(text::contains)
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val REMOTE_PATTERNS =
        listOf("curl ", "wget ", "http://", "https://", "busybox wget", "| sh", "|sh")
}
