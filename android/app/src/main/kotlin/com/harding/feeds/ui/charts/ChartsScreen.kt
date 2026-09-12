package com.harding.feeds.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harding.feeds.client.models.Side
import com.harding.feeds.ui.bottleColor
import com.harding.feeds.ui.components.EventFilter
import com.harding.feeds.ui.components.EventFilterPill
import com.harding.feeds.ui.label
import com.harding.feeds.ui.napColor
import com.harding.feeds.ui.sideColor
import java.time.LocalDate
import kotlin.math.ceil

/**
 * Charts over a chosen window, filtered to feeds, naps or both.
 *
 * The time-of-day plot is the one that carries the reading: each event drawn at its time of day
 * per day column, so the empty vertical space between marks IS the gap. Naps are wide bands
 * behind the feed marks, which is the classic newborn raster - you watch the pattern consolidate
 * rather than read a number.
 *
 * That chart is also the one that gets busy, which is what the filter is for. It uses the same
 * Feeds / Both / Naps control as the day timeline, because both answer the same question and a
 * parent should not have to learn two of them.
 *
 * Drawn with plain Compose Canvas: a handful of single-series charts do not justify a chart
 * library dependency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(vm: ChartsViewModel, onBack: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val window by vm.window.collectAsStateWithLifecycle()

    val showFeeds = filter != EventFilter.NAPS
    val showNaps = filter != EventFilter.FEEDS
    val span = window.label

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                // The window lives in the bar rather than the body: two pills stacked over the
                // first chart pushed it off the screen on a phone.
                actions = { WindowPicker(window, vm::selectWindow) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            EventFilterPill(
                selected = filter,
                onSelect = vm::selectFilter,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))

            Legend(filter)
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                title = "Time of day",
                caption = when (filter) {
                    EventFilter.FEEDS ->
                        "Each mark is a feed at its time of day - vertical gaps show the interval pattern"
                    EventFilter.NAPS -> "Each band is a nap at its time of day"
                    EventFilter.BOTH -> "Feeds as marks, naps as bands behind them"
                },
            )
            TimeOfDayChart(data, filter, Modifier.fillMaxWidth().height(320.dp))

            if (showFeeds) {
                Spacer(Modifier.height(32.dp))
                SectionHeader("Feed minutes per day", "Total time feeding per day over $span")
                if (!data.minutesPerDay.map { it.total }.hasReading()) {
                    EmptyNote("No feed minutes logged in the last $span")
                } else {
                    FeedMinutesChart(data, Modifier.fillMaxWidth().height(220.dp))
                }

                Spacer(Modifier.height(32.dp))
                SectionHeader("Average feed length", "Mean length of each day's feeds over $span")
                if (!data.avgMinutesPerDay.hasReading()) {
                    EmptyNote("No feed lengths recorded in the last $span")
                } else {
                    TrendChart(
                        days = data.days,
                        values = data.avgMinutesPerDay,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
            }

            if (showNaps) {
                Spacer(Modifier.height(32.dp))
                SectionHeader("Slept minutes per day", "Total time asleep per day over $span")
                // An axis with no bars under it reads as broken. Say why instead.
                if (!data.sleptMinutesPerDay.hasReading()) {
                    EmptyNote("No nap minutes logged in the last $span")
                } else {
                    SleptMinutesChart(data, Modifier.fillMaxWidth().height(220.dp))
                }

                Spacer(Modifier.height(32.dp))
                SectionHeader("Average nap length", "Mean length of each day's naps over $span")
                if (!data.avgNapMinutesPerDay.hasReading()) {
                    EmptyNote("No nap lengths recorded in the last $span")
                } else {
                    TrendChart(
                        days = data.days,
                        values = data.avgNapMinutesPerDay,
                        color = napColor,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Whether a series is worth a chart at all.
 *
 * Null is not the only empty. A zero-length event is a completed one, so it yields an average of
 * 0 rather than null, and the chart then drew a lone "1m" gridline over a dot on the baseline.
 * Only a reading above zero is worth an axis.
 */
private fun Collection<Int?>.hasReading(): Boolean = any { (it ?: 0) > 0 }

/** Stands in for a chart that has nothing to draw, so a bare axis never reads as a fault. */
@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

/** 7d / 14d / 30d, in the app bar. The cap is measured - see [ChartsViewModel.Window]. */
@Composable
private fun WindowPicker(selected: ChartsViewModel.Window, onSelect: (ChartsViewModel.Window) -> Unit) {
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ChartsViewModel.Window.entries.forEach { window ->
            val isSelected = window == selected
            Surface(
                onClick = { onSelect(window) },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    window.label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Colour key for whatever the filter is showing, so the marks are interpretable. */
@Composable
private fun Legend(filter: EventFilter) {
    val keys = buildList {
        if (filter != EventFilter.NAPS) {
            addAll(listOf(Side.l, Side.r).map { "${it.label} side" to it.sideColor })
            add("bottle" to bottleColor)
        }
        if (filter != EventFilter.FEEDS) add("nap" to napColor)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        keys.forEach { (label, color) ->
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(20.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, caption: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(
        caption,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun TimeOfDayChart(
    data: ChartData,
    filter: EventFilter,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Time (horizontal) lines carry the reading, so they're the prominent ones; the day
    // (vertical) lines only need to be faint since the marks already sit on them.
    val gridHourMajor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val gridHourMinor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
    val gridDay = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val leftColor = Side.l.sideColor
    val rightColor = Side.r.sideColor
    val unknownColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bottleDotColor = bottleColor
    val napBandColor = napColor
    val showFeeds = filter != EventFilter.NAPS
    val showNaps = filter != EventFilter.FEEDS

    Canvas(modifier) {
        val leftPad = 34.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val plotWidth = size.width - leftPad
        val plotHeight = size.height - bottomPad - topPad
        val columnWidth = plotWidth / data.days.size

        // Vertical gridline per day column so a mark lines up with its date label.
        data.days.indices.forEach { i ->
            val x = leftPad + columnWidth * (i + 0.5f)
            drawLine(gridDay, Offset(x, topPad), Offset(x, topPad + plotHeight), strokeWidth = 1f)
        }

        // Hour gridlines + labels every 3h (midnight at the top); heavier at the 6-hour marks.
        for (hour in 0..24 step 3) {
            val y = topPad + plotHeight * hour / 24f
            val major = hour % 6 == 0
            drawLine(
                if (major) gridHourMajor else gridHourMinor,
                Offset(leftPad, y), Offset(size.width, y),
                strokeWidth = if (major) 2f else 1f,
            )
            val label = textMeasurer.measure(AnnotatedString("%02d".format(hour % 24)), labelStyle)
            drawText(
                label,
                topLeft = Offset(leftPad - label.size.width - 6.dp.toPx(), y - label.size.height / 2f),
            )
        }

        drawDayLabels(data.days, textMeasurer, labelStyle, leftPad, columnWidth)

        val minLength = 3.dp.toPx()

        // Naps first, so the feed marks land on top of them. A nap band is wider than a feed
        // mark and softened, so it reads as ground rather than as another event on the same
        // scale - a nap is hours where a feed is minutes.
        if (showNaps) {
            val bandWidth = minOf(columnWidth * 0.62f, 14.dp.toPx())
            data.napSegments.forEach { nap ->
                val x = leftPad + columnWidth * (nap.dayIndex + 0.5f)
                val y0 = topPad + plotHeight * nap.startMinute / MinutesPerDay
                val y1 = maxOf(topPad + plotHeight * nap.endMinute / MinutesPerDay, y0 + minLength)
                drawRoundRect(
                    color = napBandColor.copy(alpha = if (showFeeds) 0.45f else 0.85f),
                    topLeft = Offset(x - bandWidth / 2f, y0),
                    size = Size(bandWidth, y1 - y0),
                    cornerRadius = CornerRadius(bandWidth / 2f, bandWidth / 2f),
                )
            }
        }

        if (!showFeeds) return@Canvas

        data.segments.forEach { segment ->
            val x = leftPad + columnWidth * (segment.dayIndex + 0.5f)
            val y0 = topPad + plotHeight * segment.startMinute / MinutesPerDay
            if (segment.isBottle) {
                // A bottle is a point event - a dot, so top-ups read against the breast
                // segments without pretending to have a duration.
                drawCircle(bottleDotColor, radius = 4.dp.toPx(), center = Offset(x, y0))
                return@forEach
            }
            val y1 = maxOf(topPad + plotHeight * segment.endMinute / MinutesPerDay, y0 + minLength)
            val color = when (segment.side) {
                Side.l -> leftColor
                Side.r -> rightColor
                null -> unknownColor
            }
            drawLine(color, Offset(x, y0), Offset(x, y1), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Feed minutes per day, stacked by side. */
@Composable
private fun FeedMinutesChart(data: ChartData, modifier: Modifier = Modifier) {
    val leftColor = Side.l.sideColor
    val rightColor = Side.r.sideColor
    val unknownColor = MaterialTheme.colorScheme.onSurfaceVariant

    StackedBarChart(
        days = data.days,
        bars = data.minutesPerDay.map { day ->
            listOf(day.left to leftColor, day.right to rightColor, day.unknown to unknownColor)
        },
        modifier = modifier,
    )
}

/** Slept minutes per day. One series: a nap has no side to stack. */
@Composable
private fun SleptMinutesChart(data: ChartData, modifier: Modifier = Modifier) {
    val color = napColor
    StackedBarChart(
        days = data.days,
        bars = data.sleptMinutesPerDay.map { listOf(it to color) },
        modifier = modifier,
    )
}

/**
 * A bar per day, stacked from the baseline up in the order given.
 *
 * Shared by the feed and nap totals. Feeds pass three parts and naps pass one, which is the only
 * difference between the two charts and not enough to justify two copies of the axis code.
 */
@Composable
private fun StackedBarChart(
    days: List<LocalDate>,
    bars: List<List<Pair<Int, Color>>>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Canvas(modifier) {
        val leftPad = 34.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val plotWidth = size.width - leftPad
        val plotHeight = size.height - bottomPad - topPad

        val maxTotal = bars.maxOfOrNull { bar -> bar.sumOf { it.first } } ?: 0
        val step = gridStep(maxTotal)
        val axisMax = (maxTotal / step + 1) * step

        // Value gridlines + labels ("30m" / "1h" / "1h30").
        var value = 0
        while (value <= axisMax) {
            val y = topPad + plotHeight * (1f - value.toFloat() / axisMax)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
            val label = textMeasurer.measure(AnnotatedString(minutesLabel(value)), labelStyle)
            drawText(
                label,
                topLeft = Offset(leftPad - label.size.width - 6.dp.toPx(), y - label.size.height / 2f),
            )
            value += step
        }

        val columnWidth = plotWidth / days.size
        drawDayLabels(days, textMeasurer, labelStyle, leftPad, columnWidth)

        val barWidth = columnWidth * 0.55f
        bars.forEachIndexed { index, parts ->
            if (parts.sumOf { it.first } == 0) return@forEachIndexed
            val left = leftPad + columnWidth * (index + 0.5f) - barWidth / 2f
            // Stack from the baseline up, in the order the caller gave.
            var yBottom = topPad + plotHeight
            parts.forEach { (mins, color) ->
                if (mins == 0) return@forEach
                val h = plotHeight * mins / axisMax
                drawRect(color, topLeft = Offset(left, yBottom - h), size = Size(barWidth, h))
                yBottom -= h
            }
        }
    }
}

/**
 * A per-day mean as a single-series smooth trend. A day with no completed event has no point and
 * breaks the curve, so each smooth run only ever joins real data.
 *
 * Used for both the average feed length and the average nap length; they differ only in the
 * series and the colour.
 */
@Composable
private fun TrendChart(
    days: List<LocalDate>,
    values: List<Int?>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val lineColor = color

    Canvas(modifier) {
        val leftPad = 34.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val plotWidth = size.width - leftPad
        val plotHeight = size.height - bottomPad - topPad
        val columnWidth = plotWidth / days.size

        val maxAvg = values.filterNotNull().maxOrNull() ?: 0
        val step = fineGridStep(maxAvg)
        val axisMax = (maxAvg / step + 1) * step

        // Value gridlines + labels every [step] minutes ("2m" / "10m" / "1h").
        var value = 0
        while (value <= axisMax) {
            val y = topPad + plotHeight * (1f - value.toFloat() / axisMax)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
            val label = textMeasurer.measure(AnnotatedString(minutesLabel(value)), labelStyle)
            drawText(
                label,
                topLeft = Offset(leftPad - label.size.width - 6.dp.toPx(), y - label.size.height / 2f),
            )
            value += step
        }

        drawDayLabels(days, textMeasurer, labelStyle, leftPad, columnWidth)

        // Each day's average as a point; contiguous days form one smooth run, and a null day
        // (no completed event) starts a new run so the curve never bridges missing data.
        val points = values.mapIndexed { index, avg ->
            avg?.let {
                Offset(
                    leftPad + columnWidth * (index + 0.5f),
                    topPad + plotHeight * (1f - it.toFloat() / axisMax),
                )
            }
        }
        val runs = mutableListOf<List<Offset>>()
        var current = mutableListOf<Offset>()
        points.forEach { p ->
            if (p == null) {
                if (current.isNotEmpty()) { runs.add(current); current = mutableListOf() }
            } else {
                current.add(p)
            }
        }
        if (current.isNotEmpty()) runs.add(current)

        // Faint dashed least-squares trend across every day that has an average, so the overall
        // direction over the window reads at a glance beneath the day-to-day curve.
        drawTrendLine(values, axisMax, leftPad, columnWidth, topPad, plotHeight, lineColor)

        val dotRadius = 3.dp.toPx()
        runs.forEach { run ->
            drawSmoothLine(run, lineColor, 2.dp.toPx())
            run.forEach { drawCircle(lineColor, radius = dotRadius, center = it) }
        }
    }
}

/** Straight least-squares fit over the non-null [values], drawn as a faint dashed line. */
private fun DrawScope.drawTrendLine(
    values: List<Int?>,
    axisMax: Int,
    leftPad: Float,
    columnWidth: Float,
    topPad: Float,
    plotHeight: Float,
    color: Color,
) {
    val points = values.mapIndexedNotNull { index, avg -> avg?.let { index to it } }
    if (points.size < 2) return

    val meanX = points.sumOf { it.first }.toFloat() / points.size
    val meanY = points.sumOf { it.second }.toFloat() / points.size
    var sxx = 0f
    var sxy = 0f
    points.forEach { (x, y) ->
        sxx += (x - meanX) * (x - meanX)
        sxy += (x - meanX) * (y - meanY)
    }
    if (sxx == 0f) return
    val slope = sxy / sxx
    val intercept = meanY - slope * meanX

    fun pointAt(index: Int): Offset {
        val value = (intercept + slope * index).coerceIn(0f, axisMax.toFloat())
        return Offset(
            leftPad + columnWidth * (index + 0.5f),
            topPad + plotHeight * (1f - value / axisMax),
        )
    }
    drawLine(
        color.copy(alpha = 0.5f),
        pointAt(points.first().first),
        pointAt(points.last().first),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
    )
}

/** A Catmull-Rom smooth stroke through [pts]; a single point draws nothing (its dot suffices). */
private fun DrawScope.drawSmoothLine(pts: List<Offset>, color: Color, strokeWidth: Float) {
    if (pts.size < 2) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 0 until pts.size - 1) {
            val p0 = pts[if (i == 0) 0 else i - 1]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = pts[if (i + 2 <= pts.lastIndex) i + 2 else pts.lastIndex]
            val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
            val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Day-of-month labels, thinned to fit the column width, with today always labelled. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDayLabels(
    days: List<LocalDate>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: TextStyle,
    leftPad: Float,
    columnWidth: Float,
) {
    val labels = days.map { textMeasurer.measure(AnnotatedString(it.dayOfMonth.toString()), labelStyle) }
    val widest = labels.maxOfOrNull { it.size.width } ?: return

    // The stride comes from the measured label, not from a fixed count. Labelling every column
    // fits at 14 days and collides at 30, where a column is 11dp against a two-digit label.
    val stride = maxOf(1, ceil(widest * 1.5f / columnWidth).toInt())

    // Counted back from the end, so today always carries a label whatever the stride is.
    days.indices.forEach { index ->
        if ((days.lastIndex - index) % stride != 0) return@forEach
        val label = labels[index]
        drawText(
            label,
            topLeft = Offset(
                leftPad + columnWidth * (index + 0.5f) - label.size.width / 2f,
                size.height - label.size.height,
            ),
        )
    }
}

private fun gridStep(maxMinutes: Int): Int =
    listOf(15, 30, 60, 120, 240).firstOrNull { maxMinutes / it <= 3 } ?: 480

/** Finer step than [gridStep]: averages sit in a narrow band, so aim for ~5-8 gridlines. */
private fun fineGridStep(maxMinutes: Int): Int =
    listOf(1, 2, 5, 10, 15, 20, 30, 60).firstOrNull { maxMinutes / it <= 7 } ?: 120

private fun minutesLabel(minutes: Int): String = when {
    minutes == 0 -> "0"
    minutes % 60 == 0 -> "${minutes / 60}h"
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h${minutes % 60}"
}

/** The shared day length as a float, for the plot arithmetic. */
private val MinutesPerDay = MINUTES_PER_DAY.toFloat()
