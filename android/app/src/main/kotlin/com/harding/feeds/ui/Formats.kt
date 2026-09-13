package com.harding.feeds.ui

import androidx.compose.ui.graphics.Color
import com.harding.feeds.client.models.Side
import com.harding.feeds.ui.theme.ThemeState
import com.harding.feeds.ui.theme.onColorFor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

fun Instant.toLocalTime(zone: ZoneId = ZoneId.systemDefault()): LocalTime = atZone(zone).toLocalTime()

fun Instant.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = atZone(zone).toLocalDate()

fun formatClockTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.toLocalTime(zone).format(TIME_FORMAT)

/**
 * The gap between two times **as the app shows them**: both truncated to the minute first.
 *
 * Records carry seconds - `12:56:42` to `15:23:08` is 2h 26m 26s - and a raw `Duration.between`
 * truncates that to 2h 26m while the two clock readings on screen say 12:56 and 15:23. A parent
 * who subtracts one from the other gets 2h 27m and the app disagrees with itself. Truncating both
 * ends first makes every printed duration the difference of the two printed times.
 *
 * Truncation is on the instant rather than the local time, which is the same boundary: every real
 * zone offset is a whole number of minutes.
 *
 * Not for [com.harding.feeds.ui.home.pauseBefore]. That threshold decides session grouping and is
 * never displayed, so it should keep the full precision it has.
 */
fun gapBetween(from: Instant, to: Instant): Duration = Duration.between(
    from.truncatedTo(ChronoUnit.MINUTES),
    to.truncatedTo(ChronoUnit.MINUTES),
)

/** "2h 40m" for headers and durations; minute granularity, never negative. */
fun formatHoursMinutes(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Ticking elapsed time: "12:34" under an hour, "1:02:34" above. */
fun formatElapsed(duration: Duration): String {
    val s = duration.seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DAY_FORMAT)
}

/** Wire values are "L"/"R" - exactly the one-letter labels the UI wants. */
val Side.label: String get() = value

/**
 * The four event accents, taken from the palette the parent picked. Each reads at a glance
 * without parsing a letter: L and R differ, a bottle is neither, and a nap is none of them.
 *
 * These read `ThemeState.palette`, which is snapshot state. Compose records the read during
 * composition and during draw, so every one of these call sites repaints when the theme
 * changes. None of them needs to know that. See the note on
 * [com.harding.feeds.ui.theme.ThemeState] for why this is global state and not a
 * `CompositionLocal`.
 *
 * A palette keeps its four event accents apart in hue, so a nap row never reads as a side. The
 * theme editor measures that and warns when a hand-made theme breaks it.
 */
val Side.sideColor: Color
    get() = if (this == Side.l) ThemeState.palette.left else ThemeState.palette.right

val bottleColor: Color get() = ThemeState.palette.bottle

val napColor: Color get() = ThemeState.palette.nap

/**
 * The text or glyph colour to place on top of an accent.
 *
 * This used to be one fixed near-black, which worked only because every accent the app shipped
 * was light. A parent can now pick a dark accent, and a fixed dark glyph on it would be
 * invisible. [com.harding.feeds.ui.theme.onColorFor] measures the contrast both ways and picks
 * the readable one.
 */
fun onAccent(accent: Color): Color = onColorFor(accent, ThemeState.palette)

fun formatAmount(ml: Int): String = "$ml ml"

/** Accepts "14:32", "1432", "9:05", "905". */
fun parseTypedTime(text: String): LocalTime? {
    val match = Regex("^(\\d{1,2}):?(\\d{2})$").find(text.trim()) ?: return null
    val (h, m) = match.destructured
    return if (h.toInt() in 0..23 && m.toInt() in 0..59) LocalTime.of(h.toInt(), m.toInt()) else null
}
