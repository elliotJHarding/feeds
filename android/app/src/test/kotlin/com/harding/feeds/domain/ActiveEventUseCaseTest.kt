package com.harding.feeds.domain

import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.dao.BabyDao
import com.harding.feeds.data.local.dao.FeedDao
import com.harding.feeds.data.local.dao.NapDao
import com.harding.feeds.data.local.entity.BabyEntity
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.data.repository.FeedRepository
import com.harding.feeds.data.repository.NapRepository
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
 * Exercises the rules that live only in the use case, end-to-end through real repositories over
 * in-memory DAOs: the L/R alternation, the bottle point-event rules, and the invariant that at
 * most one event - feed or nap - is in progress at a time.
 */
class ActiveEventUseCaseTest {

    private val feedDao = InMemoryFeedDao()
    private val napDao = InMemoryNapDao()
    private val babyDao = InMemoryBabyDao(ids = listOf(1L))
    private val feedRepository = FeedRepository(feedDao, mockk<SyncScheduler>(relaxed = true))
    private val napRepository = NapRepository(napDao, mockk<SyncScheduler>(relaxed = true))
    private val useCase = ActiveEventUseCase(feedRepository, napRepository, babyDao)

    private val t0: Instant = Instant.parse("2026-07-23T08:00:00Z")

    // Side alternation

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

    // Bottles

    @Test
    fun `logBottle stores a completed point event with no side`() = runTest {
        val result = useCase.logBottle(amountMl = 90, time = t0)

        assertEquals(ActiveEventUseCase.Result.LoggedBottle, result)
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
        val emptyUseCase =
            ActiveEventUseCase(feedRepository, napRepository, InMemoryBabyDao(ids = emptyList()))

        assertEquals(ActiveEventUseCase.Result.NoBaby, emptyUseCase.logBottle(90, t0))
        assertTrue(feedDao.all().isEmpty())
    }

    @Test
    fun `toggle after a bottle starts a breast feed - a bottle never reads as active`() = runTest {
        useCase.logBottle(amountMl = 90, time = t0)

        val result = useCase.toggle()

        assertTrue(result is ActiveEventUseCase.Result.StartedFeed)
        val active = feedDao.activeFeed().first()
        assertNotNull(active)
        assertEquals(FeedType.bREAST, active!!.type)
    }

    // One event at a time

    @Test
    fun `starting a nap ends the running feed at the nap's start`() = runTest {
        useCase.startFeed(Side.l, t0)

        useCase.startNap(t0.plusSeconds(1_200))

        assertEquals(t0.plusSeconds(1_200), feedDao.all().single().endTime)
        assertNotNull(napDao.activeNap().first())
    }

    @Test
    fun `starting a feed ends the running nap at the feed's start`() = runTest {
        useCase.startNap(t0)

        useCase.startFeed(Side.l, t0.plusSeconds(7_200))

        assertEquals(t0.plusSeconds(7_200), napDao.all().single().endTime)
        assertNotNull(feedDao.activeFeed().first())
    }

    /**
     * Scrub a start back before the running event began and it clamps to that event's own
     * start. Overlaps are tolerated all over this codebase; negative durations are not.
     */
    @Test
    fun `a start before the running event's start clamps instead of going negative`() = runTest {
        useCase.startFeed(Side.l, t0)

        useCase.startNap(t0.minusSeconds(1_800))

        assertEquals(t0, feedDao.all().single().endTime)
    }

    @Test
    fun `only one event is ever active`() = runTest {
        useCase.startFeed(Side.l, t0)
        useCase.startNap(t0.plusSeconds(600))
        useCase.startFeed(Side.r, t0.plusSeconds(1_200))

        assertNull(napDao.activeNap().first())
        assertNotNull(feedDao.activeFeed().first())
        assertEquals(1, feedDao.all().count { it.endTime == null })
    }

    /**
     * A bottle is never in progress, so the invariant is not at stake - and its time is freely
     * scrubbed, so auto-ending a nap at a backdated bottle time would surprise. Pinned so
     * nobody "fixes" it later.
     */
    @Test
    fun `logBottle ends neither a running feed nor a running nap`() = runTest {
        useCase.startNap(t0)

        useCase.logBottle(amountMl = 90, time = t0.plusSeconds(1_200))

        assertNotNull(napDao.activeNap().first())
    }

    @Test
    fun `startNap without a baby is NoBaby and writes nothing`() = runTest {
        val emptyUseCase =
            ActiveEventUseCase(feedRepository, napRepository, InMemoryBabyDao(ids = emptyList()))

        assertEquals(ActiveEventUseCase.Result.NoBaby, emptyUseCase.startNap(t0))
        assertTrue(napDao.all().isEmpty())
    }

    // activeEvent and toggle

    @Test
    fun `activeEvent reports the running nap`() = runTest {
        useCase.startNap(t0)

        val active = useCase.activeEvent().first()

        assertTrue(active is ActiveEvent.Napping)
        assertEquals(t0, active!!.startTime)
    }

    @Test
    fun `toggle while a nap runs ends the nap and starts nothing`() = runTest {
        useCase.startNap(t0)

        assertEquals(ActiveEventUseCase.Result.Stopped, useCase.toggle())

        assertNull(useCase.activeEvent().first())
        assertTrue(feedDao.all().isEmpty())
    }

    /** Mid-sync both rows can be open; the later start is the one the parent is actually in. */
    @Test
    fun `activeEvent prefers the later start when both are open`() = runTest {
        feedDao.upsert(breastFeed(side = Side.l, start = t0, end = null))
        napDao.upsert(nap(start = t0.plusSeconds(600), end = null))

        assertTrue(useCase.activeEvent().first() is ActiveEvent.Napping)
    }

    // Fixtures

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

    private fun nap(start: Instant, end: Instant?) = NapEntity(
        id = UUID.randomUUID().toString(),
        babyId = 1L,
        startTime = start,
        endTime = end,
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

/** In-memory NapDao, the same shape as [InMemoryFeedDao]. */
private class InMemoryNapDao : NapDao {

    private val naps = MutableStateFlow<Map<String, NapEntity>>(emptyMap())

    fun all(): List<NapEntity> = naps.value.values.toList()

    private fun visible() = naps.map { rows ->
        rows.values.filter { it.syncState != SyncState.PENDING_DELETE }.sortedByDescending { it.startTime }
    }

    override fun napsBetween(from: Instant, to: Instant): Flow<List<NapEntity>> =
        visible().map { rows -> rows.filter { it.startTime >= from && it.startTime < to } }

    override fun latestNap(): Flow<NapEntity?> = visible().map { it.firstOrNull() }

    override fun latestEndedNap(): Flow<NapEntity?> =
        visible().map { rows -> rows.firstOrNull { it.endTime != null } }

    override fun activeNap(): Flow<NapEntity?> =
        visible().map { rows -> rows.firstOrNull { it.endTime == null } }

    override suspend fun byId(id: String): NapEntity? = naps.value[id]

    override suspend fun pending(): List<NapEntity> =
        naps.value.values.filter { it.syncState != SyncState.SYNCED }

    override suspend fun upsert(nap: NapEntity) {
        naps.value = naps.value + (nap.id to nap)
    }

    override suspend fun setSyncState(id: String, state: SyncState) {
        naps.value[id]?.let { upsert(it.copy(syncState = state)) }
    }

    override suspend fun hardDelete(id: String) {
        naps.value = naps.value - id
    }

    override suspend fun markSynced(
        id: String,
        revision: Long,
        createdBy: Long?,
        createdAt: Instant?,
        updatedAt: Instant?,
    ) {
        naps.value[id]?.takeIf { it.localRevision == revision }?.let {
            upsert(it.copy(syncState = SyncState.SYNCED, createdBy = createdBy, createdAt = createdAt, updatedAt = updatedAt))
        }
    }

    override suspend fun deleteSyncedNotIn(babyId: Long, from: Instant, keepIds: List<String>) {
        naps.value = naps.value.filterValues {
            !(it.syncState == SyncState.SYNCED && it.babyId == babyId && it.startTime >= from && it.id !in keepIds)
        }
    }

    override suspend fun deleteAllSyncedFrom(babyId: Long, from: Instant) {
        naps.value = naps.value.filterValues {
            !(it.syncState == SyncState.SYNCED && it.babyId == babyId && it.startTime >= from)
        }
    }

    override suspend fun clear() {
        naps.value = emptyMap()
    }
}

private class InMemoryBabyDao(private val ids: List<Long>) : BabyDao {

    override fun babies(): Flow<List<BabyEntity>> =
        MutableStateFlow(ids.map { BabyEntity(id = it, name = "Baby $it", dateOfBirth = null) })

    override suspend fun ids(): List<Long> = ids

    override suspend fun upsertAll(babies: List<BabyEntity>) = Unit

    override suspend fun clear() = Unit
}
