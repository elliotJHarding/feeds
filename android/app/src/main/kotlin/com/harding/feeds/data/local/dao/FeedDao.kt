package com.harding.feeds.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.harding.feeds.client.models.FeedType
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.entity.FeedEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Queries exclude PENDING_DELETE rows - those are local tombstones already deleted from the
 * user's point of view, kept only until the sync engine pushes the delete.
 */
@Dao
interface FeedDao {

    @Query(
        "SELECT * FROM feeds WHERE syncState != 'PENDING_DELETE' " +
            "AND startTime >= :from AND startTime < :to ORDER BY startTime DESC"
    )
    fun feedsBetween(from: Instant, to: Instant): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE syncState != 'PENDING_DELETE' ORDER BY startTime DESC LIMIT 1")
    fun latestFeed(): Flow<FeedEntity?>

    @Query(
        "SELECT * FROM feeds WHERE syncState != 'PENDING_DELETE' AND type = :type " +
            "ORDER BY startTime DESC LIMIT 1"
    )
    fun latestFeedOfType(type: FeedType): Flow<FeedEntity?>

    @Query(
        "SELECT * FROM feeds WHERE syncState != 'PENDING_DELETE' AND endTime IS NOT NULL " +
            "ORDER BY startTime DESC LIMIT 1"
    )
    fun latestEndedFeed(): Flow<FeedEntity?>

    /** The in-progress feed (no end time), if any. */
    @Query(
        "SELECT * FROM feeds WHERE syncState != 'PENDING_DELETE' AND endTime IS NULL " +
            "ORDER BY startTime DESC LIMIT 1"
    )
    fun activeFeed(): Flow<FeedEntity?>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun byId(id: String): FeedEntity?

    @Query("SELECT * FROM feeds WHERE syncState != 'SYNCED'")
    suspend fun pending(): List<FeedEntity>

    @Upsert
    suspend fun upsert(feed: FeedEntity)

    @Query("UPDATE feeds SET syncState = :state WHERE id = :id")
    suspend fun setSyncState(id: String, state: SyncState)

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun hardDelete(id: String)

    /**
     * Marks a pushed row SYNCED and stamps the server-owned fields - but only if the row was
     * not edited again while the push was in flight (localRevision guard).
     */
    @Query(
        "UPDATE feeds SET syncState = 'SYNCED', createdBy = :createdBy, createdAt = :createdAt, " +
            "updatedAt = :updatedAt WHERE id = :id AND localRevision = :revision"
    )
    suspend fun markSynced(id: String, revision: Long, createdBy: Long?, createdAt: Instant?, updatedAt: Instant?)

    /**
     * Applies a pulled server feed unless the local row has unpushed changes - local pending
     * work always wins until it has been pushed (the push then resolves against the server).
     */
    @Transaction
    suspend fun mergeServerFeed(feed: FeedEntity) {
        val local = byId(feed.id)
        when {
            local == null -> upsert(feed)
            local.syncState == SyncState.SYNCED -> upsert(feed.copy(localRevision = local.localRevision))
            else -> Unit
        }
    }

    @Query(
        "DELETE FROM feeds WHERE syncState = 'SYNCED' AND babyId = :babyId " +
            "AND startTime >= :from AND id NOT IN (:keepIds)"
    )
    suspend fun deleteSyncedNotIn(babyId: Long, from: Instant, keepIds: List<String>)

    @Query("DELETE FROM feeds WHERE syncState = 'SYNCED' AND babyId = :babyId AND startTime >= :from")
    suspend fun deleteAllSyncedFrom(babyId: Long, from: Instant)

    @Query("DELETE FROM feeds")
    suspend fun clear()
}
