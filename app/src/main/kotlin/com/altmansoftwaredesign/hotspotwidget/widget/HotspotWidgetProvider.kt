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

/**
 * A single large switch. The switch is a shortcut into Android/Samsung's own
 * hotspot Settings UI; the app never attempts to change tethering behind the
 * system UI's back.
 */
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

            // Switch widgets are stateful RemoteViews on Android 12+.
            // Unknown is shown as off visually, but its description says unknown.
            views.setCompoundButtonChecked(R.id.hotspot_switch, hotspotState == true)
            views.setContentDescription(
                R.id.hotspot_switch,
                when (hotspotState) {
                    true -> context.getString(R.string.hotspot_accessibility_on)
                    false -> context.getString(R.string.hotspot_accessibility_off)
                    null -> context.getString(R.string.hotspot_accessibility_unknown)
                }
            )

            // The mutable PendingIntent allows RemoteViews to add EXTRA_CHECKED.
            // HotspotSettingsActivity deliberately does not use that value to
            // flip tethering; it opens the system-owned control instead.
            val intent = Intent(context, HotspotSettingsActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnCheckedChangeResponse(
                R.id.hotspot_switch,
                RemoteViews.RemoteResponse.fromPendingIntent(pendingIntent)
            )

            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
