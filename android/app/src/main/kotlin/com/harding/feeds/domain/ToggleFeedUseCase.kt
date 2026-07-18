package com.harding.feeds.domain

import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.dao.BabyDao
import com.harding.feeds.data.repository.FeedRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

fun Side.opposite(): Side = if (this == Side.l) Side.r else Side.l

/**
 * The single start/stop path shared by the entry screen, the home-screen widget and the
 * Quick Settings tile. The SPEC defaulting rules - start/end backdated to now minus one
 * minute, next side the opposite of the last feed's - live only here.
 */
class ToggleFeedUseCase(
    private val feedRepository: FeedRepository,
    private val babyDao: BabyDao,
) {

    /** The side the next feed will log unless the user picks one explicitly. */
    fun defaultNextSide(): Flow<Side> =
        feedRepository.latestFeed().map { it?.side?.opposite() ?: Side.l }

    /**
     * Stops the active feed if there is one, otherwise starts one. Times default to
     * now minus one minute; an end time is never before the feed's own start. Used by the
     * widget and Quick Settings tile, which have no UI to pick a time.
     */
    suspend fun toggle(sideOverride: Side? = null): Result {
        val active = feedRepository.activeFeed().first()
        return if (active != null) finish(Instant.now().minus(START_STOP_BACKDATE))
        else start(sideOverride, Instant.now().minus(START_STOP_BACKDATE))
    }

    /** Starts an in-progress feed at an explicit time (the entry screen's scrubbed start). */
    suspend fun start(side: Side?, startTime: Instant): Result {
        val babyId = babyDao.ids().firstOrNull() ?: return Result.NoBaby
        val chosen = side ?: defaultNextSide().first()
        feedRepository.startFeed(babyId, chosen, startTime)
        return Result.Started(chosen)
    }

    /** Ends the active feed at an explicit time, never before the feed's own start. */
    suspend fun finish(endTime: Instant): Result {
        val active = feedRepository.activeFeed().first() ?: return Result.Stopped
        feedRepository.endFeed(active.id, maxOf(endTime, active.startTime))
        return Result.Stopped
    }

    sealed interface Result {
        data class Started(val side: Side) : Result
        data object Stopped : Result

        /** Nothing to log against - onboarding has not created the baby yet. */
        data object NoBaby : Result
    }

    private companion object {
        val START_STOP_BACKDATE: Duration = Duration.ofMinutes(1)
    }
}
