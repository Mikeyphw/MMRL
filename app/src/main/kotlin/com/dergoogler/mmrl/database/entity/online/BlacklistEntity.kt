package com.dergoogler.mmrl.database.entity.online

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.dergoogler.mmrl.model.online.Blacklist
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "blacklist")
data class BlacklistEntity(
    @param:Json(name = "id") @PrimaryKey val blId: String,
    @param:Json(name = "source") val blSource: String,
    @param:Json(name = "notes") val blNotes: String? = null,
    @field:TypeConverters @param:Json(name = "antifeatures")
    val blAntiFeatures: List<String>? = null,
) {
    constructor(original: Blacklist) : this(
        blId = original.id,
        blSource = original.source,
        blNotes = original.notes,
        blAntiFeatures = original.antifeatures,
    )

    fun toBlacklist() =
        Blacklist(
            id = blId,
            source = blSource,
            notes = blNotes,
            antifeatures = blAntiFeatures,
        )
}

/**
 * Blacklist metadata embedded into an online module row.
 *
 * This intentionally is not a Room entity: embedding [BlacklistEntity] caused Room to discover
 * its primary key and then warn that the key was ignored inside [OnlineModuleEntity]. Keeping a
 * plain snapshot preserves the existing embedded column names without ambiguous key semantics.
 */
data class EmbeddedBlacklist(
    val blId: String,
    val blSource: String,
    val blNotes: String? = null,
    val blAntiFeatures: List<String>? = null,
) {
    constructor(original: Blacklist) : this(
        blId = original.id,
        blSource = original.source,
        blNotes = original.notes,
        blAntiFeatures = original.antifeatures,
    )

    fun toBlacklist() =
        Blacklist(
            id = blId,
            source = blSource,
            notes = blNotes,
            antifeatures = blAntiFeatures,
        )
}
