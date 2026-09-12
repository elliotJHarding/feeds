package com.harding.feeds.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The storage round trip. A parent who spends ten minutes on a custom theme must find it again
 * after the phone restarts, and a half-written or hand-edited preferences file must fall back to
 * a working theme rather than crash the app before it draws.
 *
 * `ThemeStore` keeps the Android types and hands the format to `ThemePrefs`, so these run as
 * plain JVM tests over a map.
 */
class ThemePrefsTest {

    private val stored = mutableMapOf<String, String>()
    private val get: (String, String?) -> String? = { key, fallback -> stored[key] ?: fallback }
    private val put: (String, String) -> Unit = { key, value -> stored[key] = value }

    private val handMade = ThemePrefs.custom(
        base = Presets.Moonlight,
        background = Color(0xFF201014),
        accent = Color(0xFFE4C16A),
        left = Color(0xFF6FC2B8),
        right = Color(0xFFE08A5F),
        bottle = Color(0xFFA8CC7A),
        nap = Color(0xFFAC96E2),
    )

    @Test
    fun `a fresh install gets the default theme`() {
        assertEquals(Presets.Candlelight, ThemePrefs.read(get))
    }

    @Test
    fun `a preset survives the round trip`() {
        ThemePrefs.writePreset(Presets.Pine, put)

        assertEquals(Presets.Pine, ThemePrefs.read(get))
    }

    @Test
    fun `a custom theme survives the round trip with all seven colours`() {
        ThemePrefs.writeCustom(handMade, Presets.Moonlight, put)

        assertEquals(handMade, ThemePrefs.read(get))
    }

    /** The stop colour has no picker, so it must ride along in storage or it is lost. */
    @Test
    fun `a custom theme keeps the stop colour of the preset it came from`() {
        ThemePrefs.writeCustom(handMade, Presets.Moonlight, put)

        assertEquals(Presets.Moonlight.stop, ThemePrefs.read(get).stop)
    }

    @Test
    fun `the base preset is recorded so the editor can offer Reset`() {
        ThemePrefs.writeCustom(handMade, Presets.Moonlight, put)

        assertEquals(Presets.Moonlight.id, stored[ThemePrefs.KEY_CUSTOM_BASE])
    }

    /** A preset removed in a later release must not leave the app with no theme at all. */
    @Test
    fun `an unknown preset id falls back to the default`() {
        stored[ThemePrefs.KEY_MODE] = "preset"
        stored[ThemePrefs.KEY_PRESET] = "theme-that-was-dropped"

        assertEquals(Presets.Candlelight, ThemePrefs.read(get))
    }

    @Test
    fun `a half-written custom theme falls back to the default`() {
        ThemePrefs.writeCustom(handMade, Presets.Moonlight, put)
        stored.remove("custom_nap")

        assertNull(ThemePrefs.readCustom(get))
        assertEquals(Presets.Candlelight, ThemePrefs.read(get))
    }

    @Test
    fun `a colour survives hex and back`() {
        listOf(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF14100E), Color(0xFFBFA8D9))
            .forEach { assertEquals(it, hexToColor(it.toHex())) }
    }

    @Test
    fun `malformed hex is rejected rather than guessed at`() {
        listOf("", "#", "#FFF", "#GGGGGG", "not a colour", "#FFFFFFFF")
            .forEach { assertNull(it, hexToColor(it)) }
    }
}
