package com.harding.feeds.ui

import androidx.compose.ui.graphics.Color
import com.harding.feeds.client.models.Side
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

fun Instant.toLocalTime(zone: ZoneId = ZoneId.systemDefault()): LocalTime = atZone(zone).toLocalTime()

fun Instant.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = atZone(zone).toLocalDate()

fun formatClockTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.toLocalTime(zone).format(TIME_FORMAT)

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
 * A fixed colour per side so L/R read at a glance without parsing the letter. Left = moonlight,
 * right = candle amber; both are warm-friendly and hold contrast on the warm-ink ground.
 * [onSideColor] is the text/icon colour to place on top of [sideColor].
 */
val Side.sideColor: Color get() = if (this == Side.l) Color(0xFF82AED2) else Color(0xFFE6A45C)

/** Warm-dark foreground - both side colours are light enough that near-black reads cleanly. */
val onSideColor: Color get() = Color(0xFF17110C)

/**
 * Bottle feeds get their own accent - a soft sage, hue-distinct from both side colours so
 * bottle rows and marks never read as a breast side. Light enough that [onSideColor] holds.
 */
val bottleColor: Color get() = Color(0xFF9CBF8E)

fun formatAmount(ml: Int): String = "$ml ml"

/** Accepts "14:32", "1432", "9:05", "905". */
fun parseTypedTime(text: String): LocalTime? {
    val match = Regex("^(\\d{1,2}):?(\\d{2})$").find(text.trim()) ?: return null
    val (h, m) = match.destructured
    return if (h.toInt() in 0..23 && m.toInt() in 0..59) LocalTime.of(h.toInt(), m.toInt()) else null
}
