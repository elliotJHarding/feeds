package com.harding.feeds

import android.app.Application
import androidx.work.Configuration
import com.harding.feeds.di.AppContainer
import com.harding.feeds.sync.SyncWorkerFactory

class FeedsApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.syncScheduler.ensurePeriodicSync()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory { container.syncEngine })
            .build()
}
