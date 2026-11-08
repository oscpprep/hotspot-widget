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

It refreshes the switch state when the user returns from Settings.

On Samsung Galaxy phones, the manual path is **Settings → Connections → Mobile
Hotspot and Tethering → Mobile Hotspot**.

## Features

- One large, state-aware home-screen switch
- No battery percentage, battery icon, status panel, or confirmation dialog
- No special permission screen, foreground service, background notification,
  boot receiver, root access, or accessibility-service automation
- The live switch state is read where the device exposes the tethered-interface
  list. If an OEM blocks that read, the widget still opens system hotspot controls
  and describes its state as unavailable to accessibility services.

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
switch there. The widget then refreshes its own state when the Settings page closes.
