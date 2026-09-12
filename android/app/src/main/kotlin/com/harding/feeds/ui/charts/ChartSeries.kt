package com.harding.feeds.ui.charts

import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The chart series, as pure functions.
 *
 * They live outside the view model for the same reason the day timeline's builders do: the
 * project has junit4 and coroutines-test but no Robolectric, so anything reachable only through
 * a `ViewModel` that takes an `AppContainer` cannot be tested. Day-splitting an event across
 * midnight is exactly the kind of arithmetic that fails quietly and shows up as a wrong number
 * on a chart, so it belongs where a test can reach it.
 */

const val MINUTES_PER_DAY = 24 * 60

/** A feed's span within one day column, in minutes since that day's midnight. */
data class Segment(
    val dayIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
    val side: Side?,
    val isBottle: Boolean = false,
)

/** A nap's span within one day column. A nap carries no side and no amount. */
data class NapSegment(
    val dayIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
)

/** A day's feeding minutes split by side, for the stacked duration bar. */
data class DayMinutes(val left: Int, val right: Int, val unknown: Int) {
    val total: Int get() = left + right + unknown
}

data class ChartData(
    val days: List<LocalDate>,
    val segments: List<Segment>,
    val minutesPerDay: List<DayMinutes>,
    /** Mean length of completed feeds started on each day; null where a day has no feeds. */
    val avgMinutesPerDay: List<Int?>,
    val napSegments: List<NapSegment>,
    val sleptMinutesPerDay: List<Int>,
    /** Mean length of completed naps started on each day; null where a day has no naps. */
    val avgNapMinutesPerDay: List<Int?>,
)

fun emptyChartData(days: List<LocalDate>) = ChartData(
    days = days,
    segments = emptyList(),
    minutesPerDay = List(days.size) { DayMinutes(0, 0, 0) },
    avgMinutesPerDay = List(days.size) { null },
    napSegments = emptyList(),
    sleptMinutesPerDay = List(days.size) { 0 },
    avgNapMinutesPerDay = List(days.size) { null },
)

fun buildChartData(
    days: List<LocalDate>,
    feeds: List<FeedEntity>,
    naps: List<NapEntity>,
    now: Instant,
    zone: ZoneId,
): ChartData {
    val indexByDay = days.withIndex().associate { (i, d) -> d to i }

    val segments = feeds.flatMap { feed ->
        daySpans(indexByDay, feed.startTime, feed.endTime ?: now, zone) { day, start, end ->
            Segment(day, start, end, feed.side, feed.type == FeedType.bOTTLE)
        }
    }
    val napSegments = naps.flatMap { nap ->
        daySpans(indexByDay, nap.startTime, nap.endTime ?: now, zone) { day, start, end ->
            NapSegment(day, start, end)
        }
    }

    // Both feed duration series are breast-only: a bottle is a zero-duration point event, so it
    // has no minutes to stack and would only drag the per-day average toward zero.
    val left = IntArray(days.size)
    val right = IntArray(days.size)
    val unknown = IntArray(days.size)
    segments.filterNot { it.isBottle }.forEach { s ->
        val mins = s.endMinute - s.startMinute
        when (s.side) {
            Side.l -> left[s.dayIndex] += mins
            Side.r -> right[s.dayIndex] += mins
            null -> unknown[s.dayIndex] += mins
        }
    }

    // Slept minutes come from the day-clamped spans rather than from whole naps, so an overnight
    // nap counts its minutes against the day they were actually slept.
    val slept = IntArray(days.size)
    napSegments.forEach { slept[it.dayIndex] += it.endMinute - it.startMinute }

    return ChartData(
        days = days,
        segments = segments,
        minutesPerDay = days.indices.map { DayMinutes(left[it], right[it], unknown[it]) },
        avgMinutesPerDay = meanLengthPerDay(days, indexByDay, feeds, zone) { feed ->
            if (feed.type == FeedType.bOTTLE) null else feed.startTime to feed.endTime
        },
        napSegments = napSegments,
        sleptMinutesPerDay = slept.toList(),
        avgNapMinutesPerDay = meanLengthPerDay(days, indexByDay, naps, zone) { nap ->
            nap.startTime to nap.endTime
        },
    )
}

/**
 * The spans an event occupies within each day column it touches.
 *
 * A midnight-crossing event yields one span per day. Anything outside the window is dropped,
 * which is also what keeps the extra leading day the view model queries out of the charts.
 */
fun <T> daySpans(
    indexByDay: Map<LocalDate, Int>,
    startTime: Instant,
    endTime: Instant,
    zone: ZoneId,
    make: (dayIndex: Int, startMinute: Int, endMinute: Int) -> T,
): List<T> {
    val end = maxOf(endTime, startTime)
    return generateSequence(startTime.atZone(zone).toLocalDate()) { it.plusDays(1) }
        .takeWhile { !it.isAfter(end.atZone(zone).toLocalDate()) }
        .mapNotNull { day ->
            val dayIndex = indexByDay[day] ?: return@mapNotNull null
            val dayStart = day.atStartOfDay(zone).toInstant()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
            val startMinute = Duration.between(dayStart, maxOf(startTime, dayStart))
                .toMinutes().toInt().coerceIn(0, MINUTES_PER_DAY)
            val endMinute = Duration.between(dayStart, minOf(end, dayEnd))
                .toMinutes().toInt().coerceIn(startMinute, MINUTES_PER_DAY)
            make(dayIndex, startMinute, endMinute)
        }
        .toList()
}

/**
 * Mean length of completed events, grouped by the day each one *started* rather than by the
 * day-split spans above. An in-progress event is skipped, so it cannot drag the mean down.
 */
fun <T> meanLengthPerDay(
    days: List<LocalDate>,
    indexByDay: Map<LocalDate, Int>,
    events: List<T>,
    zone: ZoneId,
    span: (T) -> Pair<Instant, Instant?>?,
): List<Int?> {
    val total = IntArray(days.size)
    val count = IntArray(days.size)
    events.forEach { event ->
        val (start, end) = span(event) ?: return@forEach
        if (end == null) return@forEach
        val dayIndex = indexByDay[start.atZone(zone).toLocalDate()] ?: return@forEach
        total[dayIndex] +=
            Duration.between(start, end).coerceAtLeast(Duration.ZERO).toMinutes().toInt()
        count[dayIndex]++
    }
    return days.indices.map { if (count[it] == 0) null else total[it] / count[it] }
}
