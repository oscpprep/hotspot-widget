package com.altmansoftwaredesign.hotspotwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.ui.HotspotSettingsActivity
import java.util.concurrent.atomic.AtomicBoolean

/** Minimal hotspot card with a state-aware slider. */
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
        private const val API36_CALLBACK_TIMEOUT_MS = 2500L
        private val mainHandler = Handler(Looper.getMainLooper())

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

            if (Build.VERSION.SDK_INT >= 36 && hotspotStateOverride == null) {
                updateFromApi36Callback(appContext, manager, ids)
                return
            }

            val state = hotspotStateOverride ?: HotspotRepository(appContext).isHotspotEnabled()
            applyStateToWidgets(appContext, manager, ids, state)
        }

        @RequiresApi(36)
        private fun updateFromApi36Callback(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            val completed = AtomicBoolean(false)
            var unregister: (() -> Unit)? = null

            fun finishWithState(state: Boolean) {
                if (!completed.compareAndSet(false, true)) return
                mainHandler.removeCallbacksAndMessages(ids.contentHashCode())
                unregister?.invoke()
                applyStateToWidgets(context, manager, ids, state)
            }

            try {
                unregister = HotspotRepository(context).registerPublicTetheringCallback { state ->
                    finishWithState(state)
                }

                // If the OEM implementation does not send the initial callback,
                // fall back to the older read methods instead of leaving stale UI.
                mainHandler.postAtTime(
                    {
                        if (completed.compareAndSet(false, true)) {
                            unregister?.invoke()
                            val fallback = HotspotRepository(context).isHotspotEnabled()
                            applyStateToWidgets(context, manager, ids, fallback)
                        }
                    },
                    ids.contentHashCode(),
                    android.os.SystemClock.uptimeMillis() + API36_CALLBACK_TIMEOUT_MS
                )
            } catch (e: Exception) {
                Log.w(TAG, "API 36 tethering callback unavailable; using fallback", e)
                if (completed.compareAndSet(false, true)) {
                    val fallback = HotspotRepository(context).isHotspotEnabled()
                    applyStateToWidgets(context, manager, ids, fallback)
                }
            }
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
                Log.w(TAG, "Unable to refresh hotspot widgets", e)
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
