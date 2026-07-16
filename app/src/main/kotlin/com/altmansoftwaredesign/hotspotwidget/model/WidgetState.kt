package com.altmansoftwaredesign.hotspotwidget.model

/**
 * [isEnabled] is null when the hotspot state could not be read at all — the
 * hidden-API reflection failed. That is deliberately distinct from false: the
 * UI must show "unknown" rather than claim the hotspot is off, because a wrong
 * reading is worse than no reading. [error] carries the reason when known.
 */
data class HotspotState(
    val isEnabled: Boolean?,
    val lastUpdate: Long = System.currentTimeMillis(),
    val error: String? = null
)

data class BatteryState(
    val percentage: Int,
    val status: Int,
    val health: Int,
    val temperature: Int,
    val isCharging: Boolean
)

data class WidgetDisplayState(
    val hotspot: HotspotState,
    val battery: BatteryState
)
