package com.harding.feeds.ui.home

import com.harding.feeds.ui.charts.MINUTES_PER_DAY
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Geometry for the block timeline: where a record sits inside its day, measured in minutes from
 * local midnight.
 *
 * These are pure functions for the reason the builders in `HistoryTimeline.kt` are: a wrong
 * minute puts a block at the wrong time of day, which is the one thing this mode exists to get
 * right, and a Compose composable cannot be tested here.
 */
data class BlockSpan(val startMinute: Int, val endMinute: Int) {
    val lengthMinutes: Int get() = endMinute - startMinute
}

/**
 * Clamps one record to one day.
 *
 * A record that crosses midnight is truncated at the boundary rather than split. `buildDayHistory`
 * files such a record wholly under the day it started, so the part after midnight has no day to
 * draw in - 5 of the 1,102 recorded feeds cross midnight, and a nap can too. The clamp keeps the
 * visible part at its true time instead of letting the block run off the panel.
 *
 * A null [end] means the record is still running, so it draws up to [now].
 */
fun blockSpan(
    start: Instant,
    end: Instant?,
    date: LocalDate,
    zone: ZoneId,
    now: Instant,
): BlockSpan {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    val finish = (end ?: now).coerceAtLeast(start)

    val startMinute = Duration.between(dayStart, start.coerceAtLeast(dayStart))
        .toMinutes().toInt().coerceIn(0, MINUTES_PER_DAY)
    val endMinute = Duration.between(dayStart, finish.coerceAtMost(dayEnd))
        .toMinutes().toInt().coerceIn(startMinute, MINUTES_PER_DAY)

    return BlockSpan(startMinute, endMinute)
}

/** The instant a session finishes, or null while any feed in it is still running. */
fun FeedSession.endInstant(): Instant? =
    if (feeds.any { it.endTime == null }) null else feeds.mapNotNull { it.endTime }.max()

/** The instant a session begins. Taken as a minimum, so a hand-edited overlap cannot invert it. */
fun FeedSession.startInstant(): Instant = feeds.minOf { it.startTime }

fun TimelineItem.spanIn(date: LocalDate, zone: ZoneId, now: Instant): BlockSpan = when (this) {
    is TimelineItem.Feeding -> blockSpan(session.startInstant(), session.endInstant(), date, zone, now)
    is TimelineItem.Napping -> blockSpan(nap.startTime, nap.endTime, date, zone, now)
}

/**
 * Heights for one lane of blocks, from [tops] and [lengths] in ascending order of top.
 *
 * A short record needs a floor or it draws as a hairline, but a floor tall enough to read is also
 * tall enough to swallow the gap to the next record - and that gap is what the mode exists to
 * show. So a block grows towards [floor] only as far as the next block's start allows, always
 * leaving [gutter] between the two.
 *
 * Three rules, in order of who wins:
 * 1. A block is never shorter than its true length. A long record that a hand-edited time makes
 *    overlap the next one still draws its whole length; shrinking it would state a false duration.
 * 2. Otherwise a block grows to [floor], or to the room before the next block, whichever is less.
 * 3. Nothing draws below [minVisible]. A bottle has no length at all, so without this a bottle
 *    logged shortly before a feed would vanish.
 */
fun blockHeights(
    tops: List<Float>,
    lengths: List<Float>,
    floor: Float,
    gutter: Float,
    minVisible: Float,
): List<Float> = tops.indices.map { index ->
    val room = tops.getOrNull(index + 1)?.let { it - tops[index] - gutter } ?: Float.MAX_VALUE
    maxOf(lengths[index], minOf(floor, room), minVisible)
}

/**
 * Pushes labels apart so no two overlap, taking [tops] in ascending order and returning the
 * position each label is actually drawn at.
 *
 * At 0.5dp per minute a 15dp label line needs 30 minutes of clear air, and the closest two
 * sessions came in the last 21 days is 26 minutes - one pair in 221. Rather than shrink the type
 * for that pair, the lower label slides down by the few dp it needs. Only the label moves; its
 * block stays at its true minute, and the label states the exact clock time either way.
 */
fun nudgedLabelTops(tops: List<Float>, minSeparation: Float): List<Float> {
    var previous = Float.NEGATIVE_INFINITY
    return tops.map { top ->
        val placed = maxOf(top, previous + minSeparation)
        previous = placed
        placed
    }
}
