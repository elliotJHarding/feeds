package com.harding.feeds.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harding.feeds.ui.components.BottleGlyph
import com.harding.feeds.ui.components.ColorSliders
import com.harding.feeds.ui.components.MoonGlyph
import com.harding.feeds.ui.theme.Palette
import com.harding.feeds.ui.theme.dimForeground
import com.harding.feeds.ui.theme.foreground
import com.harding.feeds.ui.theme.looksAlike
import com.harding.feeds.ui.theme.onColorFor
import com.harding.feeds.ui.theme.schemeFor
import com.harding.feeds.ui.theme.toHex

/**
 * Build your own theme from six colours.
 *
 * The screen paints itself in the draft rather than in the theme in use, so the chrome around
 * the pickers is the preview. Change the background and the page changes under your thumb.
 *
 * Text colours have no picker. The app measures them from the ground instead, so no combination
 * of six choices can hide the words. That is the whole reason the editor can be this free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(vm: ThemeViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val draft = vm.draft
    var openSlot by remember { mutableStateOf<ThemeSlot?>(null) }

    // The draft's own scheme, so every control below previews the theme being built.
    MaterialTheme(
        colorScheme = schemeFor(draft),
        typography = MaterialTheme.typography,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Make your own") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { vm.save(); onSaved() }) { Text("Save") }
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
                SamplePanel(draft)
                LookAlikeWarning(draft)

                Spacer(Modifier.height(16.dp))

                ThemeSlot.entries.forEach { slot ->
                    ColorRow(
                        slot = slot,
                        color = slot.read(draft),
                        expanded = openSlot == slot,
                        resetToken = vm.resetToken,
                        onToggle = { openSlot = if (openSlot == slot) null else slot },
                        onChange = { vm.set(slot, it) },
                        onReset = { vm.resetSlot(slot) },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.resetAll(); openSlot = null }) {
                    Text("Reset all to ${vm.draftBase.name}")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * The four event chips as the history list draws them, on the draft's own ground.
 *
 * A row of bare swatches would not answer the question that matters: whether the glyph on top of
 * each chip still reads. These are the real chips, with the real derived glyph colour.
 */
@Composable
private fun SamplePanel(draft: Palette) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "2h 10m ago",
                style = MaterialTheme.typography.headlineSmall,
                color = draft.foreground,
            )
            Text(
                "07:42 - 07:58",
                style = MaterialTheme.typography.bodyMedium,
                color = draft.dimForeground,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(draft.left) {
                    Text(
                        "L",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = onColorFor(draft.left, draft),
                    )
                }
                Chip(draft.right) {
                    Text(
                        "R",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = onColorFor(draft.right, draft),
                    )
                }
                Chip(draft.bottle) { BottleGlyph(onColorFor(draft.bottle, draft)) }
                Chip(draft.nap) { MoonGlyph(onColorFor(draft.nap, draft)) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .width(56.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(draft.stop),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "STOP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = onColorFor(draft.stop, draft),
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * A warning, never a refusal. Telling a nap row from a side row at 3am is what these colours are
 * for, so the editor says when two of them have converged. It still lets the parent keep it.
 */
@Composable
private fun LookAlikeWarning(draft: Palette) {
    val eventSlots = listOf(ThemeSlot.LEFT, ThemeSlot.RIGHT, ThemeSlot.BOTTLE, ThemeSlot.NAP)
    val clashes = buildList {
        for (first in eventSlots.indices) {
            for (second in first + 1 until eventSlots.size) {
                val one = eventSlots[first]
                val other = eventSlots[second]
                if (looksAlike(one.read(draft), other.read(draft))) add("${one.label} and ${other.label}")
            }
        }
    }
    if (clashes.isEmpty()) return

    Spacer(Modifier.height(10.dp))
    Text(
        "${clashes.joinToString(", ")} look alike",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/** One colour: a swatch, its name, its hex, and three sliders once it is opened. */
@Composable
private fun ColorRow(
    slot: ThemeSlot,
    color: Color,
    expanded: Boolean,
    resetToken: Int,
    onToggle: () -> Unit,
    onChange: (Color) -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(slot.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        slot.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    color.toHex(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    ColorSliders(
                        initial = color,
                        onChange = onChange,
                        resetToken = resetToken,
                    )
                    TextButton(onClick = onReset) { Text("Reset ${slot.label.lowercase()}") }
                }
            }
        }
    }
}
