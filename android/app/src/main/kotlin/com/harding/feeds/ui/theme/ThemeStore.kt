package com.harding.feeds.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import java.util.Locale

/**
 * Reads and writes the chosen theme. Stays on this device: a theme is a display preference, not
 * family data, so the two parents can differ. There is no server field and nothing syncs.
 *
 * It uses plain `SharedPreferences` rather than the `EncryptedSharedPreferences` that
 * `TokenStore` uses. A colour is not a secret, and the encrypted store costs a master-key read
 * on every access.
 *
 * There is one custom slot. The user asked to make their own theme, singular. The slot is
 * editable, so a change to it loses nothing.
 */
class ThemeStore(context: Context, private val onThemeChanged: () -> Unit) {

    private val prefs = context.getSharedPreferences("feeds_theme", Context.MODE_PRIVATE)

    init {
        // Before any activity or widget renders. See the ordering note on ThemeState.
        ThemeState.adopt(ThemePrefs.read(prefs::getString))
    }

    val active: Palette get() = ThemeState.palette

    /** The saved custom theme, or null if the parent has never made one. */
    val custom: Palette? get() = ThemePrefs.readCustom(prefs::getString)

    /** The preset the custom theme was built from. It supplies the stop colour and Reset. */
    val customBase: Palette
        get() = Presets.byId(prefs.getString(ThemePrefs.KEY_CUSTOM_BASE, null)) ?: Presets.default

    fun applyPreset(palette: Palette) {
        prefs.edit { ThemePrefs.writePreset(palette) { key, value -> putString(key, value) } }
        adopt(palette)
    }

    fun applyCustom(palette: Palette, base: Palette) {
        prefs.edit {
            ThemePrefs.writeCustom(palette, base) { key, value -> putString(key, value) }
        }
        adopt(palette)
    }

    private fun adopt(palette: Palette) {
        ThemeState.adopt(palette)
        onThemeChanged()
    }
}

/**
 * The storage format, with no Android types in sight, so the round trip is unit-testable.
 *
 * Every value is a string. Colours are written as `#RRGGBB`, which makes the preferences file
 * readable when something looks wrong on a device.
 */
object ThemePrefs {

    const val KEY_MODE = "mode"
    const val KEY_PRESET = "preset"
    const val KEY_CUSTOM_BASE = "custom_base"

    private const val MODE_PRESET = "preset"
    private const val MODE_CUSTOM = "custom"

    private const val KEY_BACKGROUND = "custom_background"
    private const val KEY_ACCENT = "custom_accent"
    private const val KEY_LEFT = "custom_left"
    private const val KEY_RIGHT = "custom_right"
    private const val KEY_BOTTLE = "custom_bottle"
    private const val KEY_NAP = "custom_nap"
    private const val KEY_STOP = "custom_stop"

    /** Build a custom palette. It takes its stop colour from the preset it was started from. */
    fun custom(
        base: Palette,
        background: Color,
        accent: Color,
        left: Color,
        right: Color,
        bottle: Color,
        nap: Color,
    ) = Palette(
        id = Palette.CUSTOM_ID,
        name = Palette.CUSTOM_NAME,
        background = background,
        accent = accent,
        left = left,
        right = right,
        bottle = bottle,
        nap = nap,
        stop = base.stop,
    )

    fun read(get: (String, String?) -> String?): Palette = when (get(KEY_MODE, null)) {
        MODE_CUSTOM -> readCustom(get) ?: Presets.default
        else -> Presets.byId(get(KEY_PRESET, null)) ?: Presets.default
    }

    fun readCustom(get: (String, String?) -> String?): Palette? {
        val background = colorAt(KEY_BACKGROUND, get) ?: return null
        return Palette(
            id = Palette.CUSTOM_ID,
            name = Palette.CUSTOM_NAME,
            background = background,
            accent = colorAt(KEY_ACCENT, get) ?: return null,
            left = colorAt(KEY_LEFT, get) ?: return null,
            right = colorAt(KEY_RIGHT, get) ?: return null,
            bottle = colorAt(KEY_BOTTLE, get) ?: return null,
            nap = colorAt(KEY_NAP, get) ?: return null,
            stop = colorAt(KEY_STOP, get) ?: return null,
        )
    }

    fun writePreset(palette: Palette, put: (String, String) -> Unit) {
        put(KEY_MODE, MODE_PRESET)
        put(KEY_PRESET, palette.id)
    }

    /** Writes the whole palette, so a read back never has to look the base preset up again. */
    fun writeCustom(palette: Palette, base: Palette, put: (String, String) -> Unit) {
        put(KEY_MODE, MODE_CUSTOM)
        put(KEY_CUSTOM_BASE, base.id)
        put(KEY_BACKGROUND, palette.background.toHex())
        put(KEY_ACCENT, palette.accent.toHex())
        put(KEY_LEFT, palette.left.toHex())
        put(KEY_RIGHT, palette.right.toHex())
        put(KEY_BOTTLE, palette.bottle.toHex())
        put(KEY_NAP, palette.nap.toHex())
        put(KEY_STOP, palette.stop.toHex())
    }

    private fun colorAt(key: String, get: (String, String?) -> String?): Color? =
        get(key, null)?.let(::hexToColor)
}

/** `#RRGGBB`. A palette colour is always opaque, so there is no alpha to carry. */
fun Color.toHex(): String {
    fun channel(value: Float) = (value * 255f + 0.5f).toInt().coerceIn(0, 255)
    return String.format(
        Locale.ROOT, "#%02X%02X%02X", channel(red), channel(green), channel(blue),
    )
}

fun hexToColor(text: String): Color? {
    val digits = text.removePrefix("#")
    if (digits.length != 6) return null
    val value = digits.toLongOrNull(radix = 16) ?: return null
    return Color(
        red = ((value shr 16) and 0xFF).toInt() / 255f,
        green = ((value shr 8) and 0xFF).toInt() / 255f,
        blue = (value and 0xFF).toInt() / 255f,
    )
}
