package com.harding.feeds.ui.home

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.ui.components.ScrubbableTime
import com.harding.feeds.ui.components.SideToggle
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.label
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * The one-thumb entry surface. The feed time is the hero: before a feed you scrub the
 * *start* time, during one you scrub the *finish* time (both default to a minute ago, since
 * you're usually mid-feed by the time you reach the phone). No live stopwatch - the number
 * you see is the time that will be recorded. A horizontal swipe anywhere picks the side.
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

    // The scrubbed time, or null to track "a minute ago" live until the user adjusts it.
    // Reset whenever we switch between start-mode and finish-mode.
    var pending by remember { mutableStateOf<Instant?>(null) }
    LaunchedEffect(activeFeed?.id) { pending = null }

    val defaultTime = now.minus(Duration.ofMinutes(1))
    val displayedTime = when {
        activeFeed != null -> pending ?: maxOf(defaultTime, activeFeed.startTime)
        else -> pending ?: defaultTime
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > swipeThresholdPx) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnSelectSide(if (total < 0) Side.l else Side.r)
                        }
                    },
                ) { _, dragAmount -> total += dragAmount }
            }
            .padding(horizontal = 24.dp),
    ) {
        LastFeedCard(now, latestEndedFeed)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (activeFeed != null) "Finish time" else "Start time",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ScrubbableTime(
                time = displayedTime,
                onTimeChange = { pending = it },
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "swipe the time to adjust · tap to type",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (activeFeed != null) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Started ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ScrubbableTime(
                        time = activeFeed.startTime,
                        onTimeChange = onAdjustActiveStart,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        SideToggle(selected = selectedSide, onSelect = onSelectSide)

        StartStopButton(
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

/** The last completed feed, promoted to a prominent card so "when/what was last" reads at a glance. */
@Composable
private fun LastFeedCard(now: Instant, latestEndedFeed: FeedEntity?) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Last feed",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val end = latestEndedFeed?.endTime
            if (latestEndedFeed == null || end == null) {
                Text("No feeds yet", style = MaterialTheme.typography.headlineSmall)
            } else {
                val side = latestEndedFeed.side?.let { " · ${it.label}" } ?: ""
                Text(
                    "${formatHoursMinutes(Duration.between(end, now))} ago$side",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatClockTime(latestEndedFeed.startTime)} – ${formatClockTime(end)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StartStopButton(active: Boolean, side: Side, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(200.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (active) "FINISH" else "START",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (!active) {
                    Text(
                        text = side.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
