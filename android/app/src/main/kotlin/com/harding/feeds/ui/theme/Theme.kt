package com.harding.feeds.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app's one theme, built from whichever [Palette] the parent picked.
 *
 * The default is [Presets.Candlelight]: candlelight on warm ink. The app's one job is one-thumb
 * entry at 3am, so the default palette is warm rather than the cool blue it began as. Warm light
 * preserves night vision and disturbs melatonin far less than the ~460nm blue that Night Shift
 * and f.lux exist to remove.
 *
 * The theme is no longer dark-only. A parent picks from nine presets, six dark and three light,
 * or builds their own on the theme settings screen. The nocturnal default stands, but a parent
 * who reads the history in daylight now has an answer.
 *
 * `schemeFor` derives all 30-odd Material slots from the palette's seven colours. The two feed
 * sides get their own hues in `com.harding.feeds.ui.sideColor`, so a glance tells you the side.
 */
@Composable
fun FeedsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = schemeFor(ThemeState.palette),
        typography = AppTypography,
        content = content,
    )
}
