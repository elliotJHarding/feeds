package com.harding.feeds.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import com.harding.feeds.data.local.dao.BabyDao
import com.harding.feeds.data.local.dao.FeedDao
import com.harding.feeds.data.local.dao.SessionDao
import com.harding.feeds.data.local.dao.SyncCursorDao
import com.harding.feeds.data.local.entity.BabyEntity
import com.harding.feeds.data.local.entity.FamilyGroupEntity
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.SyncCursorEntity
import com.harding.feeds.data.local.entity.UserEntity

@Database(
    entities = [
        FeedEntity::class,
        BabyEntity::class,
        UserEntity::class,
        FamilyGroupEntity::class,
        SyncCursorEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FeedsDatabase : RoomDatabase() {

    abstract fun feedDao(): FeedDao
    abstract fun babyDao(): BabyDao
    abstract fun sessionDao(): SessionDao
    abstract fun syncCursorDao(): SyncCursorDao

    /** Wipes all cached data - used on sign-out. */
    suspend fun clearAll() = withTransaction {
        feedDao().clear()
        babyDao().clear()
        sessionDao().clearUsers()
        sessionDao().clearGroup()
        syncCursorDao().clear()
    }

    companion object {
        fun build(context: Context): FeedsDatabase =
            Room.databaseBuilder(context, FeedsDatabase::class.java, "feeds.db").build()
    }
}
