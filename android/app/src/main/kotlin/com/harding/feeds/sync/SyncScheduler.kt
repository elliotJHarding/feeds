package com.harding.feeds.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager wiring for the sync engine. Every local write calls [requestSync] so pending
 * feeds leave the device as soon as there is network; [ensurePeriodicSync] is a safety net
 * that also pushes strays while the app is backgrounded.
 */
class SyncScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    private val requiresNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(requiresNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: a write landing while a sync runs still gets its own run after -
        // REPLACE would cancel the in-flight attempt, KEEP would drop the new write until the
        // next trigger.
        workManager.enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(requiresNetwork)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val SYNC_WORK = "feeds-sync"
        const val PERIODIC_WORK = "feeds-periodic-sync"
    }
}
