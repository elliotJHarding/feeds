package com.harding.feeds.ui.home

import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.ui.components.EventFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which records the history sheet is showing. */
/**
 * One row-group in the day timeline. Feeds arrive grouped into sessions (see
 * [groupIntoSessions]); a nap stands alone, because only one event runs at a time so a nap is
 * never part of a feeding session.
 */
sealed interface TimelineItem {
    /** The item's position in the day: the start of its newest record. */
    val anchor: Instant

    data class Feeding(val session: FeedSession) : TimelineItem {
        // Feeds inside a session are newest-first.
        override val anchor: Instant get() = session.feeds.first().startTime
    }

    data class Napping(val nap: NapEntity) : TimelineItem {
        override val anchor: Instant get() = nap.startTime
    }
}

/**
 * One day of history. [feeds] and [naps] drive the header totals; [items] is what the list
 * renders.
 */
data class DayHistory(
    val date: LocalDate,
    val feeds: List<FeedEntity>,
    val naps: List<NapEntity>,
    val items: List<TimelineItem>,
)

/**
 * Interleaves feeding sessions and naps, newest first.
 *
 * Ordering is by each item's own anchor, so nothing here assumes feeds and naps do not
 * overlap: the one-at-a-time rule governs live starts, but a hand-edited time can still cross,
 * and such an item simply sorts by its own start. Ties put the session first - the sort is
 * stable and sessions are added first - so a nap that starts exactly when a session does reads
 * as following it.
 */
fun buildTimeline(sessions: List<FeedSession>, naps: List<NapEntity>): List<TimelineItem> =
    (sessions.map(TimelineItem::Feeding) + naps.map(TimelineItem::Napping))
        .sortedByDescending { it.anchor }

/**
 * Groups feeds and naps into days by the local date of their start, newest day first.
 *
 * A nap that crosses midnight belongs wholly to the day it started, which is also where its
 * whole duration counts in that day's header. Splitting it would put two rows on screen for
 * one record, both opening the same edit sheet, and break the list's one-row-one-record rule.
 */
fun buildDayHistory(
    feeds: List<FeedEntity>,
    naps: List<NapEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<DayHistory> {
    val feedsByDay = feeds.groupBy { it.startTime.atZone(zone).toLocalDate() }
    val napsByDay = naps.groupBy { it.startTime.atZone(zone).toLocalDate() }

    return (feedsByDay.keys + napsByDay.keys)
        .sortedDescending()
        .map { date ->
            val dayFeeds = feedsByDay[date].orEmpty().sortedByDescending { it.startTime }
            val dayNaps = napsByDay[date].orEmpty().sortedByDescending { it.startTime }
            DayHistory(
                date = date,
                feeds = dayFeeds,
                naps = dayNaps,
                items = buildTimeline(groupIntoSessions(dayFeeds), dayNaps),
            )
        }
}

/**
 * The feed interval preceding each feed, keyed by feed id: this feed's start minus the next-older
 * feed's start.
 *
 * Start-to-start, because that is how feeding frequency is measured, and it needs no end time, so
 * a still-running predecessor still yields an interval. Computed over the flattened list, so the
 * overnight interval lands on the first feed of a day even though its predecessor sits in the
 * previous day group.
 *
 * **Feeds only, and a nap never replaces one.** The interval measures feeding frequency, which is
 * what the every-N-hours rule works from, and whether the baby slept through it does not change
 * that number. The pair reads as "she went 3h 10m, and slept 1h 15m of it".
 *
 * Both history modes read this, so the two can never print different numbers for the same pair.
 *
 * Feeds arrive newest-first from [buildDayHistory], so an interval is never negative however the
 * times were hand-edited. A sub-minute interval produces no entry: two feeds logged seconds apart
 * are one action, and "0m" would read as a measurement.
 */
fun gapsBeforeFeed(days: List<DayHistory>): Map<String, Duration> = buildMap {
    days.flatMap { it.feeds }.zipWithNext { newer, older ->
        val gap = Duration.between(older.startTime, newer.startTime)
        if (gap >= Duration.ofMinutes(1)) put(newer.id, gap)
    }
}

/**
 * The awake stretch before each nap, keyed by nap id: this nap's start minus the previous nap's
 * end.
 *
 * **End-to-start, where the feed interval is start-to-start**, and the difference is the point. A
 * nap band already draws its own length, so a start-to-start measure would only restate it. What
 * the blank between two bands is, is the time she was awake.
 *
 * **It stops at midnight**, unlike the feed interval, and that is the second difference. A nap
 * records daytime sleep, so the stretch from one evening's last nap to the next morning's first is
 * mostly night sleep - 12h 31m of it between 12 and 13 Sep - and "awake" would be false. The day's
 * first nap therefore has no entry. Within one day the stretch really is time awake.
 *
 * A previous nap with no end has not finished, so the stretch is unknown and no entry is made. Its
 * start cannot stand in the way it does for a session pause: that would count the nap itself as
 * awake time. A hand-edited overlap gives a negative stretch, which the same one-minute bar drops.
 */
fun awakeBeforeNap(days: List<DayHistory>): Map<String, Duration> = buildMap {
    days.forEach { day ->
        day.naps.zipWithNext { newer, older ->
            val end = older.endTime ?: return@zipWithNext
            val awake = Duration.between(end, newer.startTime)
            if (awake >= Duration.ofMinutes(1)) put(newer.id, awake)
        }
    }
}

/**
 * Narrows a day to one filter, or drops it entirely when nothing matches - otherwise the list
 * fills with bare day headers over nothing.
 *
 * The unmatched list is emptied too, because the header counts read from [DayHistory.feeds] and
 * [DayHistory.naps]: leaving them populated would print "5 feeds" above a nap-only list.
 */
fun DayHistory.filtered(filter: EventFilter): DayHistory? {
    val narrowed = when (filter) {
        EventFilter.BOTH -> this
        EventFilter.FEEDS -> copy(naps = emptyList(), items = items.filterIsInstance<TimelineItem.Feeding>())
        EventFilter.NAPS -> copy(feeds = emptyList(), items = items.filterIsInstance<TimelineItem.Napping>())
    }
    return narrowed.takeIf { it.items.isNotEmpty() }
}

fun List<DayHistory>.filtered(filter: EventFilter): List<DayHistory> =
    mapNotNull { it.filtered(filter) }
