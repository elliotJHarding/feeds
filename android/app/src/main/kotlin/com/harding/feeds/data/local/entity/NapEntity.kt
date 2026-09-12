package com.harding.feeds.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.harding.feeds.data.local.SyncState
import java.time.Instant

/**
 * Local copy of a nap. Deliberate mirror of [FeedEntity] minus type, side and amount - a nap
 * has none of those. The id is a client-generated UUID (string form), which is what makes
 * offline sync retries idempotent - the server stores it verbatim.
 *
 * [localRevision] increments on every local edit. The sync engine captures it before a push
 * and marks the row SYNCED only if it is unchanged, so an edit made while the push was
 * in flight is never silently flattened back to SYNCED.
 */
@Entity(
    tableName = "naps",
    indices = [Index("babyId", "startTime"), Index("syncState")],
)
data class NapEntity(
    @PrimaryKey val id: String,
    val babyId: Long,
    val startTime: Instant,
    /** Null while the nap is in progress. */
    val endTime: Instant?,
    val createdBy: Long?,
    val createdAt: Instant?,
    /** Server-set on every write; null until first synced. */
    val updatedAt: Instant?,
    val syncState: SyncState,
    val localRevision: Long = 0,
)
