package com.harding.feeds.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A crescent moon - naps, matching the app's other hand-drawn Canvas glyphs.
 *
 * Drawn as one filled path of two arcs rather than a background-coloured circle punched out of
 * a filled one: the chip ground varies (card, widget, entry surface), and [BottleGlyph] sets the
 * precedent that a glyph paints only its own colour.
 */
@Composable
fun MoonGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 14.dp) {
    Canvas(modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height

        // Outer edge sweeps down the right; the inner edge sweeps back up, biting the shape
        // into a crescent. Both arcs share their start and end points, so the path closes clean.
        val outer = Rect(Offset(w * 0.12f, h * 0.08f), Size(w * 0.76f, h * 0.84f))
        val inner = Rect(Offset(w * 0.38f, h * 0.02f), Size(w * 0.74f, h * 0.96f))

        val path = Path().apply {
            arcTo(outer, -60f, 300f, forceMoveTo = true)
            arcTo(inner, 70f, -220f, forceMoveTo = false)
            close()
        }
        drawPath(path, color)
    }
}
