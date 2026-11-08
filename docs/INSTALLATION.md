# Installation and use

## Install the APK

1. Open the [latest Hotspot Switch release](https://github.com/oscpprep/hotspot-widget/releases/latest).
2. Download `hotspot-switch.apk`.
3. Open the downloaded APK on the Android phone.
4. If Android asks, allow the browser or file manager to install unknown apps.
5. Complete installation.

The app does not require a special permission-granting setup screen. It declares the normal Android permissions `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE`.

## Add the home-screen widget

1. Long-press an empty area of the home screen.
2. Select **Widgets**.
3. Find **Hotspot Switch**.
4. Drag the widget onto the home screen.
5. Resize it horizontally if you want a wider card.

If an older version was already installed, update the app and remove/re-add the widget if the launcher continues showing the old layout.

## Turn the hotspot on or off

1. Tap the Hotspot Switch widget.
2. The app opens the device's native hotspot Settings screen.
3. Use the operating system's own Mobile Hotspot switch.
4. Leave Settings or press Home to return to the home screen.
5. The widget reflects the state reported by Android.

On Samsung Galaxy phones, the manual route is:

**Settings → Connections → Mobile Hotspot and Tethering → Mobile Hotspot**

The widget is a state-aware shortcut; the system Settings app performs the actual ON/OFF operation.

## Expected visual states

- **OFF:** muted slider track, thumb on the left, OFF label.
- **ON:** green slider track, thumb on the right, ON label.
- **Unknown:** the state could not be read from the available Android/OEM interface. The app does not claim that the hotspot is OFF when it cannot confirm the state.
