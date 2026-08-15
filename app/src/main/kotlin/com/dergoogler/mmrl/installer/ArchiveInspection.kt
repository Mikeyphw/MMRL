package com.dergoogler.mmrl.installer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Deterministic inspection of the exact module archive that may later cross the root boundary. */
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
    val hasBootScripts: Boolean get() = scripts.any { it.substringAfterLast('/').lowercase(Locale.ROOT) in BOOT_SCRIPT_NAMES }
    val hasSensitiveChanges: Boolean get() = sePolicyFiles.isNotEmpty() || propertyFiles.isNotEmpty() || remoteExecutionFiles.isNotEmpty()

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
        internal val BOOT_SCRIPT_NAMES = setOf("service.sh", "post-fs-data.sh", "post-mount.sh", "boot-completed.sh", "customize.sh")
    }
}

internal data class ArchiveInspectionLimits(
    val maxEntries: Int = 20_000,
    val maxTotalUncompressed: Long = 1_073_741_824L,
    val maxSingleEntry: Long = 268_435_456L,
    val maxCompressionRatio: Long = 250L,
)

object ArchiveInspector {
    internal const val MAX_ENTRIES = 20_000
    internal const val MAX_TOTAL_UNCOMPRESSED = 1_073_741_824L
    internal const val MAX_SINGLE_ENTRY = 268_435_456L
    internal const val MAX_COMPRESSION_RATIO = 250L
    private const val MAX_CLASSIFICATION_BYTES = 1_048_576

    suspend fun inspect(file: File): ArchiveInspection = inspect(file, ArchiveInspectionLimits())

    internal suspend fun inspect(file: File, limits: ArchiveInspectionLimits): ArchiveInspection = withContext(Dispatchers.IO) {
        require(limits.maxEntries > 0)
        require(limits.maxTotalUncompressed > 0)
        require(limits.maxSingleEntry > 0)
        require(limits.maxCompressionRatio > 0)
        require(file.isFile) { "Archive does not exist: ${file.absolutePath}" }

        val scripts = linkedSetOf<String>()
        val binaries = linkedSetOf<String>()
        val apks = linkedSetOf<String>()
        val sePolicy = linkedSetOf<String>()
        val properties = linkedSetOf<String>()
        val remoteExecution = linkedSetOf<String>()
        val warnings = linkedSetOf<String>()
        val blocked = linkedSetOf<String>()
        val seen = linkedSetOf<String>()
        var entries = 0
        var compressed = 0L
        var uncompressed = 0L

        val central = runCatching { ZipCentralDirectory.read(file) }.getOrElse { error ->
            blocked += "Unable to validate ZIP central directory: ${error.message ?: error.javaClass.simpleName}"
            emptyMap()
        }

        ZipFile(file).use { zip ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                entries += 1
                if (entries > limits.maxEntries) {
                    blocked += "Archive contains more than ${limits.maxEntries} entries"
                    break
                }

                val normalized = normalizeName(entry.name)
                if (normalized == null) {
                    blocked += "Unsafe archive path: ${entry.name}"
                    continue
                }
                if (!seen.add(normalized)) {
                    blocked += "Duplicate normalized archive path: $normalized"
                    continue
                }
                if (entry.isDirectory) continue

                val metadata = central[entry.name]
                if (central.isNotEmpty() && metadata == null) {
                    blocked += "ZIP entry is missing from the validated central directory: ${entry.name}"
                    continue
                }
                if (metadata?.isSymlink == true) {
                    blocked += "Symbolic links are not permitted in module archives: $normalized"
                    continue
                }

                val packed = entry.compressedSize.coerceAtLeast(0L)
                compressed = safeAdd(compressed, packed)
                val sample = ByteArray(MAX_CLASSIFICATION_BYTES)
                var sampleSize = 0
                var entryBytes = 0L
                zip.getInputStream(entry).buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        entryBytes = safeAdd(entryBytes, count.toLong())
                        uncompressed = safeAdd(uncompressed, count.toLong())
                        if (sampleSize < sample.size) {
                            val copy = minOf(count, sample.size - sampleSize)
                            buffer.copyInto(sample, sampleSize, 0, copy)
                            sampleSize += copy
                        }
                        if (entryBytes > limits.maxSingleEntry) {
                            blocked += "Oversized archive entry: $normalized"
                            break
                        }
                        if (uncompressed > limits.maxTotalUncompressed) {
                            blocked += "Archive expands beyond 1 GiB"
                            break
                        }
                    }
                }

                if (entryBytes > 0L && packed <= 0L) {
                    blocked += "Invalid compressed-size metadata: $normalized"
                } else if (packed > 0 && entryBytes / packed > limits.maxCompressionRatio) {
                    blocked += "Suspicious compression ratio: $normalized"
                }

                val lower = normalized.lowercase(Locale.ROOT)
                val filename = lower.substringAfterLast('/')
                val bytes = sample.copyOf(sampleSize)
                val hasShebang = bytes.size >= 2 && bytes[0] == '#'.code.toByte() && bytes[1] == '!'.code.toByte()
                val isElf = bytes.size >= 4 && bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
                val executable = metadata?.isExecutable == true

                when {
                    filename.endsWith(".apk") -> apks += normalized
                    filename == "sepolicy.rule" || filename.endsWith(".cil") || filename.endsWith(".te") -> sePolicy += normalized
                    filename == "system.prop" || filename.endsWith(".prop") -> properties += normalized
                }

                val isScript = filename.endsWith(".sh") || filename in ArchiveInspection.BOOT_SCRIPT_NAMES || hasShebang
                if (isScript) {
                    scripts += normalized
                    if (containsRemoteExecutionReference(bytes)) remoteExecution += normalized
                }
                if (isElf || (!isScript && (executable || looksLikeNativeBinary(lower, filename)))) {
                    binaries += normalized
                }
                if (executable && !isScript && !isElf) warnings += "Executable archive entry: $normalized"

                if (uncompressed > limits.maxTotalUncompressed) break
            }
        }

        if (central.size != entries && entries <= limits.maxEntries) {
            blocked += "ZIP central-directory entry count does not match readable entries"
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

    internal fun normalizeName(name: String): String? {
        val raw = name.replace('\\', '/')
        if (raw.startsWith('/') || raw.contains('\u0000')) return null
        val parts = raw.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun looksLikeNativeBinary(path: String, filename: String): Boolean {
        if (path.startsWith("bin/") || path.contains("/bin/") || path.startsWith("system/bin/")) return true
        if (filename.endsWith(".so")) return true
        return filename.isNotBlank() && '.' !in filename && (path.contains("arm64") || path.contains("armeabi") || path.contains("x86"))
    }

    private fun containsRemoteExecutionReference(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        val text = data.decodeToString().lowercase(Locale.ROOT)
        return REMOTE_PATTERNS.any(text::contains)
    }

    internal fun sha256(file: File): String {
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

    private fun safeAdd(a: Long, b: Long): Long = Math.addExact(a, b)
    private val REMOTE_PATTERNS = listOf("curl ", "wget ", "http://", "https://", "busybox wget", "| sh", "|sh")
}

/** Minimal CEN parser used only for Unix mode/type metadata that java.util.zip.ZipFile does not expose. */
internal object ZipCentralDirectory {
    data class Metadata(val unixMode: Int) {
        val isSymlink: Boolean get() = unixMode and 0xF000 == 0xA000
        val isExecutable: Boolean get() = unixMode and 0x49 != 0
    }

    fun read(file: File): Map<String, Metadata> = RandomAccessFile(file, "r").use { raf ->
        val eocdOffset = findEocd(raf)
        raf.seek(eocdOffset + 4)
        val diskNumber = readU16(raf)
        val centralDisk = readU16(raf)
        val entriesOnDisk = readU16(raf)
        val entries = readU16(raf)
        val centralSize = readU32(raf)
        val centralOffset = readU32(raf)
        val commentLength = readU16(raf)

        require(diskNumber == 0 && centralDisk == 0) { "split ZIP archives are not supported for privileged module review" }
        require(entriesOnDisk == entries) { "split ZIP central-directory entry count mismatch" }
        require(entries != 0xffff) { "ZIP64 entry count is not supported for privileged module review" }
        require(centralSize != 0xffffffffL && centralOffset != 0xffffffffL) {
            "ZIP64 central directory is not supported for privileged module review"
        }
        require(entries <= ArchiveInspector.MAX_ENTRIES) { "too many central-directory entries" }
        require(eocdOffset + 22L + commentLength == raf.length()) { "trailing or malformed ZIP end record" }
        val centralEnd = Math.addExact(centralOffset, centralSize)
        require(centralOffset >= 0L && centralEnd <= eocdOffset) { "central directory is outside the ZIP bounds" }

        raf.seek(centralOffset)
        val metadata = buildMap(entries) {
            repeat(entries) {
                require(raf.filePointer + 46L <= centralEnd) { "truncated central-directory entry" }
                require(readU32(raf) == 0x02014b50L) { "invalid central-directory signature" }
                raf.skipBytes(16)
                val compressedSize = readU32(raf)
                val uncompressedSize = readU32(raf)
                val nameLength = readU16(raf)
                val extraLength = readU16(raf)
                val entryCommentLength = readU16(raf)
                val entryDisk = readU16(raf)
                raf.skipBytes(2)
                val externalAttributes = readU32(raf)
                val localHeaderOffset = readU32(raf)
                require(entryDisk == 0) { "split ZIP entry is not supported" }
                require(compressedSize != 0xffffffffL && uncompressedSize != 0xffffffffL && localHeaderOffset != 0xffffffffL) {
                    "ZIP64 entry metadata is not supported for privileged module review"
                }
                require(nameLength in 1..65535) { "invalid ZIP entry name length" }
                val variableLength = nameLength.toLong() + extraLength + entryCommentLength
                require(raf.filePointer + variableLength <= centralEnd) { "central-directory entry exceeds declared bounds" }
                val nameBytes = ByteArray(nameLength)
                raf.readFully(nameBytes)
                val name = nameBytes.toString(Charsets.UTF_8)
                require(name !in this) { "duplicate raw central-directory entry: $name" }
                put(name, Metadata(((externalAttributes ushr 16) and 0xffff).toInt()))
                raf.skipBytes(extraLength + entryCommentLength)
            }
        }
        require(raf.filePointer == centralEnd) { "central-directory size does not match parsed entries" }
        metadata
    }

    private fun findEocd(raf: RandomAccessFile): Long {
        val length = raf.length()
        require(length >= 22) { "truncated ZIP" }
        val search = minOf(length, 65_557L).toInt()
        val start = length - search
        val bytes = ByteArray(search)
        raf.seek(start)
        raf.readFully(bytes)
        for (i in bytes.size - 22 downTo 0) {
            if (u32(bytes, i) == 0x06054b50L) return start + i
        }
        error("end-of-central-directory record not found")
    }

    private fun readU16(raf: RandomAccessFile): Int = raf.readUnsignedByte() or (raf.readUnsignedByte() shl 8)
    private fun readU32(raf: RandomAccessFile): Long =
        readU16(raf).toLong() or (readU16(raf).toLong() shl 16)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)
}
