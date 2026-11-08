package com.altmansoftwaredesign.hotspotwidget.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.altmansoftwaredesign.hotspotwidget.MainActivity
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.repository.Api36TetheringObserver
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.widget.HotspotWidgetProvider

/**
 * Event-driven hotspot observer kept alive independently of the transient
 * Settings helper Activity. It listens to Android 16's public tethering
 * callback for the entire time at least one widget instance exists.
 *
 * No polling, network access, or tethering control is performed.
 */
class HotspotMonitorService : Service() {

    private var unregisterTetheringCallback: (() -> Unit)? = null
    private var legacyReceiverRegistered = false

    private val legacyTetherReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val repository = HotspotRepository(this@HotspotMonitorService)
            val state = when (intent.action) {
                ACTION_WIFI_AP_STATE_CHANGED -> repository.parseWifiApStateIntent(intent)
                ACTION_TETHER_STATE_CHANGED -> repository.parseTetherStateIntent(intent)
                else -> null
            }
            Log.i(TAG, "Legacy tether event ${intent.action}: state=$state")
            if (state != null) {
                setCachedState(state)
                HotspotWidgetProvider.updateWidgets(
                    this@HotspotMonitorService,
                    hotspotStateOverride = state
                )
            } else {
                refreshFromCurrentState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForegroundService()
        registerStateObserver()
        Log.i(TAG, "Hotspot state monitor started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY lets Android recreate this event listener after reclaiming
        // the app process. The callback sends an initial current-state snapshot.
        if (unregisterTetheringCallback == null && !legacyReceiverRegistered) {
            registerStateObserver()
        }
        return START_STICKY
    }

    private fun startAsForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monitor_notification)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.monitor_notification_channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun registerStateObserver() {
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                unregisterTetheringCallback = Api36TetheringObserver.register(this) { state ->
                    setCachedState(state)
                    Log.i(TAG, "Public Android 16 tethering event: Wi-Fi active=$state")
                    // The callback remains registered; every ON and OFF event is
                    // applied immediately to all installed widget instances.
                    HotspotWidgetProvider.updateWidgets(
                        this@HotspotMonitorService,
                        hotspotStateOverride = state
                    )
                }
                Log.i(TAG, "Persistent Android 16 TetheringEventCallback registered")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Public tethering observer failed; using broadcasts", e)
            }
        }

        registerLegacyBroadcastObserver()
        refreshFromCurrentState()
    }

    private fun registerLegacyBroadcastObserver() {
        if (legacyReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_WIFI_AP_STATE_CHANGED)
            addAction(ACTION_TETHER_STATE_CHANGED)
        }
        try {
            ContextCompat.registerReceiver(
                this,
                legacyTetherReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            legacyReceiverRegistered = true
            Log.i(TAG, "Legacy dynamic tether-state receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Could not register legacy tether-state receiver", e)
        }
    }

    private fun refreshFromCurrentState() {
        val state = HotspotRepository(this).isHotspotEnabled()
        if (state != null) {
            setCachedState(state)
            HotspotWidgetProvider.updateWidgets(this, hotspotStateOverride = state)
        } else {
            HotspotWidgetProvider.updateWidgets(this, hotspotStateOverride = null)
        }
    }

    override fun onDestroy() {
        unregisterTetheringCallback?.invoke()
        unregisterTetheringCallback = null

        if (legacyReceiverRegistered) {
            try {
                unregisterReceiver(legacyTetherReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Legacy tether-state receiver was already unregistered", e)
            }
            legacyReceiverRegistered = false
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "Hotspot state monitor stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "HotspotMonitorService"
        private const val NOTIFICATION_ID = 2401
        private const val CHANNEL_ID = "hotspot_state_monitor"
        private const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        private const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"

        fun start(context: Context) {
            val intent = Intent(context, HotspotMonitorService::class.java)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        @Volatile
        private var cachedHotspotState: Boolean? = null

        fun currentState(): Boolean? = cachedHotspotState

        private fun setCachedState(state: Boolean) {
            cachedHotspotState = state
        }

        fun stop(context: Context) {
            cachedHotspotState = null
            context.stopService(Intent(context, HotspotMonitorService::class.java))
        }
    }
}
