package com.harding.feeds.ui.home

import com.harding.feeds.data.local.entity.FeedEntity
import java.time.Duration

/**
 * A run of consecutive feeds that reads as one feeding session: the baby came off, paused
 * briefly, and went back on (or got a bottle top-up). Feeds are newest-first, matching the
 * history list's order.
 */
data class FeedSession(val feeds: List<FeedEntity>)

/**
 * Two feeds belong to the same session when the pause between them is under this. A pause is
 * measured end-to-start - deliberately different from the displayed feed intervals, which are
 * start-to-start (how feeding frequency is measured) - because a pause is time spent *not*
 * feeding, and start-to-start would overstate it by the length of the previous feed.
 */
val SessionPauseThreshold: Duration = Duration.ofMinutes(20)

/**
 * The pause before [feed]: from [previous]'s end to [feed]'s start. A still-running previous
 * feed has no end, so its start stands in - the largest the pause could be, which errs
 * towards splitting sessions rather than merging them. Overlapping hand-edited times give a
 * negative pause, which merges.
 */
fun pauseBefore(feed: FeedEntity, previous: FeedEntity): Duration =
    Duration.between(previous.endTime ?: previous.startTime, feed.startTime)

/** Groups one day's newest-first feeds into sessions, newest-first. */
fun groupIntoSessions(feeds: List<FeedEntity>): List<FeedSession> {
    val sessions = mutableListOf<MutableList<FeedEntity>>()
    for (older in feeds) {
        val current = sessions.lastOrNull()
        if (current != null && pauseBefore(current.last(), previous = older) < SessionPauseThreshold) {
            current.add(older)
        } else {
            sessions.add(mutableListOf(older))
        }
    }
    return sessions.map(::FeedSession)
}
