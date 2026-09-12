package com.harding.feeds.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The palette the app is painting with, right now.
 *
 * This is mutable global state, and that is a deliberate choice over a `CompositionLocal`:
 *
 * - The accent colours in `com.harding.feeds.ui.Formats` are plain properties read from 30 call
 *   sites. A `CompositionLocal` would need every one of them inside a composition.
 * - There are two compositions, not one. The app is one and the Glance widget is the other. A
 *   provider forgotten in either gives the default palette silently, with no error.
 * - A `CompositionLocal` cannot be read inside a `Canvas` draw lambda. This can. The app draws
 *   its glyphs and its charts in `Canvas` blocks.
 * - `FeedsWidget.loadState` reads outside any composition at all.
 *
 * Compose records a read of snapshot state during composition and during draw, so a write here
 * repaints every surface that reads a colour. No call site needs to know this object exists.
 *
 * [ThemeStore] is the only writer, and it sets the saved palette from the `AppContainer`
 * constructor. That ordering matters: the widget can render after a process restart, before any
 * activity exists.
 */
object ThemeState {

    var palette: Palette by mutableStateOf(Presets.default)
        private set

    internal fun adopt(next: Palette) {
        palette = next
    }
}
