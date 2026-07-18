package com.harding.feeds.sync

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground freshness, per SPEC: sync immediately on resume, then poll every 15 seconds
 * while the app is visible. The UI stage wires [onForeground]/[onBackground] to lifecycle
 * events and can call [refreshNow] for explicit pull-to-refresh.
 */
class ForegroundSync(
    private val syncEngine: SyncEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private var pollJob: Job? = null

    fun onForeground() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                syncEngine.sync()
                delay(POLL_INTERVAL)
            }
        }
    }

    fun onBackground() {
        pollJob?.cancel()
        pollJob = null
    }

    suspend fun refreshNow() {
        syncEngine.sync()
    }

    private companion object {
        val POLL_INTERVAL = 15.seconds
    }
}
