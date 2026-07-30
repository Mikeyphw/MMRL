package com.dergoogler.mmrl.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class GitHubArtifactContentShapeTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `vector extracted artifact is accepted as module layout instead of missing zip`() {
        val artifact = zipTextFile(
            "vector-artifact.zip",
            mapOf(
                "module.prop" to "id=zygisk_vector\nname=Vector\nversion=2.0\nversionCode=3052\n",
                "manager.apk" to "apk",
                "zygisk/arm64-v8a.so" to "so",
            ),
        )

        val materialized = GitHubArtifactArchivePolicy.materializeModuleZip(
            archive = artifact,
            targetDirectory = folder.root,
            outputNamePrefix = "vector",
        )

        assertEquals(GitHubArtifactStrategy.EXTRACTED_MODULE_LAYOUT, materialized.analysis.strategy)
        assertTrue(materialized.analysis.installable)
        assertEquals(artifact, materialized.file)
    }

    @Test
    fun `wrapped artifact directory is repacked as module zip`() {
        val artifact = zipTextFile(
            "wrapped-vector.zip",
            mapOf(
                "Vector-Release/module.prop" to "id=zygisk_vector\nname=Vector\n",
                "Vector-Release/service.sh" to "#!/system/bin/sh\n",
            ),
        )

        val materialized = GitHubArtifactArchivePolicy.materializeModuleZip(
            archive = artifact,
            targetDirectory = folder.root,
            outputNamePrefix = "vector",
        )

        assertEquals(GitHubArtifactStrategy.SINGLE_FOLDER_MODULE_LAYOUT, materialized.analysis.strategy)
        assertTrue(materialized.file.name.endsWith("module.zip"))
        ZipFile(materialized.file).use { zip ->
            assertTrue(zip.getEntry("module.prop") != null)
            assertTrue(zip.getEntry("service.sh") != null)
        }
    }

    @Test
    fun `nested zip artifact still extracts preferred module archive`() {
        val nested = zipBytes(mapOf("module.prop" to "id=rezygisk\nname=ReZygisk\n"))
        val artifact = zipFile(
            "rezygisk-artifact.zip",
            mapOf(
                "ReZygisk-v1-release.zip" to nested,
                "mapping.txt" to "debug mapping".toByteArray(),
            ),
        )

        val materialized = GitHubArtifactArchivePolicy.materializeModuleZip(
            archive = artifact,
            targetDirectory = folder.root,
            outputNamePrefix = "rezygisk",
        )

        assertEquals(GitHubArtifactStrategy.NESTED_ZIP, materialized.analysis.strategy)
        ZipFile(materialized.file).use { zip ->
            assertTrue(zip.getEntry("module.prop") != null)
        }
    }

    private fun zipTextFile(
        name: String,
        entries: Map<String, String>,
    ): File = zipFile(name, entries.mapValues { it.value.toByteArray() })

    private fun zipFile(
        name: String,
        entries: Map<String, ByteArray>,
    ): File = folder.newFile(name).also { file ->
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (entryName, data) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(data)
                zip.closeEntry()
            }
        }
    }

    private fun zipBytes(entries: Map<String, String>): ByteArray =
        folder.newFile("nested-${entries.hashCode()}.zip").also { file ->
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                entries.forEach { (entryName, text) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
        }.readBytes()
}
