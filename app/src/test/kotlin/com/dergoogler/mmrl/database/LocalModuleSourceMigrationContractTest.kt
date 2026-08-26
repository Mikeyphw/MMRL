package com.dergoogler.mmrl.database

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModuleSourceMigrationContractTest {
    private val root: File = repositoryRoot()

    @Test
    fun `room 22 repairs the stray local source index without declaring it in the target schema`() {
        val source =
            root.resolve("app/src/main/kotlin/com/dergoogler/mmrl/database/AppDatabase.kt")
                .readText()
        val migration20To21 =
            source.substringAfter("private val MIGRATION_20_21")
                .substringBefore("internal val MIGRATION_21_22")
        val migration21To22 =
            source.substringAfter("internal val MIGRATION_21_22")
                .substringBefore("private val ALL_MIGRATIONS")
        val schema22 =
            root.resolve("app/schemas/com.dergoogler.mmrl.database.AppDatabase/22.json")
                .readText()

        assertTrue(source.contains("version = 22"))
        assertTrue(source.contains("CURRENT_SCHEMA_VERSION = 22"))
        assertFalse(migration20To21.contains("index_localModules_source_id_repoUrl"))
        assertTrue(
            migration21To22.contains(
                "DROP INDEX IF EXISTS `index_localModules_source_id_repoUrl`",
            ),
        )
        assertTrue(source.contains("MIGRATION_21_22,"))
        assertTrue(schema22.contains("\"version\": 22"))
        assertFalse(schema22.contains("index_localModules_source_id_repoUrl"))
    }

    private fun repositoryRoot(): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .firstOrNull { candidate ->
                candidate.resolve("app/src/main/kotlin/com/dergoogler/mmrl/database/AppDatabase.kt").isFile
            } ?: error("Unable to locate MMRL repository root")
}
