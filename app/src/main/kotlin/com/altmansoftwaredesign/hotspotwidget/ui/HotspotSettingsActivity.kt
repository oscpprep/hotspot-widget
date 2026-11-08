package com.altmansoftwaredesign.hotspotwidget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.widget.HotspotWidgetProvider

/**
 * Opens the OEM/system hotspot controls.
 *
 * No root, hidden tethering start/stop API, WRITE_SETTINGS permission, or
 * accessibility automation is used. The user makes the final change in the
 * Settings UI supplied by Android or the device manufacturer.
 */
class HotspotSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            openSystemHotspotSettings()
        }
    }

    @Suppress("DEPRECATION")
    private fun openSystemHotspotSettings() {
        // Device manufacturers do not all expose the same Settings action.
        // Try the direct Wi-Fi tether screen first, then the general tethering
        // screen, then progressively broader Settings destinations.
        val candidates = listOf(
            Intent(ACTION_WIFI_TETHER_SETTINGS),
            Intent(ACTION_TETHER_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in candidates) {
            try {
                startActivityForResult(intent, REQUEST_SETTINGS)
                return
            } catch (e: ActivityNotFoundException) {
                Log.i(TAG, "Settings action not available: ${intent.action}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Settings action blocked: ${intent.action}", e)
            }
        }

        Toast.makeText(this, R.string.open_settings_manually, Toast.LENGTH_LONG).show()
        finish()
    }

    @Deprecated("Uses the legacy activity-result callback for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SETTINGS) {
            // Re-read the real state after the user leaves system Settings.
            HotspotWidgetProvider.updateWidgets(this)
            finish()
        }
    }

    private companion object {
        const val TAG = "HotspotSettings"
        const val REQUEST_SETTINGS = 4107
        const val ACTION_WIFI_TETHER_SETTINGS = "com.android.settings.WIFI_TETHER_SETTINGS"
        const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
    }
}
