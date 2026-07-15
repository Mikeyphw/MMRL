package com.dergoogler.mmrl.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class DownloadTargetPolicyTest {
    @Test
    fun `missing destination can be downloaded`() {
        val dir = Files.createTempDirectory("mmrl-download-policy").toFile()
        assertEquals(ExistingDownload.MISSING, DownloadTargetPolicy.classify(dir.resolve("module.zip")))
        dir.deleteRecursively()
    }

    @Test
    fun `zero byte destination is recoverable`() {
        val file = Files.createTempFile("mmrl-empty", ".zip").toFile()
        assertEquals(ExistingDownload.EMPTY, DownloadTargetPolicy.classify(file))
        file.delete()
    }

    @Test
    fun `non empty destination is preserved`() {
        val file = Files.createTempFile("mmrl-valid", ".zip").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(ExistingDownload.VALID, DownloadTargetPolicy.classify(file))
        file.delete()
    }
    @Test
    fun `hidden filename is made MediaStore stable`() {
        assertEquals("_.BRENE-module.zip", DownloadTargetPolicy.sanitizeFilename(".BRENE-module.zip"))
    }

    @Test
    fun `filename cannot escape destination directory`() {
        assertEquals("module.zip", DownloadTargetPolicy.sanitizeFilename("../../module.zip"))
    }

}
