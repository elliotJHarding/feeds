package com.harding.feeds.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.di.AppContainer
import com.harding.feeds.domain.ActiveEvent
import com.harding.feeds.domain.ActiveEventUseCase
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

/** What the entry surface is set up to log. Breast is primary; bottle and nap are detours. */
enum class EntryMode { BREAST, BOTTLE, NAP }

/** Entry surface + history, backed entirely by Room flows (sync keeps them fresh). */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val feedRepository = container.feedRepository
    private val napRepository = container.napRepository
    private val activeEventUseCase = container.activeEvent
    private val zone = ZoneId.systemDefault()

    val baby = container.babyRepository.babies()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whatever is in progress, feed or nap - at most one at a time. */
    val activeEvent = activeEventUseCase.activeEvent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestEndedFeed = feedRepository.latestEndedFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestEndedNap = napRepository.latestEndedNap()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Explicit user choice before starting; cleared when the feed starts. */
    private val sideOverride = MutableStateFlow<Side?>(null)

    /**
     * Plain view state, never persisted - the app always opens in breast mode, and an event
     * starting (locally or via a partner's sync) forces the surface back to breast. Breast is
     * the right landing place after either kind of event ends: a waking baby usually feeds.
     */
    private val entryModeState = MutableStateFlow(EntryMode.BREAST)
    val entryMode: StateFlow<EntryMode> = entryModeState

    private val bottleAmountState = MutableStateFlow<Int?>(null)
    val bottleAmount: StateFlow<Int?> = bottleAmountState

    private val historyFilterState = MutableStateFlow(HistoryFilter.BOTH)
    val historyFilter: StateFlow<HistoryFilter> = historyFilterState

    init {
        viewModelScope.launch {
            activeEvent.collect { if (it != null) entryModeState.value = EntryMode.BREAST }
        }
    }

    /**
     * The side the big button will log: the active feed's side while feeding, else the
     * user's explicit pick, else the use-case default (opposite of the last feed's side).
     */
    val selectedSide: StateFlow<Side> =
        combine(activeEvent, activeEventUseCase.defaultNextSide(), sideOverride) { active, default, override ->
            (active as? ActiveEvent.Feeding)?.feed?.side ?: override ?: default
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Side.l)

    /**
     * The day-grouped timeline, built off both Room streams. The interleave runs here rather
     * than in the list composable: it is a pure function, so it is testable without Compose
     * test infrastructure this project does not carry, and it recomputes per Room emission
     * instead of per frame.
     */
    val history: StateFlow<List<DayHistory>> = run {
        val from = LocalDate.now(zone).minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant()
        val to = Instant.now().plus(Duration.ofDays(2))
        combine(
            feedRepository.feedsBetween(from, to),
            napRepository.napsBetween(from, to),
        ) { feeds, naps -> buildDayHistory(feeds, naps, zone) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    var inviteCode by mutableStateOf<InviteCodeState>(InviteCodeState.Loading)
        private set

    /**
     * Tap or swipe before an event: picks the side to log. Ignored while one is in progress -
     * an accidental swipe used to silently rewrite the active feed's side; corrections go
     * through the history sheet's edit instead.
     */
    fun selectSide(side: Side) {
        if (activeEvent.value == null) sideOverride.value = side
    }

    /** Start an in-progress feed at the time scrubbed on the entry surface. */
    fun startFeed(side: Side, startTime: Instant) {
        viewModelScope.launch {
            if (activeEventUseCase.startFeed(side, startTime) is ActiveEventUseCase.Result.StartedFeed) {
                sideOverride.value = null
            }
        }
    }

    /** Start an in-progress nap at the time scrubbed on the entry surface. */
    fun startNap(startTime: Instant) {
        viewModelScope.launch { activeEventUseCase.startNap(startTime) }
    }

    /** Finish whatever is active, at the time scrubbed on the entry surface. */
    fun finishActive(endTime: Instant) {
        viewModelScope.launch { activeEventUseCase.finish(endTime) }
    }

    /** Switching into bottle mode seeds the amount from the last bottle logged (window: history). */
    fun selectMode(mode: EntryMode) {
        if (mode == EntryMode.BOTTLE && entryModeState.value != EntryMode.BOTTLE) {
            bottleAmountState.value = lastBottleAmount() ?: DEFAULT_BOTTLE_ML
        }
        entryModeState.value = mode
    }

    fun setBottleAmount(amountMl: Int?) {
        bottleAmountState.value = amountMl
    }

    fun selectHistoryFilter(filter: HistoryFilter) {
        historyFilterState.value = filter
    }

    /** Log a bottle as a completed point event at the scrubbed time; one-shot back to breast. */
    fun logBottle(time: Instant) {
        viewModelScope.launch {
            if (activeEventUseCase.logBottle(bottleAmountState.value, time)
                is ActiveEventUseCase.Result.LoggedBottle
            ) {
                entryModeState.value = EntryMode.BREAST
            }
        }
    }

    private fun lastBottleAmount(): Int? = history.value
        .asSequence()
        .flatMap { it.feeds }
        .firstOrNull { it.type == FeedType.bOTTLE && it.amountMl != null }
        ?.amountMl

    /** Corrects the start of the running event - a parent often realises it began earlier. */
    fun adjustActiveStart(newStart: Instant) {
        val start = newStart.coerceAtMost(Instant.now())
        viewModelScope.launch {
            when (val active = activeEvent.value) {
                is ActiveEvent.Feeding -> feedRepository.updateFeed(
                    id = active.feed.id,
                    side = active.feed.side,
                    startTime = start,
                    endTime = null,
                    amountMl = active.feed.amountMl,
                )

                is ActiveEvent.Napping -> napRepository.updateNap(
                    id = active.nap.id,
                    startTime = start,
                    endTime = null,
                )

                null -> Unit
            }
        }
    }

    fun saveFeed(feed: FeedEntity, side: Side?, startTime: Instant, endTime: Instant?, amountMl: Int?) {
        viewModelScope.launch {
            feedRepository.updateFeed(feed.id, side, startTime, endTime, amountMl)
        }
    }

    fun deleteFeed(id: String) {
        viewModelScope.launch { feedRepository.deleteFeed(id) }
    }

    fun saveNap(nap: NapEntity, startTime: Instant, endTime: Instant?) {
        viewModelScope.launch { napRepository.updateNap(nap.id, startTime, endTime) }
    }

    fun deleteNap(id: String) {
        viewModelScope.launch { napRepository.deleteNap(id) }
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
        const val DEFAULT_BOTTLE_ML = 100
    }
}
