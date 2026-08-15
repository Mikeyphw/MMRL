package com.dergoogler.mmrl.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AtomicFilePublicationTest {
    @Test fun `same-directory partial is atomically replaced by complete final artifact`() {
        val dir = Files.createTempDirectory("mmrl-atomic-publication").toFile()
        try {
            val partial = dir.resolve("artifact.part").apply { writeText("complete artifact") }
            val final = dir.resolve("artifact.zip")
            AtomicFilePublication.move(partial, final)
            assertFalse(partial.exists())
            assertTrue(final.isFile)
            assertEquals("complete artifact", final.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cross-directory publication is rejected before move`() {
        val root = Files.createTempDirectory("mmrl-atomic-cross").toFile()
        try {
            val one = root.resolve("one").apply { mkdirs() }
            val two = root.resolve("two").apply { mkdirs() }
            AtomicFilePublication.move(one.resolve("a.part").apply { writeText("x") }, two.resolve("a.zip"))
        } finally {
            root.deleteRecursively()
        }
    }
}
