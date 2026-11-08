# Installation and use

## Install the APK

1. Open the [latest Hotspot Switch release](https://github.com/oscpprep/hotspot-widget/releases/latest).
2. Download `hotspot-switch.apk`.
3. Open the downloaded APK on the Android phone.
4. If Android asks, allow the browser or file manager to install unknown apps.
5. Complete installation.

The app uses normal network-state permissions and a foreground service to keep Android's event callback active. It does not show a special runtime permission screen.

## Add the home-screen widget

1. Long-press an empty area of the home screen.
2. Select **Widgets**.
3. Find **Hotspot Switch**.
4. Drag the widget onto the home screen.
5. Resize it horizontally if you want a wider card.

When the first widget is added, Hotspot Switch starts its event-driven state monitor. Android displays a quiet, low-priority ongoing notification while monitoring is active. The monitor uses callbacks rather than periodic polling.

If an older version was already installed, update the app and remove/re-add the widget if the launcher continues showing the old layout.

## Turn the hotspot on or off

1. Tap the Hotspot Switch widget.
2. The app opens the device's native hotspot Settings screen.
3. Use the operating system's own Mobile Hotspot switch.
4. Leave Settings or press Home to return to the home screen.
5. The widget updates as Android reports the tethering state.

The monitor stays active after the helper screen closes, so later hotspot changes from Samsung Settings or Quick Settings can also update the widget.

On Samsung Galaxy phones, the manual route is:

**Settings → Connections → Mobile Hotspot and Tethering → Mobile Hotspot**

The widget is a state-aware shortcut; the system Settings app performs the actual ON/OFF operation.

## Monitor notification and battery behavior

The persistent monitor runs as an Android foreground service. Its low-priority notification says **Hotspot Switch is monitoring**. It is silent and does not use vibration or sound.

On Android 16, updates are event-driven through the public tethering callback; the app does not poll at intervals or transfer data. The service stops automatically when the last Hotspot Switch widget is removed.

If the phone is rebooted, the app is force-stopped, or Android/Samsung stops the service, tap the widget once to start the monitor again. When Android restarts a sticky service after reclaiming its process, it registers the callback again and receives the current tethering state.

## Expected visual states

- **OFF:** muted slider track, thumb on the left, OFF label.
- **ON:** green slider track, thumb on the right, ON label.
- **Unknown:** the state could not be read from the available Android/OEM interface. The app does not claim that the hotspot is OFF when it cannot confirm the state.
