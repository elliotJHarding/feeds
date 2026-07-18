package com.harding.feeds.widget

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import com.harding.feeds.tile.FeedTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pushes fresh state to the OS quick-entry surfaces. Wired as the feed-write hook of
 * [com.harding.feeds.data.repository.FeedRepository] and the post-sync hook of
 * [com.harding.feeds.sync.SyncEngine], so both local writes and feeds arriving from the
 * other phone show up on the widget without waiting for the periodic refresh.
 */
class QuickEntryNotifier(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun feedsChanged() {
        scope.launch { refreshNow() }
        // No-op unless the tile is added and currently listening (shade open); the tile
        // re-reads Room in onStartListening anyway. Guarded because some OS versions throw
        // for tiles the user has not added.
        runCatching {
            TileService.requestListeningState(context, ComponentName(context, FeedTileService::class.java))
        }
    }

    suspend fun refreshNow() = FeedsWidget().updateAll(context)
}
