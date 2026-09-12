package com.harding.feeds.ui.home

import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Records for the timeline tests.
 *
 * Every test in this package needs a feed or a nap, and only two or three of the twelve fields
 * ever matter to a test. Each class used to carry its own copy of these builders, which put the
 * sync bookkeeping in front of the reader on every line. Here the defaults carry it instead, and
 * a test names only what it is actually asserting on.
 */

fun feed(
    start: Instant,
    end: Instant?,
    side: Side? = Side.l,
): FeedEntity = FeedEntity(
    id = UUID.randomUUID().toString(),
    babyId = 1L,
    type = FeedType.bREAST,
    side = side,
    amountMl = null,
    startTime = start,
    endTime = end,
    createdBy = null,
    createdAt = null,
    updatedAt = null,
    syncState = SyncState.SYNCED,
)

/** The same feed, given as a length rather than an end - which is how a session reads. */
fun feedFor(start: Instant, minutes: Long, side: Side? = Side.l): FeedEntity =
    feed(start, start.plus(Duration.ofMinutes(minutes)), side)

/** A point event: no length, and a volume instead of a side. */
fun bottle(time: Instant, amountMl: Int = 90): FeedEntity =
    feed(time, time, side = null).copy(type = FeedType.bOTTLE, amountMl = amountMl)

fun nap(start: Instant, end: Instant?): NapEntity = NapEntity(
    id = UUID.randomUUID().toString(),
    babyId = 1L,
    startTime = start,
    endTime = end,
    createdBy = null,
    createdAt = null,
    updatedAt = null,
    syncState = SyncState.SYNCED,
)
