package com.harding.feeds.di

import android.content.Context
import com.harding.feeds.BuildConfig
import com.harding.feeds.auth.AuthRepository
import com.harding.feeds.auth.GoogleSignInClient
import com.harding.feeds.auth.TokenStore
import com.harding.feeds.data.local.FeedsDatabase
import com.harding.feeds.data.remote.ApiFactory
import com.harding.feeds.data.repository.BabyRepository
import com.harding.feeds.data.repository.FeedRepository
import com.harding.feeds.data.repository.GroupRepository
import com.harding.feeds.domain.ToggleFeedUseCase
import com.harding.feeds.sync.ForegroundSync
import com.harding.feeds.sync.SyncEngine
import com.harding.feeds.sync.SyncScheduler
import com.harding.feeds.widget.QuickEntryNotifier

/**
 * Manual DI: the whole graph fits in one screen, so a container beats Hilt here - no extra
 * plugins or processors, and the Glance widget / QS tile (later stages) reach it straight
 * off the Application.
 */
class AppContainer(context: Context) {

    val database = FeedsDatabase.build(context)
    val tokenStore = TokenStore(context)

    private val apiFactory = ApiFactory(BuildConfig.API_BASE_URL, tokenStore)
    private val quickEntryNotifier = QuickEntryNotifier(context)

    val syncEngine = SyncEngine(
        feedsApi = apiFactory.feedsApi,
        babiesApi = apiFactory.babiesApi,
        feedDao = database.feedDao(),
        babyDao = database.babyDao(),
        syncCursorDao = database.syncCursorDao(),
        tokenStore = tokenStore,
        onFeedsChanged = quickEntryNotifier::feedsChanged,
    )

    val syncScheduler = SyncScheduler(context)
    val foregroundSync = ForegroundSync(syncEngine)

    val feedRepository = FeedRepository(database.feedDao(), syncScheduler, quickEntryNotifier::feedsChanged)
    val toggleFeed = ToggleFeedUseCase(feedRepository, database.babyDao())
    val babyRepository = BabyRepository(database.babyDao(), apiFactory.babiesApi)
    val groupRepository = GroupRepository(apiFactory.familyGroupApi, database.sessionDao())
    val authRepository = AuthRepository(
        googleSignInClient = GoogleSignInClient(),
        authApi = apiFactory.authenticationApi,
        tokenStore = tokenStore,
        database = database,
    )
}
