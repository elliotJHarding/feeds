package com.harding.feeds.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A baby bottle - teat, collar, body - hand-drawn to match the app's other Canvas glyphs. */
@Composable
fun BottleGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 14.dp) {
    Canvas(modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        // Teat: a small dome at the top centre.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.38f, h * 0.02f),
            size = Size(w * 0.24f, h * 0.2f),
        )
        // Collar between teat and body.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.28f, h * 0.16f),
            size = Size(w * 0.44f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
        )
        // Body.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.2f, h * 0.34f),
            size = Size(w * 0.6f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
        )
    }
}
