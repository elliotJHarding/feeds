package com.harding.feeds.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.harding.feeds.di.AppContainer
import com.harding.feeds.ui.theme.Palette
import com.harding.feeds.ui.theme.Presets
import com.harding.feeds.ui.theme.ThemePrefs
import com.harding.feeds.ui.theme.ThemeState

/**
 * The six colours the editor can change, each with the pair of functions that reads it and
 * writes it back.
 *
 * One list drives the whole editor. Without it the editor would repeat the same row six times,
 * and a seventh colour would mean a seventh copy.
 *
 * The stop colour is absent on purpose. It has no picker, and a custom theme carries the stop
 * colour of the preset it started from.
 */
enum class ThemeSlot(
    val label: String,
    val caption: String,
    val read: (Palette) -> Color,
    val write: (Palette, Color) -> Palette,
) {
    BACKGROUND(
        "Background", "The ground every screen sits on",
        { it.background }, { palette, color -> palette.copy(background = color) },
    ),
    ACCENT(
        "Accent", "Chrome, labels and the default action",
        { it.accent }, { palette, color -> palette.copy(accent = color) },
    ),
    LEFT(
        "Left", "The L side", { it.left }, { palette, color -> palette.copy(left = color) },
    ),
    RIGHT(
        "Right", "The R side", { it.right }, { palette, color -> palette.copy(right = color) },
    ),
    BOTTLE(
        "Bottle", "Bottle feeds", { it.bottle }, { palette, color -> palette.copy(bottle = color) },
    ),
    NAP(
        "Nap", "Naps", { it.nap }, { palette, color -> palette.copy(nap = color) },
    ),
}

/**
 * Holds the theme in use and the theme being built.
 *
 * The theme in use lives in `ThemeState`, not here, because the widget and the entry surface
 * read it too. This view model owns only the draft, which needs to survive a rotation while a
 * parent is halfway through a colour.
 */
class ThemeViewModel(container: AppContainer) : ViewModel() {

    private val store = container.themeStore

    /** Snapshot state, so any composable that reads it repaints when the theme changes. */
    val active: Palette get() = ThemeState.palette

    var draft by mutableStateOf(Presets.default)
        private set

    /** The preset the draft started from. It supplies the stop colour and the Reset values. */
    var draftBase by mutableStateOf(Presets.default)
        private set

    /**
     * Bumped whenever the draft changes from outside a slider. The sliders hold their own hue
     * and saturation, so they need telling when a Reset moves the colour under them.
     */
    var resetToken by mutableStateOf(0)
        private set

    init {
        // Seeded here rather than from a LaunchedEffect, so the editor's first frame is already
        // the right theme instead of a flash of the default.
        beginEdit()
    }

    val hasSavedCustom: Boolean get() = store.custom != null

    fun choose(preset: Palette) = store.applyPreset(preset)

    /**
     * Opens the editor on the theme the parent can see. Editing a saved custom theme continues
     * it; editing a preset starts a new custom theme from that preset's own six colours.
     */
    fun beginEdit() {
        val current = store.active
        draftBase = if (current.isCustom) store.customBase else current
        draft = if (current.isCustom) current else current.asCustom()
        resetToken++
    }

    fun set(slot: ThemeSlot, color: Color) {
        draft = slot.write(draft, color)
    }

    fun resetSlot(slot: ThemeSlot) {
        draft = slot.write(draft, slot.read(draftBase))
        resetToken++
    }

    fun resetAll() {
        draft = draftBase.asCustom()
        resetToken++
    }

    fun save() = store.applyCustom(draft, draftBase)

    private fun Palette.asCustom() = ThemePrefs.custom(
        base = this,
        background = background,
        accent = accent,
        left = left,
        right = right,
        bottle = bottle,
        nap = nap,
    )
}
