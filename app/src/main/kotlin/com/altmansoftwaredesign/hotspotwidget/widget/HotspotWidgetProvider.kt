package com.altmansoftwaredesign.hotspotwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.repository.BatteryRepository
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.service.BatteryMonitorService
import com.altmansoftwaredesign.hotspotwidget.ui.AboutActivity
import com.altmansoftwaredesign.hotspotwidget.ui.ConfirmToggleActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HotspotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Just draw the widgets. The live monitor is started from a user tap
        // (ConfirmToggleActivity) or BootReceiver — never from here, because
        // Android 12+ forbids starting a foreground service from a receiver.
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDisabled(context: Context) {
        // Last widget removed: tear the monitor (and its notification) down.
        BatteryMonitorService.stop(context)
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val hotspotRepo = HotspotRepository(context)
                val batteryRepo = BatteryRepository(context)

                val hotspotState = hotspotRepo.getHotspotState()
                val batteryState = batteryRepo.getBatteryState()

                val views = RemoteViews(context.packageName, R.layout.widget_layout)

                // Update hotspot status. A null reading means we genuinely don't
                // know, so show that rather than defaulting to "off".
                val hotspotIconRes = when (hotspotState.isEnabled) {
                    true -> R.drawable.ic_hotspot_on
                    false -> R.drawable.ic_hotspot_off
                    null -> R.drawable.ic_hotspot_unknown
                }
                val hotspotStatusText = when (hotspotState.isEnabled) {
                    true -> R.string.hotspot_on
                    false -> R.string.hotspot_off
                    null -> R.string.hotspot_unknown
                }
                views.setImageViewResource(R.id.hotspot_icon, hotspotIconRes)
                views.setTextViewText(R.id.hotspot_status, context.getString(hotspotStatusText))

                // Update battery status
                val batteryIconRes = when {
                    batteryState.percentage > 60 -> R.drawable.ic_battery_high
                    batteryState.percentage > 20 -> R.drawable.ic_battery_medium
                    else -> R.drawable.ic_battery_low
                }
                views.setImageViewResource(R.id.battery_icon, batteryIconRes)
                views.setTextViewText(
                    R.id.battery_percentage,
                    "${batteryState.percentage}%"
                )

                // Tapping the widget opens the confirmation dialog before toggling.
                val confirmIntent = Intent(context, ConfirmToggleActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val confirmPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    confirmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, confirmPendingIntent)

                // Tapping the battery side instead shows the About dialog. A child
                // click overrides the root's for that region.
                val aboutIntent = Intent(context, AboutActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val aboutPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    aboutIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.battery_section, aboutPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
