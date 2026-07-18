package com.harding.feeds.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.harding.feeds.data.local.entity.SyncCursorEntity
import java.time.Instant

@Dao
interface SyncCursorDao {

    @Query("SELECT lastServerUpdatedAt FROM sync_cursors WHERE babyId = :babyId")
    suspend fun cursor(babyId: Long): Instant?

    @Upsert
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursors")
    suspend fun clear()
}
