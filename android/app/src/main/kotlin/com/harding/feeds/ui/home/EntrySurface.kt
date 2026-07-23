package com.harding.feeds.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.ui.components.SideToggle
import com.harding.feeds.ui.components.TimeRuler
import com.harding.feeds.ui.components.TypeTimeDialog
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.label
import com.harding.feeds.ui.onSideColor
import com.harding.feeds.ui.sideColor
import com.harding.feeds.ui.toLocalTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * The one-thumb entry surface, organised read-high / touch-low: what you read (last feed, the
 * hero time) sits up top for the eyes; what you operate (ruler, side, action) sits in the
 * bottom thumb arc. The feed time is the hero - before a feed it's the *start* time, during one
 * it's the *finish* time, both defaulting to now and tracking that until scrubbed. The
 * ruler owns horizontal drag; a horizontal swipe on the rest of the surface is the side
 * shortcut, so the two gestures no longer share a target. The swipe only works *before* a feed:
 * during one it used to rewrite the active feed's side, and because the detector covers the
 * FINISH button a wobbly press changed the record silently - mid-feed corrections now go
 * through the history sheet instead. The controls sit directly above the
 * history sheet - the scaffold's own content padding reserves the peek, so the button is never
 * covered without pushing the layout up.
 */
@Composable
fun EntrySurface(
    now: Instant,
    activeFeed: FeedEntity?,
    latestEndedFeed: FeedEntity?,
    selectedSide: Side,
    canStart: Boolean,
    onStart: (side: Side, startTime: Instant) -> Unit,
    onFinish: (endTime: Instant) -> Unit,
    onSelectSide: (Side) -> Unit,
    onAdjustActiveStart: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val currentOnSelectSide by rememberUpdatedState(onSelectSide)
    val sideSwipeEnabled by rememberUpdatedState(activeFeed == null)

    // The scrubbed time, or null to track "now" live until the user adjusts it.
    // Reset whenever we switch between start-mode and finish-mode.
    var pending by remember { mutableStateOf<Instant?>(null) }
    LaunchedEffect(activeFeed?.id) { pending = null }

    val defaultTime = now
    val displayedTime = when {
        activeFeed != null -> pending ?: maxOf(defaultTime, activeFeed.startTime)
        else -> pending ?: defaultTime
    }
    val activeSide = activeFeed?.side
    val accent = (activeSide ?: selectedSide).sideColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (sideSwipeEnabled && abs(total) > swipeThresholdPx) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnSelectSide(if (total < 0) Side.l else Side.r)
                        }
                    },
                ) { _, dragAmount -> total += dragAmount }
            }
            .padding(horizontal = 20.dp),
    ) {
        // Clear the floating top bar (brand + action icons) with a gap beneath it.
        Spacer(Modifier.height(76.dp))
        StatusCard(now, activeFeed, latestEndedFeed, onAdjustActiveStart)

        HeroTime(
            label = if (activeFeed != null) "Finish time" else "Start time",
            time = displayedTime,
            onTimeChange = { pending = it },
            caption = if (activeFeed != null) null else "feeding",
            captionAccent = accent,
            captionSide = selectedSide,
            modifier = Modifier.weight(1f),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeRuler(
                time = displayedTime,
                onTimeChange = { pending = it },
                accent = accent,
            )
            if (activeFeed == null) {
                SideToggle(selected = selectedSide, onSelect = onSelectSide)
            }
            Spacer(Modifier.height(2.dp))
            ActionPill(
                active = activeFeed != null,
                side = selectedSide,
                enabled = canStart || activeFeed != null,
                onClick = {
                    if (activeFeed != null) onFinish(displayedTime)
                    else onStart(selectedSide, displayedTime)
                },
            )
        }
    }
}

/**
 * The top card: before a feed it shows how long ago the last one was; during one it shows the
 * live state with started-time and elapsed carrying equal weight to the fact a feed is running.
 */
@Composable
private fun StatusCard(
    now: Instant,
    activeFeed: FeedEntity?,
    latestEndedFeed: FeedEntity?,
    onAdjustActiveStart: (Instant) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (activeFeed != null) InProgressContent(now, activeFeed, onAdjustActiveStart)
            else LastFeedContent(now, latestEndedFeed)
        }
    }
}

@Composable
private fun LastFeedContent(now: Instant, latestEndedFeed: FeedEntity?) {
    CardLabel("Last feed")
    val end = latestEndedFeed?.endTime
    if (latestEndedFeed == null || end == null) {
        Text("No feeds yet", style = MaterialTheme.typography.headlineSmall)
    } else {
        val side = latestEndedFeed.side?.let { " · ${it.label}" } ?: ""
        Text(
            "${formatHoursMinutes(Duration.between(end, now))} ago$side",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatClockTime(latestEndedFeed.startTime)} – ${formatClockTime(end)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InProgressContent(
    now: Instant,
    activeFeed: FeedEntity,
    onAdjustActiveStart: (Instant) -> Unit,
) {
    var editStart by remember { mutableStateOf(false) }
    val side = activeFeed.side
    CardLabel("In progress")
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("Feeding", side?.label ?: "—", side?.sideColor ?: MaterialTheme.colorScheme.onSurface)
        Stat(
            "Started",
            formatClockTime(activeFeed.startTime),
            MaterialTheme.colorScheme.onSurface,
            onClick = { editStart = true },
        )
        Stat(
            "Elapsed",
            formatHoursMinutes(Duration.between(activeFeed.startTime, now)),
            MaterialTheme.colorScheme.onSurface,
        )
    }

    if (editStart) {
        TypeTimeDialog(
            initialText = activeFeed.startTime.toLocalTime().format(com.harding.feeds.ui.TIME_FORMAT),
            onDismiss = { editStart = false },
            onConfirm = { typed ->
                editStart = false
                onAdjustActiveStart(activeFeed.startTime.atZone(ZoneId.systemDefault()).with(typed).toInstant())
            },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier,
    ) {
        CardLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The big serif clock. Reading it is glanceable; tapping opens the type-it-in fallback. */
@Composable
private fun HeroTime(
    label: String,
    time: Instant,
    onTimeChange: (Instant) -> Unit,
    caption: String?,
    captionAccent: Color,
    captionSide: Side,
    modifier: Modifier = Modifier,
) {
    var showType by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        CardLabel(label)
        Spacer(Modifier.height(10.dp))
        Text(
            time.toLocalTime().format(com.harding.feeds.ui.TIME_FORMAT),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { showType = true }
                .padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        if (caption != null) {
            Row {
                Text(
                    caption.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " ${captionSide.label}",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = captionAccent,
                )
            }
        }
    }

    if (showType) {
        TypeTimeDialog(
            initialText = time.toLocalTime().format(com.harding.feeds.ui.TIME_FORMAT),
            onDismiss = { showType = false },
            onConfirm = { typed ->
                showType = false
                onTimeChange(time.atZone(ZoneId.systemDefault()).with(typed).toInstant())
            },
        )
    }
}

/** Full-width, bottom-anchored primary action. Glows the side colour to start, ember to finish. */
@Composable
private fun ActionPill(active: Boolean, side: Side, enabled: Boolean, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.error else side.sideColor
    val content = if (active) MaterialTheme.colorScheme.onError else onSideColor
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = color,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                if (active) "FINISH" else "START",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            if (!active) {
                Text(
                    "  ·  ${side.label}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
