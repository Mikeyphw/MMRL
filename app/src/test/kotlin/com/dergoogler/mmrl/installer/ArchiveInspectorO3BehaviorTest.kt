package com.dergoogler.mmrl.installer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveInspectorO3BehaviorTest {
    @Test fun `duplicate normalized path is blocked`() = runBlocking {
        val archive = zip("module.prop" to "id=test", "a//b" to "one", "a/b" to "two")
        val result = ArchiveInspector.inspect(archive)
        assertFalse(result.canInstall)
        assertTrue(result.blockedReasons.any { it.contains("Duplicate normalized") })
    }

    @Test fun `actual inflated bytes enforce single entry and total limits`() = runBlocking {
        val archive = zip("module.prop" to "id=test", "payload.bin" to "x".repeat(4096))
        val result = ArchiveInspector.inspect(
            archive,
            ArchiveInspectionLimits(maxEntries = 10, maxSingleEntry = 1024, maxTotalUncompressed = 2048, maxCompressionRatio = 10_000),
        )
        assertFalse(result.canInstall)
        assertTrue(result.blockedReasons.any { it.contains("Oversized archive entry") || it.contains("expands beyond") })
    }

    @Test fun `shebang and ELF magic classify executable content regardless of filename`() = runBlocking {
        val archive = binaryZip(
            "module.prop" to "id=test".toByteArray(),
            "odd-name" to "#!/system/bin/sh\necho hi".toByteArray(),
            "payload.data" to byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 1, 2),
        )
        val result = ArchiveInspector.inspect(archive)
        assertTrue("odd-name" in result.scripts)
        assertTrue("payload.data" in result.nativeBinaries)
    }

    @Test fun `unix symlink entry is blocked from privileged review`() = runBlocking {
        val archive = zip("module.prop" to "id=test", "link" to "target")
        patchExternalUnixMode(archive, "link", 0xA1FF)
        val result = ArchiveInspector.inspect(archive)
        assertFalse(result.canInstall)
        assertTrue(result.blockedReasons.any { it.contains("Symbolic links") })
    }


    @Test fun `split zip end record is rejected before privileged review`() = runBlocking {
        val archive = zip("module.prop" to "id=test")
        patchEocdDiskNumber(archive, 1)
        val result = ArchiveInspector.inspect(archive)
        assertFalse(result.canInstall)
        assertTrue(result.blockedReasons.any { it.contains("split ZIP") })
    }

    @Test fun `inspection hash binds exact archive bytes`() = runBlocking {
        val first = zip("module.prop" to "id=test", "file" to "one")
        val second = zip("module.prop" to "id=test", "file" to "two")
        assertNotEquals(ArchiveInspector.inspect(first).sha256, ArchiveInspector.inspect(second).sha256)
    }

    private fun zip(vararg entries: Pair<String, String>) = binaryZip(*entries.map { it.first to it.second.toByteArray() }.toTypedArray())

    private fun binaryZip(vararg entries: Pair<String, ByteArray>): File {
        val file = kotlin.io.path.createTempFile(suffix = ".zip").toFile().apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, body) ->
                output.putNextEntry(ZipEntry(name))
                output.write(body)
                output.closeEntry()
            }
        }
        return file
    }


    private fun patchEocdDiskNumber(file: File, disk: Int) {
        RandomAccessFile(file, "rw").use { raf ->
            var offset = raf.length() - 22
            while (offset >= 0) {
                raf.seek(offset)
                if (readU32(raf) == 0x06054b50L) {
                    raf.seek(offset + 4)
                    raf.write(disk and 0xff)
                    raf.write((disk ushr 8) and 0xff)
                    return
                }
                offset--
            }
            error("EOCD not found")
        }
    }

    private fun patchExternalUnixMode(file: File, targetName: String, mode: Int) {
        RandomAccessFile(file, "rw").use { raf ->
            var offset = 0L
            while (offset <= raf.length() - 46) {
                raf.seek(offset)
                if (readU32(raf) == 0x02014b50L) {
                    raf.seek(offset + 28)
                    val nameLength = readU16(raf)
                    val extraLength = readU16(raf)
                    val commentLength = readU16(raf)
                    raf.seek(offset + 46)
                    val name = ByteArray(nameLength).also(raf::readFully).toString(Charsets.UTF_8)
                    if (name == targetName) {
                        raf.seek(offset + 38)
                        writeU32(raf, (mode.toLong() and 0xffffL) shl 16)
                        return
                    }
                    offset += 46L + nameLength + extraLength + commentLength
                } else {
                    offset++
                }
            }
            error("central entry not found")
        }
    }

    private fun readU16(raf: RandomAccessFile): Int = raf.readUnsignedByte() or (raf.readUnsignedByte() shl 8)
    private fun readU32(raf: RandomAccessFile): Long = readU16(raf).toLong() or (readU16(raf).toLong() shl 16)
    private fun writeU32(raf: RandomAccessFile, value: Long) {
        repeat(4) { shift -> raf.write(((value ushr (8 * shift)) and 0xff).toInt()) }
    }
}
