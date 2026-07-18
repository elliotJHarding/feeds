package com.harding.feeds.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.harding.feeds.FeedsApplication
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.label
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Start/stop from the pull-down shade, through the same [com.harding.feeds.domain.ToggleFeedUseCase]
 * as the in-app button and the widget. State is read from Room on every onStartListening
 * (the shade opening), so the tile needs no push updates to be correct when seen.
 */
class FeedTileService : TileService() {

    private val container get() = (application as FeedsApplication).container

    /** Alive only between onStartListening and onStopListening - the tile's visible window. */
    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        listeningScope?.cancel()
        listeningScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            .also { it.launch { refreshTile() } }
    }

    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
    }

    override fun onClick() {
        listeningScope?.launch {
            container.toggleFeed.toggle()
            refreshTile()
        }
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val database = container.database

        if (database.babyDao().ids().isEmpty()) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Feeds"
            tile.setSubtitleCompat("Set up in app")
        } else {
            val active = database.feedDao().activeFeed().first()
            if (active != null) {
                val elapsed = formatHoursMinutes(Duration.between(active.startTime, Instant.now()))
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Stop feed"
                tile.setSubtitleCompat(listOfNotNull(active.side?.label, elapsed).joinToString(" · "))
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Start feed"
                tile.setSubtitleCompat("${container.toggleFeed.defaultNextSide().first().label} next")
            }
        }
        tile.updateTile()
    }

    private fun Tile.setSubtitleCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = text
    }
}
