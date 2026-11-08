package com.altmansoftwaredesign.hotspotwidget.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.service.HotspotMonitorService
import com.altmansoftwaredesign.hotspotwidget.widget.HotspotWidgetProvider

/**
 * Opens native Android/Samsung hotspot Settings. Continuous state observation
 * belongs to HotspotMonitorService, not to this short-lived helper Activity.
 */
class HotspotSettingsActivity : Activity() {

    private var settingsOpened = false
    private var hasPausedForSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This Activity is launched by a direct widget tap. It also retries
        // monitor startup if Android stopped the service or the phone restarted.
        try {
            HotspotMonitorService.start(this)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start persistent hotspot monitor", e)
        }

        if (savedInstanceState == null) openSystemHotspotSettings()
    }

    override fun onPause() {
        if (settingsOpened) {
            hasPausedForSettings = true
            Log.d(TAG, "Paused while system hotspot Settings is open")
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (settingsOpened && hasPausedForSettings) {
            hasPausedForSettings = false
            Log.i(TAG, "Returned from system Settings; requesting a final widget refresh")
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    HotspotWidgetProvider.updateWidgets(this)
                    finish()
                }
            }, RETURN_REFRESH_DELAY_MS)
        }
    }

    private fun openSystemHotspotSettings() {
        val candidates = listOf(
            Intent(ACTION_WIFI_TETHER_SETTINGS),
            Intent(ACTION_TETHER_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in candidates) {
            try {
                settingsOpened = true
                startActivity(intent)
                Log.i(TAG, "Opened system hotspot Settings: ${intent.action}")
                return
            } catch (e: ActivityNotFoundException) {
                settingsOpened = false
                Log.i(TAG, "Settings action not available: ${intent.action}")
            } catch (e: SecurityException) {
                settingsOpened = false
                Log.w(TAG, "Settings action blocked: ${intent.action}", e)
            }
        }

        Toast.makeText(this, R.string.open_settings_manually, Toast.LENGTH_LONG).show()
        finish()
    }

    private companion object {
        const val TAG = "HotspotSettings"
        const val RETURN_REFRESH_DELAY_MS = 700L
        const val ACTION_WIFI_TETHER_SETTINGS = "com.android.settings.WIFI_TETHER_SETTINGS"
        const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
    }
}
