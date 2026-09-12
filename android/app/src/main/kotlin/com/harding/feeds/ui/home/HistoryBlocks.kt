package com.harding.feeds.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harding.feeds.client.models.FeedType
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.ui.bottleColor
import com.harding.feeds.ui.charts.MINUTES_PER_DAY
import com.harding.feeds.ui.components.EventFilter
import com.harding.feeds.ui.components.MoonGlyph
import com.harding.feeds.ui.formatAmount
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.label
import com.harding.feeds.ui.napColor
import com.harding.feeds.ui.sideColor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The day drawn against a real time-of-day axis: each record sits at its own minute, so the empty
 * space between blocks *is* the gap. Where [HistoryList] answers "how long, and how often", this
 * answers "when".
 *
 * **The scale is 0.5dp per minute** - 720dp a day, against a list viewport of roughly 737dp on a
 * Pixel 10, so a whole day lands on one screen. That is the point of the mode, and everything
 * below follows from it.
 *
 * **The block is the session, not the feed.** Measured over the last 21 days of real records, two
 * consecutive feeds came as close as 0.7 minutes apart and 29 of 281 pairs were under 15 minutes;
 * at 0.5dp per minute those blocks would draw on top of each other. Two consecutive *sessions*
 * were never closer than 26 minutes, because [SessionPauseThreshold] puts a 20-minute floor under
 * the interval. That floor is what keeps the clock labels legible. Inside a session each feed
 * still draws its own coloured block, so an L-to-R switch stays visible; the tap target is the
 * session, since a 7dp block cannot be hit.
 *
 * **Later runs downward, and the newest day is at the bottom.** The app's own time-of-day chart
 * already puts midnight at the top with hours increasing downward. `reverseLayout` gives that
 * ordering while still opening on the newest records, so the sheet's peek shows what the compact
 * list's peek showed.
 *
 * **A feed block has a 7dp floor.** Feed length runs 1.7 minutes at the tenth percentile and 7.1
 * at the median, so below the floor a feed is a hairline. Lengths therefore stop being comparable
 * near the floor - duration comparison stays [HistoryList]'s job, and its bars are built for it.
 *
 * Like [HistoryList] this does NOT take the ticking clock. It takes [nowEpochMinute], a minute
 * counter, so a running record's block grows once a minute instead of recomposing every block on
 * screen once a second - the stutter [HistoryList] records.
 */
@Composable
fun HistoryBlocks(
    days: List<DayHistory>,
    filter: EventFilter,
    nowEpochMinute: Long,
    onSessionTap: (FeedSession) -> Unit,
    onNapTap: (NapEntity) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
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

    val today = LocalDate.now(zone)
    val now = remember(nowEpochMinute) { Instant.ofEpochSecond(nowEpochMinute * 60) }

    // reverseLayout stacks item 0 at the bottom and opens there. `days` is newest-first, so the
    // newest day lands at the bottom under the thumb and older days run upward, while each day
    // panel still draws midnight at its own top.
    LazyColumn(
        modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(bottom = FloatingFilterClearance),
    ) {
        days.forEach { day ->
            item(key = "day-${day.date}") {
                DayPanel(day, today, filter, zone, now, onSessionTap, onNapTap)
            }
        }
    }
}

/** One day: its header, then 24 hours of axis with the day's records placed on it. */
@Composable
private fun DayPanel(
    day: DayHistory,
    today: LocalDate,
    filter: EventFilter,
    zone: ZoneId,
    now: Instant,
    onSessionTap: (FeedSession) -> Unit,
    onNapTap: (NapEntity) -> Unit,
) {
    val placed = remember(day, now) { place(day, zone, now) }

    Column {
        DayHeader(day, today)
        Box(Modifier.fillMaxWidth().height(DayHeight)) {
            HourGrid()

            // Three passes, so the z-order is fixed rather than incidental: nap bands are ground,
            // feed blocks sit on them, and every label reads over both.
            placed.forEach { block ->
                if (block.item is TimelineItem.Napping) NapBand(block, filter)
            }
            placed.forEach { block ->
                (block.item as? TimelineItem.Feeding)?.let { FeedBlocks(it.session, day.date, zone, now) }
            }
            placed.forEach { block -> BlockLabel(block, onSessionTap, onNapTap) }
        }
    }
}

/** A record placed on the axis: where its block starts, how tall it is, where its label goes. */
private data class PlacedBlock(
    val item: TimelineItem,
    val top: Dp,
    val height: Dp,
    val labelTop: Dp,
)

/**
 * Places one day's records, oldest first so the label nudge only ever pushes downward - a nudge
 * that pushed upward could walk a label off the top of the panel.
 */
private fun place(day: DayHistory, zone: ZoneId, now: Instant): List<PlacedBlock> {
    val ordered = day.items.asReversed()
    val spans = ordered.map { it.spanIn(day.date, zone, now) }
    val tops = spans.map { it.startMinute * DpPerMinute }
    val labelTops = nudgedLabelTops(tops, LabelSeparation)

    return ordered.mapIndexed { index, item ->
        PlacedBlock(
            item = item,
            top = tops[index].dp,
            height = blockHeight(spans[index].lengthMinutes),
            labelTop = labelTops[index].dp,
        )
    }
}

private fun blockHeight(minutes: Int): Dp = maxOf((minutes * DpPerMinute).dp, MinBlockHeight)

/**
 * The hour axis: a hairline an hour, a heavier rule every six, and a label every two. Drawn on a
 * Canvas so 25 rules and 12 labels cost one draw rather than 37 layout nodes per day.
 */
@Composable
private fun HourGrid() {
    val measurer = rememberTextMeasurer()
    val minor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    val major = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    Canvas(Modifier.fillMaxSize()) {
        val railPx = RailWidth.toPx()
        val endPx = size.width - PlotEndInset.toPx()
        val hourPx = (60 * DpPerMinute).dp.toPx()

        for (hour in 0..24) {
            val y = hour * hourPx
            val isMajor = hour % 6 == 0
            drawLine(
                color = if (isMajor) major else minor,
                start = Offset(railPx, y),
                end = Offset(endPx, y),
                strokeWidth = if (isMajor) 2f else 1f,
            )
            if (hour % 2 != 0 || hour == 24) continue

            val text = measurer.measure(AnnotatedString("%02d".format(hour)), labelStyle)
            drawText(
                text,
                topLeft = Offset(
                    x = railPx - 6.dp.toPx() - text.size.width,
                    // Midnight's label would sit half off the panel; clamping keeps it whole.
                    y = (y - text.size.height / 2f).coerceIn(0f, size.height - text.size.height),
                ),
            )
        }
    }
}

/**
 * A nap: a soft band across the whole plot, behind the feed blocks. A nap is hours where a feed is
 * minutes, so it reads as ground rather than as another event on the same scale - the treatment
 * the charts already use. Under the Naps filter nothing sits on top of it, so it deepens.
 */
@Composable
private fun NapBand(block: PlacedBlock, filter: EventFilter) {
    Box(
        Modifier
            .offset(y = block.top)
            .padding(start = RailWidth, end = PlotEndInset)
            .fillMaxWidth()
            .height(block.height)
            .clip(RoundedCornerShape(9.dp))
            .background(napColor.copy(alpha = if (filter == EventFilter.NAPS) 0.85f else 0.32f)),
    )
}

/**
 * One block per feed, at each feed's own minutes. The session is the unit for reading and for
 * tapping, but drawing per feed is what keeps a side switch inside a session visible.
 */
@Composable
private fun FeedBlocks(session: FeedSession, date: LocalDate, zone: ZoneId, now: Instant) {
    session.feeds.forEach { feed ->
        val span = blockSpan(feed.startTime, feed.endTime, date, zone, now)
        Box(
            Modifier
                .offset(x = BlockLeft, y = (span.startMinute * DpPerMinute).dp)
                .width(BlockWidth)
                .height(blockHeight(span.lengthMinutes))
                .clip(RoundedCornerShape(5.dp))
                .background(feed.blockColor()),
        )
    }
}

/** The bottle accent for a bottle, the side accent for a breast feed, muted where no side is set. */
@Composable
private fun FeedEntity.blockColor(): Color = when {
    type == FeedType.bOTTLE -> bottleColor
    side != null -> side.sideColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * The clock time, in a fixed column beside the blocks, and the tap target for the record.
 *
 * The tap box covers the block and its label together and never falls below [MinTapHeight], so a
 * 7dp block is still reachable. Where two boxes overlap the later one wins, which is the lower of
 * the two - the one whose label was nudged down and therefore the harder of the two to aim at.
 */
@Composable
private fun BlockLabel(
    block: PlacedBlock,
    onSessionTap: (FeedSession) -> Unit,
    onNapTap: (NapEntity) -> Unit,
) {
    val tapTop = minOf(block.top, block.labelTop)
    val tapBottom = maxOf(block.top + block.height, block.labelTop + LabelSeparation.dp)
    val onTap = when (val item = block.item) {
        is TimelineItem.Feeding -> ({ onSessionTap(item.session) })
        is TimelineItem.Napping -> ({ onNapTap(item.nap) })
    }

    Box(
        Modifier
            .offset(y = tapTop)
            .padding(start = RailWidth)
            .fillMaxWidth()
            .height(maxOf(tapBottom - tapTop, MinTapHeight))
            .clickable(onClick = onTap),
    ) {
        Row(
            Modifier
                .offset(y = block.labelTop - tapTop)
                .padding(start = LabelStart - RailWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The moon keeps a nap from reading as a feed on the letter alone. The band already
            // carries the nap accent, but the accent is colour, and colour cannot be the only
            // encoding.
            if (block.item is TimelineItem.Napping) {
                MoonGlyph(napColor, glyphSize = 13.dp)
            }
            Text(
                leadText(block.item),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                tailText(block.item),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * The clock reading. A session gets its start, because its extent is drawn; a nap gets its full
 * range, because a band's two edges are the thing being read.
 *
 * Both come from the record's own times rather than from the clamped span, so a record truncated
 * at midnight still states what actually happened.
 */
private fun leadText(item: TimelineItem): String = when (item) {
    is TimelineItem.Feeding -> formatClockTime(item.session.startInstant())
    is TimelineItem.Napping -> {
        val end = item.nap.endTime
        "${formatClockTime(item.nap.startTime)} – ${end?.let { formatClockTime(it) } ?: "…"}"
    }
}

private fun tailText(item: TimelineItem): String = when (item) {
    is TimelineItem.Feeding -> sessionTail(item.session)
    is TimelineItem.Napping -> item.nap.endTime
        ?.let { formatHoursMinutes(Duration.between(item.nap.startTime, it)) }
        ?: "napping"
}

/**
 * A bottle states its volume, since it has no length. A single breast feed states its length and
 * its side, so the side is never carried by colour alone. A session of several states the span it
 * covers and how many feeds are in it - the sides differ, so no single letter is true.
 */
private fun sessionTail(session: FeedSession): String {
    val end = session.endInstant() ?: return "feeding"
    val single = session.feeds.singleOrNull()

    if (single != null && single.type == FeedType.bOTTLE) {
        return single.amountMl?.let(::formatAmount) ?: "bottle"
    }

    val span = formatHoursMinutes(Duration.between(session.startInstant(), end))
    return when {
        single == null -> "$span · ${session.feeds.size} feeds"
        single.side != null -> "$span · ${single.side.label}"
        else -> span
    }
}

// 0.5dp a minute: 720dp a day against a list viewport of about 737dp, so one screen is one day.
// Finer scales were measured too - 0.75 shows about 16 hours and 1.0 about 12 - and the whole-day
// reading is what this mode is for.
private const val DpPerMinute = 0.5f

private val DayHeight = (MINUTES_PER_DAY * DpPerMinute).dp

// A label line, and so the least room two labels may share. 26 minutes is the closest two sessions
// came in 21 days, which is 13dp here, so the nudge fires on about one pair in 221.
private const val LabelSeparation = 15f

// Below this a feed is a hairline: feed length is 1.7 minutes at the tenth percentile.
private val MinBlockHeight = 7.dp

// Small, but a 7dp block is unhittable, and the label shares the box.
private val MinTapHeight = 24.dp

private val RailWidth = 34.dp
private val BlockLeft = 40.dp
private val BlockWidth = 38.dp
private val LabelStart = 88.dp
private val PlotEndInset = 12.dp
