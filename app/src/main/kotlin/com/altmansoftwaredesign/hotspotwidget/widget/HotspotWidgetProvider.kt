package com.altmansoftwaredesign.hotspotwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.ui.HotspotSettingsActivity

/** Minimal hotspot card with a custom, state-aware slider. */
class HotspotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateWidgets(
            context: Context,
            manager: AppWidgetManager = AppWidgetManager.getInstance(context),
            ids: IntArray = manager.getAppWidgetIds(
                ComponentName(context, HotspotWidgetProvider::class.java)
            )
        ) {
            ids.forEach { id -> updateWidget(context, manager, id) }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val hotspotState = HotspotRepository(context).isHotspotEnabled()
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            when (hotspotState) {
                true -> {
                    views.setTextViewText(R.id.hotspot_state, context.getString(R.string.hotspot_on))
                    views.setTextColor(R.id.hotspot_state, context.getColor(R.color.widget_state_on))
                    views.setViewVisibility(R.id.switch_track_on, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.switch_track_off, android.view.View.GONE)
                    views.setViewVisibility(R.id.switch_thumb_on, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.switch_thumb_off, android.view.View.GONE)
                    views.setContentDescription(
                        R.id.widget_root,
                        context.getString(R.string.hotspot_accessibility_on)
                    )
                }
                false -> {
                    views.setTextViewText(R.id.hotspot_state, context.getString(R.string.hotspot_off))
                    views.setTextColor(R.id.hotspot_state, context.getColor(R.color.widget_state_off))
                    views.setViewVisibility(R.id.switch_track_on, android.view.View.GONE)
                    views.setViewVisibility(R.id.switch_track_off, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.switch_thumb_on, android.view.View.GONE)
                    views.setViewVisibility(R.id.switch_thumb_off, android.view.View.VISIBLE)
                    views.setContentDescription(
                        R.id.widget_root,
                        context.getString(R.string.hotspot_accessibility_off)
                    )
                }
                null -> {
                    views.setTextViewText(R.id.hotspot_state, context.getString(R.string.hotspot_state_unknown))
                    views.setTextColor(R.id.hotspot_state, context.getColor(R.color.widget_state_unknown))
                    views.setViewVisibility(R.id.switch_track_on, android.view.View.GONE)
                    views.setViewVisibility(R.id.switch_track_off, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.switch_thumb_on, android.view.View.GONE)
                    views.setViewVisibility(R.id.switch_thumb_off, android.view.View.VISIBLE)
                    views.setContentDescription(
                        R.id.widget_root,
                        context.getString(R.string.hotspot_accessibility_unknown)
                    )
                }
            }

            // The whole card is one large hit target. It opens the system-owned
            // hotspot page; it does not pretend this third-party widget can toggle
            // Samsung's privileged switch itself.
            val intent = Intent(context, HotspotSettingsActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
