package com.harding.feeds.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.harding.feeds.FeedsApplication
import com.harding.feeds.domain.ActiveEvent
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Start/stop from the pull-down shade, through the same
 * [com.harding.feeds.domain.ActiveEventUseCase] as the in-app button and the widget. State is
 * read from Room on every onStartListening (the shade opening), so the tile needs no push
 * updates to be correct when seen.
 *
 * The tile starts feeds only - it has one action and no type picker - but it stops whatever is
 * running, naps included. The label always names which of the two a tap will do, so the tile
 * can never say "Start feed" and then silently end a nap.
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
            container.activeEvent.toggle()
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
            when (val active = container.activeEvent.activeEvent().first()) {
                is ActiveEvent.Feeding -> {
                    val since = "since ${formatClockTime(active.startTime)}"
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Stop feed"
                    tile.setSubtitleCompat(
                        listOfNotNull(active.feed.side?.label, since).joinToString(" · ")
                    )
                }

                is ActiveEvent.Napping -> {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Stop nap"
                    tile.setSubtitleCompat("since ${formatClockTime(active.startTime)}")
                }

                null -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Start feed"
                    tile.setSubtitleCompat("${container.activeEvent.defaultNextSide().first().label} next")
                }
            }
        }
        tile.updateTile()
    }

    private fun Tile.setSubtitleCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = text
    }
}
