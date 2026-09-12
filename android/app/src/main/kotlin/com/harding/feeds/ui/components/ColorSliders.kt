package com.harding.feeds.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.harding.feeds.ui.theme.Hsv
import com.harding.feeds.ui.theme.hsvColor
import com.harding.feeds.ui.theme.toHsv
import kotlin.math.roundToInt

/**
 * Three sliders - hue, saturation and lightness - for picking one colour.
 *
 * Three separate axes beat the usual hue bar plus saturation square here. Each axis moves on its
 * own, so "the same colour but lighter" is one drag rather than a two-axis aim at 3am. Each one
 * is also a plain horizontal scrub, which is the gesture the rest of this app already uses.
 *
 * The sliders keep hue, saturation and value as their own state rather than reading them back
 * out of the colour on each frame. A colour at zero saturation carries no hue, so a round trip
 * through [Color] would throw the hue away the moment a parent dragged saturation to nothing.
 * [resetToken] is the one way to reseed them: bump it when the colour changes from outside.
 */
@Composable
fun ColorSliders(
    initial: Color,
    onChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    resetToken: Int = 0,
) {
    var hsv by remember(resetToken) { mutableStateOf(initial.toHsv()) }
    val emit by rememberUpdatedState(onChange)

    fun move(next: Hsv) {
        hsv = next
        emit(hsvColor(next.hue, next.saturation, next.value))
    }

    Column(modifier) {
        // The hue track shows every hue at a readable strength, whatever the current colour is
        // set to. At low saturation or a near-black value the true track would be a grey smear
        // and the parent could not see what they were choosing.
        GradientSlider(
            label = "Hue",
            reading = "${(hsv.hue).roundToInt()}°",
            fraction = hsv.hue / 360f,
            stops = (0..6).map { hsvColor(it * 60f, 0.65f, 0.95f) },
        ) { move(hsv.copy(hue = it * 360f)) }

        Spacer(Modifier.height(6.dp))

        GradientSlider(
            label = "Saturation",
            reading = "${(hsv.saturation * 100).roundToInt()}%",
            fraction = hsv.saturation,
            stops = listOf(
                hsvColor(hsv.hue, 0f, hsv.value.coerceAtLeast(0.25f)),
                hsvColor(hsv.hue, 1f, hsv.value.coerceAtLeast(0.25f)),
            ),
        ) { move(hsv.copy(saturation = it)) }

        Spacer(Modifier.height(6.dp))

        GradientSlider(
            label = "Lightness",
            reading = "${(hsv.value * 100).roundToInt()}%",
            fraction = hsv.value,
            stops = listOf(
                hsvColor(hsv.hue, hsv.saturation, 0f),
                hsvColor(hsv.hue, hsv.saturation, 1f),
            ),
        ) { move(hsv.copy(value = it)) }
    }
}

/**
 * One axis. The track is the gradient of what this axis does, so the parent picks by looking
 * rather than by reading a number. A press anywhere jumps to that point, and a drag scrubs.
 */
@Composable
private fun GradientSlider(
    label: String,
    reading: String,
    fraction: Float,
    stops: List<Color>,
    onChange: (Float) -> Unit,
) {
    val moveTo by rememberUpdatedState(onChange)
    val ground = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(74.dp),
        )
        Canvas(
            Modifier
                .weight(1f)
                .height(36.dp)
                .semantics { contentDescription = "$label $reading" }
                // The thumb travels inside an inset, so the gesture must map the same way or a
                // press would not land where the thumb sits.
                .pointerInput(Unit) {
                    val inset = ThumbRadius.toPx()
                    detectTapGestures { at -> moveTo(fractionAt(at.x, size.width.toFloat(), inset)) }
                }
                .pointerInput(Unit) {
                    val inset = ThumbRadius.toPx()
                    detectHorizontalDragGestures { change, _ ->
                        moveTo(fractionAt(change.position.x, size.width.toFloat(), inset))
                    }
                },
        ) {
            val trackHeight = TrackHeight.toPx()
            val radius = ThumbRadius.toPx()
            val top = (size.height - trackHeight) / 2f

            // The track is inset by the thumb's radius at both ends. Without it the thumb runs
            // off the canvas at 0% and 100%, where it is clipped and sits over the reading.
            drawRoundRect(
                brush = Brush.horizontalGradient(stops),
                topLeft = Offset(radius, top),
                size = Size(size.width - radius * 2f, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
            )

            // Two rings, ground outside and ink inside. One ring alone disappears wherever the
            // track happens to match it, and a colour track passes through every tone.
            val centre = Offset(radius + (size.width - radius * 2f) * fraction, size.height / 2f)
            drawCircle(ground, radius, centre, style = Stroke(width = 4.dp.toPx()))
            drawCircle(ink, radius, centre, style = Stroke(width = 2.dp.toPx()))
        }
        Text(
            reading,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp).padding(start = 8.dp),
        )
    }
}

/** The thumb sits proud of the track, so the track is inset by this much at both ends. */
private val TrackHeight = 14.dp
private val ThumbRadius = 12.dp

/** Where a press lands, in 0..1, given the same inset the thumb travels within. */
private fun fractionAt(x: Float, width: Float, inset: Float): Float {
    val span = width - inset * 2f
    return if (span <= 0f) 0f else ((x - inset) / span).coerceIn(0f, 1f)
}
