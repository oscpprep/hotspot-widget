package com.altmansoftwaredesign.hotspotwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
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
        private const val TAG = "HotspotWidget"

        /**
         * Read hotspot state off the launcher thread. A non-null override comes
         * from the protected tether-state system broadcast and is more reliable
         * than OEM-dependent interface reflection.
         */
        fun updateWidgets(
            context: Context,
            manager: AppWidgetManager = AppWidgetManager.getInstance(context),
            ids: IntArray = manager.getAppWidgetIds(
                ComponentName(context, HotspotWidgetProvider::class.java)
            ),
            hotspotStateOverride: Boolean? = null
        ) {
            if (ids.isEmpty()) return
            Thread({
                try {
                    val state = hotspotStateOverride
                        ?: HotspotRepository(context.applicationContext).isHotspotEnabled()
                    ids.forEach { id -> updateWidget(context.applicationContext, manager, id, state) }
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to refresh hotspot widgets", e)
                }
            }, "HotspotWidgetRefresh").start()
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            hotspotState: Boolean?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            when (hotspotState) {
                true -> {
                    views.setTextViewText(R.id.hotspot_state, context.getString(R.string.hotspot_on))
                    views.setTextColor(R.id.hotspot_state, context.getColor(R.color.widget_state_on))
                    views.setViewVisibility(R.id.switch_track_on, View.VISIBLE)
                    views.setViewVisibility(R.id.switch_track_off, View.GONE)
                    views.setViewVisibility(R.id.switch_thumb_on, View.VISIBLE)
                    views.setViewVisibility(R.id.switch_thumb_off, View.GONE)
                    views.setContentDescription(R.id.widget_root, context.getString(R.string.hotspot_accessibility_on))
                }
                false -> {
                    views.setTextViewText(R.id.hotspot_state, context.getString(R.string.hotspot_off))
                    views.setTextColor(R.id.hotspot_state, context.getColor(R.color.widget_state_off))
                    views.setViewVisibility(R.id.switch_track_on, View.GONE)
                    views.setViewVisibility(R.id.switch_track_off, View.VISIBLE)
                    views.setViewVisibility(R.id.switch_thumb_on, View.GONE)
                    views.setViewVisibility(R.id.switch_thumb_off, View.VISIBLE)
                    views.setContentDescription(R.id.widget_root, context.getString(R.string.hotspot_accessibility_off))
                }
                null -> {
                    views.setTextViewText(R.id.hotspot_state, context.getString(R.string.hotspot_state_unknown))
                    views.setTextColor(R.id.hotspot_state, context.getColor(R.color.widget_state_unknown))
                    views.setViewVisibility(R.id.switch_track_on, View.GONE)
                    views.setViewVisibility(R.id.switch_track_off, View.VISIBLE)
                    views.setViewVisibility(R.id.switch_thumb_on, View.GONE)
                    views.setViewVisibility(R.id.switch_thumb_off, View.VISIBLE)
                    views.setContentDescription(R.id.widget_root, context.getString(R.string.hotspot_accessibility_unknown))
                }
            }

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
