package com.harding.feeds.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: naps.
 *
 * Additive only - two new tables and their indices. Nothing existing is altered, dropped or
 * copied, so this migration cannot lose the family's feed history. That is also why the nap
 * pull cursor gets its own table instead of a `resource` column widening `sync_cursors`'
 * primary key: SQLite cannot alter a primary key, so that would mean create-copy-drop-rename
 * over live data on the project's first-ever migration.
 *
 * The statements below are copied verbatim out of
 * `app/schemas/com.harding.feeds.data.local.FeedsDatabase/2.json`. They must match Room's
 * generated schema byte for byte: a mismatch throws
 * `IllegalStateException: Migration didn't properly handle` at runtime, and only on an
 * upgrading install - a fresh install runs createAllTables and never executes this. Do not
 * hand-edit them.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `naps` (`id` TEXT NOT NULL, `babyId` INTEGER NOT NULL, " +
                "`startTime` INTEGER NOT NULL, `endTime` INTEGER, `createdBy` INTEGER, " +
                "`createdAt` INTEGER, `updatedAt` INTEGER, `syncState` TEXT NOT NULL, " +
                "`localRevision` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_naps_babyId_startTime` ON `naps` (`babyId`, `startTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_naps_syncState` ON `naps` (`syncState`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `nap_sync_cursors` (`babyId` INTEGER NOT NULL, " +
                "`lastServerUpdatedAt` INTEGER NOT NULL, PRIMARY KEY(`babyId`))"
        )
    }
}
