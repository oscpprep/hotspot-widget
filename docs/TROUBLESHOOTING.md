# Troubleshooting and compatibility

## Widget says OFF while Mobile Hotspot is ON

1. Confirm that the phone's system Mobile Hotspot switch is ON.
2. Tap the widget to open native hotspot Settings and allow the page to load.
3. Return to the home screen rather than leaving the helper Activity running in Recents.
4. Give Android a moment to report the final tethering state.
5. If the widget still shows OFF, remove the widget and add it again, then test once more.

On Android 16, the app listens for the public tethering-interface state callback while the system Settings page is open. Version 1.3.9 fixed a lifecycle bug where the helper Activity could finish too early and unregister that callback before the user changed the hotspot.

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
| Android 16 (API 36) | Public `TetheringManager.TetheringEventCallback` | Preferred path. Tested on Samsung Galaxy S24 with Hotspot Switch 1.3.9. |
| Android 12–15 (API 31–35) | Legacy Wi-Fi AP/tethering state fallbacks | OEM-dependent; behavior can vary. |

## Check the build version

Open the repository's [latest Releases page](https://github.com/oscpprep/hotspot-widget/releases/latest) and compare the APK version. The latest app version is also set by `versionName` in `app/build.gradle.kts`.

## Report a device-specific issue

When reporting an issue, include:

- Device manufacturer and model.
- Android version and One UI/OEM software version.
- Hotspot Switch app version.
- Whether the widget opens native hotspot Settings.
- Whether the widget changes after you switch hotspot ON/OFF and return.
- A screenshot or short screen recording that shows both the system hotspot state and widget state.

Do not post private Wi-Fi passwords, hotspot passwords, or personal information in an issue.
