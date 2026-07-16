package com.altmansoftwaredesign.hotspotwidget.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.altmansoftwaredesign.hotspotwidget.model.BatteryState

class BatteryRepository(private val context: Context) {

    /** Reads the current level from the sticky ACTION_BATTERY_CHANGED broadcast. */
    fun getBatteryState(): BatteryState {
        val batteryStatus =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return BatteryState(percentage = 0)

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return BatteryState(percentage = (level.coerceAtLeast(0) * 100) / scale.coerceAtLeast(1))
    }
}
