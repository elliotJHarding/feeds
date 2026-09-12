package com.harding.feeds

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.harding.feeds.ui.FeedsApp
import com.harding.feeds.ui.theme.FeedsTheme
import com.harding.feeds.ui.theme.ThemeState
import com.harding.feeds.ui.theme.isDark

class MainActivity : ComponentActivity() {

    private val container get() = (application as FeedsApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeedsTheme {
                SystemBarsFollowTheme()
                FeedsApp(container)
            }
        }
    }

    /** SPEC freshness: sync on resume, then the 15s poll while foregrounded. */
    override fun onResume() {
        super.onResume()
        container.foregroundSync.onForeground()
    }

    override fun onPause() {
        container.foregroundSync.onBackground()
        super.onPause()
    }
}

/**
 * Flips the status-bar and navigation-bar icons with the theme.
 *
 * `targetSdk` is 36, so the window always draws edge to edge and the system bars sit over the
 * app's own ground. The manifest theme is `Theme.Material.NoActionBar`, which gives light
 * icons. That was right while the app was dark-only. A parent can now pick a light theme, and
 * white icons on a near-white ground are invisible.
 *
 * The effect is keyed on the theme, so it runs again when the parent picks a new one rather
 * than only at launch.
 */
@Composable
private fun SystemBarsFollowTheme() {
    val view = LocalView.current
    val lightBars = !ThemeState.palette.isDark

    LaunchedEffect(lightBars) {
        if (view.isInEditMode) return@LaunchedEffect
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}
