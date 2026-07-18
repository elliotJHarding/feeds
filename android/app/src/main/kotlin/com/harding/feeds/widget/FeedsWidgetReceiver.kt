package com.harding.feeds.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class FeedsWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FeedsWidget()

    /** Periodic refresh runs only while at least one widget is placed. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshWorker.cancel(context)
        super.onDisabled(context)
    }
}
