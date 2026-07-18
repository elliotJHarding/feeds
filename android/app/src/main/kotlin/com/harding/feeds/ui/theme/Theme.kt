package com.harding.feeds.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Candlelight on warm ink. The app's one job is one-thumb entry at 3am, so the palette is
 * warm (amber/candle) rather than the cool blue it began as: warm light preserves night
 * vision and disturbs melatonin far less than the ~460nm blue that Night Shift / f.lux exist
 * to remove. Deliberately dark-only (SPEC: "dark theme default") - a nocturnal product commits
 * to one visual world rather than shipping a half-considered light variant.
 *
 * `primary` is the candle amber used for chrome and the default action; the two feed sides get
 * their own hues in [com.harding.feeds.ui.sideColor] (moonlight L / amber R) so a glance tells
 * you the side.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE6A45C),            // candle amber
    onPrimary = Color(0xFF2A1B0C),
    primaryContainer = Color(0xFF47372A),
    onPrimaryContainer = Color(0xFFF2C892),
    secondary = Color(0xFF82AED2),          // moonlight
    onSecondary = Color(0xFF10222F),
    secondaryContainer = Color(0xFF2C3A47),
    onSecondaryContainer = Color(0xFFCBE1F5),
    background = Color(0xFF14100E),          // warm near-black ink
    onBackground = Color(0xFFF3E9DD),
    surface = Color(0xFF14100E),
    onSurface = Color(0xFFF3E9DD),
    surfaceVariant = Color(0xFF251D18),      // raised card
    onSurfaceVariant = Color(0xFFA89384),    // warm grey
    surfaceContainerLowest = Color(0xFF100C0A),
    surfaceContainerLow = Color(0xFF1C1613),
    surfaceContainer = Color(0xFF211A16),
    surfaceContainerHigh = Color(0xFF2A211B),
    surfaceContainerHighest = Color(0xFF302620),
    outline = Color(0xFF3A2E26),             // warm hairline
    outlineVariant = Color(0xFF2A211B),
    error = Color(0xFFCF7367),               // ember - the finish/stop state
    onError = Color(0xFF1B0F0D),
    errorContainer = Color(0xFF47302C),
    onErrorContainer = Color(0xFFF3C7BF),
)

@Composable
fun FeedsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
