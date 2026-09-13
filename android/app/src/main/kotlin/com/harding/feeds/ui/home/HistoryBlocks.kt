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
 * **One block per session, striped by its feeds.** Measured over 21 days of real records, two
 * consecutive feeds came as close as 0.7 minutes apart, so a block per feed would draw on top of
 * itself at this scale. Two consecutive *sessions* were never closer than 26 minutes, because
 * [SessionPauseThreshold] puts a 20-minute floor under the interval. So the session is the block,
 * and each feed in it takes an equal stripe of that block in time order - 45 of 222 sessions ran
 * both sides, and a single-colour block would misreport every one of them.
 *
 * The stripes are equal rather than proportional on purpose. 199 of those 222 sessions spanned
 * under 24 minutes, which is under the block floor, so a stripe's height already cannot be a
 * duration. It says which sides, in which order. Duration comparison stays [HistoryList]'s job,
 * and its bars are built for it.
 *
 * **Later runs downward, and the newest day is at the bottom.** The app's own time-of-day chart
 * already puts midnight at the top with hours increasing downward. `reverseLayout` gives that
 * ordering while still opening on the newest records, so the sheet's peek shows what the compact
 * list's peek showed.
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
    // Shared with the compact list, so the two modes cannot print different numbers for one pair.
    val gapsBefore = remember(days) { gapsBeforeFeed(days) }

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
                DayPanel(day, today, filter, zone, now, gapsBefore, onSessionTap, onNapTap)
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
    gapsBefore: Map<String, Duration>,
    onSessionTap: (FeedSession) -> Unit,
    onNapTap: (NapEntity) -> Unit,
) {
    val layout = remember(day, now, gapsBefore) { layOut(day, zone, now, gapsBefore) }

    Column {
        DayHeader(day, today)
        Box(Modifier.fillMaxWidth().height(DayHeight)) {
            HourGrid()

            // Four passes, so the z-order is fixed rather than incidental: nap bands are ground,
            // the interval marks sit in the space between blocks, session blocks sit on the
            // bands, and every label reads over all of it.
            layout.napBars.forEach { NapBand(it, filter) }
            if (filter != EventFilter.NAPS) layout.feedGaps.forEach { FeedGapMark(it) }
            layout.sessionBars.forEach { SessionBlock(it) }
            layout.items.forEach { BlockLabel(it, onSessionTap, onNapTap) }
        }
    }
}

/** One drawn rectangle and the record behind it. */
private data class Bar<T>(val record: T, val top: Dp, val height: Dp)

/** A record's label and tap target, which follow the timeline item rather than the drawn bar. */
private data class PlacedItem(
    val item: TimelineItem,
    val top: Dp,
    val height: Dp,
    val labelTop: Dp,
)

/**
 * The interval between two feeding sessions, drawn in the space it measures. [connected] is false
 * where the interval began on the previous day, since a day panel cannot draw across midnight.
 */
private data class GapMark(val top: Dp, val height: Dp, val text: String, val connected: Boolean)

private data class DayLayout(
    val sessionBars: List<Bar<FeedSession>>,
    val napBars: List<Bar<NapEntity>>,
    val feedGaps: List<GapMark>,
    val items: List<PlacedItem>,
)

/**
 * Places one day's records, oldest first so the label nudge only ever pushes downward - a nudge
 * that pushed upward could walk a label off the top of the panel.
 *
 * Sessions and naps are laid out as two lanes, because a feed during a nap is meant to sit on the
 * band. Within a lane [blockHeights] keeps the blocks apart.
 */
private fun layOut(
    day: DayHistory,
    zone: ZoneId,
    now: Instant,
    gapsBefore: Map<String, Duration>,
): DayLayout {
    val ordered = day.items.asReversed()
    val spans = ordered.map { it.spanIn(day.date, zone, now) }
    val labelTops = nudgedLabelTops(spans.map { it.startMinute * DpPerMinute }, LabelSeparation)

    val items = ordered.mapIndexed { index, item ->
        PlacedItem(
            item = item,
            top = (spans[index].startMinute * DpPerMinute).dp,
            height = (spans[index].lengthMinutes * DpPerMinute).dp,
            labelTop = labelTops[index].dp,
        )
    }

    fun <T> lane(records: List<T>, span: (T) -> BlockSpan): List<Bar<T>> {
        val spansOf = records.map(span)
        val tops = spansOf.map { it.startMinute * DpPerMinute }
        val heights = blockHeights(
            tops = tops,
            lengths = spansOf.map { it.lengthMinutes * DpPerMinute },
            floor = BlockFloor,
            gutter = BlockGutter,
            minVisible = MinVisibleBlock,
        )
        return records.mapIndexed { index, record -> Bar(record, tops[index].dp, heights[index].dp) }
    }

    val sessionBars = lane(ordered.filterIsInstance<TimelineItem.Feeding>().map { it.session }) {
        blockSpan(it.startInstant(), it.endInstant(), day.date, zone, now)
    }

    return DayLayout(
        sessionBars = sessionBars,
        napBars = lane(ordered.filterIsInstance<TimelineItem.Napping>().map { it.nap }) {
            blockSpan(it.startTime, it.endTime, day.date, zone, now)
        },
        feedGaps = feedGaps(sessionBars, gapsBefore),
        items = items,
    )
}

/**
 * An interval mark for the space above each session block.
 *
 * Only where the space is at least [GapMarkMinSpace]. A short interval does not read as empty, so
 * labelling it would only add clutter - and the label needs the room anyway, or it would collide
 * with the clock labels on the same rail. Measured session intervals run 66 minutes at the lower
 * quartile and 122 at the median, so most intervals clear the bar and the tight ones stay quiet.
 */
private fun feedGaps(
    bars: List<Bar<FeedSession>>,
    gapsBefore: Map<String, Duration>,
): List<GapMark> = bars.mapIndexedNotNull { index, bar ->
    // The interval is keyed on the session's oldest feed, the same lookup the compact list uses.
    val gap = gapsBefore[bar.record.feeds.last().id] ?: return@mapIndexedNotNull null
    val previous = bars.getOrNull(index - 1)
    val spaceTop = previous?.let { it.top + it.height } ?: 0.dp
    val space = bar.top - spaceTop
    if (space < GapMarkMinSpace) return@mapIndexedNotNull null

    GapMark(
        top = spaceTop,
        height = space,
        text = formatHoursMinutes(gap),
        connected = previous != null,
    )
}

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
 * A nap: a soft band across the whole plot, behind the session blocks. A nap is hours where a feed
 * is minutes, so it reads as ground rather than as another event on the same scale - the treatment
 * the charts already use. Under the Naps filter nothing sits on top of it, so it deepens.
 */
@Composable
private fun NapBand(bar: Bar<NapEntity>, filter: EventFilter) {
    Box(
        Modifier
            .offset(y = bar.top)
            .padding(start = RailWidth, end = PlotEndInset)
            .fillMaxWidth()
            .height(bar.height)
            .clip(RoundedCornerShape(9.dp))
            .background(napColor.copy(alpha = if (filter == EventFilter.NAPS) 0.85f else 0.32f)),
    )
}

/**
 * The interval between two feeding sessions, written in the space it measures.
 *
 * A dotted spine down the middle of the block column, with the value on the clock labels' rail at
 * the midpoint. The compact list puts its intervals on a dotted spine between cards too, so the
 * two modes read the same way. The spine is dropped where the interval began on the previous day:
 * the value still shows, but a line from the top of the panel would say the interval started at
 * midnight.
 */
@Composable
private fun FeedGapMark(mark: GapMark) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant

    if (mark.connected) {
        Box(Modifier.offset(y = mark.top).padding(start = BlockLeft).height(mark.height)) {
            Canvas(Modifier.width(BlockWidth).fillMaxSize()) {
                drawLine(
                    color = ink.copy(alpha = 0.28f),
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

    Box(
        Modifier
            .offset(y = mark.top)
            .padding(start = LabelStart)
            .height(mark.height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            mark.text,
            style = MaterialTheme.typography.labelMedium,
            color = ink.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

/**
 * One session, as one block. Its feeds take equal stripes down it, oldest at the top so the
 * stripes run the same way as the axis. A session of one feed is therefore a plain block, and two
 * feeds on the same side read as one block too, because the stripes share a colour.
 */
@Composable
private fun SessionBlock(bar: Bar<FeedSession>) {
    Column(
        Modifier
            .offset(x = BlockLeft, y = bar.top)
            .width(BlockWidth)
            .height(bar.height)
            .clip(RoundedCornerShape(5.dp)),
    ) {
        bar.record.feeds.asReversed().forEach { feed ->
            Box(Modifier.weight(1f).fillMaxWidth().background(feed.blockColor()))
        }
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
 * small block is still reachable. Where two boxes overlap the later one wins, which is the lower
 * of the two - the one whose label was nudged down and therefore the harder of the two to aim at.
 */
@Composable
private fun BlockLabel(
    placed: PlacedItem,
    onSessionTap: (FeedSession) -> Unit,
    onNapTap: (NapEntity) -> Unit,
) {
    val tapTop = minOf(placed.top, placed.labelTop)
    val tapBottom = maxOf(placed.top + placed.height, placed.labelTop + LabelSeparation.dp)
    val onTap = when (val item = placed.item) {
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
                .offset(y = placed.labelTop - tapTop)
                .padding(start = LabelStart - RailWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The moon keeps a nap from reading as a feed on the letter alone. The band already
            // carries the nap accent, but the accent is colour, and colour cannot be the only
            // encoding.
            if (placed.item is TimelineItem.Napping) {
                MoonGlyph(napColor, glyphSize = 13.dp)
            }
            Text(
                leadText(placed.item),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                tailText(placed.item),
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

// Small, but a short block is unhittable, and the label shares the box.
private val MinTapHeight = 24.dp

private val RailWidth = 34.dp
private val BlockLeft = 40.dp
private val BlockWidth = 38.dp
private val LabelStart = 88.dp
private val PlotEndInset = 12.dp
