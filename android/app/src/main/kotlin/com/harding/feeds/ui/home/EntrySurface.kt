package com.harding.feeds.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
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
import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.domain.ActiveEvent
import com.harding.feeds.ui.components.BottleGlyph
import com.harding.feeds.ui.components.MoonGlyph
import com.harding.feeds.ui.components.ScrubbableAmount
import com.harding.feeds.ui.components.SideToggle
import com.harding.feeds.ui.components.TimeRuler
import com.harding.feeds.ui.components.TypeTimeDialog
import com.harding.feeds.ui.bottleColor
import com.harding.feeds.ui.formatAmount
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.label
import com.harding.feeds.ui.napColor
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
    activeEvent: ActiveEvent?,
    latestEndedFeed: FeedEntity?,
    latestEndedNap: NapEntity?,
    selectedSide: Side,
    canStart: Boolean,
    mode: EntryMode,
    bottleAmountMl: Int?,
    onStart: (side: Side, startTime: Instant) -> Unit,
    onStartNap: (startTime: Instant) -> Unit,
    onFinish: (endTime: Instant) -> Unit,
    onSelectSide: (Side) -> Unit,
    onSelectMode: (EntryMode) -> Unit,
    onBottleAmountChange: (Int?) -> Unit,
    onLogBottle: (time: Instant) -> Unit,
    onAdjustActiveStart: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val currentOnSelectSide by rememberUpdatedState(onSelectSide)
    // The side-swipe shortcut belongs to breast mode; bottle and nap have no side to pick.
    val sideSwipeEnabled by rememberUpdatedState(activeEvent == null && mode == EntryMode.BREAST)

    // The scrubbed time, or null to track "now" live until the user adjusts it.
    // Reset whenever we switch between start-mode, finish-mode, bottle-mode and nap-mode.
    var pending by remember { mutableStateOf<Instant?>(null) }
    LaunchedEffect(activeEvent, mode) { pending = null }

    val defaultTime = now
    val displayedTime = when {
        activeEvent != null -> pending ?: maxOf(defaultTime, activeEvent.startTime)
        else -> pending ?: defaultTime
    }
    val activeFeed = (activeEvent as? ActiveEvent.Feeding)?.feed
    val napRunning = activeEvent is ActiveEvent.Napping
    val bottleMode = activeEvent == null && mode == EntryMode.BOTTLE
    val napMode = activeEvent == null && mode == EntryMode.NAP
    val accent = when {
        napMode || napRunning -> napColor
        bottleMode -> bottleColor
        else -> (activeFeed?.side ?: selectedSide).sideColor
    }

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
        // Clear the floating top bar (brand + action icons) with a gap beneath it. The bar is
        // a 44dp button row plus 8dp padding, so this only needs to clear that.
        Spacer(Modifier.height(60.dp))
        StatusCard(now, activeEvent, latestEndedFeed, latestEndedNap, onAdjustActiveStart)
        if (activeEvent == null) {
            Spacer(Modifier.height(12.dp))
            ModeToggle(mode = mode, onSelect = onSelectMode)
        }

        HeroTime(
            label = when {
                napRunning -> "Wake time"
                activeEvent != null -> "Finish time"
                napMode -> "Nap start"
                bottleMode -> "Bottle time"
                else -> "Start time"
            },
            time = displayedTime,
            onTimeChange = { pending = it },
            caption = when {
                activeEvent != null -> null
                bottleMode || napMode -> "logging"
                else -> "feeding"
            },
            captionAccent = accent,
            captionDetail = when {
                napMode -> "nap"
                bottleMode -> "bottle"
                else -> selectedSide.label
            },
            modifier = Modifier.weight(1f),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeRuler(
                time = displayedTime,
                onTimeChange = { pending = it },
                accent = accent,
            )
            if (activeEvent == null) {
                when {
                    bottleMode -> AmountRow(amountMl = bottleAmountMl, onChange = onBottleAmountChange)
                    // A nap has neither a side nor an amount. The empty slot keeps its height
                    // so the action pill does not jump when the mode changes.
                    napMode -> Spacer(Modifier.height(52.dp))
                    else -> SideToggle(selected = selectedSide, onSelect = onSelectSide)
                }
            }
            Spacer(Modifier.height(2.dp))
            val action = when {
                napRunning -> PillAction.Wake
                activeEvent != null -> PillAction.FinishFeed
                napMode -> PillAction.StartNap
                bottleMode -> PillAction.LogBottle
                else -> PillAction.StartFeed(selectedSide)
            }
            ActionPill(
                action = action,
                enabled = canStart || activeEvent != null,
                onClick = {
                    when (action) {
                        is PillAction.Wake, is PillAction.FinishFeed -> onFinish(displayedTime)
                        is PillAction.StartNap -> onStartNap(displayedTime)
                        is PillAction.LogBottle -> onLogBottle(displayedTime)
                        is PillAction.StartFeed -> onStart(action.side, displayedTime)
                    }
                },
            )
        }
    }
}

/**
 * Breast|Bottle|Nap picker in SideToggle's visual language, but compact - it selects what the
 * surface logs, not a value, so it stays quieter than the side/amount controls below.
 *
 * Nap is a button rather than a gesture because the horizontal axis is already spoken for: the
 * surface owns the side swipe, the ruler consumes its own drags, and the sheet owns the
 * vertical. A long-press would be undiscoverable at 3am.
 */
@Composable
private fun ModeToggle(mode: EntryMode, onSelect: (EntryMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeButton(
            label = "BREAST",
            isSelected = mode == EntryMode.BREAST,
            accent = MaterialTheme.colorScheme.onSurface,
            onClick = { onSelect(EntryMode.BREAST) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            label = "BOTTLE",
            isSelected = mode == EntryMode.BOTTLE,
            accent = bottleColor,
            glyph = { BottleGlyph(it) },
            onClick = { onSelect(EntryMode.BOTTLE) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            label = "NAP",
            isSelected = mode == EntryMode.NAP,
            accent = napColor,
            glyph = { MoonGlyph(it) },
            onClick = { onSelect(EntryMode.NAP) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: (@Composable (Color) -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        contentColor = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isSelected) accent else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.height(36.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (glyph != null) {
                glyph(if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

/** Bottle mode's stand-in for the side toggle: the optional amount, scrub or tap to set. */
@Composable
private fun AmountRow(amountMl: Int?, onChange: (Int?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CardLabel("Amount")
        Spacer(Modifier.width(12.dp))
        ScrubbableAmount(
            amountMl = amountMl,
            onChange = onChange,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/**
 * The top card, always two blocks: whatever is running (or the last feed) above, and the other
 * kind's last completed record below.
 *
 * "How long since the last feed" and "how long since the last nap" are separate questions - due
 * to feed, versus overtired - and a parent needs both answers at once, at 3am, without changing
 * mode. Showing one or the other could not do that.
 *
 * The two blocks stack rather than sitting side by side. The card's inner width is about 280dp
 * on a 360dp screen, so a column would get roughly 134dp, and "2h 10m ago · L" needs about
 * 200dp at headlineMedium. Fitting it into a column would force the feed reading down to
 * titleMedium, gutting the reading SPEC.md calls the prominent one.
 */
@Composable
private fun StatusCard(
    now: Instant,
    activeEvent: ActiveEvent?,
    latestEndedFeed: FeedEntity?,
    latestEndedNap: NapEntity?,
    onAdjustActiveStart: (Instant) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            when (activeEvent) {
                is ActiveEvent.Feeding -> {
                    InProgressContent(
                        label = "In progress",
                        sideStat = activeEvent.feed.side,
                        now = now,
                        startTime = activeEvent.startTime,
                        onAdjustActiveStart = onAdjustActiveStart,
                    )
                    BlockSpacer()
                    LastNapContent(now, latestEndedNap)
                }

                is ActiveEvent.Napping -> {
                    InProgressContent(
                        label = "Napping",
                        sideStat = null,
                        now = now,
                        startTime = activeEvent.startTime,
                        onAdjustActiveStart = onAdjustActiveStart,
                    )
                    BlockSpacer()
                    LastFeedContent(now, latestEndedFeed)
                }

                null -> {
                    LastFeedContent(now, latestEndedFeed)
                    BlockSpacer()
                    LastNapContent(now, latestEndedNap)
                }
            }
        }
    }
}

@Composable
private fun BlockSpacer() = Spacer(Modifier.height(10.dp))

@Composable
private fun LastFeedContent(now: Instant, latestEndedFeed: FeedEntity?) {
    CardLabel("Last feed")
    val end = latestEndedFeed?.endTime
    if (latestEndedFeed == null || end == null) {
        Text("No feeds yet", style = MaterialTheme.typography.headlineSmall)
    } else {
        val isBottle = latestEndedFeed.type == FeedType.bOTTLE
        // The side stays on the "ago" line, welded to the feed reading it belongs to. It is a
        // feed attribute, never a peer of the nap block below.
        val detail = if (isBottle) " · bottle" else latestEndedFeed.side?.let { " · ${it.label}" } ?: ""
        Text(
            // Anchored on the feed's start: intervals are measured start-to-start, so
            // "ago" + the usual every-N-hours rule points straight at the next feed.
            "${formatHoursMinutes(Duration.between(latestEndedFeed.startTime, now))} ago$detail",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            // A bottle is a point event - one time (plus the amount), not a degenerate range.
            if (isBottle) {
                formatClockTime(latestEndedFeed.startTime) +
                    (latestEndedFeed.amountMl?.let { " · ${formatAmount(it)}" } ?: "")
            } else {
                "${formatClockTime(latestEndedFeed.startTime)} – ${formatClockTime(end)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The nap half of the pair. A nap has no side and no amount, so it is simply shorter. */
@Composable
private fun LastNapContent(now: Instant, latestEndedNap: NapEntity?) {
    CardLabel("Last nap")
    val end = latestEndedNap?.endTime
    if (latestEndedNap == null || end == null) {
        Text("No naps yet", style = MaterialTheme.typography.headlineSmall)
    } else {
        Text(
            // Anchored on the nap's END, unlike the feed block above. The useful number here is
            // how long the baby has been awake, which is what an overtired window works from;
            // feeds measure start-to-start because that is how feeding frequency works.
            "${formatHoursMinutes(Duration.between(end, now))} ago",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatClockTime(latestEndedNap.startTime)} – ${formatClockTime(end)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The live block. A feed carries a third stat for the side; a nap has none, so it drops that
 * column rather than printing a dash where a value should be.
 */
@Composable
private fun InProgressContent(
    label: String,
    sideStat: Side?,
    now: Instant,
    startTime: Instant,
    onAdjustActiveStart: (Instant) -> Unit,
) {
    var editStart by remember { mutableStateOf(false) }
    CardLabel(label)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (sideStat != null) Stat("Feeding", sideStat.label, sideStat.sideColor)
        Stat(
            "Started",
            formatClockTime(startTime),
            MaterialTheme.colorScheme.onSurface,
            onClick = { editStart = true },
        )
        Stat(
            "Elapsed",
            formatHoursMinutes(Duration.between(startTime, now)),
            MaterialTheme.colorScheme.onSurface,
        )
    }

    if (editStart) {
        TypeTimeDialog(
            initialText = startTime.toLocalTime().format(com.harding.feeds.ui.TIME_FORMAT),
            onDismiss = { editStart = false },
            onConfirm = { typed ->
                editStart = false
                onAdjustActiveStart(startTime.atZone(ZoneId.systemDefault()).with(typed).toInstant())
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
    captionDetail: String,
    modifier: Modifier = Modifier,
) {
    var showType by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        CardLabel(label)
        Spacer(Modifier.height(6.dp))
        Text(
            time.toLocalTime().format(com.harding.feeds.ui.TIME_FORMAT),
            // displayMedium (45sp), not displayLarge (57sp). The status card now carries two
            // anchors, so the surface had two elements competing to be the hero and the screen
            // read as crowded. The clock is still unmistakably the largest thing; it just stops
            // shouting. Note the size is fixed - the weight(1f) around it sizes the box, not the
            // glyphs, so growing the card above never shrank this on its own.
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { showType = true }
                .padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (caption != null) {
            Row {
                Text(
                    caption.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " ${captionDetail.uppercase()}",
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

/**
 * What the big button will do. A closed set rather than a pair of booleans, so a future event
 * type is a compile error here instead of falling through to "start a feed".
 */
private sealed interface PillAction {
    data class StartFeed(val side: Side) : PillAction
    data object LogBottle : PillAction
    data object StartNap : PillAction
    data object FinishFeed : PillAction
    data object Wake : PillAction
}

/**
 * Full-width, bottom-anchored primary action. Glows the side colour to start a feed, sage for
 * a bottle, lavender for a nap, and ember to end whatever is running - on this surface and on
 * the widget, ember already means "end the running thing".
 */
@Composable
private fun ActionPill(action: PillAction, enabled: Boolean, onClick: () -> Unit) {
    val ending = action is PillAction.FinishFeed || action is PillAction.Wake
    val color = when (action) {
        is PillAction.FinishFeed, is PillAction.Wake -> MaterialTheme.colorScheme.error
        is PillAction.LogBottle -> bottleColor
        is PillAction.StartNap -> napColor
        is PillAction.StartFeed -> action.side.sideColor
    }
    val label = when (action) {
        is PillAction.FinishFeed -> "FINISH"
        // One word, like FINISH, and the hero label above it already reads "Wake time".
        is PillAction.Wake -> "WAKE"
        is PillAction.LogBottle -> "LOG BOTTLE"
        is PillAction.StartNap -> "START NAP"
        is PillAction.StartFeed -> "START"
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = color,
        contentColor = if (ending) MaterialTheme.colorScheme.onError else onSideColor,
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
                label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            if (action is PillAction.StartFeed) {
                Text(
                    "  ·  ${action.side.label}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
