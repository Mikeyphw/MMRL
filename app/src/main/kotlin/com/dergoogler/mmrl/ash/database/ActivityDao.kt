package com.dergoogler.mmrl.ash.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ActivityEntity)

    @Query(
        "DELETE FROM activity WHERE id NOT IN " +
            "(SELECT id FROM activity ORDER BY timestamp DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)
}
