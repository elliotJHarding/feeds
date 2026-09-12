package com.harding.feeds.ui.home

import com.harding.feeds.ui.components.EventFilter
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
