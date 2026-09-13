package com.harding.feeds.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.PathEffect
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
import com.harding.feeds.ui.formatAmount
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.gapBetween
import com.harding.feeds.ui.label
import com.harding.feeds.ui.napColor
import com.harding.feeds.ui.sideColor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The day drawn against a real time-of-day axis, in two lanes: feeds against the hour rail, sleep
 * beside them. Where [HistoryList] answers "how long, and how often", this answers "when".
 *
 * **The scale is 0.5dp per minute** - 720dp a day, against a list viewport of roughly 737dp on a
 * Pixel 10, so a whole day lands on one screen.
 *
 * **Two lanes, and the reason is what a parent asks of it.** The screen serves three readings, in
 * this order: the shape of the day at a glance; then two numbers that decide the next hour - how
 * long she has been awake, and how long since the last feed; and only then finding a record to
 * correct, which the compact list does better anyway.
 *
 * A single column served none of them. Nap bands ran the full width, so six of nine interval
 * values were drawn underneath one; the two measures sat on two rails at the same height with
 * nothing to tie either to its event; and a feed and a nap minutes apart collapsed into one clump.
 * Lanes fix all three by construction rather than by tuning. A band cannot cover a feed's interval
 * because it is not in that column, and each interval prints in the lane of the thing it measures,
 * so no second rail is needed.
 *
 * It also shows the cycle. The records run feed, awake, sleep - 15:23-15:34 fed then 15:34-16:35
 * slept, on 13 Sep - and side by side that sequence is visible.
 *
 * **One block per session, striped by its feeds.** Two consecutive feeds came as close as 0.7
 * minutes apart in 21 days of records, so a block per feed would draw on top of itself here. Two
 * sessions were never closer than 26 minutes, because [SessionPauseThreshold] puts a 20-minute
 * floor under the interval. Each feed takes an equal stripe: 45 of 222 sessions ran both sides, and
 * 199 of them spanned under the block floor, so a stripe cannot be a duration - it says which
 * sides, in which order.
 *
 * **Later runs downward, and the newest day is at the bottom**, matching the app's time-of-day
 * chart. `reverseLayout` gives that ordering while still opening on the newest records.
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
    // Computed across all days, not per panel, so the stretch that crosses midnight still counts.
    // The feed measure is shared with the compact list, so the two modes cannot print different
    // numbers for one pair.
    val intervals = remember(days) { Intervals(gapsBeforeFeed(days), awakeBeforeNap(days)) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val lanes = laneGeometry(maxWidth, filter)

        // reverseLayout stacks item 0 at the bottom and opens there. `days` is newest-first, so the
        // newest day lands at the bottom under the thumb and older days run upward, while each day
        // panel still draws midnight at its own top.
        LazyColumn(
            Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(bottom = FloatingFilterClearance),
        ) {
            days.forEach { day ->
                item(key = "day-${day.date}") {
                    DayPanel(day, today, lanes, zone, now, intervals, onSessionTap, onNapTap)
                }
            }
        }
    }
}

/** One day: its header, then 24 hours of axis with the day's records placed in the two lanes. */
@Composable
private fun DayPanel(
    day: DayHistory,
    today: LocalDate,
    lanes: LaneGeometry,
    zone: ZoneId,
    now: Instant,
    intervals: Intervals,
    onSessionTap: (FeedSession) -> Unit,
    onNapTap: (NapEntity) -> Unit,
) {
    val layout = remember(day, now, intervals, lanes) { layOut(day, zone, now, intervals) }

    Column {
        DayHeader(day, today)
        Box(Modifier.fillMaxWidth().height(DayHeight)) {
            HourGrid(lanes)

            // Sleep lane first: a band is ground, and its label reads over it.
            layout.napGaps.forEach { AwakeMark(it, lanes) }
            layout.napLane.forEach { NapBand(it, lanes, onNapTap) }

            // Feed lane. Nothing here can touch the sleep lane, so the order between them is free.
            layout.feedGaps.forEach { FeedGapMark(it, lanes) }
            layout.feedLane.forEach { SessionBlock(it, lanes, onSessionTap) }
        }
    }
}

/** The two derived intervals. Different measures, so they are never merged into one number. */
private data class Intervals(
    val beforeFeed: Map<String, Duration>,
    val awakeBeforeNap: Map<String, Duration>,
)

/**
 * Where the two lanes sit, measured from the panel's own width rather than hard-coded, so the
 * split holds on a screen wider or narrower than the Pixel's 411dp.
 *
 * Under a single-kind filter the visible lane takes the whole plot. Half an empty screen is not a
 * useful reading, and the filter has already said which kind is wanted.
 */
private data class LaneGeometry(
    val railWidth: Dp,
    val plotEnd: Dp,
    val blockStart: Dp,
    val blockWidth: Dp,
    val feedLabelStart: Dp,
    val feedLaneEnd: Dp,
    val bandStart: Dp,
    val bandWidth: Dp,
    val napLabelStart: Dp,
)

private fun laneGeometry(width: Dp, filter: EventFilter): LaneGeometry {
    val plotStart = RailWidth
    val plotEnd = width - PlotEndInset
    val full = plotEnd - plotStart

    // Both kinds showing: split the plot, with a gutter so neither lane's text reaches the other.
    val laneWidth = if (filter == EventFilter.BOTH) (full - LaneGutter) / 2 else full
    val bandStart = if (filter == EventFilter.BOTH) plotStart + laneWidth + LaneGutter else plotStart

    return LaneGeometry(
        railWidth = plotStart,
        plotEnd = plotEnd,
        blockStart = plotStart + 4.dp,
        blockWidth = BlockWidth,
        feedLabelStart = plotStart + 4.dp + BlockWidth + 6.dp,
        feedLaneEnd = plotStart + laneWidth,
        bandStart = bandStart,
        bandWidth = laneWidth,
        napLabelStart = bandStart + 8.dp,
    )
}

/** A record placed in its lane: its block, and where the label beside it goes. */
private data class Placed<T>(
    val record: T,
    val top: Dp,
    val height: Dp,
    val labelTop: Dp,
)

/**
 * The interval between two records in one lane, drawn in the space it measures. [connected] is
 * false where the interval began on the previous day, since a day panel cannot draw across
 * midnight.
 */
private data class GapMark(val top: Dp, val height: Dp, val text: String, val connected: Boolean)

private data class DayLayout(
    val feedLane: List<Placed<FeedSession>>,
    val napLane: List<Placed<NapEntity>>,
    val feedGaps: List<GapMark>,
    val napGaps: List<GapMark>,
)

private fun layOut(
    day: DayHistory,
    zone: ZoneId,
    now: Instant,
    intervals: Intervals,
): DayLayout {
    // Oldest first, so the label nudge only ever pushes downward - a nudge that pushed upward
    // could walk a label off the top of the panel.
    val ordered = day.items.asReversed()

    val feedLane = place(ordered.filterIsInstance<TimelineItem.Feeding>().map { it.session }) {
        blockSpan(it.startInstant(), it.endInstant(), day.date, zone, now)
    }
    val napLane = place(ordered.filterIsInstance<TimelineItem.Napping>().map { it.nap }) {
        blockSpan(it.startTime, it.endTime, day.date, zone, now)
    }

    return DayLayout(
        feedLane = feedLane,
        napLane = napLane,
        // Keyed on the session's oldest feed, the same lookup the compact list uses. The day's
        // first session keeps its mark: that is the overnight interval, and the night is where the
        // signal is. Its space is only the part after midnight, which is why it draws no spine -
        // the value is the whole interval, not the height of the space.
        feedGaps = gapMarks(feedLane, spine = true, { intervals.beforeFeed[it.feeds.last().id] }) {
            formatHoursMinutes(it)
        },
        // The day's first nap gets nothing, because awakeBeforeNap stops at midnight - see the
        // note there. No spine either: the band edges already bound the stretch.
        napGaps = gapMarks(napLane, spine = false, { intervals.awakeBeforeNap[it.id] }) {
            "${formatHoursMinutes(it)} awake"
        },
    )
}

/**
 * Lays out one lane: block tops from the record's own minutes, heights from [blockHeights], and
 * labels centred on their block then pushed apart so no two overlap.
 */
private fun <T> place(records: List<T>, span: (T) -> BlockSpan): List<Placed<T>> {
    val spans = records.map(span)
    val tops = spans.map { it.startMinute * DpPerMinute }
    val heights = blockHeights(
        tops = tops,
        lengths = spans.map { it.lengthMinutes * DpPerMinute },
        floor = BlockFloor,
        gutter = BlockGutter,
        minVisible = MinVisibleBlock,
    )
    val labelTops = nudgedLabelTops(
        tops = tops.mapIndexed { index, top -> top + (heights[index] - LabelSeparation) / 2f },
        minSeparation = LabelSeparation,
    )

    return records.mapIndexed { index, record ->
        Placed(
            record = record,
            top = tops[index].dp,
            height = heights[index].dp,
            labelTop = labelTops[index].coerceAtLeast(0f).dp,
        )
    }
}

/**
 * An interval mark for the space above each block in a lane.
 *
 * Only where the space is at least [GapMarkMinSpace]. A short interval does not read as empty, so
 * labelling it would only add clutter - and the label needs the room anyway, or it would crowd the
 * clock labels on the same rail. Measured session intervals run 66 minutes at the lower quartile
 * and 122 at the median, so most clear the bar and the tight ones stay quiet.
 */
private fun <T> gapMarks(
    lane: List<Placed<T>>,
    spine: Boolean,
    interval: (T) -> Duration?,
    label: (Duration) -> String,
): List<GapMark> = lane.mapIndexedNotNull { index, placed ->
    val gap = interval(placed.record) ?: return@mapIndexedNotNull null
    val previous = lane.getOrNull(index - 1)
    val spaceTop = previous?.let { it.top + it.height } ?: 0.dp
    val space = placed.top - spaceTop
    if (space < GapMarkMinSpace) return@mapIndexedNotNull null

    GapMark(
        top = spaceTop,
        height = space,
        text = label(gap),
        connected = spine && previous != null,
    )
}

/**
 * The hour axis: a hairline an hour, a heavier rule every six, and a label every two. Drawn on a
 * Canvas so 25 rules and 12 labels cost one draw rather than 37 layout nodes per day.
 */
@Composable
private fun HourGrid(lanes: LaneGeometry) {
    val measurer = rememberTextMeasurer()
    val minor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    val major = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    Canvas(Modifier.fillMaxSize()) {
        val railPx = lanes.railWidth.toPx()
        val endPx = lanes.plotEnd.toPx()
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
 * A nap: a soft band filling the sleep lane, with its range read over it. A nap is hours where a
 * feed is minutes, so it stays ground rather than another mark on the same scale - the treatment
 * the charts already use. The lane, not the colour, is what says this is sleep.
 */
@Composable
private fun NapBand(placed: Placed<NapEntity>, lanes: LaneGeometry, onTap: (NapEntity) -> Unit) {
    Box(
        Modifier
            .offset(x = lanes.bandStart, y = placed.top)
            .width(lanes.bandWidth)
            .height(placed.height)
            .clip(RoundedCornerShape(9.dp))
            .background(napColor.copy(alpha = 0.32f)),
    )

    val nap = placed.record
    val end = nap.endTime
    LaneLabel(
        top = placed.labelTop,
        start = lanes.napLabelStart,
        width = lanes.plotEnd - lanes.napLabelStart,
        lead = "${formatClockTime(nap.startTime)} – ${end?.let { formatClockTime(it) } ?: "…"}",
        tail = end?.let { formatHoursMinutes(gapBetween(nap.startTime, it)) } ?: "napping",
        blockTop = placed.top,
        blockHeight = placed.height,
        onTap = { onTap(nap) },
    )
}

/**
 * One session, as one block in the feed lane. Its feeds take equal stripes down it, oldest at the
 * top so the stripes run the same way as the axis. A session of one feed is a plain block, and two
 * feeds on the same side read as one too, because the stripes share a colour.
 */
@Composable
private fun SessionBlock(
    placed: Placed<FeedSession>,
    lanes: LaneGeometry,
    onTap: (FeedSession) -> Unit,
) {
    val session = placed.record
    Column(
        Modifier
            .offset(x = lanes.blockStart, y = placed.top)
            .width(lanes.blockWidth)
            .height(placed.height)
            .clip(RoundedCornerShape(5.dp)),
    ) {
        session.feeds.asReversed().forEach { feed ->
            Box(Modifier.weight(1f).fillMaxWidth().background(feed.blockColor()))
        }
    }

    LaneLabel(
        top = placed.labelTop,
        start = lanes.feedLabelStart,
        width = lanes.feedLaneEnd - lanes.feedLabelStart,
        lead = formatClockTime(session.startInstant()),
        tail = sessionTail(session),
        blockTop = placed.top,
        blockHeight = placed.height,
        onTap = { onTap(session) },
    )
}

/**
 * A record's clock reading, and the tap target for it.
 *
 * The tap box covers the block and its label together and never falls below [MinTapHeight], so a
 * block at the floor is still reachable. It is confined to its own lane, so the two lanes can
 * never take each other's taps.
 *
 * The clock time is the quietest text here on purpose. The blocks carry the reading this mode
 * exists for; times are what you drop to when a block is not enough.
 */
@Composable
private fun LaneLabel(
    top: Dp,
    start: Dp,
    width: Dp,
    lead: String,
    tail: String,
    blockTop: Dp,
    blockHeight: Dp,
    onTap: () -> Unit,
) {
    val tapTop = minOf(blockTop, top)
    val tapBottom = maxOf(blockTop + blockHeight, top + LabelSeparation.dp)

    Box(
        Modifier
            .offset(x = start, y = tapTop)
            .width(width)
            .height(maxOf(tapBottom - tapTop, MinTapHeight))
            .clickable(onClick = onTap),
    ) {
        Row(
            Modifier.offset(y = top - tapTop),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                lead,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                tail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The feed interval, on a dotted spine down the block column - the compact list's language. */
@Composable
private fun FeedGapMark(mark: GapMark, lanes: LaneGeometry) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant

    if (mark.connected) {
        Box(Modifier.offset(x = lanes.blockStart, y = mark.top).height(mark.height)) {
            Canvas(Modifier.width(lanes.blockWidth).fillMaxSize()) {
                drawLine(
                    color = ink.copy(alpha = 0.30f),
                    start = Offset(size.width / 2f, 3.dp.toPx()),
                    end = Offset(size.width / 2f, size.height - 3.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(2.dp.toPx(), 4.dp.toPx()),
                    ),
                )
            }
        }
    }

    IntervalText(mark, lanes.feedLabelStart, ink.copy(alpha = 0.85f))
}

/** The awake stretch, in the sleep lane, in the nap accent so it belongs to the band above it. */
@Composable
private fun AwakeMark(mark: GapMark, lanes: LaneGeometry) {
    IntervalText(mark, lanes.napLabelStart, napColor)
}

@Composable
private fun IntervalText(mark: GapMark, start: Dp, color: Color) {
    Box(
        Modifier.offset(x = start, y = mark.top).height(mark.height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            mark.text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
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

    val span = formatHoursMinutes(gapBetween(session.startInstant(), end))
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

// A label line, and so the least room two labels in one lane may share.
private const val LabelSeparation = 15f

/**
 * How tall a block grows when its record is too short to read - 24 minutes of the axis.
 *
 * 12dp is the largest floor at which no session pair in the measured 221 runs into the next, and
 * the worst pair still keeps 2 minutes of air. The sample cannot rule out a tighter pair in
 * future, so [blockHeights] enforces the separation as well rather than trusting this number.
 */
private const val BlockFloor = 12f

// Kept clear between two blocks in one lane, so a gap always reads as a gap.
private const val BlockGutter = 2f

// A bottle has no length at all, so it needs a size of its own when the next block crowds it.
private const val MinVisibleBlock = 3f

// The least clear space an interval mark is written into - an hour of the axis. Below this the
// space does not read as empty, and the value would crowd the clock labels on the same rail.
private val GapMarkMinSpace = 30.dp

// Small, but a block at the floor is unhittable, and the label shares the box.
private val MinTapHeight = 24.dp

private val RailWidth = 28.dp
private val BlockWidth = 40.dp
private val LaneGutter = 9.dp
private val PlotEndInset = 12.dp
