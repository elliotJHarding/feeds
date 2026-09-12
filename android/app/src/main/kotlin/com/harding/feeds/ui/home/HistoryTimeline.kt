package com.harding.feeds.ui.home

import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which records the history sheet is showing. */
enum class HistoryFilter { FEEDS, BOTH, NAPS }

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
 * Narrows a day to one filter, or drops it entirely when nothing matches - otherwise the list
 * fills with bare day headers over nothing.
 *
 * The unmatched list is emptied too, because the header counts read from [DayHistory.feeds] and
 * [DayHistory.naps]: leaving them populated would print "5 feeds" above a nap-only list.
 */
fun DayHistory.filtered(filter: HistoryFilter): DayHistory? {
    val narrowed = when (filter) {
        HistoryFilter.BOTH -> this
        HistoryFilter.FEEDS -> copy(naps = emptyList(), items = items.filterIsInstance<TimelineItem.Feeding>())
        HistoryFilter.NAPS -> copy(feeds = emptyList(), items = items.filterIsInstance<TimelineItem.Napping>())
    }
    return narrowed.takeIf { it.items.isNotEmpty() }
}

fun List<DayHistory>.filtered(filter: HistoryFilter): List<DayHistory> =
    mapNotNull { it.filtered(filter) }
