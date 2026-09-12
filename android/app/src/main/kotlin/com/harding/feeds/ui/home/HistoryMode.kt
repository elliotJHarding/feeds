package com.harding.feeds.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * How the history sheet draws a day.
 *
 * [LIST] is the compact stack of cards: every record on its own row, exact times, duration bars
 * that compare with each other. It answers "how long, and how often".
 *
 * [BLOCKS] puts the same records on a real time-of-day axis, so the empty space between them is
 * the gap. It answers "when". The two are modes of one sheet rather than two screens, because
 * they show the same records under the same filter.
 */
enum class HistoryMode { LIST, BLOCKS }

val HistoryMode.other: HistoryMode
    get() = if (this == HistoryMode.LIST) HistoryMode.BLOCKS else HistoryMode.LIST

/**
 * The mode control: one round pill carrying the glyph of the mode it will switch **to**, the way
 * a play button shows what it does rather than what is happening.
 *
 * It toggles rather than segmenting, and the width is the reason. The filter pill is centred and
 * measures roughly 240dp - three labels of about 76dp - so on a 411dp screen it reaches about
 * 326dp. This pill is 44dp, the same as the top bar's icon buttons, and inset 14dp from the right
 * edge, so it starts at 353dp. A two-segment pill would need about 98dp and would land on the
 * filter. The 240dp is an estimate from the label metrics, not a measurement, so the clearance is
 * the thing to check first if the two ever touch.
 *
 * Like [com.harding.feeds.ui.components.EventFilterPill] it carries no position of its own.
 */
@Composable
fun HistoryModePill(
    selected: HistoryMode,
    onSelect: (HistoryMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = selected.other
    Surface(
        onClick = { onSelect(target) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        // Matches the filter pill: both float over the list, and both need lifting off the text
        // that passes underneath.
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
        modifier = modifier,
    ) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        val description = when (target) {
            HistoryMode.LIST -> "Show the compact list"
            HistoryMode.BLOCKS -> "Show the day as blocks"
        }
        Box(
            // 20dp glyph plus 12dp each side is 44dp - the top bar's ActionIcon size.
            Modifier.padding(12.dp).semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            when (target) {
                HistoryMode.LIST -> ListGlyph(tint)
                HistoryMode.BLOCKS -> BlocksGlyph(tint)
            }
        }
    }
}

/** Three equal stacked rules - rows of the same weight, which is what the compact list is. */
@Composable
private fun ListGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val h = size.height * 0.13f
        val gap = (size.height - 3 * h) / 2f
        repeat(3) { i ->
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, i * (h + gap)),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f),
            )
        }
    }
}

/** An axis on the left with three bars hung off it at their own lengths - blocks against time. */
@Composable
private fun BlocksGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val axis = size.width * 0.09f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(axis, size.height),
            cornerRadius = CornerRadius(axis / 2f, axis / 2f),
        )
        val h = size.height * 0.17f
        val gap = (size.height - 3 * h) / 2f
        val left = axis + size.width * 0.2f
        listOf(0.5f, 0.78f, 0.34f).forEachIndexed { i, fraction ->
            drawRoundRect(
                color = color,
                topLeft = Offset(left, i * (h + gap)),
                size = Size((size.width - left) * fraction, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f),
            )
        }
    }
}
