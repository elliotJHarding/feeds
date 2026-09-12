package com.harding.feeds.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.ui.components.EventFilterPill
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home: the entry surface owns the screen; history lives in a bottom sheet peeking from
 * below, so one-thumb entry stays primary and the list is a flick away. An upward swipe
 * anywhere on the entry surface opens the sheet - not just a drag on the tray itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel, onOpenCharts: () -> Unit, onOpenTheme: () -> Unit) {
    val now by rememberNow()
    val baby by vm.baby.collectAsStateWithLifecycle()
    val activeEvent by vm.activeEvent.collectAsStateWithLifecycle()
    val latestEnded by vm.latestEndedFeed.collectAsStateWithLifecycle()
    val latestEndedNap by vm.latestEndedNap.collectAsStateWithLifecycle()
    val selectedSide by vm.selectedSide.collectAsStateWithLifecycle()
    val entryMode by vm.entryMode.collectAsStateWithLifecycle()
    val bottleAmount by vm.bottleAmount.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val historyFilter by vm.historyFilter.collectAsStateWithLifecycle()
    val historyMode by vm.historyMode.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var showInvite by remember { mutableStateOf(false) }

    val visibleDays = remember(history, historyFilter) { history.filtered(historyFilter) }

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetSwipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = PeekHeight,
        sheetContent = {
            // The filter floats over the list rather than taking a band of its own: the sheet
            // peek is only 160dp, and a full-width bar spent a quarter of it on a control that
            // is used occasionally. Floating keeps every pixel of the peek showing history,
            // and puts the pill in the thumb arc when the sheet is expanded.
            Box(Modifier.fillMaxSize()) {
                when (historyMode) {
                    HistoryMode.LIST -> HistoryList(
                        days = visibleDays,
                        filter = historyFilter,
                        onFeedTap = { editing = EditTarget.Feed(it) },
                        onNapTap = { editing = EditTarget.Nap(it) },
                    )

                    HistoryMode.BLOCKS -> HistoryBlocks(
                        days = visibleDays,
                        filter = historyFilter,
                        // Minute granularity, not the ticking second: a running block only needs
                        // to grow once a minute, and a primitive lets Compose skip the rest.
                        nowEpochMinute = now.epochSecond / 60,
                        onSessionTap = { session ->
                            editing = session.feeds.singleOrNull()?.let(EditTarget::Feed)
                                ?: EditTarget.Session(session)
                        },
                        onNapTap = { editing = EditTarget.Nap(it) },
                    )
                }
                EventFilterPill(
                    selected = historyFilter,
                    onSelect = vm::selectHistoryFilter,
                    // Position and inset belong to this surface, not to the control.
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = 14.dp),
                )
                // The corner, clear of the centred filter - HistoryModePill has the widths.
                HistoryModePill(
                    selected = historyMode,
                    onSelect = vm::selectHistoryMode,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(end = 14.dp, bottom = 14.dp),
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Vertical axis only, so it coexists with the entry surface's horizontal
                // side-swipe and the ruler's scrub via drag-axis slop.
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -sheetSwipeThresholdPx) {
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            }
                        },
                    ) { _, dragAmount -> total += dragAmount }
                },
        ) {
            EntrySurface(
                now = now,
                activeEvent = activeEvent,
                latestEndedFeed = latestEnded,
                latestEndedNap = latestEndedNap,
                selectedSide = selectedSide,
                canStart = baby != null,
                mode = entryMode,
                bottleAmountMl = bottleAmount,
                onStart = vm::startFeed,
                onStartNap = vm::startNap,
                onFinish = vm::finishActive,
                onSelectSide = vm::selectSide,
                onSelectMode = vm::selectMode,
                onBottleAmountChange = vm::setBottleAmount,
                onLogBottle = vm::logBottle,
                onAdjustActiveStart = vm::adjustActiveStart,
            )
            TopBar(
                onOpenCharts = onOpenCharts,
                onOpenTheme = onOpenTheme,
                onInvite = {
                    showInvite = true
                    vm.loadInviteCode()
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    when (val target = editing) {
        is EditTarget.Feed -> FeedEditSheet(
            feed = target.feed,
            onSave = { side, start, end, amountMl ->
                vm.saveFeed(target.feed, side, start, end, amountMl)
                editing = null
            },
            onDelete = {
                vm.deleteFeed(target.feed.id)
                editing = null
            },
            onDismiss = { editing = null },
        )

        is EditTarget.Nap -> NapEditSheet(
            nap = target.nap,
            onSave = { start, end ->
                vm.saveNap(target.nap, start, end)
                editing = null
            },
            onDelete = {
                vm.deleteNap(target.nap.id)
                editing = null
            },
            onDismiss = { editing = null },
        )

        is EditTarget.Session -> SessionSheet(
            session = target.session,
            onFeedTap = { editing = EditTarget.Feed(it) },
            onDismiss = { editing = null },
        )

        null -> Unit
    }

    if (showInvite) {
        InviteDialog(
            state = vm.inviteCode,
            onRegenerate = vm::regenerateInviteCode,
            onDismiss = { showInvite = false },
        )
    }
}

/** One sheet open at a time, whichever kind of record was tapped. */
private sealed interface EditTarget {
    data class Feed(val feed: FeedEntity) : EditTarget
    data class Nap(val nap: NapEntity) : EditTarget

    /** A tapped block holding more than one feed, so which feed to edit is still open. */
    data class Session(val session: FeedSession) : EditTarget
}

/**
 * The feeds inside one block, when the block holds more than one.
 *
 * Blocks mode draws a session's feeds at their true minutes, and inside a session those are
 * minutes apart - at 0.5dp a minute there is no per-feed tap target left. So the block opens the
 * session the way the compact list already draws it: the same card, the same rows, the same
 * pauses. A row opens its feed. Three quarters of recorded sessions hold one feed and go straight
 * to the edit sheet without passing through here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSheet(
    session: FeedSession,
    onFeedTap: (FeedEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Which feed?",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        SessionCard(session, onFeedTap)
        Spacer(Modifier.height(28.dp))
    }
}

/** Brand on the left, actions on the right - a real top bar, floating over the entry surface. */
@Composable
private fun TopBar(
    onOpenCharts: () -> Unit,
    onOpenTheme: () -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            // Clear the status bar / display cutout - the app draws edge-to-edge on API 35+.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Feeds",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionIcon(onClick = onOpenCharts, description = "Charts") { ChartGlyph(it) }
            ActionIcon(onClick = onOpenTheme, description = "Theme") { ThemeGlyph(it) }
            ActionIcon(onClick = onInvite, description = "Invite partner") { InviteGlyph(it) }
        }
    }
}

/** A bordered circular icon button matching the warm surface treatment. */
@Composable
private fun ActionIcon(
    onClick: () -> Unit,
    description: String,
    glyph: @Composable (Color) -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(44.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.semantics { contentDescription = description }) { glyph(tint) }
        }
    }
}

/** Three rising bars - trends/charts, drawn to match the mockup rather than a stock glyph. */
@Composable
private fun ChartGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val barW = size.width * 0.2f
        val gap = (size.width - 3 * barW) / 2f
        val r = CornerRadius(barW / 2f, barW / 2f)
        listOf(0.45f, 0.9f, 0.65f).forEachIndexed { i, frac ->
            val h = size.height * frac
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = r,
            )
        }
    }
}

/**
 * A circle with one half filled - appearance. The glyph takes a single tint like its siblings,
 * so it cannot show the four accents; a split circle says "how this looks" in one colour.
 */
@Composable
private fun ThemeGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val radius = size.minDimension * 0.42f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, radius, centre, style = Stroke(width = size.height * 0.11f))
        drawArc(
            color = color,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
        )
    }
}

/** A person with a plus - invite a partner. */
@Composable
private fun InviteGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val sw = h * 0.11f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(color, radius = h * 0.16f, center = Offset(w * 0.36f, h * 0.26f), style = stroke)
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.1f, h * 0.46f),
            size = Size(w * 0.52f, h * 0.62f),
            style = stroke,
        )
        drawLine(color, Offset(w * 0.83f, h * 0.28f), Offset(w * 0.83f, h * 0.56f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.69f, h * 0.42f), Offset(w * 0.97f, h * 0.42f), sw, StrokeCap.Round)
    }
}

private val PeekHeight = 160.dp

@Composable
private fun InviteDialog(
    state: HomeViewModel.InviteCodeState,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite partner") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    is HomeViewModel.InviteCodeState.Loading -> CircularProgressIndicator()
                    is HomeViewModel.InviteCodeState.Failed ->
                        Text("Couldn't fetch a code - check your connection and try again.")
                    is HomeViewModel.InviteCodeState.Loaded -> {
                        Text(
                            "Your partner signs in with Google and enters this code to join.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            state.code,
                            style = MaterialTheme.typography.headlineLarge
                                .copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(state.code))
                            }) { Text("Copy") }
                            OutlinedButton(onClick = { context.shareInviteCode(state.code) }) {
                                Text("Share")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (state is HomeViewModel.InviteCodeState.Loaded) {
                TextButton(onClick = onRegenerate) { Text("Regenerate") }
            }
        },
    )
}

/** Fires the system share sheet so the code can go via any messaging app. */
fun Context.shareInviteCode(code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Join our Feeds group: sign in with Google and enter code $code")
    }
    startActivity(Intent.createChooser(send, "Share invite code"))
}

/** One ticking clock drives the live time and the since-last header. */
@Composable
fun rememberNow(periodMillis: Long = 1_000L): State<Instant> {
    val now = remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.value = Instant.now()
            delay(periodMillis)
        }
    }
    return now
}
