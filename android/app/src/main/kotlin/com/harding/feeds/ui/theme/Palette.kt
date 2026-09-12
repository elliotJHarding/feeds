package com.harding.feeds.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Every colour a theme picks. Seven values. The app derives everything else from them - see
 * `schemeFor` and `onColorFor` in Derive.kt.
 *
 * A preset and a hand-made theme are the same type on purpose. One shape gives one code path.
 * A preset therefore cannot reach a look the editor cannot, and a defect cannot hide in a path
 * that only presets use.
 *
 * Six of the seven have a picker. [stop] does not. The editor always starts from a preset, so a
 * custom theme carries that preset's stop colour.
 */
data class Palette(
    val id: String,
    val name: String,
    /** The ground. Its luminance decides whether the theme is dark or light. */
    val background: Color,
    /** Chrome and the default action. Material `primary`. */
    val accent: Color,
    /** The L side. */
    val left: Color,
    /** The R side. */
    val right: Color,
    val bottle: Color,
    val nap: Color,
    /** The finish/stop state. Material `error`. No picker. */
    val stop: Color,
) {
    /** The four event accents, in the order the editor lists them. */
    val eventAccents: List<Color> get() = listOf(left, right, bottle, nap)

    val isCustom: Boolean get() = id == CUSTOM_ID

    companion object {
        const val CUSTOM_ID = "custom"
        const val CUSTOM_NAME = "Custom"
    }
}

/**
 * The preset range: six dark themes and three light ones.
 *
 * [Candlelight] is first, is the default, and holds exactly the colours the app shipped before
 * this screen existed. An upgrade therefore changes nobody's app. `PaletteTest` pins that.
 *
 * Within one preset the four event accents stay apart in hue, for the reason the old `napColor`
 * comment recorded: a nap row must never read as a side. `looksAlike` measures it and
 * `PaletteTest` asserts it.
 *
 * Across presets the rule is narrower, and it is deliberately narrow: **L is cool and R is
 * warm**, everywhere. A parent who changes theme keeps that much muscle memory, so the letter
 * never has to be read. Everything else varies - each theme picks its own cool, its own warm,
 * and its own two remaining lanes, so the range is a range of palettes rather than a range of
 * backgrounds. [Ember] is the one exception, and it says why on itself.
 */
object Presets {

    /**
     * The palette the app shipped with. Warm candle amber on warm ink. The whole app is built
     * for one-thumb entry at 3am, and warm light preserves night vision.
     */
    val Candlelight = Palette(
        id = "candlelight",
        name = "Candlelight",
        background = Color(0xFF14100E),
        accent = Color(0xFFE6A45C),
        left = Color(0xFF82AED2),
        right = Color(0xFFE6A45C),
        bottle = Color(0xFF9CBF8E),
        nap = Color(0xFFBFA8D9),
        stop = Color(0xFFCF7367),
    )

    /** Cyan and peach on deep navy ink. The coolest of the dark set. */
    val Moonlight = Palette(
        id = "moonlight",
        name = "Moonlight",
        background = Color(0xFF0E1116),
        accent = Color(0xFF8FB6DE),
        left = Color(0xFF77D1D9),
        right = Color(0xFFE6A17E),
        bottle = Color(0xFF7DD192),
        nap = Color(0xFFAE9DE0),
        stop = Color(0xFFE08C7A),
    )

    /**
     * Indigo and rose on a near-black plum ground. The one theme where R is a pink rather than
     * an amber - still the warm half of the wheel, so L and R stay cool against warm.
     */
    val Blackcurrant = Palette(
        id = "blackcurrant",
        name = "Blackcurrant",
        background = Color(0xFF141018),
        accent = Color(0xFFD9A8C8),
        left = Color(0xFF8292E0),
        right = Color(0xFFE68A99),
        bottle = Color(0xFF76CCB7),
        nap = Color(0xFFDB95E5),
        stop = Color(0xFFE0837E),
    )

    /**
     * The warmest theme, and the best one for a night feed. Every colour sits outside the
     * 180-260 degree band, which is where the ~460nm blue that suppresses melatonin lives.
     *
     * This is the one theme where L is not cool. It cannot be: the cool half of the wheel is
     * exactly the band this palette exists to avoid. L takes a dusty rose instead, and R the
     * amber, so the two still separate.
     */
    val Ember = Palette(
        id = "ember",
        name = "Ember",
        background = Color(0xFF1A0F0B),
        accent = Color(0xFFF09C6C),
        left = Color(0xFFE68EAB),
        right = Color(0xFFF09C6C),
        bottle = Color(0xFF87C787),
        nap = Color(0xFFCE96D6),
        stop = Color(0xFFE07B62),
    )

    /** Cyan and coral on a deep forest ground, with a gold bottle rather than a green one. */
    val Pine = Palette(
        id = "pine",
        name = "Pine",
        background = Color(0xFF0D1410),
        accent = Color(0xFF9AD1A8),
        left = Color(0xFF7AD4DE),
        right = Color(0xFFEB8975),
        bottle = Color(0xFFE0D87B),
        nap = Color(0xFFC29BDE),
        stop = Color(0xFFDE8570),
    )

    /**
     * The quiet one, for a parent who wants no colour character. It varies by saturation rather
     * than by hue: neutral grey chrome, and four accents muted to about 0.3 saturation, so the
     * whole app reads softer without losing which colour means which.
     */
    val Slate = Palette(
        id = "slate",
        name = "Slate",
        background = Color(0xFF111417),
        accent = Color(0xFFB9C2C9),
        left = Color(0xFF8BAAD6),
        right = Color(0xFFE0A992),
        bottle = Color(0xFF8BC7AE),
        nap = Color(0xFFB69FD6),
        stop = Color(0xFFD4837A),
    )

    /**
     * The light twin of [Candlelight]: the same hues on a warm off-white ground.
     *
     * A light theme needs deep accents, not pale ones, and the reason is measurable rather than
     * a matter of taste. An accent is a chip ground with a glyph on it. A mid-tone chip is the
     * worst case: neither the near-white nor the near-black tone reaches 4.5:1 on it, so the
     * glyph is unreadable whichever way the rule turns. `DeriveTest` holds every preset to that
     * bar, and it is the reason these three sets are darker than a light theme first suggests.
     */
    val Daybreak = Palette(
        id = "daybreak",
        name = "Daybreak",
        background = Color(0xFFFAF4EC),
        accent = Color(0xFF8F5420),
        left = Color(0xFF275D80),
        right = Color(0xFF8F5420),
        bottle = Color(0xFF3F6330),
        nap = Color(0xFF5C4489),
        stop = Color(0xFF9B3224),
    )

    /** A faint lilac ground, with deep teal and ochre. The softest of the light set. */
    val Nursery = Palette(
        id = "nursery",
        name = "Nursery",
        background = Color(0xFFF6F2F7),
        accent = Color(0xFF6E5178),
        left = Color(0xFF2F6475),
        right = Color(0xFF805924),
        bottle = Color(0xFF2D6B47),
        nap = Color(0xFF693D80),
        stop = Color(0xFF8E3B34),
    )

    /** Near-white and ink. The most legible of the nine in daylight. */
    val Paper = Palette(
        id = "paper",
        name = "Paper",
        background = Color(0xFFFCFBF7),
        accent = Color(0xFF33333C),
        left = Color(0xFF1F5FA8),
        right = Color(0xFF8F4C11),
        bottle = Color(0xFF2E6B33),
        nap = Color(0xFF5B3A9E),
        stop = Color(0xFFA81F1F),
    )

    /** Candlelight first: it is the default, and the grid reads as "what you have now" first. */
    val all: List<Palette> = listOf(
        Candlelight, Moonlight, Blackcurrant, Ember, Pine, Slate, Daybreak, Nursery, Paper,
    )

    val default: Palette get() = Candlelight

    fun byId(id: String?): Palette? = all.firstOrNull { it.id == id }
}
