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

        // BATTERY_STATUS_FULL counts as charging: a plugged-in phone sitting at
        // 100% still reports FULL rather than CHARGING, and the bolt should stay.
        val status = batteryStatus.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryState(
            percentage = (level.coerceAtLeast(0) * 100) / scale.coerceAtLeast(1),
            isCharging = isCharging
        )
    }
}
