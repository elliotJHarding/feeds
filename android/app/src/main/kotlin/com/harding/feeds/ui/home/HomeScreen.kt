package com.harding.feeds.ui.home

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harding.feeds.data.local.entity.FeedEntity
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * Home: the entry surface owns the screen; history lives in a bottom sheet peeking from
 * below, so one-thumb entry stays primary and the list is a flick away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel, onOpenCharts: () -> Unit) {
    val now by rememberNow()
    val baby by vm.baby.collectAsStateWithLifecycle()
    val activeFeed by vm.activeFeed.collectAsStateWithLifecycle()
    val latestEnded by vm.latestEndedFeed.collectAsStateWithLifecycle()
    val selectedSide by vm.selectedSide.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    var editingFeed by remember { mutableStateOf<FeedEntity?>(null) }
    var showInvite by remember { mutableStateOf(false) }

    BottomSheetScaffold(
        sheetPeekHeight = 96.dp,
        sheetContent = {
            HistoryList(
                days = history,
                onFeedTap = { editingFeed = it },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            EntrySurface(
                now = now,
                activeFeed = activeFeed,
                latestEndedFeed = latestEnded,
                selectedSide = selectedSide,
                canStart = baby != null,
                onStart = vm::startFeed,
                onFinish = vm::finishFeed,
                onSelectSide = vm::selectSide,
                onAdjustActiveStart = vm::adjustActiveStart,
            )
            HomeActions(
                onOpenCharts = onOpenCharts,
                onInvite = {
                    showInvite = true
                    vm.loadInviteCode()
                },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }

    editingFeed?.let { feed ->
        FeedEditSheet(
            feed = feed,
            onSave = { side, start, end ->
                vm.saveFeed(feed, side, start, end)
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

@Composable
private fun HomeActions(onOpenCharts: () -> Unit, onInvite: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            // Clear the status bar / display cutout - the app draws edge-to-edge on API 35+.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onOpenCharts) { Text("Charts") }
        IconButton(onClick = onInvite) {
            Icon(
                Icons.Filled.Share,
                contentDescription = "Invite partner",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
