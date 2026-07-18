package com.harding.feeds.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.ui.dayLabel
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.label
import com.harding.feeds.ui.onSideColor
import com.harding.feeds.ui.sideColor
import java.time.Duration
import java.time.LocalDate

/**
 * Day-grouped feed history shown in the sheet behind the entry surface. Deliberately does NOT
 * take the ticking clock: completed feeds render identically every second, so binding the list
 * to `now` recomposed every visible row once a second and made scrolling stutter. The live
 * elapsed time for an in-progress feed lives on the entry surface, not here.
 */
@Composable
fun HistoryList(
    days: List<DayFeeds>,
    onFeedTap: (FeedEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()

    if (days.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                "Feeds you log will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier.fillMaxSize()) {
        days.forEach { day ->
            item(key = "header-${day.date}") { DayHeader(day, today) }
            items(day.feeds, key = { it.id }) { feed ->
                FeedRow(feed, onTap = { onFeedTap(feed) })
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayFeeds, today: LocalDate) {
    // Sum completed feeds only; an in-progress feed contributes once it ends.
    val total = day.feeds.fold(Duration.ZERO) { acc, feed ->
        val end = feed.endTime ?: return@fold acc
        acc + Duration.between(feed.startTime, end).coerceAtLeast(Duration.ZERO)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            dayLabel(day.date, today),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "${day.feeds.size} feeds · ${formatHoursMinutes(total)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedRow(feed: FeedEntity, onTap: () -> Unit) {
    val end = feed.endTime
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        val side = feed.side
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(side?.sideColor ?: MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                side?.label ?: "·",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (side != null) onSideColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${formatClockTime(feed.startTime)} – ${end?.let { formatClockTime(it) } ?: "…"}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (end == null) "in progress"
            else formatHoursMinutes(Duration.between(feed.startTime, end)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (end == null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
