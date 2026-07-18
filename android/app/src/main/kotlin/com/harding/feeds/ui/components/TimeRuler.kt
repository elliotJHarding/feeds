package com.harding.feeds.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.harding.feeds.ui.TIME_FORMAT
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The hero time control: a horizontal ruler that lives in the thumb zone. Drag to scrub with a
 * haptic detent every minute (deliberately geared slow - ~[MINUTE_WIDTH]dp of travel per
 * minute - so single-minute accuracy is easy); tap the left or right end to nudge one minute
 * without dragging. Typing an exact time is the readout's job, so the ruler has a single
 * meaning: scrub.
 *
 * Dragging right winds *back* (labels increase left-to-right like a timeline, so a later
 * minute is pulled under the caret by dragging the strip left). Ticks are drawn on a Canvas,
 * so cost is independent of how far you scrub.
 */
@Composable
fun TimeRuler(
    time: Instant,
    onTimeChange: (Instant) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(time)
    val onChange by rememberUpdatedState(onTimeChange)
    val measurer = rememberTextMeasurer()

    val minor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val major = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = major)
    val surface = MaterialTheme.colorScheme.surfaceVariant

    fun detent() = haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun shift(minutes: Long) = onChange(current.plus(minutes, ChronoUnit.MINUTES))

    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) {
                val pxPerMin = MINUTE_WIDTH.dp.toPx()
                var accumulated = 0f
                detectHorizontalDragGestures(onDragStart = { accumulated = 0f }) { change, drag ->
                    change.consume()
                    accumulated += drag
                    val steps = (accumulated / pxPerMin).toInt()
                    if (steps != 0) {
                        accumulated -= steps * pxPerMin
                        repeat(abs(steps)) { detent() }
                        shift(-steps.toLong()) // drag right (+) = earlier
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val w = size.width
                    when {
                        pos.x < w * 0.42f -> { shift(-1); detent() }
                        pos.x > w * 0.58f -> { shift(1); detent() }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pxPerMin = MINUTE_WIDTH.dp.toPx()
            val centerX = size.width / 2f
            val baseline = size.height - 12.dp.toPx()
            val minorLen = 10.dp.toPx()
            val majorLen = 20.dp.toPx()
            val span = (centerX / pxPerMin).toInt() + 2
            val centerMinute = current.atZone(zone).let { it.hour * 60 + it.minute }

            for (d in -span..span) {
                val x = centerX + d * pxPerMin
                if (x < -1f || x > size.width + 1f) continue
                val isMajor = ((centerMinute + d) % 5 + 5) % 5 == 0
                val len = if (isMajor) majorLen else minorLen
                drawLine(
                    color = if (isMajor) major else minor,
                    start = Offset(x, baseline - len),
                    end = Offset(x, baseline),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (isMajor) {
                    val measured = measurer.measure(AnnotatedString(minuteLabel(centerMinute + d)), labelStyle)
                    drawText(measured, topLeft = Offset(x - measured.size.width / 2f, 8.dp.toPx()))
                }
            }

            drawLine(
                color = accent,
                start = Offset(centerX, 8.dp.toPx()),
                end = Offset(centerX, size.height - 8.dp.toPx()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        EndScrim(surface, major, glyph = "‹", alignment = Alignment.CenterStart)
        EndScrim(surface, major, glyph = "›", alignment = Alignment.CenterEnd)
    }
}

/** Fades the ticks out at an edge and carries that edge's ±1-min tap chevron (cosmetic only). */
@Composable
private fun BoxScope.EndScrim(surface: Color, color: Color, glyph: String, alignment: Alignment) {
    val toStart = alignment == Alignment.CenterStart
    Box(
        Modifier
            .align(alignment)
            .fillMaxHeight()
            .width(46.dp)
            .background(
                Brush.horizontalGradient(
                    if (toStart) listOf(surface, Color.Transparent)
                    else listOf(Color.Transparent, surface)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

private fun minuteLabel(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    return LocalTime.of(m / 60, m % 60).format(TIME_FORMAT)
}

private const val MINUTE_WIDTH = 24
