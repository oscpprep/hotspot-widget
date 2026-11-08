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
import com.altmansoftwaredesign.hotspotwidget.service.HotspotMonitorService
import com.altmansoftwaredesign.hotspotwidget.ui.HotspotSettingsActivity

/** Minimal hotspot card driven by the persistent state monitor. */
class HotspotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        startMonitorSafely(context, "widget update or restore")
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startMonitorSafely(context, "first widget added")
    }

    override fun onDisabled(context: Context) {
        // Stop the persistent observer only after the final widget is removed.
        HotspotMonitorService.stop(context)
        super.onDisabled(context)
    }

    companion object {
        private const val TAG = "HotspotWidget"

        private fun startMonitorSafely(context: Context, reason: String) {
            try {
                HotspotMonitorService.start(context)
                Log.i(TAG, "Started event-driven monitor: $reason")
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start event-driven monitor: $reason", e)
            }
        }

        fun updateWidgets(
            context: Context,
            manager: AppWidgetManager = AppWidgetManager.getInstance(context),
            ids: IntArray = manager.getAppWidgetIds(
                ComponentName(context, HotspotWidgetProvider::class.java)
            ),
            hotspotStateOverride: Boolean? = null
        ) {
            if (ids.isEmpty()) return
            val appContext = context.applicationContext

            // Android 16: the persistent service is the one source of truth.
            // Do not register/unregister a second temporary callback here; the
            // service's initial callback supplies the snapshot and all future
            // callbacks push changes. Until then, display unknown rather than
            // falsely claiming OFF.
            val state = if (hotspotStateOverride != null) {
                hotspotStateOverride
            } else if (android.os.Build.VERSION.SDK_INT >= 36) {
                HotspotMonitorService.currentState()
            } else {
                HotspotRepository(appContext).isHotspotEnabled()
            }

            applyStateToWidgets(appContext, manager, ids, state)
        }

        private fun applyStateToWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            hotspotState: Boolean?
        ) {
            try {
                ids.forEach { id -> updateWidget(context, manager, id, hotspotState) }
                Log.d(TAG, "Updated ${ids.size} widget(s), hotspotState=$hotspotState")
            } catch (e: Exception) {
                Log.w(TAG, "Unable to apply widget state", e)
            }
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
