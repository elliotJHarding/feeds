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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harding.feeds.client.models.Side
import com.harding.feeds.ui.label
import com.harding.feeds.ui.sideColor
import java.time.LocalDate

/**
 * The two v1 charts (SPEC): interval pattern as a time-of-day plot - each feed drawn at its
 * time of day per day column, so the empty vertical space between marks IS the gap between
 * feeds - and a duration trend of feed minutes per day. Drawn with plain Compose Canvas:
 * two single-series charts don't justify a chart library dependency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(vm: ChartsViewModel, onBack: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
            SideLegend()
            Spacer(Modifier.height(16.dp))
            SectionHeader(
                title = "Time of day",
                caption = "Each mark is a feed at its time of day - vertical gaps show the interval pattern",
            )
            TimeOfDayChart(data, Modifier.fillMaxWidth().height(320.dp))

            Spacer(Modifier.height(32.dp))

            SectionHeader(
                title = "Feed minutes per day",
                caption = "Total time feeding per day over the last two weeks",
            )
            DurationTrendChart(data, Modifier.fillMaxWidth().height(220.dp))

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** L/R colour key, so the mark and bar colours are interpretable. */
@Composable
private fun SideLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(Side.l, Side.r).forEach { side ->
            Box(
                Modifier.size(12.dp).clip(CircleShape).background(side.sideColor),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${side.label} side",
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
private fun TimeOfDayChart(data: ChartsViewModel.ChartData, modifier: Modifier = Modifier) {
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
        data.segments.forEach { segment ->
            val x = leftPad + columnWidth * (segment.dayIndex + 0.5f)
            val y0 = topPad + plotHeight * segment.startMinute / MINUTES_PER_DAY
            val y1 = maxOf(topPad + plotHeight * segment.endMinute / MINUTES_PER_DAY, y0 + minLength)
            val color = when (segment.side) {
                Side.l -> leftColor
                Side.r -> rightColor
                null -> unknownColor
            }
            drawLine(color, Offset(x, y0), Offset(x, y1), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun DurationTrendChart(data: ChartsViewModel.ChartData, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val leftColor = Side.l.sideColor
    val rightColor = Side.r.sideColor
    val unknownColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier) {
        val leftPad = 34.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val plotWidth = size.width - leftPad
        val plotHeight = size.height - bottomPad - topPad

        val maxTotal = data.minutesPerDay.maxOfOrNull { it.total } ?: 0
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

        val columnWidth = plotWidth / data.days.size
        drawDayLabels(data.days, textMeasurer, labelStyle, leftPad, columnWidth)

        val barWidth = columnWidth * 0.55f
        data.minutesPerDay.forEachIndexed { index, dm ->
            if (dm.total == 0) return@forEachIndexed
            val left = leftPad + columnWidth * (index + 0.5f) - barWidth / 2f
            // Stack the sides from the baseline up: left, then right, then unknown.
            var yBottom = topPad + plotHeight
            listOf(dm.left to leftColor, dm.right to rightColor, dm.unknown to unknownColor)
                .forEach { (mins, color) ->
                    if (mins == 0) return@forEach
                    val h = plotHeight * mins / axisMax
                    drawRect(color, topLeft = Offset(left, yBottom - h), size = Size(barWidth, h))
                    yBottom -= h
                }
        }
    }
}

/** Day-of-month labels on alternating columns, aligned so today is always labelled. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDayLabels(
    days: List<LocalDate>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: TextStyle,
    leftPad: Float,
    columnWidth: Float,
) {
    days.forEachIndexed { index, day ->
        val label = textMeasurer.measure(AnnotatedString(day.dayOfMonth.toString()), labelStyle)
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

private fun minutesLabel(minutes: Int): String = when {
    minutes == 0 -> "0"
    minutes % 60 == 0 -> "${minutes / 60}h"
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h${minutes % 60}"
}

private const val MINUTES_PER_DAY = 24 * 60f
