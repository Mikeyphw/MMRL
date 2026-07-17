package com.dergoogler.mmrl.ash.data

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AshSnapshotStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val atomicFile = AtomicFile(
        File(context.filesDir, "ashrexcue/last-successful-snapshot.json").apply {
            parentFile?.mkdirs()
        },
    )

    data class Entry(
        val savedAt: Long,
        val moduleStateRaw: String,
        val snapshotRaw: String,
    )

    suspend fun read(): Entry? = withContext(Dispatchers.IO) {
        if (!atomicFile.baseFile.isFile) return@withContext null
        runCatching {
            val root = JSONObject(atomicFile.readFully().toString(Charsets.UTF_8))
            Entry(
                savedAt = root.optLong("savedAt"),
                moduleStateRaw = root.getJSONObject("moduleState").toString(),
                snapshotRaw = root.getJSONObject("snapshot").toString(),
            )
        }.getOrNull()
    }

    suspend fun write(
        moduleStateRaw: String,
        snapshotRaw: String,
        savedAt: Long = System.currentTimeMillis() / 1000L,
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("savedAt", savedAt)
            .put("moduleState", JSONObject(moduleStateRaw))
            .put("snapshot", JSONObject(snapshotRaw))
            .toString()
            .toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(payload)
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }
}
