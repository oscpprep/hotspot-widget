# Architecture and hotspot state detection

## Overview

Hotspot Switch is a home-screen App Widget. Its card is rendered with Android `RemoteViews` and contains one large clickable surface. Tapping that surface opens `HotspotSettingsActivity`, which launches the device's native hotspot Settings screen.

The app does not force-enable or force-disable tethering. The system Settings UI owns that privileged operation. A separate foreground service observes the tethering state and updates the widget.

## Main components

| Component | Responsibility |
|---|---|
| `HotspotWidgetProvider` | Creates/updates the RemoteViews card, starts monitoring when a widget is added/updated, and stops it when the last widget is removed. |
| `HotspotSettingsActivity` | Launches native Settings and requests a final refresh when the user returns. |
| `HotspotMonitorService` | Long-lived event-driven observer. Keeps the tethering callback registered independently of Activity lifecycle and applies every state change to all widgets. |
| `Api36TetheringObserver` | Wraps Android 16's public `TetheringManager.TetheringEventCallback`. |
| `HotspotRepository` | Provides legacy/OEM fallback state reads and parses Wi-Fi AP/tethering broadcasts. |
| `TetherStateReceiver` | Receives legacy tether-state broadcasts. |

## Android 16 (API 36) path

On Android 16, `HotspotMonitorService` obtains the public `TetheringManager` service and registers `TetheringEventCallback` using the application's main executor.

The callback receives `onTetheredInterfacesChanged` immediately after registration with the current set of `TetheringInterface` values, and receives further calls when that set changes. The service treats the hotspot as ON when the set contains an interface whose type equals `TetheringManager.TETHERING_WIFI`.

This avoids relying on vendor-specific interface names such as `wlan0`, `swlan0`, or `ap0`. Because the callback remains registered in the service, OFF transitions are not dependent on the temporary Settings Activity still existing.

## Monitor lifecycle

1. The first widget is added. `HotspotWidgetProvider.onEnabled()` starts `HotspotMonitorService`.
2. The service promotes itself to a foreground service and posts a low-priority, silent, ongoing notification.
3. On Android 16, the service registers one public `TetheringEventCallback` and keeps its unregister handle for the lifetime of the service.
4. The initial callback supplies the current tethered-interface set. The service updates all widget instances.
5. Every later `onTetheredInterfacesChanged` callback is checked for `TETHERING_WIFI`, then the service immediately applies the new ON/OFF state to the widget RemoteViews.
6. Tapping the widget opens native Settings. The service remains alive while the helper Activity pauses and after the Activity closes.
7. When the last widget is removed, `HotspotWidgetProvider.onDisabled()` stops the service and removes its notification.
8. The service uses `START_STICKY`; if Android reclaims the process, it can recreate the service and re-register the callback. A user force-stop or device restart may require tapping the widget once to re-arm monitoring.

No timer or periodic polling loop is used.

## Why a foreground service is used

A callback registered only by an Activity is tied to that Activity/process lifecycle. It can disappear after the Activity closes or if Android reclaims the process while Settings is in front. A normal background process is not guaranteed to remain alive indefinitely.

The foreground service keeps the listener registered while the widget is installed. Android requires the ongoing notification for this user-visible background work. The notification is low priority, silent, and contains no activity or battery data.

The service is event-driven. It does not poll tethering state, access the internet, or control tethering.

## Older Android fallback

For Android 12–15, the service registers dynamic receivers for Wi-Fi AP and tether-state broadcasts and performs best-effort reads through:

1. The legacy `WifiManager.getWifiApState()` reflection path where available.
2. The `WIFI_AP_STATE_CHANGED` broadcast.
3. The `TETHER_STATE_CHANGED` broadcast.
4. The legacy tethered-interface list when accessible.

These APIs and broadcasts are OEM-dependent and may not behave identically on every device.

## Widget rendering

The widget uses RemoteViews-supported views and drawable resources:

- `widget_background.xml` — rounded dark gradient card.
- `switch_track_off.xml` / `switch_track_on.xml` — state-specific track.
- `switch_thumb.xml` — white thumb.
- `ic_switch_power.xml` — slider power glyph.
- `ic_monitor_notification.xml` — monochrome status icon.
- `HotspotWidgetProvider` — sets ON/OFF text, colors, visibility, and accessibility description.

Keep new widget layouts within the RemoteViews-supported view and operation set; unsupported custom view classes can cause launchers to display “Couldn't add widget.”

## Permissions and notification

The manifest declares:

- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.ACCESS_WIFI_STATE`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`

The service is declared as `specialUse`, with a manifest subtype explaining that it continuously observes tethering events to keep the user-visible widget accurate. These foreground-service permissions do not produce a runtime permission dialog. Android displays the required ongoing notification while the service runs.

## Important boundary

State observation is separate from hotspot control. The app does not bypass carrier provisioning, OEM rules, or privileged tethering permissions. A tap opens the native system control; the monitor then reflects the state reported by Android.
