package com.harding.feeds.ui.home

import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.entity.FeedEntity
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sessions cluster consecutive feeds by the pause between them, measured end-to-start: a
 * pause is time spent not feeding, so a long feed followed quickly by another must not read
 * as a long break (as the start-to-start interval measure would make it).
 */
class FeedSessionsTest {

    private val t0: Instant = Instant.parse("2026-07-23T08:00:00Z")

    @Test
    fun `pause under the threshold merges feeds into one session`() {
        val first = feed(start = t0, minutes = 15)
        val second = feed(start = t0 + 34.minutes, minutes = 6) // ends 08:15, resumes 08:34

        assertEquals(listOf(listOf(second, first)), sessionFeeds(second, first))
    }

    @Test
    fun `pause at the threshold starts a new session`() {
        val first = feed(start = t0, minutes = 15)
        val second = feed(start = t0 + 35.minutes, minutes = 6) // exactly 20m after first ended

        assertEquals(listOf(listOf(second), listOf(first)), sessionFeeds(second, first))
    }

    @Test
    fun `pause is measured from the previous feed's end, not its start`() {
        // Start-to-start is 25m (over the threshold) but the actual pause is only 10m.
        val first = feed(start = t0, minutes = 15)
        val second = feed(start = t0 + 25.minutes, minutes = 8)

        assertEquals(listOf(listOf(second, first)), sessionFeeds(second, first))
    }

    @Test
    fun `bottle top-up shortly after a breast feed joins its session`() {
        val breast = feed(start = t0, minutes = 12)
        val bottle = bottle(time = t0 + 20.minutes)

        assertEquals(listOf(listOf(bottle, breast)), sessionFeeds(bottle, breast))
    }

    @Test
    fun `overlapping hand-edited feeds stay in one session`() {
        val first = feed(start = t0, minutes = 20)
        val second = feed(start = t0 + 10.minutes, minutes = 5)

        assertEquals(listOf(listOf(second, first)), sessionFeeds(second, first))
    }

    @Test
    fun `a still-running previous feed measures start-to-start, erring towards a split`() {
        val running = feed(start = t0, minutes = 10).copy(endTime = null)
        val later = feed(start = t0 + 25.minutes, minutes = 5)

        assertEquals(listOf(listOf(later), listOf(running)), sessionFeeds(later, running))
    }

    @Test
    fun `a cluster and a lone feed group independently`() {
        val a = feed(start = t0, minutes = 14)
        val b = feed(start = t0 + 18.minutes, minutes = 6)
        val c = feed(start = t0 + 35.minutes, minutes = 6)
        val lone = feed(start = t0 + 180.minutes, minutes = 15)

        assertEquals(listOf(listOf(lone), listOf(c, b, a)), sessionFeeds(lone, c, b, a))
    }

    @Test
    fun `no feeds means no sessions`() {
        assertEquals(emptyList<FeedSession>(), groupIntoSessions(emptyList()))
    }

    /** Feeds are passed and asserted newest-first, matching the history list's order. */
    private fun sessionFeeds(vararg newestFirst: FeedEntity) =
        groupIntoSessions(newestFirst.toList()).map { it.feeds }

    private fun feed(start: Instant, minutes: Long) = FeedEntity(
        id = UUID.randomUUID().toString(),
        babyId = 1,
        type = FeedType.bREAST,
        side = Side.l,
        amountMl = null,
        startTime = start,
        endTime = start + minutes.minutes,
        createdBy = null,
        createdAt = null,
        updatedAt = null,
        syncState = SyncState.SYNCED,
    )

    private fun bottle(time: Instant) =
        feed(time, 0).copy(type = FeedType.bOTTLE, side = null, amountMl = 60)

    private val Int.minutes: Duration get() = Duration.ofMinutes(toLong())
    private val Long.minutes: Duration get() = Duration.ofMinutes(this)
}
