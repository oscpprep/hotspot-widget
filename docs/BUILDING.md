# Build, GitHub Actions, and releases

## Local build requirements

- JDK 17
- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0
- Git

The app's Android configuration is in `app/build.gradle.kts`:

- `compileSdk = 36`
- `minSdk = 31`
- `targetSdk = 34`
- Java/Kotlin target 11
- App version is controlled by `versionCode` and `versionName`

## Build from the command line

```bash
git clone https://github.com/oscpprep/hotspot-widget.git
cd hotspot-widget
./gradlew --no-daemon clean assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install over an existing installation:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If Android reports an APK signature conflict, uninstall the previous app before installing the new debug APK.

## Build in Android Studio

1. Clone or open this repository.
2. Set the Gradle JDK to JDK 17.
3. Install Android SDK Platform 36 and Build-Tools 36.0.0 from SDK Manager.
4. Sync the Gradle project.
5. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. Find the debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions workflow

Workflow file: [`.github/workflows/android-apk.yml`](../.github/workflows/android-apk.yml)

[Open the workflow in GitHub](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml).

### Automatic triggers

- Push to `main`: builds the APK and publishes or updates the matching GitHub Release.
- Pull request targeting `main`: builds and uploads an Actions artifact for validation; it does not publish a release.
- Manual `workflow_dispatch` on `main`: builds and publishes or updates the matching release.

### Run manually

1. Open the [Android APK workflow](https://github.com/oscpprep/hotspot-widget/actions/workflows/android-apk.yml).
2. Select **Run workflow**.
3. Choose the `main` branch.
4. Select **Run workflow** again.
5. Open the new run and wait for **Build debug APK** and **Publish downloadable GitHub Release** to finish.

GitHub requires write access to the repository to manually dispatch a workflow. Public users without write access can download the latest Release. To build independently, fork the repository, enable GitHub Actions in the fork, and manually run the workflow there. See [GitHub's documentation for manually running a workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

### Build artifact

The build job creates and uploads these files:

| File | Purpose |
|---|---|
| `hotspot-switch.apk` | Stable APK filename. |
| `hotspot-switch-VERSION.apk` | APK with the app version in its filename. |
| `hotspot-switch.SHA256SUMS.txt` | SHA-256 checksum for `hotspot-switch.apk`. |

The Actions artifact is named `hotspot-switch-VERSION-apk` and is retained for 30 days. Build logs are uploaded separately for 7 days.

### GitHub Release

After a successful build on `main`, the release job uses the repository's `GITHUB_TOKEN` with `contents: write` permission to create or update a release.

The release tag is generated from `versionName` in `app/build.gradle.kts`:

- App version `1.3.9` → release tag `v1.3.9`
- Stable release asset: `hotspot-switch.apk`
- Versioned asset: `hotspot-switch-1.3.9.apk`
- Checksum asset: `hotspot-switch.SHA256SUMS.txt`

The README uses GitHub's stable latest-release asset URL:

```text
https://github.com/oscpprep/hotspot-widget/releases/latest/download/hotspot-switch.apk
```

When preparing a new app release, increment both `versionCode` and `versionName` in `app/build.gradle.kts`, then merge the change into `main`. The workflow publishes the new tag and assets automatically.

## Verify a downloaded APK

Download both `hotspot-switch.apk` and `hotspot-switch.SHA256SUMS.txt` into the same directory, then run:

```bash
sha256sum --check hotspot-switch.SHA256SUMS.txt
```

Expected output:

```text
hotspot-switch.apk: OK
```

## Workflow security

- The build job has `contents: read`.
- The release job alone has `contents: write`.
- Pull request builds do not publish releases.
- The workflow uses the short-lived repository `GITHUB_TOKEN`; no personal access token or additional secret is needed.
