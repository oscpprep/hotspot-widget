# Troubleshooting and compatibility

## Widget updates late or remains stale

Version 1.4.0 adds a persistent foreground monitor on Android 16.

1. Install the latest APK.
2. Remove and re-add the widget if you are upgrading from an earlier version.
3. Confirm the **Hotspot Switch is monitoring** notification is present (unless Android notification settings suppress it).
4. Turn Mobile Hotspot ON or OFF from Samsung Settings or Quick Settings.
5. Check whether the widget changes.

The service keeps Android's public `TetheringManager.TetheringEventCallback` registered independently of the Settings helper Activity. Each interface-state callback directly updates all widget instances. It does not rely on a short-lived one-shot callback or periodic polling.

If you force-stop the app, reboot the phone, or Android/Samsung stops the service, tap the widget once to start the monitor again. The service uses `START_STICKY` so Android can recreate it after normal process reclamation, but a force-stop prevents automatic restart until the user interacts with the app again.

## Monitor notification is not visible

The service requires an ongoing foreground-service notification. It uses a low-importance channel and is silent. On Android 13 or later, notification permission/channel settings can affect how it appears in the notification drawer; check **Settings → Apps → Hotspot Switch → Notifications**. Android may still show foreground-service activity in its active-apps/task-manager UI.

## Widget does not appear or says "Couldn't add widget"

- Update to the latest APK.
- Remove stale Hotspot Switch widgets from the home screen.
- Reboot the launcher or phone if One UI cached a failed widget instance.
- Add the widget again from **Widgets → Hotspot Switch**.
- Keep the widget layout based on RemoteViews-supported views and operations.

## Widget layout looks old after an update

Remove the old widget instance and add a fresh one. Some launchers retain an existing widget's old RemoteViews until it is re-added or the launcher refreshes.

## Settings opens, but the hotspot does not turn on automatically

This is expected behavior. The widget opens Samsung/Android's native hotspot Settings, and the system switch performs the actual ON/OFF action. The app does not bypass privileged tethering APIs, carrier provisioning, or device policy.

## Android versions

| Android version | Detection path | Notes |
|---|---|---|
| Android 16 (API 36) | Persistent public `TetheringManager.TetheringEventCallback` | Preferred event-driven path. |
| Android 12–15 (API 31–35) | Dynamic Wi-Fi AP/tether-state broadcast receiver and legacy state reads | OEM-dependent; behavior can vary. |

## Check the build version

Open the repository's [latest Releases page](https://github.com/oscpprep/hotspot-widget/releases/latest) and compare the APK version. The latest app version is also set by `versionName` in `app/build.gradle.kts`.

## Report a device-specific issue

When reporting an issue, include:

- Device manufacturer and model.
- Android version and One UI/OEM software version.
- Hotspot Switch app version.
- Whether the monitoring notification is present.
- Whether the widget changes after you switch hotspot ON/OFF.
- A screenshot or short screen recording that shows both the system hotspot state and widget state.

Do not post private Wi-Fi passwords, hotspot passwords, or personal information in an issue.
