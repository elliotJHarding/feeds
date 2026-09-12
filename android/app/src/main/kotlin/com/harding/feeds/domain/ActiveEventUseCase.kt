package com.harding.feeds.domain

import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.dao.BabyDao
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.data.repository.FeedRepository
import com.harding.feeds.data.repository.NapRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

fun Side.opposite(): Side = if (this == Side.l) Side.r else Side.l

/** Whatever is happening right now, if anything. Exactly one of these at a time. */
sealed interface ActiveEvent {
    val startTime: Instant

    data class Feeding(val feed: FeedEntity) : ActiveEvent {
        override val startTime: Instant get() = feed.startTime
    }

    data class Napping(val nap: NapEntity) : ActiveEvent {
        override val startTime: Instant get() = nap.startTime
    }
}

/**
 * The single start/stop path shared by the entry screen, the home-screen widget and the
 * Quick Settings tile. The SPEC defaulting rules - start/end at now, next side the opposite
 * of the last feed's - live only here, as does the rule that at most one event runs at a time.
 *
 * One use case rather than a feed one and a nap one: the exclusivity rule spans both
 * resources, so a sibling would need the other's repository or would duplicate the invariant,
 * and any surface could then bypass it.
 */
class ActiveEventUseCase(
    private val feedRepository: FeedRepository,
    private val napRepository: NapRepository,
    private val babyDao: BabyDao,
) {

    /**
     * The side the next feed will log unless the user picks one explicitly. Alternates off
     * the last breast feed - bottle feeds have no side and must not reset the alternation.
     */
    fun defaultNextSide(): Flow<Side> =
        feedRepository.latestBreastFeed().map { it?.side?.opposite() ?: Side.l }

    /**
     * What is in progress, across both types.
     *
     * Both sources can be non-null at once while a sync is mid-flight, so the later start
     * wins - the one the parent is actually in. The stale row is left alone rather than
     * healed here: a read must never write.
     */
    fun activeEvent(): Flow<ActiveEvent?> =
        combine(feedRepository.activeFeed(), napRepository.activeNap()) { feed, nap ->
            listOfNotNull(
                feed?.let(ActiveEvent::Feeding),
                nap?.let(ActiveEvent::Napping),
            ).maxByOrNull { it.startTime }
        }

    /**
     * Stops whatever is running, or starts a feed when nothing is. Used by the widget and
     * Quick Settings tile, which have no UI to pick a time or a type - so they stay
     * feed-first, and every label on them states which of the two a tap will do.
     */
    suspend fun toggle(sideOverride: Side? = null): Result {
        val active = activeEvent().first()
        return if (active != null) finish(Instant.now())
        else startFeed(sideOverride, Instant.now())
    }

    /** Starts an in-progress feed at an explicit time (the entry screen's scrubbed start). */
    suspend fun startFeed(side: Side?, startTime: Instant): Result {
        val babyId = babyDao.ids().firstOrNull() ?: return Result.NoBaby
        val chosen = side ?: defaultNextSide().first()
        endRunning(startTime)
        feedRepository.startFeed(babyId, chosen, startTime)
        return Result.StartedFeed(chosen)
    }

    /** Starts an in-progress nap at an explicit time. */
    suspend fun startNap(startTime: Instant): Result {
        val babyId = babyDao.ids().firstOrNull() ?: return Result.NoBaby
        endRunning(startTime)
        napRepository.startNap(babyId, startTime)
        return Result.StartedNap
    }

    /** Ends whichever event is running, never before that event's own start. */
    suspend fun finish(endTime: Instant): Result {
        when (val active = activeEvent().first()) {
            is ActiveEvent.Feeding ->
                feedRepository.endFeed(active.feed.id, maxOf(endTime, active.startTime))
            is ActiveEvent.Napping ->
                napRepository.endNap(active.nap.id, maxOf(endTime, active.startTime))
            null -> Unit
        }
        return Result.Stopped
    }

    /**
     * Logs a bottle feed as a completed point event - endTime equals startTime, so a bottle
     * can never read as the in-progress feed on any surface.
     *
     * Deliberately ends nothing. A bottle is never in progress, so the one-at-a-time rule is
     * not at stake, and its time is freely scrubbed - auto-ending a nap at a backdated bottle
     * time would surprise.
     */
    suspend fun logBottle(amountMl: Int?, time: Instant): Result {
        val babyId = babyDao.ids().firstOrNull() ?: return Result.NoBaby
        feedRepository.createFeed(
            babyId, side = null, startTime = time, endTime = time,
            type = FeedType.bOTTLE, amountMl = amountMl,
        )
        return Result.LoggedBottle
    }

    /**
     * At most one event runs at a time: whatever is running ends at [at] before a new one
     * starts. The server applies the same rule on create, from the same startTime, so an
     * offline start converges with it without coordinating.
     *
     * The clamp matters: scrub a nap's start back before a running feed's start and the feed
     * ends at its own start, giving a zero-length feed rather than a negative one. Overlaps
     * are tolerated all over this codebase; negative durations are not.
     */
    private suspend fun endRunning(at: Instant) {
        feedRepository.activeFeed().first()?.let {
            feedRepository.endFeed(it.id, maxOf(at, it.startTime))
        }
        napRepository.activeNap().first()?.let {
            napRepository.endNap(it.id, maxOf(at, it.startTime))
        }
    }

    sealed interface Result {
        data class StartedFeed(val side: Side) : Result
        data object StartedNap : Result
        data object Stopped : Result
        data object LoggedBottle : Result

        /** Nothing to log against - onboarding has not created the baby yet. */
        data object NoBaby : Result
    }
}
