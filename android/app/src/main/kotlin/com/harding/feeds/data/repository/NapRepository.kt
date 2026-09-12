package com.harding.feeds.data.repository

import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.dao.NapDao
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.sync.SyncScheduler
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first nap operations: every write lands in Room immediately (with a
 * client-generated UUID) and a background push is requested - nothing here ever blocks on
 * the network. Deliberate mirror of [FeedRepository] - keep the two in step.
 */
class NapRepository(
    private val napDao: NapDao,
    private val syncScheduler: SyncScheduler,
    /** Fired after every local write so the widget/tile re-render immediately. */
    private val onNapsChanged: () -> Unit = {},
) {

    fun napsBetween(from: Instant, to: Instant): Flow<List<NapEntity>> =
        napDao.napsBetween(from, to)

    fun latestNap(): Flow<NapEntity?> = napDao.latestNap()

    fun latestEndedNap(): Flow<NapEntity?> = napDao.latestEndedNap()

    fun activeNap(): Flow<NapEntity?> = napDao.activeNap()

    /** Starts an in-progress nap (no end time yet). */
    suspend fun startNap(babyId: Long, startTime: Instant = Instant.now()): NapEntity =
        createNap(babyId, startTime, endTime = null)

    suspend fun createNap(babyId: Long, startTime: Instant, endTime: Instant?): NapEntity {
        val nap = NapEntity(
            id = UUID.randomUUID().toString(),
            babyId = babyId,
            startTime = startTime,
            endTime = endTime,
            createdBy = null,
            createdAt = null,
            updatedAt = null,
            syncState = SyncState.PENDING_CREATE,
        )
        napDao.upsert(nap)
        syncScheduler.requestSync()
        onNapsChanged()
        return nap
    }

    /** Ends the nap by setting its end time. */
    suspend fun endNap(id: String, endTime: Instant = Instant.now()) =
        editNap(id) { it.copy(endTime = endTime) }

    suspend fun updateNap(id: String, startTime: Instant, endTime: Instant?) =
        editNap(id) { it.copy(startTime = startTime, endTime = endTime) }

    suspend fun deleteNap(id: String) {
        val current = napDao.byId(id) ?: return
        if (current.syncState == SyncState.PENDING_CREATE) {
            // Never reached the server - no tombstone needed.
            napDao.hardDelete(id)
        } else {
            napDao.setSyncState(id, SyncState.PENDING_DELETE)
            syncScheduler.requestSync()
        }
        onNapsChanged()
    }

    private suspend fun editNap(id: String, transform: (NapEntity) -> NapEntity) {
        val current = napDao.byId(id) ?: return
        val edited = transform(current).copy(
            // A nap the server has never seen stays a create; anything else becomes an update.
            syncState = if (current.syncState == SyncState.PENDING_CREATE) SyncState.PENDING_CREATE
            else SyncState.PENDING_UPDATE,
            localRevision = current.localRevision + 1,
        )
        napDao.upsert(edited)
        syncScheduler.requestSync()
        onNapsChanged()
    }
}
