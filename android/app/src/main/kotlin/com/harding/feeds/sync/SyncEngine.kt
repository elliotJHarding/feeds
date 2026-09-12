package com.harding.feeds.sync

import android.util.Log
import com.harding.feeds.auth.TokenStore
import com.harding.feeds.client.apis.BabiesApi
import com.harding.feeds.client.apis.FeedsApi
import com.harding.feeds.client.apis.NapsApi
import com.harding.feeds.client.models.FeedDto
import com.harding.feeds.client.models.NapDto
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.dao.BabyDao
import com.harding.feeds.data.local.dao.FeedDao
import com.harding.feeds.data.local.dao.NapDao
import com.harding.feeds.data.local.dao.NapSyncCursorDao
import com.harding.feeds.data.local.dao.SyncCursorDao
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.data.local.entity.NapSyncCursorEntity
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
 * Push: every locally pending record goes up - creates (POST, idempotent by client UUID),
 * updates (PUT) and deletes (DELETE, tombstone rows).
 *
 * Pull, per baby, two queries per resource by contract design (model/openapi.yaml):
 * - updatedSince incremental pull, which catches creates and updates only;
 * - a visible-window from-range refetch, because server deletes are hard deletes with no
 *   tombstone and are invisible to updatedSince - replacing the window's contents is the
 *   only way to observe them.
 *
 * Feeds and naps run the same algorithm over separate APIs, DAOs, DTOs and cursors. The
 * duplication is deliberate: FeedsApi and NapsApi share no supertype, FeedDto and NapDto share
 * no interface, and generated code must never be hand-edited, so an adapter covering push and
 * pull would need about sixteen one-line members and two implementations - more code than it
 * removes, and none of it checkable against its sibling. The codebase already handles
 * "same rule, two places" with a cross-reference comment (see FeedService.startVoiceFeed).
 * Change one of the paired methods below and you must change the other.
 *
 * The three rules that are easy to get wrong, stated once:
 * - a 200 on create means idempotent replay, and the server returned the row UNCHANGED, so the
 *   local state must follow as an update to actually land;
 * - a 404 on push means the other phone's delete wins;
 * - the window delete predicate must stay the exact complement of the server's `from` filter.
 */
class SyncEngine(
    private val feedsApi: FeedsApi,
    private val napsApi: NapsApi,
    private val babiesApi: BabiesApi,
    private val feedDao: FeedDao,
    private val napDao: NapDao,
    private val babyDao: BabyDao,
    private val syncCursorDao: SyncCursorDao,
    private val napSyncCursorDao: NapSyncCursorDao,
    private val tokenStore: TokenStore,
    /** Fired after a successful sync - a pull may have brought the other phone's records. */
    private val onDataChanged: () -> Unit = {},
) {

    private val mutex = Mutex()

    /** Returns false when the attempt failed and is worth retrying. */
    suspend fun sync(): Boolean {
        if (!tokenStore.isLoggedIn) return true
        return try {
            mutex.withLock {
                // Push order between feeds and naps does not matter: the exclusivity rule is
                // deterministic in the new event's startTime, which travels in the create body,
                // so both orders converge on the same end times.
                pushPendingFeeds()
                pushPendingNaps()
                pullBabies()
                babyDao.ids().forEach {
                    pullFeeds(it)
                    pullNaps(it)
                }
            }
            onDataChanged()
            true
        } catch (e: IOException) {
            Log.i(TAG, "Sync attempt failed, will retry", e)
            false
        }
    }

    // Feeds

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
            HTTP_REPLAYED -> pushUpdate(feed)
            else -> markSynced(feed, response.bodyOrThrow())
        }
    }

    private suspend fun pushUpdate(feed: FeedEntity) {
        val response = feedsApi.updateFeed(UUID.fromString(feed.id), feed.toDto())
        when {
            response.isSuccessful -> markSynced(feed, response.body())
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

    private suspend fun pullFeeds(babyId: Long) {
        val cursor = syncCursorDao.cursor(babyId)
        val windowFrom = Instant.now().minus(FEED_WINDOW)

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

        advanceCursor(cursor, (changed + window).map { it.updatedAt?.toInstant() }) {
            syncCursorDao.upsert(SyncCursorEntity(babyId, it))
        }
    }

    // Naps - deliberate mirror of the feed methods above; see the class comment.

    private suspend fun pushPendingNaps() {
        napDao.pending().forEach { nap ->
            when (nap.syncState) {
                SyncState.PENDING_CREATE -> pushNapCreate(nap)
                SyncState.PENDING_UPDATE -> pushNapUpdate(nap)
                SyncState.PENDING_DELETE -> pushNapDelete(nap)
                SyncState.SYNCED -> Unit
            }
        }
    }

    private suspend fun pushNapCreate(nap: NapEntity) {
        val response = napsApi.createNap(nap.toDto())
        when (response.code()) {
            HTTP_REPLAYED -> pushNapUpdate(nap)
            else -> markNapSynced(nap, response.bodyOrThrow())
        }
    }

    private suspend fun pushNapUpdate(nap: NapEntity) {
        val response = napsApi.updateNap(UUID.fromString(nap.id), nap.toDto())
        when {
            response.isSuccessful -> markNapSynced(nap, response.body())
            response.code() == HTTP_NOT_FOUND -> napDao.hardDelete(nap.id)
            else -> response.bodyOrThrow()
        }
    }

    private suspend fun pushNapDelete(nap: NapEntity) {
        val response = napsApi.deleteNap(UUID.fromString(nap.id))
        when {
            response.isSuccessful || response.code() == HTTP_NOT_FOUND -> napDao.hardDelete(nap.id)
            else -> response.bodyOrThrow()
        }
    }

    private suspend fun markNapSynced(nap: NapEntity, serverNap: NapDto?) {
        napDao.markSynced(
            id = nap.id,
            revision = nap.localRevision,
            createdBy = serverNap?.createdBy,
            createdAt = serverNap?.createdAt?.toInstant(),
            updatedAt = serverNap?.updatedAt?.toInstant(),
        )
    }

    private suspend fun pullNaps(babyId: Long) {
        val cursor = napSyncCursorDao.cursor(babyId)
        val windowFrom = Instant.now().minus(NAP_WINDOW)

        val changed = napsApi
            .getNaps(babyId, updatedSince = cursor?.atOffset(ZoneOffset.UTC))
            .bodyOrThrow()
        changed.forEach { napDao.mergeServerNap(it.toEntity()) }

        val window = napsApi
            .getNaps(babyId, from = windowFrom.atOffset(ZoneOffset.UTC))
            .bodyOrThrow()
        window.forEach { napDao.mergeServerNap(it.toEntity()) }
        val keepIds = window.map { it.id.toString() }
        if (keepIds.isEmpty()) {
            napDao.deleteAllSyncedFrom(babyId, windowFrom)
        } else {
            napDao.deleteSyncedNotIn(babyId, windowFrom, keepIds)
        }

        advanceCursor(cursor, (changed + window).map { it.updatedAt?.toInstant() }) {
            napSyncCursorDao.upsert(NapSyncCursorEntity(babyId, it))
        }
    }

    // Shared

    private suspend fun pullBabies() {
        babyDao.upsertAll(babiesApi.getBabies().bodyOrThrow().map { it.toEntity() })
    }

    /** Cursor is the greatest server updatedAt observed - server clock, never the device's. */
    private suspend fun advanceCursor(
        current: Instant?,
        seen: List<Instant?>,
        save: suspend (Instant) -> Unit,
    ) {
        val maxSeen = seen.filterNotNull().maxOrNull() ?: return
        if (current == null || maxSeen.isAfter(current)) save(maxSeen)
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val HTTP_REPLAYED = 200

        /** Feeds are minutes long; 48h of them is the visible history the UI shows. */
        val FEED_WINDOW: Duration = Duration.ofHours(48)

        /**
         * Naps are hours long, and "nobody hit stop overnight" is an ordinary Tuesday. Anything
         * that *started* before the window is invisible to delete reconciliation for good - the
         * window filters on startTime, not overlap - so a 48h window would let a forgotten
         * in-progress nap strand itself locally and show as a permanent "nap in progress" on the
         * entry screen, widget and tile. A week closes that for roughly 35 extra rows per poll.
         */
        val NAP_WINDOW: Duration = Duration.ofDays(7)
    }
}
