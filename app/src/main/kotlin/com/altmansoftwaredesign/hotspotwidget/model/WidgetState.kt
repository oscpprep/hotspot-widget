package com.altmansoftwaredesign.hotspotwidget.model

/**
 * [isEnabled] is null when the hotspot state could not be read at all — the
 * hidden-API reflection failed. That is deliberately distinct from false: the
 * UI must show "unknown" rather than claim the hotspot is off, because a wrong
 * reading is worse than no reading. The reason is logged by the repository.
 */
data class HotspotState(val isEnabled: Boolean?)

data class BatteryState(val percentage: Int)
