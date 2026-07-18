package com.harding.feeds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.harding.feeds.ui.FeedsApp
import com.harding.feeds.ui.theme.FeedsTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as FeedsApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeedsTheme {
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
