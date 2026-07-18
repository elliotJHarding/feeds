package com.harding.feeds.data.repository

import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.dao.FeedDao
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.sync.SyncScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first feed operations: every write lands in Room immediately (with a
 * client-generated UUID) and a background push is requested - nothing here ever blocks on
 * the network.
 */
class FeedRepository(
    private val feedDao: FeedDao,
    private val syncScheduler: SyncScheduler,
    /** Fired after every local write so the widget/tile re-render immediately. */
    private val onFeedsChanged: () -> Unit = {},
) {

    fun feedsBetween(from: Instant, to: Instant): Flow<List<FeedEntity>> =
        feedDao.feedsBetween(from, to)

    fun feedsOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<FeedEntity>> =
        feedsBetween(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())

    fun latestFeed(): Flow<FeedEntity?> = feedDao.latestFeed()

    fun latestEndedFeed(): Flow<FeedEntity?> = feedDao.latestEndedFeed()

    fun activeFeed(): Flow<FeedEntity?> = feedDao.activeFeed()

    /** Starts an in-progress feed (no end time yet). */
    suspend fun startFeed(babyId: Long, side: Side?, startTime: Instant = Instant.now()): FeedEntity =
        createFeed(babyId, side, startTime, endTime = null)

    suspend fun createFeed(
        babyId: Long,
        side: Side?,
        startTime: Instant,
        endTime: Instant?,
        // Generator 7.2.0 camelizes enum entries (bREAST/bOTTLE); the wire value is "BREAST".
        type: FeedType = FeedType.bREAST,
        amountMl: Int? = null,
    ): FeedEntity {
        val feed = FeedEntity(
            id = UUID.randomUUID().toString(),
            babyId = babyId,
            type = type,
            side = side,
            amountMl = amountMl,
            startTime = startTime,
            endTime = endTime,
            createdBy = null,
            createdAt = null,
            updatedAt = null,
            syncState = SyncState.PENDING_CREATE,
        )
        feedDao.upsert(feed)
        syncScheduler.requestSync()
        onFeedsChanged()
        return feed
    }

    /** Ends the feed by setting its end time. */
    suspend fun endFeed(id: String, endTime: Instant = Instant.now()) =
        editFeed(id) { it.copy(endTime = endTime) }

    suspend fun updateFeed(
        id: String,
        side: Side?,
        startTime: Instant,
        endTime: Instant?,
        amountMl: Int? = null,
    ) = editFeed(id) { it.copy(side = side, startTime = startTime, endTime = endTime, amountMl = amountMl) }

    suspend fun deleteFeed(id: String) {
        val current = feedDao.byId(id) ?: return
        if (current.syncState == SyncState.PENDING_CREATE) {
            // Never reached the server - no tombstone needed.
            feedDao.hardDelete(id)
        } else {
            feedDao.setSyncState(id, SyncState.PENDING_DELETE)
            syncScheduler.requestSync()
        }
        onFeedsChanged()
    }

    private suspend fun editFeed(id: String, transform: (FeedEntity) -> FeedEntity) {
        val current = feedDao.byId(id) ?: return
        val edited = transform(current).copy(
            // A feed the server has never seen stays a create; anything else becomes an update.
            syncState = if (current.syncState == SyncState.PENDING_CREATE) SyncState.PENDING_CREATE
            else SyncState.PENDING_UPDATE,
            localRevision = current.localRevision + 1,
        )
        feedDao.upsert(edited)
        syncScheduler.requestSync()
        onFeedsChanged()
    }
}
