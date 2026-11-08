# Hotspot Switch

A minimal Android home-screen widget that opens the phone's native Mobile Hotspot settings and keeps its ON/OFF display synchronized with Android's tethering state.

[![Android APK build](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml/badge.svg?branch=main)](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/oscpprep/hotspot-widget?label=latest%20APK)](https://github.com/oscpprep/hotspot-widget/releases/latest)

## Download

**[Download the latest Hotspot Switch APK](https://github.com/oscpprep/hotspot-widget/releases/latest/download/hotspot-switch.apk)**

Also available: [all releases and version history](https://github.com/oscpprep/hotspot-widget/releases).

The APK is attached to a GitHub Release, so the download link above remains stable as new versions are published. A SHA-256 checksum is included with each release.

## Quick start

1. Download and install the APK.
2. Long-press an empty area of the Android home screen.
3. Open **Widgets**, find **Hotspot Switch**, and add it to the home screen.
4. The app starts its event-driven hotspot monitor when the widget is added.
5. Tap the widget to open the phone's native Mobile Hotspot settings.
6. Turn Mobile Hotspot ON or OFF using the system switch.
7. Return to the home screen. The widget updates its label and slider as Android reports tethering changes.

On Samsung Galaxy phones, the native settings page is under **Settings → Connections → Mobile Hotspot and Tethering → Mobile Hotspot**.

> **Important:** The widget does not bypass Android/Samsung's privileged tethering controls. The operating system's own Settings screen performs the ON/OFF change. Hotspot Switch observes the resulting state and reflects it in the widget.

## Features

- One large, state-aware home-screen switch.
- Custom dark rounded-card design with a large ON/OFF label and slider.
- Opens the device's own hotspot settings instead of trying to force tethering through unsupported hidden APIs.
- Persistent, event-driven monitoring on Android 16 using the public `TetheringManager.TetheringEventCallback`.
- Receives subsequent ON and OFF interface-state events while the widget exists, including changes made outside the helper Activity.
- A low-priority, silent, ongoing notification indicates that the monitor is active.
- No periodic polling, network traffic, root access, or accessibility-service automation.
- The monitor stops when the last widget instance is removed.

## Compatibility

| Item | Value |
|---|---|
| Minimum Android version | Android 12 (API 31) |
| Android SDK used to compile | Android 16 (API 36) |
| Target SDK | API 34 |
| Device-confirmed baseline | Samsung Galaxy S24 running Android 16, app version 1.3.9 |
| Persistent monitor implementation | 1.4.0 |

The Android 16 public tethering callback is used on API 36 devices. Earlier Android versions use legacy Wi-Fi AP/tethering state broadcasts as a best-effort fallback. OEM behavior can differ.

## How state updates work

### Android 16 (API 36)

The app registers Android's public `TetheringManager.TetheringEventCallback` from a dedicated foreground service. The callback reports the current tethered-interface set immediately after registration and reports subsequent changes. The widget checks for an interface whose type is `TETHERING_WIFI` and updates its ON/OFF artwork.

The service starts when a widget is added or refreshed. It stays registered independently of the temporary Settings helper Activity, so it can continue to receive ON/OFF transitions after the user returns to the home screen or changes hotspot state from Quick Settings. It uses callbacks rather than a polling loop.

Android requires a foreground service to keep this event listener reliably active in the background. Hotspot Switch therefore displays a quiet, low-priority notification while monitoring. The service stops when the last widget instance is removed.

### Android 12–15

Older Android versions use dynamic receivers for Wi-Fi AP and tether-state broadcasts, plus best-effort state reads through legacy Wi-Fi/tethering interfaces. These OEM-dependent paths may not behave identically on every device.

## Build the APK yourself

### Requirements

- JDK 17
- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0
- The Gradle wrapper included in this repository

### Command line

```bash
git clone https://github.com/oscpprep/hotspot-widget.git
cd hotspot-widget
./gradlew --no-daemon clean assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

See [the build guide](docs/BUILDING.md) for Android Studio instructions and build outputs.

## Run the GitHub Actions workflow

**[Open the Android APK workflow](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml)**

The workflow runs automatically when code is pushed to `main` and for pull requests targeting `main`. It can also be started manually using GitHub's **Run workflow** button:

1. Open the [Android APK workflow](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml).
2. Select **Run workflow**.
3. Choose the `main` branch.
4. Select **Run workflow** again.

Manual dispatch on the original repository requires write access. Anyone can download the latest APK from the Releases link above. To run the workflow independently without write access to this repository, fork the repository, enable Actions in the fork, and run the workflow from the fork's Actions tab. See [GitHub's manual workflow instructions](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

### What the workflow publishes

For each successful build, the workflow:

- Compiles the APK with JDK 17 and Android SDK 36.
- Verifies that the APK exists and is not empty.
- Creates a SHA-256 checksum.
- Uploads a downloadable Actions artifact named `hotspot-switch-VERSION-apk` (kept for 30 days).
- On pushes to `main` or a manual run on `main`, creates or updates a GitHub Release tagged `vVERSION`, attaching:
  - `hotspot-switch.apk` — stable download filename used by the README.
  - `hotspot-switch-VERSION.apk` — versioned APK filename.
  - `hotspot-switch.SHA256SUMS.txt` — checksum for the stable APK.

The stable README download URL always points to the latest published release asset:

```text
https://github.com/oscpprep/hotspot-widget/releases/latest/download/hotspot-switch.apk
```

See [the workflow and release guide](docs/BUILDING.md#github-actions-and-releases).

## Documentation

- [Installation and use](docs/INSTALLATION.md)
- [Architecture and hotspot state detection](docs/ARCHITECTURE.md)
- [Build, Actions, and releases](docs/BUILDING.md)
- [Troubleshooting and compatibility](docs/TROUBLESHOOTING.md)

## Permissions, notification, and privacy

The app declares these permissions:

- `ACCESS_NETWORK_STATE` — access network/tethering state information.
- `ACCESS_WIFI_STATE` — access Wi-Fi state information used by compatibility fallbacks.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` — keep the event-driven tethering callback active while the widget is installed.

The foreground-service permissions are not runtime permission prompts. Android displays a low-priority, silent, ongoing notification while the monitor is active. The service performs no periodic polling and no network transfers. It stops when the last widget instance is removed.

The app does not ask for location, contacts, phone, SMS, storage, or accessibility permissions. It does not collect or transmit personal data.

## Project structure

```text
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/altmansoftwaredesign/hotspotwidget/
│   ├── receiver/       # Legacy tether-state broadcast receiver
│   ├── repository/     # State detection and API 36 callback
│   ├── service/        # Persistent event-driven hotspot monitor
│   ├── ui/             # Native Settings handoff
│   └── widget/         # RemoteViews rendering and monitor lifecycle
└── res/
    ├── drawable/       # Card, slider, power, and notification artwork
    ├── layout/         # Widget and launcher layouts
    └── xml/            # App Widget provider metadata
```

## License

See [LICENSE](LICENSE).
