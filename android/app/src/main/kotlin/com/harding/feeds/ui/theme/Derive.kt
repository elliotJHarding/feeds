package com.harding.feeds.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Everything the app paints that a [Palette] does not name outright.
 *
 * The editor gives six pickers. Text, hairlines and card grounds are not among them, and that
 * is the point: a parent cannot pick a combination that hides the words. [onColorFor] settles
 * every foreground by measured contrast, not by taste.
 *
 * Every function here is pure and free of the Android platform. The colour maths is written out
 * rather than taken from `android.graphics`, which the unit tests cannot call.
 */

// ---------------------------------------------------------------------------------------------
// Colour maths
// ---------------------------------------------------------------------------------------------

/** Hue in degrees, saturation and value both 0..1. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

fun Color.toHsv(): Hsv {
    val high = maxOf(red, green, blue)
    val low = minOf(red, green, blue)
    val spread = high - low
    val hue = when {
        spread == 0f -> 0f
        high == red -> 60f * (((green - blue) / spread) % 6f)
        high == green -> 60f * (((blue - red) / spread) + 2f)
        else -> 60f * (((red - green) / spread) + 4f)
    }
    return Hsv(
        hue = if (hue < 0f) hue + 360f else hue,
        saturation = if (high == 0f) 0f else spread / high,
        value = high,
    )
}

fun hsvColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val chroma = value.coerceIn(0f, 1f) * saturation.coerceIn(0f, 1f)
    val second = chroma * (1f - abs((h / 60f) % 2f - 1f))
    val lift = value.coerceIn(0f, 1f) - chroma
    val rgb = when {
        h < 60f -> Triple(chroma, second, 0f)
        h < 120f -> Triple(second, chroma, 0f)
        h < 180f -> Triple(0f, chroma, second)
        h < 240f -> Triple(0f, second, chroma)
        h < 300f -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    return Color(rgb.first + lift, rgb.second + lift, rgb.third + lift)
}

/** The WCAG 2.1 relative luminance of a colour. */
fun relativeLuminance(color: Color): Float {
    fun linear(channel: Float): Float =
        if (channel <= 0.03928f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * linear(color.red) +
        0.7152f * linear(color.green) +
        0.0722f * linear(color.blue)
}

/** The WCAG 2.1 contrast ratio, 1.0 for identical colours and 21.0 for black on white. */
fun contrastRatio(a: Color, b: Color): Float {
    val first = relativeLuminance(a)
    val second = relativeLuminance(b)
    return (max(first, second) + 0.05f) / (min(first, second) + 0.05f)
}

/** The shortest distance between two hues, in degrees, 0..180. */
fun hueGap(a: Color, b: Color): Float {
    val raw = abs(a.toHsv().hue - b.toHsv().hue)
    return min(raw, 360f - raw)
}

/** Two accents closer than this in hue are the same colour to a tired eye. */
const val LOOK_ALIKE_HUE_GAP = 35f

/** Unless they also differ this much in luminance, which tells them apart on its own. */
const val LOOK_ALIKE_LUMINANCE_GAP = 0.15f

/**
 * Whether two event accents would read as one on a 26dp chip at 3am.
 *
 * Hue alone is not enough. Two colours can share a hue and still be told apart when one is far
 * lighter than the other, which is how the light presets keep a deep blue and a deep green
 * legible side by side. Both measures must fail before this says yes.
 *
 * The theme editor warns with it, and `PaletteTest` holds every preset to it, so a preset can
 * never break the advice the editor gives.
 */
fun looksAlike(a: Color, b: Color): Boolean =
    hueGap(a, b) < LOOK_ALIKE_HUE_GAP &&
        abs(relativeLuminance(a) - relativeLuminance(b)) < LOOK_ALIKE_LUMINANCE_GAP

// ---------------------------------------------------------------------------------------------
// Derived tones
// ---------------------------------------------------------------------------------------------

/**
 * Steps of HSV value away from the ground and toward the foreground. A negative step moves the
 * other way, for the recessed slot.
 *
 * The numbers are calibrated against the hand-tuned scheme [Presets.Candlelight] replaces, so
 * that an upgrade does not repaint anybody's app. `PaletteTest` measures what that is worth: of
 * the 15 slots the app reads, 7 land exactly and the worst drifts by 5/255.
 */
private const val STEP_LOWEST = -0.016f
private const val STEP_LOW = 0.031f
private const val STEP_CONTAINER = 0.051f
private const val STEP_VARIANT = 0.067f
private const val STEP_HIGH = 0.086f
private const val STEP_HIGHEST = 0.110f
private const val STEP_OUTLINE = 0.149f

/**
 * The two foreground candidates. Both take the ground's hue, so a theme's text keeps the
 * theme's warmth. Neither is pure black or pure white, which would read as a different product.
 */
private const val PALE_SATURATION = 0.09f
private const val PALE_VALUE = 0.95f
private const val INK_SATURATION = 0.30f
private const val INK_VALUE = 0.09f

/** Dim text - captions, units, the second line of a card. */
private const val DIM_SATURATION = 0.21f
private const val DIM_VALUE_ON_DARK = 0.66f
private const val DIM_VALUE_ON_LIGHT = 0.42f

/** How much of the ground a container slot mixes into an accent. */
private const val CONTAINER_GROUND_SHARE = 0.75f

/** A container's own foreground: the accent, lifted toward the pale tone. */
private const val CONTAINER_TEXT_SATURATION = 0.55f
private const val CONTAINER_TEXT_VALUE = 0.95f

val Palette.isDark: Boolean get() = relativeLuminance(background) < 0.5f

/** The pale candidate for this theme: near-white, carrying the ground's hue. */
val Palette.paleTone: Color
    get() = hsvColor(background.toHsv().hue, PALE_SATURATION, PALE_VALUE)

/** The dark candidate for this theme: near-black, carrying the ground's hue. */
val Palette.inkTone: Color
    get() = hsvColor(background.toHsv().hue, INK_SATURATION, INK_VALUE)

/**
 * The foreground to place on [ground]. It picks whichever of the theme's two tones gives the
 * higher contrast ratio, so no choice of accent or background can hide the text on it.
 *
 * This is why a dark custom accent is safe. The old code used one fixed near-black foreground,
 * which worked only because every accent it shipped with was light.
 */
fun onColorFor(ground: Color, palette: Palette): Color {
    val pale = palette.paleTone
    val ink = palette.inkTone
    return if (contrastRatio(ground, ink) >= contrastRatio(ground, pale)) ink else pale
}

/** The theme's main text colour, on the theme's own ground. */
val Palette.foreground: Color get() = onColorFor(background, this)

/** The theme's dim text colour. */
val Palette.dimForeground: Color
    get() = hsvColor(
        background.toHsv().hue,
        DIM_SATURATION,
        if (isDark) DIM_VALUE_ON_DARK else DIM_VALUE_ON_LIGHT,
    )

/**
 * A surface [step] away from the ground. The sign flips on a light theme, so a card is always
 * raised toward the foreground and never off the end of the scale.
 */
fun Palette.surfaceAt(step: Float): Color {
    val hsv = background.toHsv()
    val moved = if (isDark) hsv.value + step else hsv.value - step
    return hsvColor(hsv.hue, hsv.saturation, moved.coerceIn(0f, 1f))
}

/**
 * A card lifted just off the ground. The home-screen widget paints its own panel with this, so
 * the panel reads as a card on the wallpaper rather than a hole in it.
 */
val Palette.raisedSurface: Color get() = surfaceAt(STEP_LOW)

/** An accent mixed down into the ground, for a container slot. */
private fun Palette.containerOf(accentColor: Color): Color {
    val share = CONTAINER_GROUND_SHARE
    return Color(
        red = accentColor.red * (1f - share) + background.red * share,
        green = accentColor.green * (1f - share) + background.green * share,
        blue = accentColor.blue * (1f - share) + background.blue * share,
    )
}

/** An accent lifted to a readable tint, for text on a container slot. */
private fun containerTextOf(accentColor: Color): Color {
    val hsv = accentColor.toHsv()
    return hsvColor(hsv.hue, hsv.saturation * CONTAINER_TEXT_SATURATION, CONTAINER_TEXT_VALUE)
}

// ---------------------------------------------------------------------------------------------
// The Material scheme
// ---------------------------------------------------------------------------------------------

/**
 * The full Material scheme for a palette. The app reads nine of its slots across 76 call sites,
 * so this one function themes almost the whole UI.
 *
 * It starts from the matching Material default, then overrides every slot the app touches. The
 * base matters for the slots it does not - `secondary` and the container family - so a light
 * theme does not inherit dark defaults underneath.
 */
fun schemeFor(palette: Palette): ColorScheme {
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
    val text = palette.foreground
    val dim = palette.dimForeground

    return base.copy(
        primary = palette.accent,
        onPrimary = onColorFor(palette.accent, palette),
        primaryContainer = palette.containerOf(palette.accent),
        onPrimaryContainer = containerTextOf(palette.accent),
        secondary = palette.left,
        onSecondary = onColorFor(palette.left, palette),
        secondaryContainer = palette.containerOf(palette.left),
        onSecondaryContainer = containerTextOf(palette.left),
        background = palette.background,
        onBackground = text,
        surface = palette.background,
        onSurface = text,
        surfaceVariant = palette.surfaceAt(STEP_VARIANT),
        onSurfaceVariant = dim,
        surfaceContainerLowest = palette.surfaceAt(STEP_LOWEST),
        surfaceContainerLow = palette.raisedSurface,
        surfaceContainer = palette.surfaceAt(STEP_CONTAINER),
        surfaceContainerHigh = palette.surfaceAt(STEP_HIGH),
        surfaceContainerHighest = palette.surfaceAt(STEP_HIGHEST),
        outline = palette.surfaceAt(STEP_OUTLINE),
        outlineVariant = palette.surfaceAt(STEP_HIGH),
        error = palette.stop,
        onError = onColorFor(palette.stop, palette),
        errorContainer = palette.containerOf(palette.stop),
        onErrorContainer = containerTextOf(palette.stop),
    )
}
