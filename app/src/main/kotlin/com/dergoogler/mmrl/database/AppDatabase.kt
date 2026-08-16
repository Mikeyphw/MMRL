package com.dergoogler.mmrl.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dergoogler.mmrl.database.dao.BlacklistDao
import com.dergoogler.mmrl.database.dao.JoinDao
import com.dergoogler.mmrl.database.dao.LocalDao
import com.dergoogler.mmrl.database.dao.OnlineDao
import com.dergoogler.mmrl.database.dao.OperationHistoryDao
import com.dergoogler.mmrl.database.dao.RepoDao
import com.dergoogler.mmrl.database.dao.VersionDao
import com.dergoogler.mmrl.database.entity.Repo
import com.dergoogler.mmrl.database.entity.VersionItemEntity
import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.history.OperationTechnicalLogEntity
import com.dergoogler.mmrl.database.entity.local.LocalModuleEntity
import com.dergoogler.mmrl.database.entity.local.LocalModuleSource
import com.dergoogler.mmrl.database.entity.local.LocalModuleUpdatable
import com.dergoogler.mmrl.database.entity.online.BlacklistEntity
import com.dergoogler.mmrl.database.entity.online.OnlineModuleEntity
import dev.dergoogler.mmrl.compat.Converters

@Database(
    entities = [
        Repo::class,
        LocalModuleUpdatable::class,
        LocalModuleSource::class,
        OnlineModuleEntity::class,
        VersionItemEntity::class,
        LocalModuleEntity::class,
        BlacklistEntity::class,
        OperationHistoryEntity::class,
        OperationTechnicalLogEntity::class,
    ],
    version = 21,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao

    abstract fun onlineDao(): OnlineDao

    abstract fun versionDao(): VersionDao

    abstract fun localDao(): LocalDao

    abstract fun joinDao(): JoinDao

    abstract fun blacklistDao(): BlacklistDao

    abstract fun operationHistoryDao(): OperationHistoryDao

    companion object {


        internal const val CURRENT_SCHEMA_VERSION = 21

        internal val supportedMigrationStarts: IntRange = 1 until CURRENT_SCHEMA_VERSION

        internal fun destructiveFallbackEnabled(): Boolean = false

        private fun legacyMigrationTo15(startVersion: Int) =
            object : Migration(startVersion, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    createVersion15TablesIfMissing(db)
                }
            }

        private val LEGACY_MIGRATIONS_TO_15: Array<Migration> =
            (1 until 15).map(::legacyMigrationTo15).toTypedArray()

        private val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `operationHistory` (
                            `id` TEXT NOT NULL,
                            `kind` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `summary` TEXT NOT NULL,
                            `moduleId` TEXT,
                            `moduleName` TEXT,
                            `sourceUri` TEXT,
                            `sourceUrl` TEXT,
                            `destinationPath` TEXT,
                            `startedAt` INTEGER NOT NULL,
                            `completedAt` INTEGER,
                            `progress` INTEGER,
                            `requiresReboot` INTEGER NOT NULL,
                            `rebootCompletedAt` INTEGER,
                            `technicalLog` TEXT NOT NULL,
                            `errorMessage` TEXT,
                            `retryAction` TEXT,
                            `rollbackAction` TEXT,
                            `useShell` INTEGER NOT NULL,
                            `parentId` TEXT,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_operationHistory_startedAt` ON `operationHistory` (`startedAt`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_operationHistory_status` ON `operationHistory` (`status`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_operationHistory_moduleId` ON `operationHistory` (`moduleId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_operationHistory_requiresReboot_rebootCompletedAt` ON `operationHistory` (`requiresReboot`, `rebootCompletedAt`)")
                }
            }

        private val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `phase` TEXT")
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `rollbackArchivePath` TEXT")
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `previousVersion` TEXT")
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `targetVersion` TEXT")
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `inspectionSummary` TEXT")
                }
            }

        private val MIGRATION_17_18 =
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `origin` TEXT")
                }
            }

        private val MIGRATION_18_19 =
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `localModules_source` (
                            `id` TEXT NOT NULL,
                            `repoUrl` TEXT NOT NULL,
                            `mode` TEXT NOT NULL,
                            `installedVersion` TEXT NOT NULL,
                            `installedVersionCode` INTEGER NOT NULL,
                            `sourceUrl` TEXT NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                }
            }


        private val MIGRATION_19_20 =
            object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `idempotencyKey` TEXT")
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `sourceOperationId` TEXT")
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `mutationStartedAt` INTEGER")
                    db.execSQL("ALTER TABLE `operationHistory` ADD COLUMN `reconciledAt` INTEGER")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_operationHistory_idempotencyKey` ON `operationHistory` (`idempotencyKey`)")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `operationTechnicalLog` (
                            `id` TEXT NOT NULL,
                            `technicalLog` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT OR REPLACE INTO `operationTechnicalLog` (`id`, `technicalLog`)
                        SELECT `id`, `technicalLog` FROM `operationHistory` WHERE `technicalLog` != ''
                        """.trimIndent(),
                    )
                }
            }



        private val MIGRATION_20_21 =
            object : Migration(20, 21) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    addColumnIfMissing(db, "onlineModules", "ksunextManager", "ALTER TABLE `onlineModules` ADD COLUMN `ksunextManager` TEXT")
                    rebuildVersionsForProvenance(db)
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_onlineModules_id_repoUrl` ON `onlineModules` (`id`, `repoUrl`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_onlineModules_repoUrl` ON `onlineModules` (`repoUrl`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_versions_id_repoUrl` ON `versions` (`id`, `repoUrl`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_localModules_source_id_repoUrl` ON `localModules_source` (`id`, `repoUrl`)")
                }
            }

        private val ALL_MIGRATIONS: Array<Migration> =
            LEGACY_MIGRATIONS_TO_15 +
                arrayOf(
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                )

        private fun createVersion15TablesIfMissing(db: SupportSQLiteDatabase) {
            normalizeTableToVersion15(
                db = db,
                table = "repos",
                createSql = ::createReposV15Sql,
                insertSql = { temp, source, columns ->
                    """
                    INSERT OR REPLACE INTO `$temp` (
                        `url`, `name`, `enable`, `submission`, `website`, `cover`, `description`,
                        `donate`, `support`, `version`, `timestamp`, `size`
                    )
                    SELECT
                        ${columnOr(columns, "url", "''")},
                        ${columnOr(columns, "name", "''")},
                        ${columnOr(columns, "enable", "1")},
                        ${columnOr(columns, "submission")},
                        ${columnOr(columns, "website")},
                        ${columnOr(columns, "cover")},
                        ${columnOr(columns, "description")},
                        ${columnOr(columns, "donate")},
                        ${columnOr(columns, "support")},
                        ${columnOr(columns, "version", "0")},
                        ${columnOr(columns, "timestamp", "0")},
                        ${columnOr(columns, "size", "0")}
                    FROM `$source`
                    """.trimIndent()
                },
            )
            normalizeTableToVersion15(
                db = db,
                table = "localModules_updatable",
                createSql = ::createLocalUpdatableV15Sql,
                insertSql = { temp, source, columns ->
                    """
                    INSERT OR REPLACE INTO `$temp` (`id`, `updatable`)
                    SELECT ${columnOr(columns, "id", "''")}, ${columnOr(columns, "updatable", "0")}
                    FROM `$source`
                    """.trimIndent()
                },
            )
            normalizeTableToVersion15(
                db = db,
                table = "onlineModules",
                createSql = ::createOnlineModulesV15Sql,
                insertSql = { temp, source, columns ->
                    """
                    INSERT OR REPLACE INTO `$temp` (
                        `id`, `repoUrl`, `name`, `version`, `versionCode`, `author`, `description`,
                        `maxApi`, `minApi`, `size`, `categories`, `icon`, `homepage`, `donate`,
                        `support`, `cover`, `screenshots`, `license`, `readme`, `verified`, `require`,
                        `devices`, `arch`, `permissions`, `stars`, `magiskManager`, `kernelsuManager`,
                        `apatchManager`, `magisk`, `kernelsu`, `apatch`, `title`, `message`, `service`,
                        `postFsData`, `resetprop`, `sepolicy`, `zygisk`, `apks`, `webroot`, `postMount`,
                        `bootCompleted`, `action`, `type`, `added`, `source`, `antifeatures`,
                        `buildMetadata`, `blId`, `blSource`, `blNotes`, `blAntiFeatures`
                    )
                    SELECT
                        ${columnOr(columns, "id", "''")},
                        ${columnOr(columns, "repoUrl", "''")},
                        ${columnOr(columns, "name", "''")},
                        ${columnOr(columns, "version", "''")},
                        ${columnOr(columns, "versionCode", "0")},
                        ${columnOr(columns, "author", "''")},
                        ${columnOr(columns, "description")},
                        ${columnOr(columns, "maxApi")},
                        ${columnOr(columns, "minApi")},
                        ${columnOr(columns, "size")},
                        ${columnOr(columns, "categories")},
                        ${columnOr(columns, "icon")},
                        ${columnOr(columns, "homepage")},
                        ${columnOr(columns, "donate")},
                        ${columnOr(columns, "support")},
                        ${columnOr(columns, "cover")},
                        ${columnOr(columns, "screenshots")},
                        ${columnOr(columns, "license")},
                        ${columnOr(columns, "readme")},
                        ${columnOr(columns, "verified")},
                        ${columnOr(columns, "require")},
                        ${columnOr(columns, "devices")},
                        ${columnOr(columns, "arch")},
                        ${columnOr(columns, "permissions")},
                        ${columnOr(columns, "stars")},
                        ${columnOr(columns, "magiskManager")},
                        ${columnOr(columns, "kernelsuManager")},
                        ${columnOr(columns, "apatchManager")},
                        ${columnOr(columns, "magisk")},
                        ${columnOr(columns, "kernelsu")},
                        ${columnOr(columns, "apatch")},
                        ${columnOr(columns, "title")},
                        ${columnOr(columns, "message")},
                        ${columnOr(columns, "service")},
                        ${columnOr(columns, "postFsData")},
                        ${columnOr(columns, "resetprop")},
                        ${columnOr(columns, "sepolicy")},
                        ${columnOr(columns, "zygisk")},
                        ${columnOr(columns, "apks")},
                        ${columnOr(columns, "webroot")},
                        ${columnOr(columns, "postMount")},
                        ${columnOr(columns, "bootCompleted")},
                        ${columnOr(columns, "action")},
                        ${columnOr(columns, "type", "'UNKNOWN'")},
                        ${columnOr(columns, "added")},
                        ${columnOr(columns, "source", "''")},
                        ${columnOr(columns, "antifeatures")},
                        ${columnOr(columns, "buildMetadata")},
                        ${columnOr(columns, "blId")},
                        ${columnOr(columns, "blSource")},
                        ${columnOr(columns, "blNotes")},
                        ${columnOr(columns, "blAntiFeatures")}
                    FROM `$source`
                    """.trimIndent()
                },
            )
            normalizeTableToVersion15(
                db = db,
                table = "versions",
                createSql = ::createVersionsV15Sql,
                insertSql = { temp, source, columns ->
                    """
                    INSERT OR REPLACE INTO `$temp` (
                        `id`, `repoUrl`, `timestamp`, `version`, `versionCode`, `zipUrl`, `changelog`
                    )
                    SELECT
                        ${columnOr(columns, "id", "''")},
                        ${columnOr(columns, "repoUrl", "''")},
                        ${columnOr(columns, "timestamp", "0")},
                        ${columnOr(columns, "version", "''")},
                        ${columnOr(columns, "versionCode", "0")},
                        ${columnOr(columns, "zipUrl", "''")},
                        ${columnOr(columns, "changelog", "''")}
                    FROM `$source`
                    """.trimIndent()
                },
            )
            normalizeTableToVersion15(
                db = db,
                table = "localModules",
                createSql = ::createLocalModulesV15Sql,
                insertSql = { temp, source, columns ->
                    """
                    INSERT OR REPLACE INTO `$temp` (
                        `id`, `name`, `version`, `versionCode`, `author`, `description`, `state`,
                        `size`, `updateJson`, `lastUpdated`
                    )
                    SELECT
                        ${columnOr(columns, "id", "''")},
                        ${columnOr(columns, "name", "''")},
                        ${columnOr(columns, "version", "''")},
                        ${columnOr(columns, "versionCode", "0")},
                        ${columnOr(columns, "author", "''")},
                        ${columnOr(columns, "description", "''")},
                        ${columnOr(columns, "state", "''")},
                        ${columnOr(columns, "size", "0")},
                        ${columnOr(columns, "updateJson", "''")},
                        ${columnOr(columns, "lastUpdated", "0")}
                    FROM `$source`
                    """.trimIndent()
                },
            )
            normalizeTableToVersion15(
                db = db,
                table = "blacklist",
                createSql = ::createBlacklistV15Sql,
                insertSql = { temp, source, columns ->
                    """
                    INSERT OR REPLACE INTO `$temp` (`blId`, `blSource`, `blNotes`, `blAntiFeatures`)
                    SELECT
                        ${columnOr(columns, "blId", "''")},
                        ${columnOr(columns, "blSource", "''")},
                        ${columnOr(columns, "blNotes")},
                        ${columnOr(columns, "blAntiFeatures")}
                    FROM `$source`
                    """.trimIndent()
                },
            )
        }

        private fun normalizeTableToVersion15(
            db: SupportSQLiteDatabase,
            table: String,
            createSql: (String) -> String,
            insertSql: (temp: String, source: String, columns: Set<String>) -> String,
        ) {
            if (!tableExists(db, table)) {
                db.execSQL(createSql(table))
                return
            }
            val temp = "${table}_v15_new"
            db.execSQL("DROP TABLE IF EXISTS `$temp`")
            db.execSQL(createSql(temp))
            db.execSQL(insertSql(temp, table, tableColumns(db, table)))
            db.execSQL("DROP TABLE `$table`")
            db.execSQL("ALTER TABLE `$temp` RENAME TO `$table`")
        }

        private fun tableExists(
            db: SupportSQLiteDatabase,
            table: String,
        ): Boolean {
            val escaped = table.replace("'", "''")
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$escaped'").use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun tableColumns(
            db: SupportSQLiteDatabase,
            table: String,
        ): Set<String> =
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(nameIndex))
                }
                names
            }

        private fun columnOr(
            columns: Set<String>,
            column: String,
            fallback: String = "NULL",
        ): String = if (column in columns) "`$column`" else fallback

        private fun createReposV15Sql(table: String) =
            """
            CREATE TABLE `$table` (
                `url` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `enable` INTEGER NOT NULL,
                `submission` TEXT,
                `website` TEXT,
                `cover` TEXT,
                `description` TEXT,
                `donate` TEXT,
                `support` TEXT,
                `version` INTEGER NOT NULL,
                `timestamp` REAL NOT NULL,
                `size` INTEGER NOT NULL,
                PRIMARY KEY(`url`)
            )
            """.trimIndent()

        private fun createLocalUpdatableV15Sql(table: String) =
            """
            CREATE TABLE `$table` (
                `id` TEXT NOT NULL,
                `updatable` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()

        private fun createOnlineModulesV15Sql(table: String) =
            """
            CREATE TABLE `$table` (
                `id` TEXT NOT NULL,
                `repoUrl` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `version` TEXT NOT NULL,
                `versionCode` INTEGER NOT NULL,
                `author` TEXT NOT NULL,
                `description` TEXT,
                `maxApi` INTEGER,
                `minApi` INTEGER,
                `size` INTEGER,
                `categories` TEXT,
                `icon` TEXT,
                `homepage` TEXT,
                `donate` TEXT,
                `support` TEXT,
                `cover` TEXT,
                `screenshots` TEXT,
                `license` TEXT,
                `readme` TEXT,
                `verified` INTEGER,
                `require` TEXT,
                `devices` TEXT,
                `arch` TEXT,
                `permissions` TEXT,
                `stars` INTEGER,
                `magiskManager` TEXT,
                `kernelsuManager` TEXT,
                `apatchManager` TEXT,
                `magisk` TEXT,
                `kernelsu` TEXT,
                `apatch` TEXT,
                `title` TEXT,
                `message` TEXT,
                `service` INTEGER,
                `postFsData` INTEGER,
                `resetprop` INTEGER,
                `sepolicy` INTEGER,
                `zygisk` INTEGER,
                `apks` INTEGER,
                `webroot` INTEGER,
                `postMount` INTEGER,
                `bootCompleted` INTEGER,
                `action` INTEGER,
                `type` TEXT NOT NULL,
                `added` REAL,
                `source` TEXT NOT NULL,
                `antifeatures` TEXT,
                `buildMetadata` TEXT,
                `blId` TEXT,
                `blSource` TEXT,
                `blNotes` TEXT,
                `blAntiFeatures` TEXT,
                PRIMARY KEY(`id`, `repoUrl`)
            )
            """.trimIndent()

        private fun createVersionsV15Sql(table: String) =
            """
            CREATE TABLE `$table` (
                `id` TEXT NOT NULL,
                `repoUrl` TEXT NOT NULL,
                `timestamp` REAL NOT NULL,
                `version` TEXT NOT NULL,
                `versionCode` INTEGER NOT NULL,
                `zipUrl` TEXT NOT NULL,
                `changelog` TEXT NOT NULL,
                PRIMARY KEY(`id`, `repoUrl`, `versionCode`)
            )
            """.trimIndent()

        private fun createLocalModulesV15Sql(table: String) =
            """
            CREATE TABLE `$table` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `version` TEXT NOT NULL,
                `versionCode` INTEGER NOT NULL,
                `author` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `size` INTEGER NOT NULL,
                `updateJson` TEXT NOT NULL,
                `lastUpdated` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()

        private fun createBlacklistV15Sql(table: String) =
            """
            CREATE TABLE `$table` (
                `blId` TEXT NOT NULL,
                `blSource` TEXT NOT NULL,
                `blNotes` TEXT,
                `blAntiFeatures` TEXT,
                PRIMARY KEY(`blId`)
            )
            """.trimIndent()

        private fun rebuildVersionsForProvenance(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `versions_new` (
                    `id` TEXT NOT NULL,
                    `repoUrl` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `version` TEXT NOT NULL,
                    `versionCode` INTEGER NOT NULL,
                    `zipUrl` TEXT NOT NULL,
                    `changelog` TEXT NOT NULL,
                    `size` INTEGER,
                    `sourceProvenance` TEXT,
                    PRIMARY KEY(`id`, `repoUrl`, `versionCode`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `versions_new` (
                    `id`, `repoUrl`, `timestamp`, `version`, `versionCode`, `zipUrl`, `changelog`, `size`, `sourceProvenance`
                )
                SELECT
                    `id`,
                    `repoUrl`,
                    CAST(`timestamp` AS INTEGER),
                    `version`,
                    `versionCode`,
                    `zipUrl`,
                    `changelog`,
                    NULL,
                    NULL
                FROM `versions`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `versions`")
            db.execSQL("ALTER TABLE `versions_new` RENAME TO `versions`")
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            sql: String,
        ) {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return
                }
            }
            db.execSQL(sql)
        }
        /**
         * Every supported legacy schema is routed through an explicit migration path.
         * User repository, installed-module, source, operation-history, and version rows are
         * never discarded by destructive fallback during ordinary upgrades.
         */
        fun build(context: Context) =
            Room
                .databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "mmrl_v2",
                ).addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
