# Hotspot Switch

A minimal Android home-screen widget that opens the phone's native Mobile Hotspot settings and reflects the hotspot's ON/OFF state.

[![Android APK build](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml/badge.svg?branch=main)](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/oscpprep/hotspot-widget?label=latest%20APK)](https://github.com/oscpprep/hotspot-widget/releases/latest)

## Download

**[Download the latest Hotspot Switch APK](https://github.com/oscpprep/hotspot-widget/releases/latest/download/hotspot-switch.apk)**

Also available: [all releases and version history](https://github.com/oscpprep/hotspot-widget/releases).

The APK is attached to a GitHub Release, so the download link above remains stable as new versions are published. The checksum file is included with each release.

## Quick start

1. Download and install the APK.
2. Long-press an empty area of the Android home screen.
3. Open **Widgets**, find **Hotspot Switch**, and add it to the home screen.
4. Tap the widget. It opens the phone's native Mobile Hotspot settings.
5. Turn Mobile Hotspot ON or OFF using the system switch.
6. Return to the home screen. The widget updates its label and slider to reflect the state reported by Android.

On Samsung Galaxy phones, the native settings page is under **Settings → Connections → Mobile Hotspot and Tethering → Mobile Hotspot**.

> **Important:** The widget does not try to bypass Android/Samsung's privileged tethering controls. The operating system's own Settings screen performs the ON/OFF change. The widget observes and displays the resulting state.

## Features

- One large, state-aware home-screen switch.
- Custom dark rounded-card design with a large ON/OFF label and slider.
- Opens the device's own hotspot settings instead of trying to force tethering through unsupported hidden APIs.
- On Android 16, observes the public `TetheringManager.TetheringEventCallback` interface-state events while the system Settings page is open.
- Keeps the tethering listener alive during the Settings visit and refreshes the widget when the user returns.
- Retains older Android state-detection fallbacks.
- No root, accessibility service, persistent foreground service, or battery/status panel.

## Compatibility

| Item | Value |
|---|---|
| Minimum Android version | Android 12 (API 31) |
| Android SDK used to compile | Android 16 (API 36) |
| Target SDK | API 34 |
| Verified device | Samsung Galaxy S24 running Android 16 |
| Latest confirmed working app version | 1.3.9 |

**Device-specific note:** The Android 16 public tethering callback is used on API 36 devices. Earlier Android versions use legacy Wi-Fi AP/tethering state fallbacks. OEM behavior can differ; the Samsung Galaxy S24 / Android 16 combination has been tested with version 1.3.9.

## How state updates work

### Android 16 (API 36)

The app registers Android's public `TetheringManager.TetheringEventCallback`. The callback reports the current tethered-interface set and subsequent changes. The widget checks for an interface whose type is `TETHERING_WIFI` and updates its ON/OFF artwork.

The temporary helper Activity remains alive while the system hotspot Settings screen is in front. It keeps the callback registered during that visit, applies state changes to the widget as callbacks arrive, and refreshes again after the user returns.

### Android 12–15

Older Android versions use best-effort fallback state detection through the Wi-Fi AP state broadcast, reflective `WifiManager.getWifiApState()`, and the tethered-interface query. These older paths are OEM-dependent and may not behave identically on every device.

## Build the APK yourself

### Requirements

- JDK 17
- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0
- The Gradle wrapper included in this repository

### Command line

Clone the repository and build:

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

- Compiles the debug APK with JDK 17 and Android SDK 36.
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

## Permissions and privacy

The app declares only these normal Android permissions:

- `ACCESS_NETWORK_STATE` — access network/tethering state information.
- `ACCESS_WIFI_STATE` — access Wi-Fi state information used by compatibility fallbacks.

It does not ask for location, contacts, phone, SMS, storage, or accessibility permissions. The app does not collect or transmit personal data.

## Project structure

```text
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/altmansoftwaredesign/hotspotwidget/
│   ├── receiver/       # Legacy tether-state broadcast receiver
│   ├── repository/     # State detection and API 36 callback
│   ├── ui/             # Native Settings handoff and listener lifecycle
│   └── widget/         # RemoteViews widget rendering
└── res/
    ├── drawable/       # Card, slider, and power-icon artwork
    ├── layout/         # Widget and launcher layouts
    └── xml/            # App Widget provider metadata
```

## License

See [LICENSE](LICENSE).
