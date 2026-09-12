package com.harding.feeds.ui.charts

import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The chart series, and mostly the midnight arithmetic.
 *
 * A feed is minutes long, so splitting one across midnight barely moves a number. A nap is hours
 * long, so the same split decides whether a parent is told they slept four hours or eight. That
 * is what these tests are for.
 *
 * UTC throughout, so a day boundary is exactly where the test says it is.
 */
class ChartSeriesTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 9, 12)
    private val days: List<LocalDate> = (2L downTo 0L).map(today::minusDays)
    private val now: Instant = at(today, 20, 0)

    @Test
    fun `a nap inside one day lands on that day only`() {
        val data = build(naps = listOf(nap(at(today, 13, 5), at(today, 14, 20))))

        assertEquals(listOf(0, 0, 75), data.sleptMinutesPerDay)
        assertEquals(1, data.napSegments.size)
        assertEquals(NapSegment(dayIndex = 2, startMinute = 13 * 60 + 5, endMinute = 14 * 60 + 20), data.napSegments.single())
    }

    /** The case the whole split exists for: minutes count against the day they were slept. */
    @Test
    fun `a nap across midnight splits its minutes between the two days`() {
        val yesterday = today.minusDays(1)
        val data = build(naps = listOf(nap(at(yesterday, 22, 30), at(today, 6, 15))))

        assertEquals(listOf(0, 90, 375), data.sleptMinutesPerDay)
        assertEquals(2, data.napSegments.size)
        assertEquals(MINUTES_PER_DAY, data.napSegments.first().endMinute)
        assertEquals(0, data.napSegments.last().startMinute)
    }

    /**
     * The view model queries one day earlier than the window, because the range query filters on
     * startTime alone. That day must contribute its in-window minutes and nothing more.
     */
    @Test
    fun `a nap starting before the window contributes only its in-window part`() {
        val beforeWindow = days.first().minusDays(1)
        val data = build(naps = listOf(nap(at(beforeWindow, 23, 0), at(days.first(), 1, 30))))

        assertEquals(listOf(90, 0, 0), data.sleptMinutesPerDay)
        assertEquals(1, data.napSegments.size)
        assertEquals(0, data.napSegments.single().dayIndex)
    }

    @Test
    fun `an in-progress nap counts up to now`() {
        val data = build(naps = listOf(nap(at(today, 19, 0), end = null)))

        assertEquals(60, data.sleptMinutesPerDay[2])
    }

    @Test
    fun `average nap length skips an in-progress nap`() {
        val data = build(
            naps = listOf(
                nap(at(today, 9, 0), at(today, 10, 0)),
                nap(at(today, 19, 0), end = null),
            ),
        )

        assertEquals(60, data.avgNapMinutesPerDay[2])
    }

    /** Grouped by the day a nap started, not by where its minutes fell. */
    @Test
    fun `average nap length counts an overnight nap on the day it started`() {
        val yesterday = today.minusDays(1)
        val data = build(naps = listOf(nap(at(yesterday, 22, 0), at(today, 4, 0))))

        assertEquals(360, data.avgNapMinutesPerDay[1])
        assertNull(data.avgNapMinutesPerDay[2])
    }

    @Test
    fun `a day with no naps has no average`() {
        val data = build(naps = listOf(nap(at(today, 9, 0), at(today, 10, 0))))

        assertEquals(listOf(null, null, 60), data.avgNapMinutesPerDay)
    }

    @Test
    fun `feeds still split by side and bottles stay out of both duration series`() {
        val data = build(
            feeds = listOf(
                feed(at(today, 8, 0), minutes = 10, side = Side.l),
                feed(at(today, 9, 0), minutes = 20, side = Side.r),
                bottle(at(today, 10, 0)),
            ),
        )

        assertEquals(DayMinutes(left = 10, right = 20, unknown = 0), data.minutesPerDay[2])
        assertEquals(15, data.avgMinutesPerDay[2])
        assertEquals(3, data.segments.size)
    }

    @Test
    fun `feeds and naps are counted independently`() {
        val data = build(
            feeds = listOf(feed(at(today, 8, 0), minutes = 10, side = Side.l)),
            naps = listOf(nap(at(today, 13, 0), at(today, 14, 0))),
        )

        assertEquals(10, data.minutesPerDay[2].total)
        assertEquals(60, data.sleptMinutesPerDay[2])
    }

    @Test
    fun `no events gives zeroes and nulls, not an empty list`() {
        val data = build()

        assertEquals(days.size, data.sleptMinutesPerDay.size)
        assertEquals(listOf(0, 0, 0), data.sleptMinutesPerDay)
        assertEquals(listOf(null, null, null), data.avgNapMinutesPerDay)
        assertEquals(emptyChartData(days), data)
    }

    // -----------------------------------------------------------------------------------------

    private fun build(feeds: List<FeedEntity> = emptyList(), naps: List<NapEntity> = emptyList()) =
        buildChartData(days, feeds, naps, now, zone)

    private fun at(day: LocalDate, hour: Int, minute: Int): Instant =
        day.atStartOfDay(zone).plusHours(hour.toLong()).plusMinutes(minute.toLong()).toInstant()

    private fun nap(start: Instant, end: Instant?) = NapEntity(
        id = UUID.randomUUID().toString(),
        babyId = 1,
        startTime = start,
        endTime = end,
        createdBy = null,
        createdAt = null,
        updatedAt = null,
        syncState = SyncState.SYNCED,
    )

    private fun feed(start: Instant, minutes: Long, side: Side?) = FeedEntity(
        id = UUID.randomUUID().toString(),
        babyId = 1,
        type = FeedType.bREAST,
        side = side,
        amountMl = null,
        startTime = start,
        endTime = start.plus(Duration.ofMinutes(minutes)),
        createdBy = null,
        createdAt = null,
        updatedAt = null,
        syncState = SyncState.SYNCED,
    )

    private fun bottle(start: Instant) = FeedEntity(
        id = UUID.randomUUID().toString(),
        babyId = 1,
        type = FeedType.bOTTLE,
        side = null,
        amountMl = 90,
        startTime = start,
        endTime = start,
        createdBy = null,
        createdAt = null,
        updatedAt = null,
        syncState = SyncState.SYNCED,
    )
}
