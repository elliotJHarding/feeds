package com.harding.feeds.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark-only theme: the app's defining use case is one-thumb entry at 3am (SPEC: "dark theme
 * default"), so v1 ships a single dark palette instead of a light variant.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7FF),
    onPrimary = Color(0xFF06304F),
    primaryContainer = Color(0xFF174A73),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD5E4F7),
    background = Color(0xFF0E1215),
    onBackground = Color(0xFFE0E3E8),
    surface = Color(0xFF0E1215),
    onSurface = Color(0xFFE0E3E8),
    surfaceVariant = Color(0xFF1C2329),
    onSurfaceVariant = Color(0xFF9AA4AE),
    surfaceContainerLow = Color(0xFF151B20),
    surfaceContainer = Color(0xFF181F25),
    surfaceContainerHigh = Color(0xFF1D252C),
    outline = Color(0xFF3A444D),
    outlineVariant = Color(0xFF29323A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun FeedsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
