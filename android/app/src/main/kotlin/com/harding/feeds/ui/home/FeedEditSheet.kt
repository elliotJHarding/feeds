package com.harding.feeds.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.ui.components.DateTimeRow
import com.harding.feeds.ui.components.ScrubbableAmount
import com.harding.feeds.ui.components.SideToggle
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.gapBetween
import java.time.Duration
import java.time.Instant

/**
 * Full edit of any feed, including retrospective completed ones - plus delete. Type-aware
 * but never type-converting: a breast feed edits side, start and end date/time (same
 * scrub-with-haptic-detents control as the entry surface, tap to type; times can cross days
 * via the date chips); a bottle is a point event, so it edits one time and the amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedEditSheet(
    feed: FeedEntity,
    onSave: (side: Side?, startTime: Instant, endTime: Instant?, amountMl: Int?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isBottle = feed.type == FeedType.bOTTLE
    var side by remember { mutableStateOf(feed.side) }
    var startTime by remember { mutableStateOf(feed.startTime) }
    var endTime by remember { mutableStateOf(feed.endTime) }
    var amountMl by remember { mutableStateOf(feed.amountMl) }

    val endsBeforeStart = endTime?.isBefore(startTime) == true

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(if (isBottle) "Edit bottle" else "Edit feed", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))

            if (isBottle) {
                DateTimeRow(
                    label = "Time",
                    time = startTime,
                    onTimeChange = { startTime = it },
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Amount",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(80.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    ScrubbableAmount(
                        amountMl = amountMl,
                        onChange = { amountMl = it },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            } else {
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

                // Bound to a local: gapBetween takes a non-null Instant, and endTime is a
                // delegated property that Kotlin will not smart-cast.
                val duration = endTime?.let { gapBetween(startTime, it) }
                Text(
                    text = when {
                        endsBeforeStart -> "End is before start"
                        duration != null -> "Duration ${formatHoursMinutes(duration)}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (endsBeforeStart) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        // A bottle stays a completed point event: the edited time is both ends.
                        if (isBottle) onSave(null, startTime, startTime, amountMl)
                        else onSave(side, startTime, endTime, amountMl)
                    },
                    enabled = isBottle || !endsBeforeStart,
                ) { Text("Save") }
            }
        }
    }
}
