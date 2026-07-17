package com.dergoogler.mmrl.ash.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AshModuleLocatorTest {
    @Test
    fun `finds bundled AshLooper module even when control script is not executable`() {
        val activeRoot = temporaryRoot()
        val updateRoot = temporaryRoot()
        val module = createModule(activeRoot, folder = "AshLooper", id = "AshLooper")
        val control = File(module, "ashrexcuectl").apply {
            writeText("#!/system/bin/sh\n")
            setExecutable(false, false)
        }

        val inspection = AshModuleLocator(activeRoot, updateRoot).inspect()
        assertEquals(control.absolutePath, inspection.controlScript?.absolutePath)
        assertTrue(inspection.installed)
        assertTrue(inspection.active)
    }

    @Test
    fun `finds module installed under a renamed folder by module id`() {
        val activeRoot = temporaryRoot()
        val module = createModule(activeRoot, folder = "renamed-by-manager", id = "AshLooper")
        val control = File(module, "ashrexcuectl").apply { writeText("#!/system/bin/sh\n") }

        assertEquals(
            control.absolutePath,
            AshModuleLocator(activeRoot, temporaryRoot()).locateControlScript()?.absolutePath,
        )
    }

    @Test
    fun `reports disabled and staged update state`() {
        val activeRoot = temporaryRoot()
        val updateRoot = temporaryRoot()
        val active = createModule(activeRoot, folder = "AshLooper", id = "AshLooper", versionCode = 204)
        File(active, "ashrexcuectl").writeText("#!/system/bin/sh\n")
        File(active, "disable").createNewFile()
        val staged = createModule(updateRoot, folder = "AshLooper", id = "AshLooper", versionCode = 210)
        File(staged, "ashrexcuectl").writeText("#!/system/bin/sh\n")

        val inspection = AshModuleLocator(activeRoot, updateRoot).inspect()
        assertTrue(inspection.disabled)
        assertTrue(inspection.updatePending)
        assertEquals("active", inspection.source)
    }

    @Test
    fun `reports a staged-only install as reboot pending`() {
        val activeRoot = temporaryRoot()
        val updateRoot = temporaryRoot()
        val staged = createModule(updateRoot, folder = "AshLooper", id = "AshLooper")
        File(staged, "ashrexcuectl").writeText("#!/system/bin/sh\n")

        val inspection = AshModuleLocator(activeRoot, updateRoot).inspect()
        assertTrue(inspection.installed)
        assertFalse(inspection.active)
        assertTrue(inspection.updatePending)
        assertEquals("staged", inspection.source)
    }

    @Test
    fun `ignores unrelated control script`() {
        val activeRoot = temporaryRoot()
        val module = createModule(
            activeRoot,
            folder = "unrelated",
            id = "unrelated.module",
            name = "Other module",
        )
        File(module, "ashrexcuectl").writeText("#!/system/bin/sh\n")

        assertNull(AshModuleLocator(activeRoot, temporaryRoot()).locateControlScript())
    }

    private fun temporaryRoot(): File = Files.createTempDirectory("ash-module-root").toFile().apply {
        deleteOnExit()
    }

    private fun createModule(
        root: File,
        folder: String,
        id: String,
        name: String = "AshReXcue BootLoop Protector",
        versionCode: Int = 210,
    ): File = File(root, folder).apply {
        mkdirs()
        File(this, "module.prop").writeText(
            """
            id=$id
            name=$name
            version=11.1.0
            versionCode=$versionCode
            """.trimIndent(),
        )
    }
}
