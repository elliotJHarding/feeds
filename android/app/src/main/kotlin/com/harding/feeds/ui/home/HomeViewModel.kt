package com.harding.feeds.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.di.AppContainer
import com.harding.feeds.domain.ToggleFeedUseCase
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayFeeds(val date: LocalDate, val feeds: List<FeedEntity>)

/** Entry surface + history, backed entirely by Room flows (sync keeps them fresh). */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val feedRepository = container.feedRepository
    private val toggleFeed = container.toggleFeed
    private val zone = ZoneId.systemDefault()

    val baby = container.babyRepository.babies()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeFeed = feedRepository.activeFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestEndedFeed = feedRepository.latestEndedFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Explicit user choice before starting; cleared when the feed starts. */
    private val sideOverride = MutableStateFlow<Side?>(null)

    /**
     * The side the big button will log: the active feed's side while feeding, else the
     * user's explicit pick, else the use-case default (opposite of the last feed's side).
     */
    val selectedSide: StateFlow<Side> =
        combine(activeFeed, toggleFeed.defaultNextSide(), sideOverride) { active, default, override ->
            active?.side ?: override ?: default
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Side.l)

    val history: StateFlow<List<DayFeeds>> = feedRepository
        .feedsBetween(
            from = LocalDate.now(zone).minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant(),
            to = Instant.now().plus(Duration.ofDays(2)),
        )
        .map { feeds ->
            feeds.groupBy { it.startTime.atZone(zone).toLocalDate() }
                .map { (date, dayFeeds) -> DayFeeds(date, dayFeeds) }
                .sortedByDescending { it.date }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var inviteCode by mutableStateOf<InviteCodeState>(InviteCodeState.Loading)
        private set

    /**
     * Tap or swipe before a feed: picks the side to log. Ignored while a feed is in progress -
     * an accidental swipe used to silently rewrite the active feed's side; corrections go
     * through the history sheet's edit instead.
     */
    fun selectSide(side: Side) {
        if (activeFeed.value == null) sideOverride.value = side
    }

    /** Start an in-progress feed at the time scrubbed on the entry surface. */
    fun startFeed(side: Side, startTime: Instant) {
        viewModelScope.launch {
            if (toggleFeed.start(side, startTime) is ToggleFeedUseCase.Result.Started) {
                sideOverride.value = null
            }
        }
    }

    /** Finish the active feed at the time scrubbed on the entry surface. */
    fun finishFeed(endTime: Instant) {
        viewModelScope.launch { toggleFeed.finish(endTime) }
    }

    fun adjustActiveStart(newStart: Instant) {
        val active = activeFeed.value ?: return
        viewModelScope.launch {
            feedRepository.updateFeed(
                id = active.id,
                side = active.side,
                startTime = newStart.coerceAtMost(Instant.now()),
                endTime = null,
                amountMl = active.amountMl,
            )
        }
    }

    fun saveFeed(feed: FeedEntity, side: Side?, startTime: Instant, endTime: Instant?) {
        viewModelScope.launch {
            feedRepository.updateFeed(feed.id, side, startTime, endTime, feed.amountMl)
        }
    }

    fun deleteFeed(id: String) {
        viewModelScope.launch { feedRepository.deleteFeed(id) }
    }

    /** The group's stable current code, shown when the invite view opens. */
    fun loadInviteCode() {
        inviteCode = InviteCodeState.Loading
        viewModelScope.launch {
            inviteCode = runCatching { container.groupRepository.inviteCode() }
                .fold(InviteCodeState::Loaded) { InviteCodeState.Failed }
        }
    }

    /** Explicit user action to mint a new code, invalidating the previous one. */
    fun regenerateInviteCode() {
        inviteCode = InviteCodeState.Loading
        viewModelScope.launch {
            inviteCode = runCatching { container.groupRepository.regenerateInviteCode() }
                .fold(InviteCodeState::Loaded) { InviteCodeState.Failed }
        }
    }

    sealed interface InviteCodeState {
        data object Loading : InviteCodeState
        data object Failed : InviteCodeState
        data class Loaded(val code: String) : InviteCodeState
    }

    private companion object {
        const val HISTORY_DAYS = 30L
    }
}
