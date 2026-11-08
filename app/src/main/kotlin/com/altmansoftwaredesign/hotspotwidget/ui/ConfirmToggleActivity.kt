package com.altmansoftwaredesign.hotspotwidget.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.service.BatteryMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfirmToggleActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A widget tap is a user-initiated foreground action. Keep this Activity
        // alive while requesting tethering so the user sees Android's actual result.
        BatteryMonitorService.start(this)

        scope.launch {
            val isEnabled = withContext(Dispatchers.IO) {
                HotspotRepository(applicationContext).getHotspotState().isEnabled
            }
            if (isEnabled == null) showUnavailableDialog() else showDialog(isEnabled)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    private fun showUnavailableDialog() {
        dialog = AlertDialog.Builder(this)
            .setTitle("Hotspot state unavailable")
            .setMessage(
                "This device isn't reporting its hotspot state, so the widget won't guess. Use Quick Settings to change the hotspot."
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showDialog(isCurrentlyEnabled: Boolean) {
        val message = if (isCurrentlyEnabled) {
            "Turn the hotspot off?"
        } else {
            "Turn the hotspot on?"
        }
        val confirmLabel = if (isCurrentlyEnabled) "Turn Off" else "Turn On"

        dialog = AlertDialog.Builder(this)
            .setTitle("Toggle Hotspot")
            .setMessage(message)
            .setPositiveButton(confirmLabel) { _, _ ->
                if (!Settings.System.canWrite(this)) {
                    dialog?.dismiss()
                    dialog = AlertDialog.Builder(this)
                        .setTitle("Permission required")
                        .setMessage(
                            "Allow “Modify system settings” for Hotspot Widget, return to the home screen, and tap the widget again."
                        )
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:$packageName")
                                )
                            )
                            finish()
                        }
                        .setOnCancelListener { finish() }
                        .show()
                } else {
                    vibrate()
                    performToggle(enable = !isCurrentlyEnabled)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun performToggle(enable: Boolean) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this)
            .setTitle(if (enable) "Turning hotspot on" else "Turning hotspot off")
            .setMessage("Waiting for Android to respond…")
            .setCancelable(false)
            .create()
            .also { it.show() }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val repository = HotspotRepository(applicationContext)
                if (enable) repository.enableHotspot() else repository.disableHotspot()
            }

            dialog?.dismiss()

            // Re-read widget/notification state after the tethering operation.
            BatteryMonitorService.start(this@ConfirmToggleActivity)

            dialog = AlertDialog.Builder(this@ConfirmToggleActivity)
                .setTitle(
                    when {
                        result.succeeded && enable -> "Hotspot is on"
                        result.succeeded -> "Hotspot is off"
                        enable -> "Hotspot could not be turned on"
                        else -> "Hotspot could not be turned off"
                    }
                )
                .setMessage(result.message)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
    }
}
