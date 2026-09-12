package com.harding.feeds.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.harding.feeds.data.local.entity.NapSyncCursorEntity
import java.time.Instant

/** [SyncCursorDao] for the nap pull stream, which advances independently of the feed one. */
@Dao
interface NapSyncCursorDao {

    @Query("SELECT lastServerUpdatedAt FROM nap_sync_cursors WHERE babyId = :babyId")
    suspend fun cursor(babyId: Long): Instant?

    @Upsert
    suspend fun upsert(cursor: NapSyncCursorEntity)

    @Query("DELETE FROM nap_sync_cursors")
    suspend fun clear()
}
