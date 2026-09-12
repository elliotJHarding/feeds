package com.harding.feeds.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harding.feeds.ui.DAY_FORMAT
import com.harding.feeds.ui.toLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One editable date and time: a date chip so an edit can cross days, plus the same
 * scrub-with-haptic-detents [ScrubbableTime] the entry surface uses (tap to type).
 *
 * Lives beside the control it wraps, so both the feed and nap edit sheets share it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeRow(
    label: String,
    time: Instant,
    onTimeChange: (Instant) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(56.dp),
        )
        OutlinedButton(onClick = { showDatePicker = true }) {
            Text(time.toLocalDate(zone).format(DAY_FORMAT))
        }
        Spacer(Modifier.weight(1f))
        ScrubbableTime(
            time = time,
            onTimeChange = onTimeChange,
            style = MaterialTheme.typography.headlineSmall,
            zone = zone,
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = time.toLocalDate(zone)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onTimeChange(applyDate(time, newDate, zone))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Keeps the time of day, moves the date - so a scrubbed time survives a date change. */
private fun applyDate(time: Instant, date: LocalDate, zone: ZoneId): Instant =
    date.atTime(time.atZone(zone).toLocalTime()).atZone(zone).toInstant()
