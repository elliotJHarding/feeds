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
import com.harding.feeds.data.local.entity.NapEntity
import com.harding.feeds.ui.components.DateTimeRow
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.gapBetween
import java.time.Duration
import java.time.Instant

/**
 * Full edit of any nap, including retrospective completed ones - plus delete. Its own sheet
 * rather than a branch inside [FeedEditSheet]: a bottle can share that sheet because a bottle
 * genuinely is a feed - same table, same entity, same save callback - but a nap is a different
 * entity with a different write path, so folding it in would need a discriminated union at both
 * the parameter and the callback, carrying a side and an amount a nap never has.
 *
 * No overlap checking against feeds. The one-at-a-time rule governs live starts, not
 * retrospective correction, and refusing to let a parent fix history would be the wrong trade.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NapEditSheet(
    nap: NapEntity,
    onSave: (startTime: Instant, endTime: Instant?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var startTime by remember { mutableStateOf(nap.startTime) }
    var endTime by remember { mutableStateOf(nap.endTime) }

    val endsBeforeStart = endTime?.isBefore(startTime) == true

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Edit nap", style = MaterialTheme.typography.titleLarge)
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
                        "Woke",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(
                        "Napping",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { endTime = maxOf(Instant.now(), startTime) }) {
                        Text("Wake now")
                    }
                }
            } else {
                DateTimeRow(
                    label = "Woke",
                    time = end,
                    onTimeChange = { endTime = it },
                )
            }
            Spacer(Modifier.height(16.dp))

            // Bound to a local: gapBetween takes a non-null Instant, and endTime is a delegated
            // property that Kotlin will not smart-cast.
            val slept = endTime?.let { gapBetween(startTime, it) }
            Text(
                text = when {
                    endsBeforeStart -> "Woke before the nap started"
                    slept != null -> "Slept ${formatHoursMinutes(slept)}"
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
                    onClick = { onSave(startTime, endTime) },
                    enabled = !endsBeforeStart,
                ) { Text("Save") }
            }
        }
    }
}
