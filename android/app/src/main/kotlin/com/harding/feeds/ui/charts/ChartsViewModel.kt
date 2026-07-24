package com.harding.feeds.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.di.AppContainer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Chart data for the last [WINDOW_DAYS] days, derived once per Room emission. A feed is
 * rendered as day-clamped segments (a midnight-crossing feed appears on both days), and the
 * same segments feed the per-day minute totals so the two charts always agree. An
 * in-progress feed counts up to "now at load".
 */
class ChartsViewModel(container: AppContainer) : ViewModel() {

    /** A feed's span within one day column, in minutes since that day's midnight. */
    data class Segment(
        val dayIndex: Int,
        val startMinute: Int,
        val endMinute: Int,
        val side: Side?,
        val isBottle: Boolean = false,
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
    )

    private val zone = ZoneId.systemDefault()

    private val days: List<LocalDate> = LocalDate.now(zone).let { today ->
        (WINDOW_DAYS - 1 downTo 0L).map(today::minusDays)
    }

    val data: StateFlow<ChartData> = container.feedRepository
        .feedsBetween(
            from = days.first().atStartOfDay(zone).toInstant(),
            to = days.last().plusDays(1).atStartOfDay(zone).toInstant(),
        )
        .map { feeds -> buildChartData(feeds) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ChartData(days, emptyList(), List(days.size) { DayMinutes(0, 0, 0) }, List(days.size) { null }),
        )

    private fun buildChartData(feeds: List<FeedEntity>): ChartData {
        val indexByDay = days.withIndex().associate { (i, d) -> d to i }
        val now = Instant.now()

        val segments = feeds.flatMap { feed ->
            val end = maxOf(feed.endTime ?: now, feed.startTime)
            generateSequence(feed.startTime.atZone(zone).toLocalDate()) { it.plusDays(1) }
                .takeWhile { !it.isAfter(end.atZone(zone).toLocalDate()) }
                .mapNotNull { day ->
                    val dayIndex = indexByDay[day] ?: return@mapNotNull null
                    val dayStart = day.atStartOfDay(zone).toInstant()
                    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
                    val startMinute = Duration.between(dayStart, maxOf(feed.startTime, dayStart))
                        .toMinutes().toInt().coerceIn(0, MINUTES_PER_DAY)
                    val endMinute = Duration.between(dayStart, minOf(end, dayEnd))
                        .toMinutes().toInt().coerceIn(startMinute, MINUTES_PER_DAY)
                    Segment(dayIndex, startMinute, endMinute, feed.side, feed.type == FeedType.bOTTLE)
                }
                .toList()
        }

        // Both duration series are breast-only: a bottle is a zero-duration point event, so
        // it has no minutes to stack and would only drag the per-day average toward zero.
        val left = IntArray(days.size); val right = IntArray(days.size); val unknown = IntArray(days.size)
        segments.filterNot { it.isBottle }.forEach { s ->
            val mins = s.endMinute - s.startMinute
            when (s.side) {
                Side.l -> left[s.dayIndex] += mins
                Side.r -> right[s.dayIndex] += mins
                null -> unknown[s.dayIndex] += mins
            }
        }
        val minutesPerDay = days.indices.map { DayMinutes(left[it], right[it], unknown[it]) }

        // Average feed length groups whole feeds by their start day (not the day-split segments
        // above) and counts only completed feeds, so an in-progress feed doesn't drag the mean.
        val totalByDay = IntArray(days.size)
        val countByDay = IntArray(days.size)
        feeds.forEach { feed ->
            if (feed.type == FeedType.bOTTLE) return@forEach
            val end = feed.endTime ?: return@forEach
            val dayIndex = indexByDay[feed.startTime.atZone(zone).toLocalDate()] ?: return@forEach
            totalByDay[dayIndex] +=
                Duration.between(feed.startTime, end).coerceAtLeast(Duration.ZERO).toMinutes().toInt()
            countByDay[dayIndex]++
        }
        val avgMinutesPerDay =
            days.indices.map { if (countByDay[it] == 0) null else totalByDay[it] / countByDay[it] }

        return ChartData(days, segments, minutesPerDay, avgMinutesPerDay)
    }

    private companion object {
        const val WINDOW_DAYS = 14L
        const val MINUTES_PER_DAY = 24 * 60
    }
}
