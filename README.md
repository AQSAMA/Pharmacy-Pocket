# Pharmacy Pocket

Pharmacy Pocket is a native Android application written in Kotlin with Jetpack Compose. The native app in `native-android/` is the canonical and only application in this repository.

## Repository layout

- `native-android/` — Android application, unit tests, Gradle configuration, and version source.
- `.github/workflows/android-ci.yml` — pull-request and normal-commit validation.
- `.github/workflows/android-release.yml` — signed production releases from semantic-version tags.
- `RELEASING.md` — production signing and release procedure.

The Android project intentionally remains under `native-android/`. Removing the obsolete Expo application makes the repository unambiguous without a path-only move that would add migration risk and noisy history.

## Build locally

Requirements:

- JDK 17
- Android SDK platform 37
- Android Build Tools 36.0.0
- Gradle 9.6

From the repository root:

```sh
cd native-android
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The debug build uses `com.aqsama.pharmacypocket.debug` so it can be installed beside the production app.

## Data compatibility

The production application ID remains `com.aqsama.pharmacypocket`.

The native storage layer deliberately retains the final migration path from the old Expo application: it opens the existing `SQLite/pharmacy-pocket.db` database in place, upgrades its schema, and imports the old Expo preference store when present. JSON backups from earlier Pharmacy Pocket versions remain supported.

Android will preserve the old app's private data during an in-place update only when the new APK has a higher version code **and is signed with the same certificate as the installed legacy package**. See `RELEASING.md` before the first production release.

## Releases

Application versioning lives in `native-android/VERSION`. Production releases are intentionally created from tags such as `v3.0.0`; pull requests never create GitHub Releases.

Release builds require production signing credentials and fail rather than falling back to an unsigned or debug-signed package. See `RELEASING.md` for the exact secrets and one-time key setup.
