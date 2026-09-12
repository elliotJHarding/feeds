package com.harding.feeds.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.entity.NapEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Deliberate mirror of [FeedDao] for naps. Queries exclude PENDING_DELETE rows - those are
 * local tombstones already deleted from the user's point of view, kept only until the sync
 * engine pushes the delete.
 */
@Dao
interface NapDao {

    @Query(
        "SELECT * FROM naps WHERE syncState != 'PENDING_DELETE' " +
            "AND startTime >= :from AND startTime < :to ORDER BY startTime DESC"
    )
    fun napsBetween(from: Instant, to: Instant): Flow<List<NapEntity>>

    @Query("SELECT * FROM naps WHERE syncState != 'PENDING_DELETE' ORDER BY startTime DESC LIMIT 1")
    fun latestNap(): Flow<NapEntity?>

    @Query(
        "SELECT * FROM naps WHERE syncState != 'PENDING_DELETE' AND endTime IS NOT NULL " +
            "ORDER BY startTime DESC LIMIT 1"
    )
    fun latestEndedNap(): Flow<NapEntity?>

    /** The in-progress nap (no end time), if any. */
    @Query(
        "SELECT * FROM naps WHERE syncState != 'PENDING_DELETE' AND endTime IS NULL " +
            "ORDER BY startTime DESC LIMIT 1"
    )
    fun activeNap(): Flow<NapEntity?>

    @Query("SELECT * FROM naps WHERE id = :id")
    suspend fun byId(id: String): NapEntity?

    @Query("SELECT * FROM naps WHERE syncState != 'SYNCED'")
    suspend fun pending(): List<NapEntity>

    @Upsert
    suspend fun upsert(nap: NapEntity)

    @Query("UPDATE naps SET syncState = :state WHERE id = :id")
    suspend fun setSyncState(id: String, state: SyncState)

    @Query("DELETE FROM naps WHERE id = :id")
    suspend fun hardDelete(id: String)

    /**
     * Marks a pushed row SYNCED and stamps the server-owned fields - but only if the row was
     * not edited again while the push was in flight (localRevision guard).
     */
    @Query(
        "UPDATE naps SET syncState = 'SYNCED', createdBy = :createdBy, createdAt = :createdAt, " +
            "updatedAt = :updatedAt WHERE id = :id AND localRevision = :revision"
    )
    suspend fun markSynced(id: String, revision: Long, createdBy: Long?, createdAt: Instant?, updatedAt: Instant?)

    /**
     * Applies a pulled server nap unless the local row has unpushed changes - local pending
     * work always wins until it has been pushed (the push then resolves against the server).
     */
    @Transaction
    suspend fun mergeServerNap(nap: NapEntity) {
        val local = byId(nap.id)
        when {
            local == null -> upsert(nap)
            local.syncState == SyncState.SYNCED -> upsert(nap.copy(localRevision = local.localRevision))
            else -> Unit
        }
    }

    /**
     * The window delete that reconciles server deletes, which carry no tombstone.
     *
     * The `startTime >= :from` predicate must stay the exact complement of the server's `from`
     * filter on GET /naps, which also matches on startTime rather than on overlap. Change one
     * side alone - say, to "overlaps the window" - and this deletes naps that are still alive
     * on the server.
     */
    @Query(
        "DELETE FROM naps WHERE syncState = 'SYNCED' AND babyId = :babyId " +
            "AND startTime >= :from AND id NOT IN (:keepIds)"
    )
    suspend fun deleteSyncedNotIn(babyId: Long, from: Instant, keepIds: List<String>)

    @Query("DELETE FROM naps WHERE syncState = 'SYNCED' AND babyId = :babyId AND startTime >= :from")
    suspend fun deleteAllSyncedFrom(babyId: Long, from: Instant)

    @Query("DELETE FROM naps")
    suspend fun clear()
}
