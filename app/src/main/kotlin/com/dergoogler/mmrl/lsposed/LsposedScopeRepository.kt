package com.dergoogler.mmrl.lsposed

import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import com.dergoogler.mmrl.utils.withNewRootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LsposedScopeRepository(private val context: Context) {
    private val scopeCacheDir: File by lazy { File(context.cacheDir, "lsposed-scope").apply { mkdirs() } }

    suspend fun readState(): LsposedScopeState = withContext(Dispatchers.IO) {
        val copied = copyConfigDbForRead()
            ?: return@withContext LsposedScopeState(
                readable = false,
                message = "LSPosed config DB was not found or could not be copied with root.",
            )

        runCatching { readCopiedState(copied) }
            .getOrElse { error ->
                LsposedScopeState(
                    readable = false,
                    message = error.message ?: "Unable to read LSPosed config DB.",
                )
            }
    }

    fun installedTargets(): List<LsposedScopeTarget> {
        val pm = context.packageManager
        return installedPackages(pm).map { info ->
            val label = info.applicationInfo?.loadLabel(pm)?.toString().orEmpty().ifBlank { info.packageName }
            LsposedScopeTarget(
                packageName = info.packageName,
                label = label,
                userId = 0,
            )
        }.sortedWith(compareBy<LsposedScopeTarget> { it.label.lowercase() }.thenBy { it.packageName })
    }

    suspend fun applyPlan(plan: LsposedScopeEditPlan): LsposedScopeState = withContext(Dispatchers.IO) {
        val working = copyConfigDbForRead()
            ?: error("LSPosed config DB was not found or could not be copied with root.")
        writePlanToCopy(working, plan)
        restoreConfigDbFromCopy(working)
        readState()
    }

    private fun writePlanToCopy(file: File, plan: LsposedScopeEditPlan) {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val hasAutoInclude = tableHasColumn(db, "modules", "auto_include")
            db.beginTransaction()
            try {
                val mid = findModuleId(db, plan.packageName) ?: error("LSPosed module ${plan.packageName} is not present in provider config")
                val moduleValues = ContentValues().apply {
                    put("enabled", if (plan.enabled) 1 else 0)
                    if (hasAutoInclude) put("auto_include", if (plan.autoInclude) 1 else 0)
                }
                db.update("modules", moduleValues, "mid = ?", arrayOf(mid.toString()))
                db.delete("scope", "mid = ?", arrayOf(mid.toString()))
                plan.targets.forEach { target ->
                    val values = ContentValues().apply {
                        put("mid", mid)
                        put("app_pkg_name", target.packageName)
                        put("user_id", target.userId)
                    }
                    db.insertWithOnConflict("scope", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun findModuleId(db: SQLiteDatabase, packageName: String): Long? {
        db.rawQuery(
            "SELECT mid FROM modules WHERE module_pkg_name = ?",
            arrayOf(packageName),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun restoreConfigDbFromCopy(file: File) {
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val command = """
            db=${shellQuote(LsposedScopeState.DEFAULT_DB_PATH)}
            src=${shellQuote(file.absolutePath)}
            if [ ! -f "${'$'}db" ]; then
              echo missing
              exit 1
            fi
            backup="${'$'}db.mmrl-bak-${'$'}(date +%Y%m%d%H%M%S)"
            cp -p "${'$'}db" "${'$'}backup" || exit 1
            [ -f "${'$'}db-wal" ] && cp -p "${'$'}db-wal" "${'$'}backup-wal" || true
            [ -f "${'$'}db-shm" ] && cp -p "${'$'}db-shm" "${'$'}backup-shm" || true
            owner=${'$'}(stat -c '%u:%g' "${'$'}db" 2>/dev/null || echo 0:0)
            cp -f "${'$'}src" "${'$'}db" || exit 1
            chown "${'$'}owner" "${'$'}db" 2>/dev/null || true
            chmod 600 "${'$'}db" 2>/dev/null || true
            rm -f "${'$'}db-wal" "${'$'}db-shm"
            echo ok:${'$'}backup
        """.trimIndent()
        val result = runCatching { withNewRootShell { newJob().add(command).to(output, errors).exec() } }.getOrNull()
        if (result?.isSuccess != true || output.none { it.startsWith("ok:") }) {
            error((errors + output).joinToString("\n").ifBlank { "Unable to restore LSPosed config DB" })
        }
    }

    private fun readCopiedState(file: File): LsposedScopeState {
        val modules = mutableListOf<LsposedModuleScope>()
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val hasAutoInclude = tableHasColumn(db, "modules", "auto_include")
            val moduleSql = if (hasAutoInclude) {
                "SELECT mid, module_pkg_name, apk_path, enabled, auto_include FROM modules ORDER BY module_pkg_name"
            } else {
                "SELECT mid, module_pkg_name, apk_path, enabled, 0 AS auto_include FROM modules ORDER BY module_pkg_name"
            }
            db.rawQuery(moduleSql, emptyArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val mid = cursor.getLong(0)
                    modules += LsposedModuleScope(
                        modulePackageName = cursor.getString(1),
                        mid = mid,
                        apkPath = cursor.getString(2).orEmpty(),
                        enabled = cursor.getInt(3) != 0,
                        autoInclude = cursor.getInt(4) != 0,
                        targets = scopeTargets(db, mid),
                    )
                }
            }
        }
        return LsposedScopeState(readable = true, modules = modules)
    }

    private fun scopeTargets(db: SQLiteDatabase, mid: Long): List<LsposedScopeTarget> {
        val targets = mutableListOf<LsposedScopeTarget>()
        db.rawQuery(
            "SELECT app_pkg_name, user_id FROM scope WHERE mid = ? ORDER BY user_id, app_pkg_name",
            arrayOf(mid.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val packageName = cursor.getString(0)
                targets += LsposedScopeTarget(
                    packageName = packageName,
                    label = packageLabel(packageName),
                    userId = cursor.getInt(1),
                )
            }
        }
        return targets
    }

    private fun tableHasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1).equals(column, ignoreCase = true)) return true
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun packageLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrDefault(packageName)

    private fun copyConfigDbForRead(): File? {
        val out = File(scopeCacheDir, "modules_config_read.db")
        val uid = context.applicationInfo.uid
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val command = """
            db=${shellQuote(LsposedScopeState.DEFAULT_DB_PATH)}
            out=${shellQuote(out.absolutePath)}
            if [ ! -f "${'$'}db" ]; then
              echo missing
              exit 0
            fi
            cp -f "${'$'}db" "${'$'}out" && chown $uid:$uid "${'$'}out" && chmod 600 "${'$'}out" && echo ok
        """.trimIndent()
        val result = runCatching { withNewRootShell { newJob().add(command).to(output, errors).exec() } }.getOrNull()
        return out.takeIf { result?.isSuccess == true && output.any { it.trim() == "ok" } && it.isFile && it.length() > 0L }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    @Suppress("DEPRECATION")
    private fun installedPackages(pm: PackageManager): List<PackageInfo> =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }
}
