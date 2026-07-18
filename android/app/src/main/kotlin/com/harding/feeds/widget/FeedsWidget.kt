package com.harding.feeds.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.harding.feeds.FeedsApplication
import com.harding.feeds.MainActivity
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.ui.formatHoursMinutes
import com.harding.feeds.ui.label
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Home-screen quick entry. Renders a snapshot read straight from Room (works offline, no
 * app launch); freshness comes from [QuickEntryNotifier] after every feed write and from
 * [WidgetRefreshWorker] periodically, so the "since last" and elapsed texts are as fresh
 * as the last update, not ticking.
 */
class FeedsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent {
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }

    private suspend fun loadState(context: Context): WidgetState {
        val container = (context.applicationContext as FeedsApplication).container
        val feedDao = container.database.feedDao()
        if (container.database.babyDao().ids().isEmpty()) return WidgetState.NotSetUp

        val active = feedDao.activeFeed().first()
        if (active != null) {
            return WidgetState.Feeding(active.side, Duration.between(active.startTime, Instant.now()))
        }
        return WidgetState.Idle(
            lastEnded = feedDao.latestEndedFeed().first(),
            nextSide = container.toggleFeed.defaultNextSide().first(),
        )
    }
}

private sealed interface WidgetState {
    data object NotSetUp : WidgetState
    data class Idle(val lastEnded: FeedEntity?, val nextSide: Side) : WidgetState
    data class Feeding(val side: Side?, val elapsed: Duration) : WidgetState
}

/** One tap on the button starts/stops via the same use-case as the in-app button. */
class ToggleFeedAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as FeedsApplication).container
        container.toggleFeed.toggle()
        // The write hook re-renders every widget asynchronously; updating this one directly
        // as well makes the tap feedback immediate.
        FeedsWidget().update(context, glanceId)
    }
}

@Composable
private fun WidgetContent(state: WidgetState) {
    Column(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when (state) {
            is WidgetState.NotSetUp -> Header("Open Feeds to set up")

            is WidgetState.Idle -> {
                Header(sinceLastText(state.lastEnded))
                Spacer(GlanceModifier.height(8.dp))
                Button(
                    text = "Start ${state.nextSide.label}",
                    onClick = actionRunCallback<ToggleFeedAction>(),
                )
            }

            is WidgetState.Feeding -> {
                val side = state.side?.let { "${it.label} · " } ?: ""
                Header("Feeding $side${formatHoursMinutes(state.elapsed)}")
                Spacer(GlanceModifier.height(8.dp))
                Button(
                    text = "Stop",
                    onClick = actionRunCallback<ToggleFeedAction>(),
                )
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

private fun sinceLastText(lastEnded: FeedEntity?): String {
    val end = lastEnded?.endTime ?: return "No feeds yet"
    val side = lastEnded.side?.let { " (${it.label})" } ?: ""
    return "${formatHoursMinutes(Duration.between(end, Instant.now()))} since last$side"
}
