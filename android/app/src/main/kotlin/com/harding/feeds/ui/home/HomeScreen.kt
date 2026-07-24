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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
fun HomeScreen(vm: HomeViewModel, onOpenCharts: () -> Unit) {
    val now by rememberNow()
    val baby by vm.baby.collectAsStateWithLifecycle()
    val activeFeed by vm.activeFeed.collectAsStateWithLifecycle()
    val latestEnded by vm.latestEndedFeed.collectAsStateWithLifecycle()
    val selectedSide by vm.selectedSide.collectAsStateWithLifecycle()
    val entryMode by vm.entryMode.collectAsStateWithLifecycle()
    val bottleAmount by vm.bottleAmount.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    var editingFeed by remember { mutableStateOf<FeedEntity?>(null) }
    var showInvite by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetSwipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = PeekHeight,
        sheetContent = {
            HistoryList(
                days = history,
                onFeedTap = { editingFeed = it },
            )
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
                activeFeed = activeFeed,
                latestEndedFeed = latestEnded,
                selectedSide = selectedSide,
                canStart = baby != null,
                mode = entryMode,
                bottleAmountMl = bottleAmount,
                onStart = vm::startFeed,
                onFinish = vm::finishFeed,
                onSelectSide = vm::selectSide,
                onSelectMode = vm::selectMode,
                onBottleAmountChange = vm::setBottleAmount,
                onLogBottle = vm::logBottle,
                onAdjustActiveStart = vm::adjustActiveStart,
            )
            TopBar(
                onOpenCharts = onOpenCharts,
                onInvite = {
                    showInvite = true
                    vm.loadInviteCode()
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    editingFeed?.let { feed ->
        FeedEditSheet(
            feed = feed,
            onSave = { side, start, end, amountMl ->
                vm.saveFeed(feed, side, start, end, amountMl)
                editingFeed = null
            },
            onDelete = {
                vm.deleteFeed(feed.id)
                editingFeed = null
            },
            onDismiss = { editingFeed = null },
        )
    }

    if (showInvite) {
        InviteDialog(
            state = vm.inviteCode,
            onRegenerate = vm::regenerateInviteCode,
            onDismiss = { showInvite = false },
        )
    }
}

/** Brand on the left, actions on the right - a real top bar, floating over the entry surface. */
@Composable
private fun TopBar(onOpenCharts: () -> Unit, onInvite: () -> Unit, modifier: Modifier = Modifier) {
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
