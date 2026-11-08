# Hotspot Switch

A minimal Android home-screen widget with one large switch.

## How it works

The widget does **not** try to force Wi-Fi tethering on through hidden Android APIs.
A normal third-party app is not guaranteed permission to start or stop tethering,
even when the user grants "Modify system settings". Instead, tapping the widget
opens the phone's own hotspot controls, where Android or the device manufacturer
performs the change.

The widget tries these Settings actions in order:

1. `com.android.settings.WIFI_TETHER_SETTINGS` — direct Wi-Fi hotspot screen
2. `android.settings.TETHER_SETTINGS` — tethering settings screen
3. Android wireless/network settings
4. General Android Settings

On Samsung Galaxy phones, the manual path is **Settings → Connections → Mobile
Hotspot and Tethering → Mobile Hotspot**.

## Hotspot state detection

The widget listens for Android's Wi-Fi AP state-change broadcast:

- Action: `android.net.wifi.WIFI_AP_STATE_CHANGED`
- State extra: `wifi_state`
- `13` = enabled, `11` = disabled, `12` = enabling, `10` = disabling,
  `14` = failed

The app keeps a receiver active while the system Settings page is open. It applies
the received enabled/disabled event directly to the widget and uses that observed
event when returning from Settings, so a stale interface query cannot overwrite it.
A reflective `WifiManager.getWifiApState()` query and tethered-interface checks are
retained as fallbacks for OEM builds that do not expose the AP state broadcast.

## Features

- One large, state-aware home-screen switch
- No battery percentage, battery icon, status panel, or confirmation dialog
- No foreground service, background notification, boot receiver, root access, or
  accessibility-service automation
- The state is read-only; the system Settings app performs the actual change

## Requirements

- Android 12 or later (API 31+; uses RemoteViews stateful Switch support)
- JDK 17
- Android Studio or the bundled Gradle wrapper

## Build and install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then add the widget: long-press the home screen → **Widgets** → **Hotspot Switch**
→ drag it onto a home screen. Resize it to make the switch larger if desired.

## Important limitation

A third-party app cannot reliably change mobile-hotspot state directly across Android
versions and manufacturers. This version deliberately uses the system Settings UI.
Tapping the widget opens the native hotspot page; the user taps the actual system
switch there. The widget then updates from the Wi-Fi AP state event when the system
reports the change.
