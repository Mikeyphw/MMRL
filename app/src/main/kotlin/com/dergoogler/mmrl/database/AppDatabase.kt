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
    ],
    version = 19,
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

        /**
         * Existing fallback behavior is retained for unsupported legacy schemas.
         * Versions 15 to 16, 16 to 17, and 17 to 18 are migrated explicitly so operation history
         * and reviewed transaction metadata can be added without discarding repositories
         * or installed-module state.
         */
        fun build(context: Context) =
            Room
                .databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "mmrl_v2",
                ).addMigrations(MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
