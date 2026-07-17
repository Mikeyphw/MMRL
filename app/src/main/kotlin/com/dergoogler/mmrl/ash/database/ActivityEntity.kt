package com.dergoogler.mmrl.ash.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val type: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val details: String,
)
