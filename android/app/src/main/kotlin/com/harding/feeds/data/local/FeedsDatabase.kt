package com.harding.feeds.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import com.harding.feeds.data.local.dao.BabyDao
import com.harding.feeds.data.local.dao.FeedDao
import com.harding.feeds.data.local.dao.NapDao
import com.harding.feeds.data.local.dao.NapSyncCursorDao
import com.harding.feeds.data.local.dao.SessionDao
import com.harding.feeds.data.local.dao.SyncCursorDao
import com.harding.feeds.data.local.entity.BabyEntity
import com.harding.feeds.data.local.entity.FamilyGroupEntity
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.data.local.entity.NapSyncCursorEntity
import com.harding.feeds.data.local.entity.SyncCursorEntity
import com.harding.feeds.data.local.entity.UserEntity

@Database(
    entities = [
        FeedEntity::class,
        NapEntity::class,
        BabyEntity::class,
        UserEntity::class,
        FamilyGroupEntity::class,
        SyncCursorEntity::class,
        NapSyncCursorEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FeedsDatabase : RoomDatabase() {

    abstract fun feedDao(): FeedDao
    abstract fun napDao(): NapDao
    abstract fun babyDao(): BabyDao
    abstract fun sessionDao(): SessionDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun napSyncCursorDao(): NapSyncCursorDao

    /**
     * Wipes all cached data - used on sign-out.
     *
     * Hand-enumerated, so a new table must be added here too. Miss one and the other parent's
     * records stay on the device after this one signs out.
     */
    suspend fun clearAll() = withTransaction {
        feedDao().clear()
        napDao().clear()
        babyDao().clear()
        sessionDao().clearUsers()
        sessionDao().clearGroup()
        syncCursorDao().clear()
        napSyncCursorDao().clear()
    }

    companion object {
        fun build(context: Context): FeedsDatabase =
            Room.databaseBuilder(context, FeedsDatabase::class.java, "feeds.db")
                // No fallbackToDestructiveMigration: on a mismatch it would silently wipe the
                // family's feed history. A crash is better - the data survives on the device
                // while a fix ships.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
