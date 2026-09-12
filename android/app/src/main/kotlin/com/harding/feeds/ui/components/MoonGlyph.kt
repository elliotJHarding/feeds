package com.harding.feeds.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A crescent moon - naps, matching the app's other hand-drawn Canvas glyphs.
 *
 * Built as a disc with a second disc punched out of it, rather than as a path of two arcs.
 * Arc endpoints on two different ellipses do not meet, so that version closed across a chord
 * and drew a blob. This construction cannot go wrong: subtracting one circle from another is a
 * crescent for any overlap that leaves the horns intact.
 *
 * The bite is cut with [BlendMode.Clear] on an offscreen layer, so the hole is genuinely
 * transparent and the chip's own colour shows through - whatever colour that happens to be.
 * Painting the bite in a background colour instead would only work on one ground, and this
 * glyph sits on a card, a mode button and the entry surface. The layer is required: without it
 * Clear would punch through everything drawn beneath this composable.
 */
@Composable
fun MoonGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 14.dp) {
    Canvas(
        modifier
            .size(glyphSize)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val r = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = color, radius = r, center = centre)

        // Offset up and to the right, so the horns point down-left and it reads as a waxing
        // moon rather than a bitten disc. The radius and offset leave the crescent about
        // 0.6r thick at its widest - chunky enough to stay legible at 14dp.
        drawCircle(
            color = Color.Black,
            radius = r * 0.85f,
            center = Offset(centre.x + r * 0.42f, centre.y - r * 0.22f),
            blendMode = BlendMode.Clear,
        )
    }
}
