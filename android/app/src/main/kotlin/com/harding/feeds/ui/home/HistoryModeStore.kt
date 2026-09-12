package com.harding.feeds.ui.home

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which way the history sheet draws a day.
 *
 * The Feeds / Both / Naps filter deliberately does not persist - it resets to Both, which shows
 * everything, so a reset costs nothing. A mode reset is not free: a parent who prefers blocks
 * would land back on the list at every cold start, several times a day.
 *
 * Plain `SharedPreferences`, and it stays on this device, for the reasons
 * [com.harding.feeds.ui.theme.ThemeStore] gives: a display preference is not family data, the two
 * parents may differ, and nothing here is a secret.
 */
class HistoryModeStore(context: Context) {

    private val prefs = context.getSharedPreferences("feeds_history", Context.MODE_PRIVATE)

    /** Falls back to the compact list, which is what the sheet did before this mode existed. */
    var mode: HistoryMode
        get() = runCatching { HistoryMode.valueOf(prefs.getString(KEY_MODE, "") ?: "") }
            .getOrDefault(HistoryMode.LIST)
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    private companion object {
        const val KEY_MODE = "mode"
    }
}
