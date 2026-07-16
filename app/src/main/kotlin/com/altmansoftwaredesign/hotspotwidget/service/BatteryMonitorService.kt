package com.altmansoftwaredesign.hotspotwidget.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.repository.BatteryRepository
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.widget.HotspotWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the widget's battery (and hotspot) reading live.
 *
 * ACTION_BATTERY_CHANGED cannot be declared in the manifest on Android 8+, so we
 * register it at runtime from a foreground service. The service's ongoing
 * notification — required for any foreground service — is made useful by showing
 * the current hotspot/battery status. The service is started when the first
 * widget is added and stopped when the last one is removed (see
 * [HotspotWidgetProvider.onEnabled] / onDisabled), so it never outlives the widget.
 */
class BatteryMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverRegistered = false

    // Fires on battery changes (sticky) and on tethering changes from ANY source
    // — Quick Settings, Settings, another app, or Android auto-disabling it.
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Satisfy the 5-second startForeground requirement immediately with a
        // placeholder, then refresh asynchronously with real data.
        startForeground(NOTIFICATION_ID, buildNotification(null, null))
        registerStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) {
            scope.launch {
                performToggle()
                doRefresh()
            }
        } else {
            refresh()
        }
        return START_STICKY
    }

    /** Flips the hotspot based on its current state. */
    private suspend fun performToggle() {
        val repo = HotspotRepository(applicationContext)
        when (repo.getHotspotState().isEnabled) {
            true -> repo.disableHotspot()
            false -> repo.enableHotspot()
            // State unreadable: refuse to guess. Toggling blind could just as
            // easily kill a live hotspot as start one.
            null -> Log.w(TAG, "Hotspot state unknown; refusing to toggle")
        }
    }

    private fun registerStateReceiver() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(ACTION_TETHER_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                this,
                stateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    /** Re-reads battery + hotspot state, then updates the notification and all widgets. */
    private fun refresh() {
        scope.launch { doRefresh() }
    }

    private suspend fun doRefresh() {
        val battery = BatteryRepository(applicationContext).getBatteryState()
        val hotspotOn = HotspotRepository(applicationContext).getHotspotState().isEnabled

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(hotspotOn, battery.percentage)
        )

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(applicationContext, HotspotWidgetProvider::class.java)
        )
        for (id in ids) {
            HotspotWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, id)
        }
    }

    private fun buildNotification(hotspotOn: Boolean?, batteryPct: Int?): Notification {
        val hotspotText = when (hotspotOn) {
            true -> getString(R.string.status_on)
            false -> getString(R.string.status_off)
            null -> getString(R.string.status_unknown)
        }
        val batteryText = batteryPct?.let { "$it%" } ?: getString(R.string.status_unknown)
        val content = getString(R.string.notification_status, hotspotText, batteryText)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver)
            receiverRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BatteryMonitorSvc"
        private const val CHANNEL_ID = "battery_monitor"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "com.altmansoftwaredesign.hotspotwidget.action.TOGGLE"

        // Hidden system broadcast (ConnectivityManager.ACTION_TETHER_STATE_CHANGED).
        // Not a public constant, but the action string is stable in practice; if a
        // future Android stops sending it we fall back to the battery-tick refresh.
        private const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"

        /**
         * Start (or ensure running) the monitor. Must be called from an allowed
         * context — a foreground Activity or BOOT_COMPLETED — never from a
         * BroadcastReceiver/Worker, which Android 12+ blocks from starting an FGS.
         */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, BatteryMonitorService::class.java))
        }

        /** Toggle the hotspot via the (foreground) service, then refresh everything. */
        fun toggle(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_TOGGLE
            }
            context.startForegroundService(intent)
        }

        /** Stop the monitor; removes the notification and unregisters the receiver. */
        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryMonitorService::class.java))
        }
    }
}
