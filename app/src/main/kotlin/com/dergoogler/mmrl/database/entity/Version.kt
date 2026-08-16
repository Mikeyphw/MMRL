package com.dergoogler.mmrl.database.entity

import androidx.room.Entity
import androidx.room.Index
import com.dergoogler.mmrl.model.online.VersionItem

@Entity(tableName = "versions", primaryKeys = ["id", "repoUrl", "versionCode"], indices = [Index(value = ["id", "repoUrl"])])
data class VersionItemEntity(
    val id: String,
    val repoUrl: String,
    val timestamp: Long,
    val version: String,
    val versionCode: Int,
    val zipUrl: String,
    val changelog: String,
    val size: Int? = null,
    val sourceProvenance: String? = null,
) {
    constructor(
        original: VersionItem,
        id: String,
        repoUrl: String,
    ) : this(
        id = id,
        repoUrl = repoUrl,
        timestamp = VersionTimestamp.toStorage(original.timestamp),
        version = original.version,
        versionCode = original.versionCode,
        zipUrl = original.zipUrl,
        changelog = original.changelog,
        size = original.size,
        sourceProvenance = original.sourceProvenance,
    )

    fun toItem() =
        VersionItem(
            repoUrl = repoUrl,
            timestamp = VersionTimestamp.toModel(timestamp),
            version = version,
            versionCode = versionCode,
            zipUrl = zipUrl,
            size = size,
            changelog = changelog,
            sourceProvenance = sourceProvenance,
        )
}

object VersionTimestamp {
    fun toStorage(timestampSeconds: Float): Long = timestampSeconds.toLong().coerceAtLeast(0L)

    fun toModel(timestampSeconds: Long): Float = timestampSeconds.toFloat()
}
