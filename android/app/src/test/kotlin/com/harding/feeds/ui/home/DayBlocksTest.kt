package com.harding.feeds.ui.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Placing a record on the day axis. A wrong minute here puts a block at the wrong time of day,
 * which is the one thing the blocks mode exists to get right.
 */
class DayBlocksTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val date: LocalDate = LocalDate.of(2026, 9, 11)

    private fun at(hour: Int, minute: Int = 0): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()

    private fun on(day: Int, hour: Int, minute: Int = 0): Instant =
        date.withDayOfMonth(day).atTime(hour, minute).atZone(zone).toInstant()

    @Test
    fun `a record sits at its own minutes of the day`() {
        val span = blockSpan(at(4, 12), at(4, 29), date, zone, now = at(23))

        assertEquals(4 * 60 + 12, span.startMinute)
        assertEquals(4 * 60 + 29, span.endMinute)
        assertEquals(17, span.lengthMinutes)
    }

    /**
     * `buildDayHistory` files a midnight-crossing record wholly under the day it started, so the
     * part after midnight has no day panel to draw in. The visible part must stay at its true
     * time rather than run off the end of the panel.
     */
    @Test
    fun `a record crossing midnight stops at the end of its own day`() {
        val span = blockSpan(at(23, 40), on(12, 0, 25), date, zone, now = on(12, 8))

        assertEquals(23 * 60 + 40, span.startMinute)
        assertEquals(24 * 60, span.endMinute)
    }

    @Test
    fun `a running record is drawn up to now`() {
        val span = blockSpan(at(7, 11), end = null, date = date, zone = zone, now = at(7, 25))

        assertEquals(7 * 60 + 25, span.endMinute)
    }

    @Test
    fun `a session spans its oldest start to its newest end`() {
        // Newest-first, as the history list orders them.
        val session = FeedSession(listOf(feedFor(at(20, 59), 8), feedFor(at(20, 50), 8)))

        val span = session.let {
            blockSpan(it.startInstant(), it.endInstant(), date, zone, now = at(23))
        }

        assertEquals(20 * 60 + 50, span.startMinute)
        assertEquals(21 * 60 + 7, span.endMinute)
    }

    /**
     * A hand-edited time can make an inner feed outlast the newest one - the one-at-a-time rule
     * governs live starts, not edits. Taking the extremes rather than the first and last feed is
     * what stops the block ending before it begins.
     */
    @Test
    fun `an overlapping hand-edited feed cannot invert a session`() {
        val session = FeedSession(listOf(feedFor(at(13, 30), 12), feedFor(at(11, 12), 189)))

        val span = blockSpan(session.startInstant(), session.endInstant(), date, zone, at(23))

        assertEquals(11 * 60 + 12, span.startMinute)
        assertEquals(14 * 60 + 21, span.endMinute)
    }

    @Test
    fun `a session with a running feed has no end and is drawn up to now`() {
        val session = FeedSession(listOf(feed(at(9, 20), end = null), feedFor(at(9, 5), 6)))

        assertEquals(null, session.endInstant())
        val span = blockSpan(session.startInstant(), session.endInstant(), date, zone, at(9, 33))
        assertEquals(9 * 60 + 33, span.endMinute)
    }

    // Block heights. Floor 12, gutter 2, minimum visible 3 - the values the timeline uses.

    private fun heights(tops: List<Float>, lengths: List<Float>) =
        blockHeights(tops, lengths, floor = 12f, gutter = 2f, minVisible = 3f)

    @Test
    fun `a short block grows to the floor when it has room`() {
        assertEquals(listOf(12f, 12f), heights(listOf(0f, 60f), listOf(4f, 4f)))
    }

    @Test
    fun `a block never grows into the gutter before the next block`() {
        // 8dp apart, so the first may take 6dp and still leave the 2dp gutter.
        assertEquals(listOf(6f, 12f), heights(listOf(0f, 8f), listOf(4f, 4f)))
    }

    /** Shrinking a long record would state a false duration, so its own length always wins. */
    @Test
    fun `a long block keeps its whole length even where it overlaps the next`() {
        assertEquals(listOf(90f, 12f), heights(listOf(0f, 30f), listOf(90f, 5f)))
    }

    /** A bottle has no length. Without a lower bound a crowded one would draw nothing at all. */
    @Test
    fun `a bottle just before another block still draws`() {
        assertEquals(listOf(3f, 12f), heights(listOf(0f, 2f), listOf(0f, 5f)))
    }

    @Test
    fun `the last block in a lane has nothing to clear`() {
        assertEquals(listOf(12f), heights(listOf(600f), listOf(1f)))
    }

    // Label placement

    @Test
    fun `labels far enough apart are left where they are`() {
        assertEquals(listOf(0f, 40f, 100f), nudgedLabelTops(listOf(0f, 40f, 100f), 15f))
    }

    /** The measured worst case: two sessions 26 minutes apart are 13dp apart at this scale. */
    @Test
    fun `a label too close to the one above slides down to clear it`() {
        assertEquals(listOf(600f, 615f), nudgedLabelTops(listOf(600f, 613f), 15f))
    }

    /** Each nudge becomes the floor for the next, so a cluster cannot collapse onto one line. */
    @Test
    fun `a nudge carries down a run of tight labels`() {
        assertEquals(
            listOf(0f, 15f, 30f, 45f),
            nudgedLabelTops(listOf(0f, 2f, 4f, 6f), 15f),
        )
    }
}
