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
import com.harding.feeds.data.repository.NapRepository
import com.harding.feeds.domain.ActiveEventUseCase
import com.harding.feeds.sync.ForegroundSync
import com.harding.feeds.sync.SyncEngine
import com.harding.feeds.sync.SyncScheduler
import com.harding.feeds.ui.theme.ThemeStore
import com.harding.feeds.widget.QuickEntryNotifier

/**
 * Manual DI: the whole graph fits in one screen, so a container beats Hilt here - no extra
 * plugins or processors, and the Glance widget / QS tile reach it straight off the
 * Application.
 */
class AppContainer(context: Context) {

    val database = FeedsDatabase.build(context)
    val tokenStore = TokenStore(context)

    private val apiFactory = ApiFactory(BuildConfig.API_BASE_URL, tokenStore)
    private val quickEntryNotifier = QuickEntryNotifier(context)

    /**
     * Built here, and eagerly, rather than from an activity. The widget reaches this container
     * straight off the Application, so it can render after a process restart with no activity
     * alive. The constructor loads the saved palette, so the widget is never painted in the
     * default theme by accident.
     *
     * A theme change repaints the widget through the same hook a new feed uses.
     */
    val themeStore = ThemeStore(context, quickEntryNotifier::quickEntryChanged)

    val syncEngine = SyncEngine(
        feedsApi = apiFactory.feedsApi,
        napsApi = apiFactory.napsApi,
        babiesApi = apiFactory.babiesApi,
        feedDao = database.feedDao(),
        napDao = database.napDao(),
        babyDao = database.babyDao(),
        syncCursorDao = database.syncCursorDao(),
        napSyncCursorDao = database.napSyncCursorDao(),
        tokenStore = tokenStore,
        onDataChanged = quickEntryNotifier::quickEntryChanged,
    )

    val syncScheduler = SyncScheduler(context)
    val foregroundSync = ForegroundSync(syncEngine)

    val feedRepository =
        FeedRepository(database.feedDao(), syncScheduler, quickEntryNotifier::quickEntryChanged)
    val napRepository =
        NapRepository(database.napDao(), syncScheduler, quickEntryNotifier::quickEntryChanged)
    val activeEvent = ActiveEventUseCase(feedRepository, napRepository, database.babyDao())
    val babyRepository = BabyRepository(database.babyDao(), apiFactory.babiesApi)
    val groupRepository = GroupRepository(apiFactory.familyGroupApi, database.sessionDao())
    val authRepository = AuthRepository(
        googleSignInClient = GoogleSignInClient(),
        authApi = apiFactory.authenticationApi,
        tokenStore = tokenStore,
        database = database,
    )
}
