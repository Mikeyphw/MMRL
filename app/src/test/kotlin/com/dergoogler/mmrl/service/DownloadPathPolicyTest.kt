package com.dergoogler.mmrl.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DownloadPathPolicyTest {
    private val downloads = File("/storage/emulated/0/Download")

    @Test
    fun `relative folder stays under public downloads`() {
        assertEquals(
            File("/storage/emulated/0/Download/Modules"),
            DownloadPathPolicy.resolveDirectory("Modules", downloads),
        )
    }

    @Test
    fun `sdcard alias resolves to canonical shared storage`() {
        assertEquals(
            File("/storage/emulated/0/Download/Modules"),
            DownloadPathPolicy.resolveDirectory("/sdcard/Download/Modules", downloads),
        )
    }

    @Test
    fun `legacy traversal path is normalized before MediaStore sees it`() {
        assertEquals(
            File("/storage/emulated/0/Download/Modules"),
            DownloadPathPolicy.resolveDirectory(
                "/storage/emulated/0/Download/../../../../sdcard/Download/Modules",
                downloads,
            ),
        )
    }

    @Test
    fun `media store escaped traversal path is repaired`() {
        assertEquals(
            File("/storage/emulated/0/Download/Modules"),
            DownloadPathPolicy.resolveDirectory(
                "/storage/emulated/0/Download/_../_../_../_../sdcard/Download/Modules",
                downloads,
            ),
        )
    }

    @Test
    fun `destination uses stable non-hidden filename`() {
        assertEquals(
            File("/storage/emulated/0/Download/Modules/_.BRENE-module.zip"),
            DownloadPathPolicy.destination("Modules", ".BRENE-module.zip", downloads),
        )
    }
}
