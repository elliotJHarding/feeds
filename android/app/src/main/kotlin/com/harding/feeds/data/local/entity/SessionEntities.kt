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
