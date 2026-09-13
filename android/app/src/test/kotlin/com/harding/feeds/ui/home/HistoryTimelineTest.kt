package com.harding.feeds.ui.home

import com.harding.feeds.ui.components.EventFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared day timeline: feeding sessions and naps interleaved, newest first, and the filter
 * that narrows it. Pure functions, so the rules are tested without Compose.
 */
class HistoryTimelineTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    /** 2026-07-23, so the clock times below read as they do in the app. */
    private fun at(hour: Int, minute: Int = 0): Instant =
        LocalDate.of(2026, 7, 23).atTime(hour, minute).atZone(zone).toInstant()

    @Test
    fun `a nap between two sessions lands between them`() {
        val days = buildDayHistory(
            feeds = listOf(feed(at(9), at(9, 10)), feed(at(14), at(14, 12))),
            naps = listOf(nap(at(11), at(12, 30))),
            zone = zone,
        )

        val day = days.single()
        assertEquals(3, day.items.size)
        assertTrue(day.items[0] is TimelineItem.Feeding)   // 14:00
        assertTrue(day.items[1] is TimelineItem.Napping)   // 11:00
        assertTrue(day.items[2] is TimelineItem.Feeding)   // 09:00
    }

    @Test
    fun `an in-progress nap sorts by its start`() {
        val days = buildDayHistory(
            feeds = listOf(feed(at(9), at(9, 10))),
            naps = listOf(nap(at(11), end = null)),
            zone = zone,
        )

        val day = days.single()
        assertTrue(day.items.first() is TimelineItem.Napping)
    }

    /**
     * The one-at-a-time rule governs live starts, not hand-edits, so an overlap is legal input.
     * It must simply sort by its own anchor rather than assume it cannot happen.
     */
    @Test
    fun `a nap overlapping a session still sorts by its own start`() {
        val days = buildDayHistory(
            feeds = listOf(feed(at(9), at(10, 30))),
            naps = listOf(nap(at(10), at(11))),
            zone = zone,
        )

        val day = days.single()
        assertEquals(2, day.items.size)
        assertTrue(day.items[0] is TimelineItem.Napping)   // 10:00
        assertTrue(day.items[1] is TimelineItem.Feeding)   // 09:00
    }

    @Test
    fun `identical anchors put the session first`() {
        val days = buildDayHistory(
            feeds = listOf(feed(at(9), at(9, 10))),
            naps = listOf(nap(at(9), at(10))),
            zone = zone,
        )

        val day = days.single()
        assertTrue(day.items[0] is TimelineItem.Feeding)
        assertTrue(day.items[1] is TimelineItem.Napping)
    }

    @Test
    fun `a day of naps with no feeds still renders`() {
        val days = buildDayHistory(feeds = emptyList(), naps = listOf(nap(at(13), at(14))), zone = zone)

        val day = days.single()
        assertTrue(day.feeds.isEmpty())
        assertEquals(1, day.items.size)
    }

    /**
     * A nap that crosses midnight belongs wholly to the day it started - one record, one row,
     * one edit sheet.
     */
    @Test
    fun `a midnight-spanning nap lands wholly on its start day`() {
        val evening = at(19, 30)
        val morning = LocalDate.of(2026, 7, 24).atTime(6, 40).atZone(zone).toInstant()

        val days = buildDayHistory(feeds = emptyList(), naps = listOf(nap(evening, morning)), zone = zone)

        val day = days.single()
        assertEquals(LocalDate.of(2026, 7, 23), day.date)
        assertEquals(1, day.naps.size)
    }

    @Test
    fun `empty input gives an empty timeline`() {
        assertTrue(buildDayHistory(emptyList(), emptyList(), zone).isEmpty())
        assertTrue(buildTimeline(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `days run newest first`() {
        val yesterday = LocalDate.of(2026, 7, 22).atTime(9, 0).atZone(zone).toInstant()

        val days = buildDayHistory(
            feeds = listOf(feed(yesterday, yesterday.plusSeconds(600)), feed(at(9), at(9, 10))),
            naps = emptyList(),
            zone = zone,
        )

        assertEquals(listOf(LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 22)), days.map { it.date })
    }

    // Feed intervals. Both history modes read these, so the two cannot disagree.

    @Test
    fun `the interval before a feed is measured start to start`() {
        val older = feed(at(9), at(9, 40))
        val newer = feed(at(12), at(12, 10))

        val days = buildDayHistory(feeds = listOf(newer, older), naps = emptyList(), zone = zone)

        // 3h, not the 2h 20m an end-to-start measure would give.
        assertEquals(Duration.ofHours(3), gapsBeforeFeed(days)[newer.id])
    }

    /** Start-to-start needs no end time, so a running predecessor still yields an interval. */
    @Test
    fun `a still-running predecessor still yields an interval`() {
        val running = feed(at(9), end = null)
        val newer = feed(at(11), at(11, 10))

        val days = buildDayHistory(feeds = listOf(newer, running), naps = emptyList(), zone = zone)

        assertEquals(Duration.ofHours(2), gapsBeforeFeed(days)[newer.id])
    }

    /** The overnight interval must survive the day grouping, or the longest one on record is lost. */
    @Test
    fun `the overnight interval crosses the day boundary`() {
        val lastNight = LocalDate.of(2026, 7, 22).atTime(22, 30).atZone(zone).toInstant()
        val morning = feed(at(5, 30), at(5, 40))

        val days = buildDayHistory(
            feeds = listOf(morning, feed(lastNight, lastNight.plusSeconds(600))),
            naps = emptyList(),
            zone = zone,
        )

        assertEquals(Duration.ofHours(7), gapsBeforeFeed(days)[morning.id])
    }

    /** A nap never replaces a feed interval - the interval measures feeding frequency. */
    @Test
    fun `a nap in between does not change the interval`() {
        val older = feed(at(9), at(9, 10))
        val newer = feed(at(12), at(12, 10))

        val days = buildDayHistory(
            feeds = listOf(newer, older),
            naps = listOf(nap(at(10), at(11, 15))),
            zone = zone,
        )

        assertEquals(Duration.ofHours(3), gapsBeforeFeed(days)[newer.id])
    }

    /**
     * Two feeds logged seconds apart are one action, not an interval. Nothing is printed rather
     * than a "0m" that would read as a real measurement.
     */
    @Test
    fun `a sub-minute interval is not printed`() {
        val older = feed(at(9), at(9, 10))
        val newer = feed(at(9).plusSeconds(30), at(9, 5))

        val days = buildDayHistory(feeds = listOf(newer, older), naps = emptyList(), zone = zone)

        assertEquals(null, gapsBeforeFeed(days)[newer.id])
    }

    // Awake stretches. A different measure from the feed interval, deliberately.

    @Test
    fun `the awake stretch before a nap is measured end to start`() {
        val earlier = nap(at(8, 20), at(9, 45))
        val later = nap(at(12, 40), at(14, 30))

        val days = buildDayHistory(feeds = emptyList(), naps = listOf(later, earlier), zone = zone)

        // 09:45 to 12:40, not the 4h 20m a start-to-start measure would give.
        assertEquals(Duration.ofMinutes(175), awakeBeforeNap(days)[later.id])
    }

    /**
     * A running nap has not finished, so the stretch after it is unknown. Its start cannot stand
     * in the way it does for a session pause - that would count the nap itself as awake time.
     */
    @Test
    fun `a running previous nap yields no awake stretch`() {
        val running = nap(at(8, 20), end = null)
        val later = nap(at(12, 40), at(14, 30))

        val days = buildDayHistory(feeds = emptyList(), naps = listOf(later, running), zone = zone)

        assertEquals(null, awakeBeforeNap(days)[later.id])
    }

    @Test
    fun `hand-edited naps that overlap yield no awake stretch`() {
        val earlier = nap(at(8), at(13))
        val later = nap(at(12), at(14))

        val days = buildDayHistory(feeds = emptyList(), naps = listOf(later, earlier), zone = zone)

        assertEquals(null, awakeBeforeNap(days)[later.id])
    }

    @Test
    fun `the awake stretch crosses the day boundary`() {
        val lastNight = LocalDate.of(2026, 7, 22).atTime(20, 0).atZone(zone).toInstant()
        val morning = nap(at(2), at(4))

        val days = buildDayHistory(
            feeds = emptyList(),
            naps = listOf(morning, nap(lastNight, lastNight.plusSeconds(3600))),
            zone = zone,
        )

        // 21:00 to 02:00.
        assertEquals(Duration.ofHours(5), awakeBeforeNap(days)[morning.id])
    }

    // The filter

    @Test
    fun `the feeds filter keeps only sessions, and empties the nap list behind the header`() {
        val day = buildDayHistory(
            feeds = listOf(feed(at(9), at(9, 10))),
            naps = listOf(nap(at(11), at(12))),
            zone = zone,
        ).single()

        val filtered = day.filtered(EventFilter.FEEDS)!!

        assertEquals(1, filtered.items.size)
        assertTrue(filtered.items.single() is TimelineItem.Feeding)
        assertTrue(filtered.naps.isEmpty())
    }

    @Test
    fun `the naps filter keeps only naps, and empties the feed list behind the header`() {
        val day = buildDayHistory(
            feeds = listOf(feed(at(9), at(9, 10))),
            naps = listOf(nap(at(11), at(12))),
            zone = zone,
        ).single()

        val filtered = day.filtered(EventFilter.NAPS)!!

        assertEquals(1, filtered.items.size)
        assertTrue(filtered.items.single() is TimelineItem.Napping)
        assertTrue(filtered.feeds.isEmpty())
    }

    /** Otherwise the list fills with bare day headers over nothing. */
    @Test
    fun `a day with nothing matching the filter drops out entirely`() {
        val days = buildDayHistory(
            feeds = listOf(feed(at(9), at(9, 10))),
            naps = emptyList(),
            zone = zone,
        )

        assertTrue(days.filtered(EventFilter.NAPS).isEmpty())
        assertEquals(1, days.filtered(EventFilter.FEEDS).size)
        assertEquals(1, days.filtered(EventFilter.BOTH).size)
    }

}
