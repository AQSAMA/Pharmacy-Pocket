# Pharmacy Pocket Android

This is the canonical Pharmacy Pocket application: Kotlin + Jetpack Compose, offline-first SQLite storage, and native Android camera/barcode support.

## Identity

- Production application ID: `com.aqsama.pharmacypocket`
- Debug application ID: `com.aqsama.pharmacypocket.debug`
- Version source: `VERSION`

The old Expo preview/production flavor split has been removed. Debug builds are the only side-by-side development identity; release builds always represent the real production package.

## Build

Requirements: JDK 17, Android SDK 37, Build Tools 36.0.0, and Gradle 9.6.

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

A production release build intentionally requires the release-signing environment variables documented in `../RELEASING.md`. Without them, release packaging fails clearly instead of producing an unsigned or debug-signed artifact.

## Data migration and backup compatibility

The production package preserves the original application ID and database path. On an accepted in-place Android upgrade, the native app upgrades the existing `SQLite/pharmacy-pocket.db` and migrates supported Expo preferences. Pharmacy Pocket JSON backup schemas from the previous applications remain importable.

The source code for the obsolete Expo application is no longer required for this compatibility and has been removed.

## Main capabilities

- Native Kotlin + Jetpack Compose UI
- Offline SQLite storage
- Arabic/English normalized search
- Categories, subcategories, favorites, sorting, and custom category colors
- Medicine notes, descriptions, prices, dates, barcode/QR data, and package photos
- CameraX + ML Kit scanning
- Trash/restore flow
- JSON backup/import compatibility
- Light/dark/system themes and large-text mode

See `../RELEASING.md` for production signing, versioning, and release instructions.
