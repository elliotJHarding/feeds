package com.harding.feeds.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Re-renders the widget so its relative texts ("2h 40m since last") do not drift too far
 * while nothing is being written. 15 minutes is WorkManager's floor - between runs the
 * texts are up to that stale, which the h/m granularity mostly hides. Writes and syncs
 * trigger immediate updates separately ([QuickEntryNotifier]).
 */
class WidgetRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        QuickEntryNotifier(applicationContext).refreshNow()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "feeds-widget-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
