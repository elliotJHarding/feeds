package com.harding.feeds.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.ui.dayLabel
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.label
import com.harding.feeds.ui.onSideColor
import com.harding.feeds.ui.sideColor
import java.time.Duration
import java.time.LocalDate

/**
 * Day-grouped feed history shown in the sheet behind the entry surface. Deliberately does NOT
 * take the ticking clock: completed feeds render identically every second, so binding the list
 * to `now` recomposed every visible row once a second and made scrolling stutter. The live
 * elapsed time for an in-progress feed lives on the entry surface, not here. The bars are
 * static too - each one relates two recorded timestamps.
 *
 * Each row carries two inline bars: the feed's duration growing from the left, and the gap
 * that *preceded* the feed (previous feed's end to this one's start) growing from the right.
 * Feeds and gaps live on different scales (a feed is ~20m, an overnight gap ~9h), so each bar
 * type has its own fixed cap - bars compare with bars of their own kind, and a given length
 * means the same thing next week as it does today.
 */
@Composable
fun HistoryList(
    days: List<DayFeeds>,
    onFeedTap: (FeedEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()

    // The gap preceding each feed, keyed by feed id: this feed's start minus the next-older
    // feed's end. Computed over the flattened list so the overnight gap lands on the first
    // feed of a day even though its predecessor sits in the previous day group. Overlaps
    // (negative gaps from hand-edited times), sub-minute gaps (which would label as "0m"),
    // and unfinished older feeds produce no bar.
    val gapsBefore = remember(days) {
        buildMap {
            days.flatMap { it.feeds }.zipWithNext { newer, older ->
                val olderEnd = older.endTime ?: return@zipWithNext
                val gap = Duration.between(olderEnd, newer.startTime)
                if (gap >= Duration.ofMinutes(1)) put(newer.id, gap)
            }
        }
    }

    if (days.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                "Feeds you log will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier.fillMaxSize()) {
        days.forEach { day ->
            item(key = "header-${day.date}") { DayHeader(day, today) }
            items(day.feeds, key = { it.id }) { feed ->
                FeedRow(feed, gapBefore = gapsBefore[feed.id], onTap = { onFeedTap(feed) })
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayFeeds, today: LocalDate) {
    // Sum completed feeds only; an in-progress feed contributes once it ends.
    val total = day.feeds.fold(Duration.ZERO) { acc, feed ->
        val end = feed.endTime ?: return@fold acc
        acc + Duration.between(feed.startTime, end).coerceAtLeast(Duration.ZERO)
    }
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
            "${day.feeds.size} feeds · ${formatHoursMinutes(total)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedRow(feed: FeedEntity, gapBefore: Duration?, onTap: () -> Unit) {
    val end = feed.endTime
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        val side = feed.side
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(side?.sideColor ?: MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                side?.label ?: "·",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (side != null) onSideColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${formatClockTime(feed.startTime)} – ${end?.let { formatClockTime(it) } ?: "…"}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(12.dp))

        // Two independent slots so a capped feed bar and a capped gap bar can never collide.
        Box(Modifier.weight(1.1f), contentAlignment = Alignment.CenterStart) {
            if (end == null) {
                Text(
                    "in progress",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                DurationBar(
                    duration = Duration.between(feed.startTime, end).coerceAtLeast(Duration.ZERO),
                    cap = FeedBarCap,
                    barColor = side?.sideColor ?: MaterialTheme.colorScheme.surfaceVariant,
                    barHeight = 24.dp,
                    labelTemplate = "59m",
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (gapBefore != null) {
                DurationBar(
                    duration = gapBefore,
                    cap = GapBarCap,
                    barColor = MaterialTheme.colorScheme.primaryContainer,
                    barHeight = 18.dp,
                    labelTemplate = "9h 59m",
                    growFromEnd = true,
                )
            }
        }
    }
}

/**
 * A horizontal bar whose length is duration/cap of the available width (clamped full at the
 * cap), with the exact value in a fixed-width column so labels align down the list: before
 * the bar normally (label column then bar growing right), after it when [growFromEnd] (bar
 * growing left then label column hugging the right edge). The label column is sized from
 * [labelTemplate], a worst-case value, not the actual label - a fixed origin per column is
 * what makes bar lengths comparable row to row.
 */
@Composable
private fun DurationBar(
    duration: Duration,
    cap: Duration,
    barColor: Color,
    barHeight: Dp,
    labelTemplate: String,
    modifier: Modifier = Modifier,
    growFromEnd: Boolean = false,
) {
    val label = formatHoursMinutes(duration)
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val labelColumnWidth = with(LocalDensity.current) {
        measurer.measure(AnnotatedString(labelTemplate), style).size.width.toDp()
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fraction = (duration.toMillis().toFloat() / cap.toMillis()).coerceIn(0.02f, 1f)
        val barWidth = (maxWidth - labelColumnWidth - 6.dp).coerceAtLeast(0.dp) * fraction

        @Composable
        fun Label() = Box(Modifier.width(labelColumnWidth), contentAlignment = Alignment.CenterEnd) {
            Text(
                label,
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        @Composable
        fun Bar() = Box(
            Modifier
                .width(barWidth)
                .height(barHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(barColor),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (growFromEnd) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (growFromEnd) {
                Bar()
                Spacer(Modifier.width(6.dp))
                Label()
            } else {
                Label()
                Spacer(Modifier.width(6.dp))
                Bar()
            }
        }
    }
}

// A typical feed fills ~half the bar; a typical daytime gap likewise. Anything at or past the
// cap reads as "long" and clamps - the text stays exact. Tuned to this baby's observed range
// (feeds 2-18m, daytime gaps 0.5-2.5h); adjust as feeding patterns lengthen.
private val FeedBarCap: Duration = Duration.ofMinutes(20)
private val GapBarCap: Duration = Duration.ofHours(4)
