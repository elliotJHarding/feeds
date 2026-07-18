package com.harding.feeds.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.SyncState
import java.time.Instant

/**
 * Local copy of a feed. The id is a client-generated UUID (string form), which is what makes
 * offline sync retries idempotent - the server stores it verbatim.
 *
 * [localRevision] increments on every local edit. The sync engine captures it before a push
 * and marks the row SYNCED only if it is unchanged, so an edit made while the push was
 * in flight is never silently flattened back to SYNCED.
 */
@Entity(
    tableName = "feeds",
    indices = [Index("babyId", "startTime"), Index("syncState")],
)
data class FeedEntity(
    @PrimaryKey val id: String,
    val babyId: Long,
    val type: FeedType,
    val side: Side?,
    val amountMl: Int?,
    val startTime: Instant,
    /** Null while the feed is in progress. */
    val endTime: Instant?,
    val createdBy: Long?,
    val createdAt: Instant?,
    /** Server-set on every write; null until first synced. */
    val updatedAt: Instant?,
    val syncState: SyncState,
    val localRevision: Long = 0,
)
