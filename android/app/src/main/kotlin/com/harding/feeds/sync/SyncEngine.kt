package com.harding.feeds.sync

import android.util.Log
import com.harding.feeds.auth.TokenStore
import com.harding.feeds.client.apis.BabiesApi
import com.harding.feeds.client.apis.FeedsApi
import com.harding.feeds.client.models.FeedDto
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.dao.BabyDao
import com.harding.feeds.data.local.dao.FeedDao
import com.harding.feeds.data.local.dao.SyncCursorDao
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.SyncCursorEntity
import com.harding.feeds.data.remote.bodyOrThrow
import com.harding.feeds.data.remote.toDto
import com.harding.feeds.data.remote.toEntity
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single sync algorithm, shared by the WorkManager job and the foreground poller.
 *
 * Push: every locally pending feed goes up - creates (POST, idempotent by client UUID),
 * updates (PUT) and deletes (DELETE, tombstone rows).
 *
 * Pull, per baby, two queries by contract design (model/openapi.yaml):
 * - updatedSince incremental pull, which catches creates and updates only;
 * - a visible-window (48h) from-range refetch, because server deletes are hard deletes with
 *   no tombstone and are invisible to updatedSince - replacing the window's contents is the
 *   only way to observe them.
 */
class SyncEngine(
    private val feedsApi: FeedsApi,
    private val babiesApi: BabiesApi,
    private val feedDao: FeedDao,
    private val babyDao: BabyDao,
    private val syncCursorDao: SyncCursorDao,
    private val tokenStore: TokenStore,
    /** Fired after a successful sync - a pull may have brought the other phone's feeds. */
    private val onFeedsChanged: () -> Unit = {},
) {

    private val mutex = Mutex()

    /** Returns false when the attempt failed and is worth retrying. */
    suspend fun sync(): Boolean {
        if (!tokenStore.isLoggedIn) return true
        return try {
            mutex.withLock {
                pushPendingFeeds()
                pullBabies()
                babyDao.ids().forEach { pullFeeds(it) }
            }
            onFeedsChanged()
            true
        } catch (e: IOException) {
            Log.i(TAG, "Sync attempt failed, will retry", e)
            false
        }
    }

    private suspend fun pushPendingFeeds() {
        feedDao.pending().forEach { feed ->
            when (feed.syncState) {
                SyncState.PENDING_CREATE -> pushCreate(feed)
                SyncState.PENDING_UPDATE -> pushUpdate(feed)
                SyncState.PENDING_DELETE -> pushDelete(feed)
                SyncState.SYNCED -> Unit
            }
        }
    }

    private suspend fun pushCreate(feed: FeedEntity) {
        val response = feedsApi.createFeed(feed.toDto())
        when (response.code()) {
            // 200 = idempotent replay: the server already had this id and returned it
            // UNCHANGED, so local state (e.g. an endTime set while offline) must follow as an
            // update to actually land.
            HTTP_REPLAYED -> pushUpdate(feed)
            else -> markSynced(feed, response.bodyOrThrow())
        }
    }

    private suspend fun pushUpdate(feed: FeedEntity) {
        val response = feedsApi.updateFeed(UUID.fromString(feed.id), feed.toDto())
        when {
            response.isSuccessful -> markSynced(feed, response.body())
            // Deleted on the server by the other phone - the delete wins.
            response.code() == HTTP_NOT_FOUND -> feedDao.hardDelete(feed.id)
            else -> response.bodyOrThrow()
        }
    }

    private suspend fun pushDelete(feed: FeedEntity) {
        val response = feedsApi.deleteFeed(UUID.fromString(feed.id))
        when {
            response.isSuccessful || response.code() == HTTP_NOT_FOUND -> feedDao.hardDelete(feed.id)
            else -> response.bodyOrThrow()
        }
    }

    private suspend fun markSynced(feed: FeedEntity, serverFeed: FeedDto?) {
        feedDao.markSynced(
            id = feed.id,
            revision = feed.localRevision,
            createdBy = serverFeed?.createdBy,
            createdAt = serverFeed?.createdAt?.toInstant(),
            updatedAt = serverFeed?.updatedAt?.toInstant(),
        )
    }

    private suspend fun pullBabies() {
        babyDao.upsertAll(babiesApi.getBabies().bodyOrThrow().map { it.toEntity() })
    }

    private suspend fun pullFeeds(babyId: Long) {
        val cursor = syncCursorDao.cursor(babyId)
        val windowFrom = Instant.now().minus(VISIBLE_WINDOW)

        // Creates and updates: everything on the first sync, incremental afterwards.
        val changed = feedsApi
            .getFeeds(babyId, updatedSince = cursor?.atOffset(ZoneOffset.UTC))
            .bodyOrThrow()
        changed.forEach { feedDao.mergeServerFeed(it.toEntity()) }

        // Deletes: replace the visible window's contents wholesale.
        val window = feedsApi
            .getFeeds(babyId, from = windowFrom.atOffset(ZoneOffset.UTC))
            .bodyOrThrow()
        window.forEach { feedDao.mergeServerFeed(it.toEntity()) }
        val keepIds = window.map { it.id.toString() }
        if (keepIds.isEmpty()) {
            feedDao.deleteAllSyncedFrom(babyId, windowFrom)
        } else {
            feedDao.deleteSyncedNotIn(babyId, windowFrom, keepIds)
        }

        advanceCursor(babyId, cursor, changed + window)
    }

    /** Cursor is the greatest server updatedAt observed - server clock, never the device's. */
    private suspend fun advanceCursor(babyId: Long, cursor: Instant?, seen: List<FeedDto>) {
        val maxSeen = seen.mapNotNull { it.updatedAt?.toInstant() }.maxOrNull() ?: return
        if (cursor == null || maxSeen.isAfter(cursor)) {
            syncCursorDao.upsert(SyncCursorEntity(babyId, maxSeen))
        }
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val HTTP_REPLAYED = 200
        val VISIBLE_WINDOW: Duration = Duration.ofHours(48)
    }
}
