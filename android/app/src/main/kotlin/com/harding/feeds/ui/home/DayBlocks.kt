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

/**
 * How many minutes of a day the panel draws.
 *
 * A finished day draws all 24 hours. **Today stops at the current hour**, because time runs upward
 * and the top of today's panel is therefore the part of the day that has not happened. Drawn in
 * full, that dead space sits between the sheet's edge and the newest record - 82dp of it at 21:16,
 * and 690dp at 01:00, against a 160dp peek that would then show nothing at all.
 *
 * Rounded up to the hour so the grid still ends on a rule, and never under an hour, so a panel a
 * few minutes after midnight still has somewhere to draw.
 *
 * A day in the future keeps its full height. Only a hand-edited time can make one, and clamping it
 * to nothing would hide the record that created it.
 */
fun panelMinutes(date: LocalDate, now: Instant, zone: ZoneId): Int {
    val here = now.atZone(zone)
    if (date != here.toLocalDate()) return MINUTES_PER_DAY

    val elapsed = here.hour * 60 + here.minute
    return (((elapsed + 59) / 60) * 60).coerceIn(60, MINUTES_PER_DAY)
}

/**
 * Turns a time-space span into a screen position, given the panel it sits in.
 *
 * Time runs **upward**: midnight sits at the panel's bottom edge and the newest minute at its top.
 * The layout maths is all done in minutes from midnight, and this is the single point where that
 * becomes a position, so nothing downstream has to think about the direction.
 */
fun flipped(panelHeight: Float, top: Float, height: Float): Float = panelHeight - top - height

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
