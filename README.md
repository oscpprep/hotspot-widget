# Hotspot Switch

A minimal Android home-screen widget for opening your phone's Mobile Hotspot settings and displaying its current state.

[![Build](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml/badge.svg?branch=main)](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/oscpprep/hotspot-widget)](https://github.com/oscpprep/hotspot-widget/releases/latest)

## Download

**[Download the latest APK](https://github.com/oscpprep/hotspot-widget/releases/latest/download/hotspot-switch.apk)** · [Releases](https://github.com/oscpprep/hotspot-widget/releases)

## Use

1. Install the APK.
2. Add **Hotspot Switch** from your Android home-screen Widgets menu.
3. Tap the widget to open your phone's native hotspot settings.
4. Turn Mobile Hotspot on or off there. The widget updates to match.

The app does not bypass Android or Samsung's hotspot controls.

## State monitoring

- Android 16: persistent, event-driven `TetheringManager` callback.
- Android 12–15: best-effort Wi-Fi AP/tethering broadcast fallback.
- A quiet, ongoing notification indicates that background monitoring is active.
- No periodic polling or network transfers. Monitoring stops when the last widget is removed.

## Build

Requires JDK 17 and Android SDK Platform 36.

```bash
git clone https://github.com/oscpprep/hotspot-widget.git
cd hotspot-widget
./gradlew --no-daemon clean assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

[Run or view the Android APK workflow](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml). Builds run on pushes and pull requests; manual runs require repository write access. Successful builds on `main` publish a GitHub Release automatically.

## Documentation

- [Installation](docs/INSTALLATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Builds and releases](docs/BUILDING.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)

## Compatibility

Android 12 (API 31) and newer. Confirmed working on Samsung Galaxy S24 with Android 16.

## License

See [LICENSE](LICENSE).
