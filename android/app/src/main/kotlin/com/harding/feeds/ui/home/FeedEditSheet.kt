package com.harding.feeds.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.ui.DAY_FORMAT
import com.harding.feeds.ui.components.ScrubbableTime
import com.harding.feeds.ui.components.SideToggle
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.toLocalDate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Full edit of any feed, including retrospective completed ones: side, start and end
 * date/time (same scrub-with-haptic-detents control as the entry surface, tap to type),
 * plus delete. Times can cross days via the date chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedEditSheet(
    feed: FeedEntity,
    onSave: (side: Side?, startTime: Instant, endTime: Instant?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var side by remember { mutableStateOf(feed.side) }
    var startTime by remember { mutableStateOf(feed.startTime) }
    var endTime by remember { mutableStateOf(feed.endTime) }

    val endsBeforeStart = endTime?.isBefore(startTime) == true

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Edit feed", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))

            SideToggle(selected = side, onSelect = { side = it })
            Spacer(Modifier.height(20.dp))

            DateTimeRow(
                label = "Start",
                time = startTime,
                onTimeChange = { startTime = it },
            )
            Spacer(Modifier.height(12.dp))

            val end = endTime
            if (end == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "End",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(
                        "In progress",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { endTime = maxOf(Instant.now(), startTime) }) {
                        Text("End now")
                    }
                }
            } else {
                DateTimeRow(
                    label = "End",
                    time = end,
                    onTimeChange = { endTime = it },
                )
            }
            Spacer(Modifier.height(16.dp))

            Text(
                text = when {
                    endsBeforeStart -> "End is before start"
                    endTime != null -> "Duration ${formatHoursMinutes(Duration.between(startTime, endTime))}"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (endsBeforeStart) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(side, startTime, endTime) },
                    enabled = !endsBeforeStart,
                ) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
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

private fun applyDate(time: Instant, date: LocalDate, zone: ZoneId): Instant =
    date.atTime(time.atZone(zone).toLocalTime()).atZone(zone).toInstant()
