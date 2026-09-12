package com.harding.feeds.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.harding.feeds.client.models.FeedType
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.ui.bottleColor
import com.harding.feeds.ui.components.BottleGlyph
import com.harding.feeds.ui.components.EventFilter
import com.harding.feeds.ui.components.MoonGlyph
import com.harding.feeds.ui.dayLabel
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.label
import com.harding.feeds.ui.napColor
import com.harding.feeds.ui.onAccent
import com.harding.feeds.ui.sideColor
import java.time.Duration
import java.time.LocalDate

/**
 * Day-grouped history shown in the sheet behind the entry surface. Deliberately does NOT
 * take the ticking clock: completed records render identically every second, so binding the list
 * to `now` recomposed every visible row once a second and made scrolling stutter. The live
 * elapsed time for an in-progress record lives on the entry surface, not here.
 *
 * The day reads as a stack of *sessions*: consecutive feeds whose pauses are short share one
 * raised card (see [groupIntoSessions]), with the pauses as faint text inside it, while the
 * gaps between sessions sit on a dotted spine between cards. The two kinds of interval used
 * to render identically, and a 6-minute side-switch carried the same weight as a 2.5-hour
 * stretch. Between-session gaps stay start-to-start (how feeding frequency is measured);
 * within-session pauses are end-to-start (time spent not feeding).
 *
 * Naps sit in the same timeline, in time order, on their own cards - only one event runs at a
 * time, so a nap is never part of a feeding session. A nap gets no duration bar: [FeedBarCap]
 * is 20 minutes, so every nap would clamp to full width and a 35-minute nap would look exactly
 * like a three-hour one. A second bar cap would be worse still - two identical-looking bars on
 * two different scales in one scrolling list is precisely what "bars compare with bars" exists
 * to prevent. Naps use the dot row instead, the encoding this file already reserves for
 * hours-scale magnitudes that must stay quieter than the feed bars.
 *
 * Each feed row carries a duration bar with a fixed cap, so bars compare with bars and a
 * given length means the same thing next week as it does today.
 */
@Composable
fun HistoryList(
    days: List<DayHistory>,
    filter: EventFilter,
    onFeedTap: (FeedEntity) -> Unit,
    onNapTap: (NapEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()

    // The interval preceding each feed, keyed by feed id: this feed's start minus the
    // next-older feed's start (start-to-start needs no end time, so an in-progress predecessor
    // still yields a gap). Computed over the flattened list so the overnight gap lands on the
    // first feed of a day even though its predecessor sits in the previous day group. Only
    // session-leading feeds are looked up - pauses inside a session render from their own
    // end-to-start measure. Overlaps (negative gaps from hand-edited times) and sub-minute
    // gaps produce no row.
    //
    // Feeds only, and a nap never replaces a gap: the gap measures feeding frequency, which is
    // what the every-N-hours rule works from, and whether the baby slept through it does not
    // change that number. The pair reads as "she went 3h 10m, and slept 1h 15m of it".
    val gapsBefore = remember(days) {
        buildMap {
            days.flatMap { it.feeds }.zipWithNext { newer, older ->
                val gap = Duration.between(older.startTime, newer.startTime)
                if (gap >= Duration.ofMinutes(1)) put(newer.id, gap)
            }
        }
    }

    if (days.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                emptyText(filter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // The filter pill floats over the bottom of this list, so the last rows need room to clear
    // it - otherwise the oldest feed of the window is permanently unreadable behind it.
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FloatingFilterClearance),
    ) {
        days.forEach { day ->
            item(key = "header-${day.date}") { DayHeader(day, today) }
            day.items.forEach { entry ->
                when (entry) {
                    is TimelineItem.Feeding -> {
                        val session = entry.session
                        item(key = "session-${session.feeds.first().id}") {
                            SessionCard(session, onFeedTap)
                        }
                        // The gap before this session's oldest feed - rendered below the card
                        // because the list runs newest-first. For a day's last session this is
                        // the overnight gap, landing just above the previous day's header.
                        gapsBefore[session.feeds.last().id]?.let { gap ->
                            item(key = "gap-${session.feeds.last().id}") { SessionGap(gap) }
                        }
                    }

                    is TimelineItem.Napping ->
                        item(key = "nap-${entry.nap.id}") { NapCard(entry.nap, onNapTap) }
                }
            }
        }
    }
}

private fun emptyText(filter: EventFilter): String = when (filter) {
    EventFilter.FEEDS -> "Feeds you log will appear here"
    EventFilter.NAPS -> "Naps you log will appear here"
    EventFilter.BOTH -> "Feeds and naps you log will appear here"
}

@Composable
private fun DayHeader(day: DayHistory, today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            dayLabel(day.date, today),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            daySummary(day),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The header counts only what the current filter actually shows - a "5 feeds" count above a
 * nap-only list would simply be false. The filter empties the list it is hiding, so the terms
 * fall away on their own.
 */
private fun daySummary(day: DayHistory): String {
    val terms = mutableListOf<String>()

    if (day.feeds.isNotEmpty()) {
        val sessionCount = day.items.count { it is TimelineItem.Feeding }
        terms += counted(sessionCount, "session")
        terms += counted(day.feeds.size, "feed")
        terms += formatHoursMinutes(completedFeedTotal(day.feeds))
        // Bottles have zero duration so they never inflate the time total; their volume gets
        // its own term instead, shown only on days that had bottles.
        val bottleMl = day.feeds.filter { it.type == FeedType.bOTTLE }.sumOf { it.amountMl ?: 0 }
        if (bottleMl > 0) terms += "$bottleMl ml"
    }

    if (day.naps.isNotEmpty()) {
        terms += counted(day.naps.size, "nap")
        // "slept" rather than a bare duration, so the term self-describes next to the feed
        // total without having to relabel that one.
        terms += "slept ${formatHoursMinutes(completedNapTotal(day.naps))}"
    }

    return terms.joinToString(" · ")
}

@Composable
private fun SessionCard(session: FeedSession, onFeedTap: (FeedEntity) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 6.dp),
    ) {
        session.feeds.forEachIndexed { index, feed ->
            FeedRow(feed, onTap = { onFeedTap(feed) })
            session.feeds.getOrNull(index + 1)?.let { older ->
                val pause = pauseBefore(feed, previous = older)
                if (pause >= Duration.ofMinutes(1)) PauseRow(pause)
            }
        }
    }
}

@Composable
private fun PauseRow(pause: Duration) {
    Text(
        "${formatHoursMinutes(pause)} pause",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        // Aligns under the times: card padding + chip + spacer.
        modifier = Modifier.padding(start = 52.dp),
    )
}

// One dot per started half hour (capped) - the interval's magnitude as a countable tick row
// rather than a measured bar, so it stays quieter than the feed bars. Right-aligned to the
// cards' inner edge (card margin + inner row padding) so gap values share a rail with the
// duration labels; the fixed label column gives the dot rows a common origin.
@Composable
private fun SessionGap(gap: Duration) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
    ) {
        DotRow(gap, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
        Spacer(Modifier.width(8.dp))
        DurationLabel(gap, MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * A nap: a record, so it lives on a card like a feed session, not on the spine where derived
 * intervals live. Three things separate it from a gap row at a glance - the card ground, the
 * moon chip, and a clock range, which gap rows have none of.
 */
@Composable
private fun NapCard(nap: NapEntity, onNapTap: (NapEntity) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNapTap(nap) }
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(26.dp).clip(CircleShape).background(napColor),
            ) {
                MoonGlyph(onAccent(napColor))
            }
            Spacer(Modifier.width(12.dp))

            val end = nap.endTime
            val timeStyle = MaterialTheme.typography.bodyLarge
            val measurer = rememberTextMeasurer()
            val timeColumnWidth = with(LocalDensity.current) {
                measurer.measure(AnnotatedString("00:00 – 00:00"), timeStyle).size.width.toDp()
            }
            Text(
                "${formatClockTime(nap.startTime)} – ${end?.let { formatClockTime(it) } ?: "…"}",
                style = timeStyle,
                modifier = Modifier.width(timeColumnWidth),
            )
            Spacer(Modifier.width(12.dp))

            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (end == null) {
                    Text(
                        "napping",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    val slept = Duration.between(nap.startTime, end).coerceAtLeast(Duration.ZERO)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DotRow(slept, napColor)
                        Spacer(Modifier.width(8.dp))
                        DurationLabel(slept, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One dot per started half hour, capped - a countable tick row for hours-scale magnitudes. */
@Composable
private fun DotRow(duration: Duration, color: Color) {
    val dots = ((duration.toMinutes() + GapDotMinutes - 1) / GapDotMinutes)
        .toInt().coerceIn(1, GapDotMax)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(dots) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        }
    }
}

/** The exact value in a fixed-width column, so every hours-scale number shares one rail. */
@Composable
private fun DurationLabel(duration: Duration, color: Color) {
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val labelColumnWidth = with(LocalDensity.current) {
        measurer.measure(AnnotatedString(GapLabelTemplate), style).size.width.toDp()
    }
    Box(Modifier.width(labelColumnWidth), contentAlignment = Alignment.CenterEnd) {
        Text(
            formatHoursMinutes(duration),
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun FeedRow(feed: FeedEntity, onTap: () -> Unit) {
    val end = feed.endTime
    val isBottle = feed.type == FeedType.bOTTLE
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        val side = feed.side
        val chip = when {
            isBottle -> bottleColor
            side != null -> side.sideColor
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(26.dp).clip(CircleShape).background(chip),
        ) {
            if (isBottle) {
                BottleGlyph(onAccent(chip))
            } else {
                Text(
                    side?.label ?: "·",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (side != null) onAccent(chip)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))

        // Fixed-width time column sized from a worst-case range, so the bars that follow share
        // one origin on every row - a bottle's single time is much narrower than a range.
        val timeStyle = MaterialTheme.typography.bodyLarge
        val measurer = rememberTextMeasurer()
        val timeColumnWidth = with(LocalDensity.current) {
            measurer.measure(AnnotatedString("00:00 – 00:00"), timeStyle).size.width.toDp()
        }
        Text(
            // A bottle is a point event: one time, not a range.
            if (isBottle) formatClockTime(feed.startTime)
            else "${formatClockTime(feed.startTime)} – ${end?.let { formatClockTime(it) } ?: "…"}",
            style = timeStyle,
            modifier = Modifier.width(timeColumnWidth),
        )
        Spacer(Modifier.width(12.dp))

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            when {
                isBottle -> ValueBar(
                    label = feed.amountMl?.let { "${it}ml" } ?: "bottle",
                    // Amount-scaled so bottle bars compare with bottle bars; an amountless
                    // bottle gets the same minimum sliver a zero value would.
                    fraction = (feed.amountMl ?: 0).toFloat() / BottleBarCapMl,
                    barColor = bottleColor,
                )
                end == null -> Text(
                    "in progress",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> ValueBar(
                    label = formatHoursMinutes(
                        Duration.between(feed.startTime, end).coerceAtLeast(Duration.ZERO)
                    ),
                    fraction = Duration.between(feed.startTime, end).toMillis().toFloat() /
                        FeedBarCap.toMillis(),
                    barColor = side?.sideColor ?: MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

/**
 * A horizontal bar whose length is value/cap of the available width (clamped full at the
 * cap), growing leftward from the right edge, with the exact value in a fixed-width column
 * hugging that edge. The column is sized from [BarLabelTemplate], a worst-case value, not
 * the actual label - a fixed origin is what makes bar lengths comparable row to row.
 */
@Composable
private fun ValueBar(
    label: String,
    fraction: Float,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val labelColumnWidth = with(LocalDensity.current) {
        measurer.measure(AnnotatedString(BarLabelTemplate), style).size.width.toDp()
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val barWidth = (maxWidth - labelColumnWidth - 6.dp).coerceAtLeast(0.dp) *
            fraction.coerceIn(0.02f, 1f)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .width(barWidth)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor),
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(labelColumnWidth), contentAlignment = Alignment.CenterEnd) {
                Text(
                    label,
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Sums completed feeds only; an in-progress feed contributes once it ends. */
private fun completedFeedTotal(feeds: List<FeedEntity>): Duration =
    feeds.fold(Duration.ZERO) { acc, feed ->
        val end = feed.endTime ?: return@fold acc
        acc + Duration.between(feed.startTime, end).coerceAtLeast(Duration.ZERO)
    }

/** Sums completed naps only; an in-progress nap contributes once it ends. */
private fun completedNapTotal(naps: List<NapEntity>): Duration =
    naps.fold(Duration.ZERO) { acc, nap ->
        val end = nap.endTime ?: return@fold acc
        acc + Duration.between(nap.startTime, end).coerceAtLeast(Duration.ZERO)
    }

private fun counted(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"

// A typical feed fills ~half the bar; anything at or past the cap reads as "long" and clamps -
// the text stays exact. Tuned to this baby's observed range (feeds 2-18m); adjust as feeding
// patterns lengthen.
private val FeedBarCap: Duration = Duration.ofMinutes(20)

// A full expressed-milk top-up bottle; bigger amounts clamp, the label stays exact.
private const val BottleBarCapMl = 150

// Worst case across feed and bottle bars: wider than "18m" and as wide as any bottle label.
private const val BarLabelTemplate = "150ml"

// Worst-case hours-scale label, shared by session gaps and nap durations.
private const val GapLabelTemplate = "9h 59m"

// Each dot is one started half hour; long stretches cap, the text stays exact.
private const val GapDotMinutes = 30L
private const val GapDotMax = 9

// Pill height (about 42dp) plus its 14dp bottom margin, plus a little air.
private val FloatingFilterClearance = 72.dp
