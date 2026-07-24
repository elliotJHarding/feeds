package com.harding.feeds.domain

import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.dao.BabyDao
import com.harding.feeds.data.local.dao.FeedDao
import com.harding.feeds.data.local.entity.BabyEntity
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.repository.FeedRepository
import com.harding.feeds.sync.SyncScheduler
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the bottle rules end-to-end through a real FeedRepository over in-memory DAOs:
 * a bottle is stored as a completed point event and never disturbs the L/R alternation.
 */
class ToggleFeedUseCaseTest {

    private val feedDao = InMemoryFeedDao()
    private val babyDao = InMemoryBabyDao(ids = listOf(1L))
    private val repository = FeedRepository(feedDao, mockk<SyncScheduler>(relaxed = true))
    private val useCase = ToggleFeedUseCase(repository, babyDao)

    private val t0: Instant = Instant.parse("2026-07-23T08:00:00Z")

    @Test
    fun `next side alternates off the last breast feed, skipping bottles`() = runTest {
        feedDao.upsert(breastFeed(side = Side.l, start = t0, end = t0.plusSeconds(600)))
        feedDao.upsert(bottleFeed(time = t0.plusSeconds(3_600)))

        assertEquals(Side.r, useCase.defaultNextSide().first())
    }

    @Test
    fun `next side defaults to L when history is bottles only`() = runTest {
        feedDao.upsert(bottleFeed(time = t0))

        assertEquals(Side.l, useCase.defaultNextSide().first())
    }

    @Test
    fun `logBottle stores a completed point event with no side`() = runTest {
        val result = useCase.logBottle(amountMl = 90, time = t0)

        assertEquals(ToggleFeedUseCase.Result.LoggedBottle, result)
        val stored = feedDao.all().single()
        assertEquals(FeedType.bOTTLE, stored.type)
        assertEquals(stored.startTime, stored.endTime)
        assertNull(stored.side)
        assertEquals(90, stored.amountMl)
    }

    @Test
    fun `logBottle amount is optional`() = runTest {
        useCase.logBottle(amountMl = null, time = t0)

        assertNull(feedDao.all().single().amountMl)
    }

    @Test
    fun `logBottle without a baby is NoBaby`() = runTest {
        val emptyUseCase = ToggleFeedUseCase(repository, InMemoryBabyDao(ids = emptyList()))

        assertEquals(ToggleFeedUseCase.Result.NoBaby, emptyUseCase.logBottle(90, t0))
        assertTrue(feedDao.all().isEmpty())
    }

    @Test
    fun `toggle after a bottle starts a breast feed - a bottle never reads as active`() = runTest {
        useCase.logBottle(amountMl = 90, time = t0)

        val result = useCase.toggle()

        assertTrue(result is ToggleFeedUseCase.Result.Started)
        val active = feedDao.activeFeed().first()
        assertNotNull(active)
        assertEquals(FeedType.bREAST, active!!.type)
    }

    private fun breastFeed(side: Side, start: Instant, end: Instant?) = FeedEntity(
        id = UUID.randomUUID().toString(),
        babyId = 1L,
        type = FeedType.bREAST,
        side = side,
        amountMl = null,
        startTime = start,
        endTime = end,
        createdBy = null,
        createdAt = null,
        updatedAt = null,
        syncState = SyncState.SYNCED,
    )

    private fun bottleFeed(time: Instant, amountMl: Int? = 90) = FeedEntity(
        id = UUID.randomUUID().toString(),
        babyId = 1L,
        type = FeedType.bOTTLE,
        side = null,
        amountMl = amountMl,
        startTime = time,
        endTime = time,
        createdBy = null,
        createdAt = null,
        updatedAt = null,
        syncState = SyncState.SYNCED,
    )
}

/** In-memory FeedDao mirroring the Room queries the tested paths rely on. */
private class InMemoryFeedDao : FeedDao {

    private val feeds = MutableStateFlow<Map<String, FeedEntity>>(emptyMap())

    fun all(): List<FeedEntity> = feeds.value.values.toList()

    private fun visible() = feeds.map { rows ->
        rows.values.filter { it.syncState != SyncState.PENDING_DELETE }.sortedByDescending { it.startTime }
    }

    override fun feedsBetween(from: Instant, to: Instant): Flow<List<FeedEntity>> =
        visible().map { rows -> rows.filter { it.startTime >= from && it.startTime < to } }

    override fun latestFeed(): Flow<FeedEntity?> = visible().map { it.firstOrNull() }

    override fun latestFeedOfType(type: FeedType): Flow<FeedEntity?> =
        visible().map { rows -> rows.firstOrNull { it.type == type } }

    override fun latestEndedFeed(): Flow<FeedEntity?> =
        visible().map { rows -> rows.firstOrNull { it.endTime != null } }

    override fun activeFeed(): Flow<FeedEntity?> =
        visible().map { rows -> rows.firstOrNull { it.endTime == null } }

    override suspend fun byId(id: String): FeedEntity? = feeds.value[id]

    override suspend fun pending(): List<FeedEntity> =
        feeds.value.values.filter { it.syncState != SyncState.SYNCED }

    override suspend fun upsert(feed: FeedEntity) {
        feeds.value = feeds.value + (feed.id to feed)
    }

    override suspend fun setSyncState(id: String, state: SyncState) {
        feeds.value[id]?.let { upsert(it.copy(syncState = state)) }
    }

    override suspend fun hardDelete(id: String) {
        feeds.value = feeds.value - id
    }

    override suspend fun markSynced(
        id: String,
        revision: Long,
        createdBy: Long?,
        createdAt: Instant?,
        updatedAt: Instant?,
    ) {
        feeds.value[id]?.takeIf { it.localRevision == revision }?.let {
            upsert(it.copy(syncState = SyncState.SYNCED, createdBy = createdBy, createdAt = createdAt, updatedAt = updatedAt))
        }
    }

    override suspend fun deleteSyncedNotIn(babyId: Long, from: Instant, keepIds: List<String>) {
        feeds.value = feeds.value.filterValues {
            !(it.syncState == SyncState.SYNCED && it.babyId == babyId && it.startTime >= from && it.id !in keepIds)
        }
    }

    override suspend fun deleteAllSyncedFrom(babyId: Long, from: Instant) {
        feeds.value = feeds.value.filterValues {
            !(it.syncState == SyncState.SYNCED && it.babyId == babyId && it.startTime >= from)
        }
    }

    override suspend fun clear() {
        feeds.value = emptyMap()
    }
}

private class InMemoryBabyDao(private val ids: List<Long>) : BabyDao {

    override fun babies(): Flow<List<BabyEntity>> =
        MutableStateFlow(ids.map { BabyEntity(id = it, name = "Baby $it", dateOfBirth = null) })

    override suspend fun ids(): List<Long> = ids

    override suspend fun upsertAll(babies: List<BabyEntity>) = Unit

    override suspend fun clear() = Unit
}
