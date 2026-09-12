package com.harding.feeds.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.harding.feeds.ui.theme.Palette
import com.harding.feeds.ui.theme.Presets
import com.harding.feeds.ui.theme.dimForeground
import com.harding.feeds.ui.theme.foreground

/**
 * Pick a theme.
 *
 * Each card is painted in the theme it offers, not in the theme currently in use, and it carries
 * a miniature of the app rather than a strip of swatches: a heading, a line of dim text, the
 * four event accents and the action colour. A parent therefore sees whether the dim text is
 * readable and whether the four accents stay apart before they choose, which a row of swatches
 * on a common ground cannot show.
 *
 * A tap applies the theme at once. There is no confirm step: the whole screen repaints, which is
 * the only preview that tells the truth, and another tap puts back what was there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(vm: ThemeViewModel, onBack: () -> Unit, onCustomise: () -> Unit) {
    val active = vm.active

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme") },
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
            Text(
                "Every card below is painted in its own colours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // Two per row, built by hand: a lazy grid inside a vertical scroll cannot measure.
            Presets.all.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { preset ->
                        PresetCard(
                            preset = preset,
                            selected = preset.id == active.id,
                            onClick = { vm.choose(preset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps the last card at half width when the count is odd.
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(12.dp))
            CustomCard(
                saved = vm.hasSavedCustom,
                active = active.isCustom,
                palette = if (active.isCustom) active else null,
                onClick = onCustomise,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A miniature of the app, painted in the preset's own seven colours. */
@Composable
private fun PresetCard(
    preset: Palette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = preset.background,
        border = BorderStroke(if (selected) 2.dp else 1.dp, preset.accent),
        modifier = modifier.height(128.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = preset.foreground,
                    maxLines = 1,
                )
                if (selected) CheckGlyph(preset.accent)
            }

            Spacer(Modifier.height(2.dp))
            // Dim text is the first thing an unreadable theme loses, so the card shows some.
            Text(
                "2h 10m ago",
                style = MaterialTheme.typography.bodySmall,
                color = preset.dimForeground,
                maxLines = 1,
            )

            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                preset.eventAccents.forEach { accent ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(accent))
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(preset.accent),
            )
        }
    }
}

/** The way into the editor. It shows the saved custom theme when there is one. */
@Composable
private fun CustomCard(
    saved: Boolean,
    active: Boolean,
    palette: Palette?,
    onClick: () -> Unit,
) {
    val ground = palette?.background ?: MaterialTheme.colorScheme.surfaceVariant
    val text = palette?.foreground ?: MaterialTheme.colorScheme.onSurface
    val dim = palette?.dimForeground ?: MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = ground,
        border = BorderStroke(
            if (active) 2.dp else 1.dp,
            palette?.accent ?: MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (saved) "Your theme" else "Make your own",
                    style = MaterialTheme.typography.titleSmall,
                    color = text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (saved) "Change any of the six colours" else "Start from the theme in use",
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                )
            }
            if (palette != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    palette.eventAccents.forEach { accent ->
                        Box(Modifier.size(14.dp).clip(CircleShape).background(accent))
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            if (active) CheckGlyph(palette?.accent ?: MaterialTheme.colorScheme.primary)
        }
    }
}

/** A tick, hand-drawn to match the app's other Canvas glyphs. */
@Composable
private fun CheckGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.54f)
            lineTo(size.width * 0.40f, size.height * 0.78f)
            lineTo(size.width * 0.86f, size.height * 0.24f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = size.height * 0.14f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
