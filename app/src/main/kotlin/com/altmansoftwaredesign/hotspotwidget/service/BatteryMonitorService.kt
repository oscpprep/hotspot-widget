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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the widget's battery (and hotspot) reading live.
 *
 * ACTION_BATTERY_CHANGED cannot be declared in the manifest on Android 8+, so we
 * register it at runtime from a foreground service. The service's ongoing
 * notification — required for any foreground service — is made useful by showing
 * the current hotspot/battery status.
 *
 * It starts on the first widget tap or at boot, NOT when the widget is placed:
 * Android 12+ forbids starting a foreground service from a broadcast receiver,
 * which is the only context onUpdate/onEnabled run in. It stops when the last
 * widget is removed ([HotspotWidgetProvider.onDisabled]), so it never outlives
 * the widget.
 */
class BatteryMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverRegistered = false

    private val refreshMutex = Mutex()
    private var lastRendered: Rendered? = null

    // Fires on battery changes (sticky) and on tethering changes from ANY source
    // — Quick Settings, Settings, another app, or Android auto-disabling it.
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TETHER_STATE_CHANGED) {
                // Cheap to log, and the only signal that external-change detection
                // is alive — it is easy to break silently via receiver flags.
                Log.d(TAG, "Tether state changed externally; refreshing")
            }
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
            // TARGET_UNSET should be unreachable — ConfirmToggleActivity always
            // sets it — but we refuse to guess rather than pick a direction.
            when (intent.getIntExtra(EXTRA_TARGET_ENABLED, TARGET_UNSET)) {
                TARGET_ON -> applyHotspot(enable = true)
                TARGET_OFF -> applyHotspot(enable = false)
                else -> Log.w(TAG, "Toggle request with no target state; ignoring")
            }
        } else {
            // An explicit start (widget tap, boot) redraws unconditionally: the
            // widget may have been re-created and be showing the unknown default,
            // in which case matching cached values must not suppress the draw.
            scope.launch { doRefresh(force = true) }
        }
        return START_STICKY
    }

    /**
     * Drives the hotspot to [enable]. Deliberately does NOT re-read the current
     * state and flip it: the user confirmed one specific action against the state
     * the dialog showed them, and if that state changed in between, re-reading
     * would silently do the opposite of what they agreed to.
     */
    private fun applyHotspot(enable: Boolean) {
        scope.launch {
            val repo = HotspotRepository(applicationContext)
            if (enable) repo.enableHotspot() else repo.disableHotspot()
            doRefresh()
        }
    }

    private fun registerStateReceiver() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(ACTION_TETHER_STATE_CHANGED)
            }
            // Must be EXPORTED: both actions are protected broadcasts sent by the
            // system, and TETHER_STATE_CHANGED comes from the networkstack uid.
            // RECEIVER_NOT_EXPORTED makes ContextCompat attach a private
            // permission that those senders don't hold, and the broadcast is then
            // silently dropped — which killed external-change detection entirely.
            // Protected broadcasts can't be spoofed by other apps, so EXPORTED
            // costs us nothing here.
            ContextCompat.registerReceiver(
                this,
                stateReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        }
    }

    /** Re-reads battery + hotspot state, then updates the notification and all widgets. */
    private fun refresh() {
        scope.launch { doRefresh() }
    }

    /**
     * ACTION_BATTERY_CHANGED fires on voltage and temperature changes too, not
     * just whole-percent steps, so most refreshes would redraw identical values.
     * The mutex serializes reads (they hit the same reflection) and makes the
     * cache check atomic.
     */
    private suspend fun doRefresh(force: Boolean = false) = refreshMutex.withLock {
        val battery = BatteryRepository(applicationContext).getBatteryState()
        val hotspotOn = HotspotRepository(applicationContext).getHotspotState().isEnabled

        val rendered = Rendered(hotspotOn, battery.percentage, battery.isCharging)
        if (!force && rendered == lastRendered) {
            Log.v(TAG, "Nothing displayable changed; skipping redraw")
            return@withLock
        }
        lastRendered = rendered

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

    /**
     * Everything the widget and notification actually display. [charging] is here
     * so a plug/unplug event still forces a redraw even when the percentage and
     * hotspot state are unchanged.
     */
    private data class Rendered(
        val hotspotOn: Boolean?,
        val batteryPct: Int,
        val charging: Boolean
    )

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
        private const val EXTRA_TARGET_ENABLED =
            "com.altmansoftwaredesign.hotspotwidget.extra.TARGET_ENABLED"
        private const val TARGET_UNSET = -1
        private const val TARGET_OFF = 0
        private const val TARGET_ON = 1

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

        /**
         * Drive the hotspot to [enable] via the (foreground) service, then refresh
         * everything. Callers pass the state the user actually confirmed, not a
         * request to flip whatever is current.
         */
        fun setHotspot(context: Context, enable: Boolean) {
            val intent = Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(EXTRA_TARGET_ENABLED, if (enable) TARGET_ON else TARGET_OFF)
            }
            context.startForegroundService(intent)
        }

        /** Stop the monitor; removes the notification and unregisters the receiver. */
        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryMonitorService::class.java))
        }
    }
}
