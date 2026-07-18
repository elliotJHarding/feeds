package com.harding.feeds.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.harding.feeds.data.local.entity.BabyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyDao {

    @Query("SELECT * FROM babies ORDER BY id")
    fun babies(): Flow<List<BabyEntity>>

    @Query("SELECT id FROM babies ORDER BY id")
    suspend fun ids(): List<Long>

    @Upsert
    suspend fun upsertAll(babies: List<BabyEntity>)

    @Query("DELETE FROM babies")
    suspend fun clear()
}
