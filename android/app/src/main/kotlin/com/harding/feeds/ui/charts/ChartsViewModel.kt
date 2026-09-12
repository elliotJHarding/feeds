package com.harding.feeds.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harding.feeds.di.AppContainer
import com.harding.feeds.ui.components.EventFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Chart state: the window, the filter, and the series for both.
 *
 * The series themselves are pure functions in ChartSeries.kt, where the tests can reach them.
 * This class only holds the two choices and wires Room to them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartsViewModel(container: AppContainer) : ViewModel() {

    /**
     * How far back the charts look.
     *
     * 30 days is the top of the range, and the limit is measured rather than chosen. The
     * time-of-day chart gives one column per day out of about 337dp of plot on a 411dp screen,
     * so 30 days leaves 11dp a column against a 5dp mark. At 60 days a column is 5.6dp and the
     * marks touch; at 90 they overlap. A longer window needs that chart to scroll sideways,
     * which is a separate piece of work.
     */
    enum class Window(val days: Long, val label: String) {
        WEEK(7, "7d"),
        FORTNIGHT(14, "14d"),
        MONTH(30, "30d"),
    }

    private val zone: ZoneId = ZoneId.systemDefault()

    private val windowState = MutableStateFlow(Window.FORTNIGHT)
    val window: StateFlow<Window> = windowState.asStateFlow()

    private val filterState = MutableStateFlow(EventFilter.BOTH)
    val filter: StateFlow<EventFilter> = filterState.asStateFlow()

    fun selectWindow(next: Window) {
        windowState.value = next
    }

    fun selectFilter(next: EventFilter) {
        filterState.value = next
    }

    val data: StateFlow<ChartData> = windowState
        .flatMapLatest { window ->
            val days = daysFor(window)
            // One day of slack on the leading edge. Both range queries filter on startTime
            // alone, so a nap that began the night before the window would otherwise vanish
            // from the first column - and a nap is hours long, so the hole would show.
            val from = days.first().minusDays(1).atStartOfDay(zone).toInstant()
            val to = days.last().plusDays(1).atStartOfDay(zone).toInstant()

            combine(
                container.feedRepository.feedsBetween(from, to),
                container.napRepository.napsBetween(from, to),
            ) { feeds, naps -> buildChartData(days, feeds, naps, Instant.now(), zone) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyChartData(daysFor(Window.FORTNIGHT)),
        )

    private fun daysFor(window: Window): List<LocalDate> =
        LocalDate.now(zone).let { today -> (window.days - 1 downTo 0L).map(today::minusDays) }
}
