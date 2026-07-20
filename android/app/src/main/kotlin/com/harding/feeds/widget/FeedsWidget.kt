package com.harding.feeds.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.harding.feeds.FeedsApplication
import com.harding.feeds.MainActivity
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.label
import com.harding.feeds.ui.sideColor
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Home-screen quick entry, sized to a single 2x1 cell: one glance-value plus one action. Renders
 * a snapshot read straight from Room (works offline, no app launch), refreshed by
 * [QuickEntryNotifier] after every feed write and after each sync. The glance-value is an
 * absolute clock time (last feed's time, or the in-progress start), so it stays correct however
 * long since the last render - no ticking needed. Tapping the value opens the app; tapping the
 * chip starts/stops via the shared use case.
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
            return WidgetState.Feeding(active.side, active.startTime)
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
    data class Feeding(val side: Side?, val startTime: Instant) : WidgetState
}

/** One tap on the chip starts/stops via the same use-case as the in-app button. */
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Ink)
            .cornerRadius(22.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        when (state) {
            is WidgetState.NotSetUp ->
                Info(
                    label = "FEEDS",
                    value = "Open to set up",
                    modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
                )

            is WidgetState.Idle -> {
                Info(
                    label = "LAST FEED",
                    value = lastFeedText(state.lastEnded),
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                )
                Spacer(GlanceModifier.width(10.dp))
                ActionChip("Start ${state.nextSide.label}", state.nextSide.sideColor)
            }

            is WidgetState.Feeding -> {
                val side = state.side?.let { "${it.label} · " } ?: ""
                Info(
                    label = "FEEDING",
                    value = "${side}since ${formatClockTime(state.startTime)}",
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                )
                Spacer(GlanceModifier.width(10.dp))
                ActionChip("Stop", Ember)
            }
        }
    }
}

/** The glance value on the left: a quiet label over a bold reading; taps open the app. */
@Composable
private fun Info(label: String, value: String, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier) {
        Text(label, style = TextStyle(color = Dim, fontSize = 10.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(2.dp))
        Text(
            value,
            maxLines = 1,
            style = TextStyle(color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** The one action, tinted the colour it drives - the side to start, or ember to stop. */
@Composable
private fun ActionChip(text: String, color: Color) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier
            .background(ColorProvider(color))
            .cornerRadius(16.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(actionRunCallback<ToggleFeedAction>()),
    )
}

private val Ink = ColorProvider(Color(0xFF1A1410))
private val TextHi = ColorProvider(Color(0xFFF3E9DD))
private val Dim = ColorProvider(Color(0xFFA89384))
private val OnAccent = ColorProvider(Color(0xFF17110C))
private val Ember = Color(0xFFCF7367)

private fun lastFeedText(lastEnded: FeedEntity?): String {
    val end = lastEnded?.endTime ?: return "No feeds yet"
    val side = lastEnded.side?.let { " · ${it.label}" } ?: ""
    return "${formatClockTime(end)}$side"
}
