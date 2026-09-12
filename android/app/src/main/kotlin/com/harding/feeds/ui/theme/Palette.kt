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
 * comment recorded: a nap row must never read as a side. [hueGap] measures it and
 * `PaletteTest` asserts it.
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

    /** Cool blue-grey on deep navy ink. The coolest of the dark set. */
    val Moonlight = Palette(
        id = "moonlight",
        name = "Moonlight",
        background = Color(0xFF0E1116),
        accent = Color(0xFF8FB6DE),
        left = Color(0xFF7FC7D9),
        right = Color(0xFFE0B77A),
        bottle = Color(0xFF9FD1A8),
        nap = Color(0xFFB3A6E0),
        stop = Color(0xFFE08C7A),
    )

    /** Orchid and violet on a near-black plum ground. */
    val Blackcurrant = Palette(
        id = "blackcurrant",
        name = "Blackcurrant",
        background = Color(0xFF141018),
        accent = Color(0xFFD9A8C8),
        left = Color(0xFF8FB8E0),
        right = Color(0xFFE8B57A),
        bottle = Color(0xFF9FD4B0),
        nap = Color(0xFFC2A6E8),
        stop = Color(0xFFE0837E),
    )

    /**
     * The warmest theme, and the best one for a night feed. Every colour sits outside the
     * 180-260 degree band, which is where the ~460nm blue that suppresses melatonin lives.
     */
    val Ember = Palette(
        id = "ember",
        name = "Ember",
        background = Color(0xFF1A0F0B),
        accent = Color(0xFFF2A960),
        left = Color(0xFFE8A0B4),
        right = Color(0xFFF2A960),
        bottle = Color(0xFFB8C48A),
        nap = Color(0xFFC9A3C9),
        stop = Color(0xFFE07B62),
    )

    /** Pale sage on a deep forest ground. */
    val Pine = Palette(
        id = "pine",
        name = "Pine",
        background = Color(0xFF0D1410),
        accent = Color(0xFF9AD1A8),
        left = Color(0xFF88C2DE),
        right = Color(0xFFE8C07A),
        bottle = Color(0xFFBCD98F),
        nap = Color(0xFFB8A8DB),
        stop = Color(0xFFDE8570),
    )

    /**
     * Neutral grey chrome, for a parent who wants no colour character. The accent is almost
     * unsaturated, so the four event colours carry all of the meaning.
     */
    val Slate = Palette(
        id = "slate",
        name = "Slate",
        background = Color(0xFF111417),
        accent = Color(0xFFB9C2C9),
        left = Color(0xFF7FB4D4),
        right = Color(0xFFE0B486),
        bottle = Color(0xFF93C99E),
        nap = Color(0xFFAFA3CE),
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

    /** A faint lilac ground. The softest of the light set. */
    val Nursery = Palette(
        id = "nursery",
        name = "Nursery",
        background = Color(0xFFF6F2F7),
        accent = Color(0xFF6E5178),
        left = Color(0xFF3A6376),
        right = Color(0xFF875A32),
        bottle = Color(0xFF46693F),
        nap = Color(0xFF57489B),
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
