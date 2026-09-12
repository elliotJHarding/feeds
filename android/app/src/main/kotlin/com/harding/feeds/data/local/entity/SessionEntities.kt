package com.harding.feeds.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** A known AppUser - the signed-in user ([isSelf]) plus fellow group members. */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val name: String?,
    val email: String?,
    val pictureUrl: String?,
    val isSelf: Boolean,
)

/** The single family group this device belongs to (one row at most). */
@Entity(tableName = "family_group")
data class FamilyGroupEntity(
    @PrimaryKey val uuid: String,
)

/**
 * Per-baby incremental pull cursor: the greatest server updatedAt seen so far. Server-derived
 * so device clock skew cannot skip changes.
 */
@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val babyId: Long,
    val lastServerUpdatedAt: Instant,
)

/**
 * The same cursor for naps. A second table rather than a `resource` column on [SyncCursorEntity]
 * because SQLite cannot alter a primary key: widening that key would mean create-copy-drop-rename
 * over the family's live data on the project's first-ever migration, to buy a tidier shape and no
 * new capability. This keeps the migration to pure CREATE statements, and its worst failure mode
 * is a reset cursor, which self-heals - a null cursor means "pull everything", exactly the
 * first-sync path, and the merge is idempotent.
 */
@Entity(tableName = "nap_sync_cursors")
data class NapSyncCursorEntity(
    @PrimaryKey val babyId: Long,
    val lastServerUpdatedAt: Instant,
)
