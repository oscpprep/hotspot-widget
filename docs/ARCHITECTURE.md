# Architecture and hotspot state detection

## Overview

Hotspot Switch is a home-screen App Widget. Its card is rendered with Android `RemoteViews` and contains one large clickable surface. Tapping that surface opens `HotspotSettingsActivity`, which launches the device's native hotspot Settings screen.

The app intentionally does not force-enable or force-disable tethering. The system Settings UI owns that privileged operation. The app observes the result and changes the widget's label, track, and slider-thumb position.

## Main components

| Component | Responsibility |
|---|---|
| `HotspotWidgetProvider` | Creates/updates the RemoteViews card and routes taps to the helper Activity. |
| `HotspotSettingsActivity` | Launches native Settings, maintains the state listener during the Settings visit, and refreshes on return. |
| `Api36TetheringObserver` | Wraps the Android 16 public `TetheringManager.TetheringEventCallback`. |
| `HotspotRepository` | Provides legacy/OEM fallback state reads and parses Wi-Fi AP/tethering broadcasts. |
| `TetherStateReceiver` | Receives legacy tether-state broadcasts on older Android versions. |

## Android 16 (API 36) path

On Android 16, `Api36TetheringObserver` obtains the public `TetheringManager` service and registers a `TetheringEventCallback` using the application's main executor.

The callback receives `onTetheredInterfacesChanged` with the current set of `TetheringInterface` values. The widget treats the hotspot as ON when the set contains an interface whose type equals `TetheringManager.TETHERING_WIFI`.

This avoids relying on vendor-specific interface names such as `wlan0`, `swlan0`, or `ap0`.

## Listener lifecycle

The helper Activity must stay alive while Samsung Settings is in the foreground, otherwise its callback is unregistered before the user changes hotspot state.

The lifecycle is deliberately:

1. Widget tap launches `HotspotSettingsActivity`.
2. During `onCreate`, the Activity registers the API 36 tethering callback.
3. The Activity launches native hotspot Settings.
4. The helper Activity pauses. It records that it was paused because Settings was opened.
5. While Settings is visible, the callback remains registered. Each tethering-interface change updates the RemoteViews widget.
6. When the user returns, the helper Activity resumes. Only this second resume is treated as a return from Settings.
7. The Activity refreshes the widget, waits briefly for the final Soft AP transition callback, refreshes again, then finishes and unregisters the listener.

The initial `onResume` is not treated as a return from Settings. This distinction is important: finishing the helper Activity during its initial resume would unregister the observer too early.

## Older Android fallback

For Android 12–15, the app retains best-effort fallback methods:

1. Read the Wi-Fi AP state using the legacy `WifiManager.getWifiApState()` reflection path where available.
2. Inspect the `WIFI_AP_STATE_CHANGED` sticky broadcast.
3. Inspect the `TETHER_STATE_CHANGED` sticky broadcast.
4. Inspect the legacy tethered-interface list when accessible.

These APIs and broadcasts are OEM-dependent. Their behavior is not guaranteed to match the Android 16 public callback on every device.

## Widget rendering

The widget is drawn with RemoteViews-supported views and drawable resources:

- `widget_background.xml` — rounded dark gradient card.
- `switch_track_off.xml` / `switch_track_on.xml` — state-specific track.
- `switch_thumb.xml` — white thumb.
- `ic_switch_power.xml` — power glyph.
- `HotspotWidgetProvider` — sets ON/OFF text, colors, visibility, and accessibility description.

Keep new widget layouts within the RemoteViews-supported view and operation set; unsupported custom view classes can cause launchers to display “Couldn't add widget.”

## Permissions

The manifest declares:

- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.ACCESS_WIFI_STATE`

These are normal permissions; the user is not asked to grant a runtime permission dialog for them.

## Important boundary

State observation is separate from hotspot control. The app does not claim that it can bypass carrier provisioning, OEM rules, or privileged tethering permissions. A tap opens the native system control; the widget then displays the state reported by Android.
