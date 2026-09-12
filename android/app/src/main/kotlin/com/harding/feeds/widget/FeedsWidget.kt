package com.harding.feeds.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import com.harding.feeds.client.models.FeedType
import com.harding.feeds.client.models.Side
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.domain.ActiveEvent
import com.harding.feeds.ui.formatClockTime
import com.harding.feeds.ui.label
import com.harding.feeds.ui.napColor
import com.harding.feeds.ui.onAccent
import com.harding.feeds.ui.sideColor
import com.harding.feeds.ui.theme.ThemeState
import com.harding.feeds.ui.theme.dimForeground
import com.harding.feeds.ui.theme.foreground
import com.harding.feeds.ui.theme.raisedSurface
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Home-screen quick entry: one glance-value plus one action, in a single 2x1 cell. Renders a
 * snapshot read straight from Room (works offline, no app launch), refreshed by
 * [QuickEntryNotifier] after every write and after each sync. The glance-value is an absolute
 * clock time (last feed's time, or the in-progress start), so it stays correct however long
 * since the last render - no ticking needed. Tapping the value opens the app; tapping the chip
 * starts/stops via the shared use case.
 *
 * Whatever is running owns the single glance-value, naps included, and the chip ends it. At 2
 * cells that leaves no room to *start* a nap - two chips plus the reading need roughly 190dp
 * and a 2-cell widget gives about 110-140dp - so a nap chip appears only when the user stretches
 * the widget wider. The default size is unchanged, so no placed widget moves on upgrade.
 */
class FeedsWidget : GlanceAppWidget() {

    // Two cells renders exactly what shipped before; three or more adds the nap chip.
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 50.dp), DpSize(180.dp, 50.dp))
    )

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
        if (container.database.babyDao().ids().isEmpty()) return WidgetState.NotSetUp

        return when (val active = container.activeEvent.activeEvent().first()) {
            is ActiveEvent.Feeding -> WidgetState.Feeding(active.feed.side, active.startTime)
            is ActiveEvent.Napping -> WidgetState.Napping(active.startTime)
            null -> WidgetState.Idle(
                lastEnded = container.database.feedDao().latestEndedFeed().first(),
                nextSide = container.activeEvent.defaultNextSide().first(),
            )
        }
    }
}

private sealed interface WidgetState {
    data object NotSetUp : WidgetState
    data class Idle(val lastEnded: FeedEntity?, val nextSide: Side) : WidgetState
    data class Feeding(val side: Side?, val startTime: Instant) : WidgetState
    data class Napping(val startTime: Instant) : WidgetState
}

/**
 * One tap on the chip ends whatever is running, or starts a feed when nothing is.
 *
 * Do not rename this class. Glance serialises the ActionCallback's class name into the
 * RemoteViews the launcher holds, so a rename leaves already-placed widgets pointing at a class
 * the new APK does not have, until the host re-renders.
 */
class ToggleFeedAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as FeedsApplication).container
        container.activeEvent.toggle()
        // The write hook re-renders every widget asynchronously; updating this one directly
        // as well makes the tap feedback immediate.
        FeedsWidget().update(context, glanceId)
    }
}

/** Starts a nap from the wider layout, which is the only one with room for a second chip. */
class StartNapAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as FeedsApplication).container
        container.activeEvent.startNap(java.time.Instant.now())
        FeedsWidget().update(context, glanceId)
    }
}

/**
 * The widget's colours, taken from the palette the parent picked in the app.
 *
 * Read inside the composition and never held as a file-level `val`. A top-level `val` is
 * initialised once when the class loads, so it would freeze whichever theme the process happened
 * to see first and never follow a change.
 */
private data class WidgetColors(
    val ground: ColorProvider,
    val text: ColorProvider,
    val dim: ColorProvider,
    val stop: Color,
)

private fun widgetColors(): WidgetColors {
    val palette = ThemeState.palette
    return WidgetColors(
        ground = ColorProvider(palette.raisedSurface),
        text = ColorProvider(palette.foreground),
        dim = ColorProvider(palette.dimForeground),
        stop = palette.stop,
    )
}

@Composable
private fun WidgetContent(state: WidgetState) {
    val colors = widgetColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.ground)
            .cornerRadius(22.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        when (state) {
            is WidgetState.NotSetUp ->
                Info(
                    label = "FEEDS",
                    value = "Open to set up",
                    colors = colors,
                    modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
                )

            is WidgetState.Idle -> {
                Info(
                    label = "LAST FEED",
                    value = lastFeedText(state.lastEnded),
                    colors = colors,
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                )
                Spacer(GlanceModifier.width(10.dp))
                ActionChip("Start ${state.nextSide.label}", state.nextSide.sideColor)
                // Only the wider layout has room for a second chip; at 2 cells a nap starts
                // from the app instead.
                if (LocalSize.current.width >= WideEnoughForNap) {
                    Spacer(GlanceModifier.width(8.dp))
                    ActionChip("Nap", napColor, actionRunCallback<StartNapAction>())
                }
            }

            is WidgetState.Feeding -> {
                val side = state.side?.let { "${it.label} · " } ?: ""
                Info(
                    label = "FEEDING",
                    value = "${side}since ${formatClockTime(state.startTime)}",
                    colors = colors,
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                )
                Spacer(GlanceModifier.width(10.dp))
                ActionChip("Stop", colors.stop)
            }

            is WidgetState.Napping -> {
                Info(
                    label = "NAPPING",
                    value = "since ${formatClockTime(state.startTime)}",
                    colors = colors,
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                )
                Spacer(GlanceModifier.width(10.dp))
                ActionChip("Wake", colors.stop)
            }
        }
    }
}

/** The glance value on the left: a quiet label over a bold reading; taps open the app. */
@Composable
private fun Info(
    label: String,
    value: String,
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        Text(label, style = TextStyle(color = colors.dim, fontSize = 10.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(2.dp))
        Text(
            value,
            maxLines = 1,
            style = TextStyle(color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** An action, tinted the colour it drives - the side to start, nap lavender, or ember to end. */
@Composable
private fun ActionChip(
    text: String,
    color: Color,
    action: Action = actionRunCallback<ToggleFeedAction>(),
) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(onAccent(color)),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = GlanceModifier
            .background(ColorProvider(color))
            .cornerRadius(16.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(action),
    )
}

/** Three cells or more. Below this the reading plus one chip already fills the row. */
private val WideEnoughForNap = 160.dp

private fun lastFeedText(lastEnded: FeedEntity?): String {
    if (lastEnded?.endTime == null) return "No feeds yet"
    val detail = if (lastEnded.type == FeedType.bOTTLE) " · bottle"
    else lastEnded.side?.let { " · ${it.label}" } ?: ""
    // The start time, not the end: feeding intervals are measured start-to-start, so this is
    // the number the every-N-hours rule works from.
    return "${formatClockTime(lastEnded.startTime)}$detail"
}
