package com.harding.feeds.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = when {
        syncEngine.sync() -> Result.success()
        runAttemptCount < MAX_ATTEMPTS -> Result.retry()
        else -> Result.failure()
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/** Manual-DI WorkerFactory: hands the app's [SyncEngine] to [SyncWorker]. */
class SyncWorkerFactory(private val syncEngine: () -> SyncEngine) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        SyncWorker::class.qualifiedName -> SyncWorker(appContext, workerParameters, syncEngine())
        else -> null
    }
}
