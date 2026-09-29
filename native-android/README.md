# Pharmacy Pocket Android

This is the canonical Pharmacy Pocket application: Kotlin + Jetpack Compose, offline-first SQLite storage, and native Android camera/barcode support.

## Android UI and Compose reference

For UI, Compose architecture, navigation, motion, theming, screen composition, or state-ownership work, read the pinned June reference before making significant changes:

- `../reference/june/README.md`
- `../reference/june/PATTERNS.md`
- `../reference/june/AGENTS.md`

June is a design and implementation reference only; production Pharmacy Pocket code remains under `native-android/`.

## Identity

- Production: `Pharmacy Pocket` — `com.aqsama.pharmacypocket`
- Preview: `Pharmacy Pocket Preview` — `com.aqsama.pharmacypocket.native`
- Debug/CI: `Pharmacy Pocket Dev` — `com.aqsama.pharmacypocket.debug`
- Production version source: `VERSION`

The preview is an optimized release-style build signed with the repository's intentionally public preview-only key. It is a separate installable identity from production and can update earlier native previews in place. Debug builds remain the short-lived CI/development identity.

## Build

Requirements: JDK 17, Android SDK 37, Build Tools 36.0.0, and Gradle 9.6.

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
gradle --no-daemon lintPreview assemblePreview
```

The plain `assemblePreview` command is suitable for a fresh install. To install a locally built preview **over an already published preview**, give it a `versionCode` greater than the one currently installed:

```sh
gradle --no-daemon lintPreview assemblePreview -PversionCode=<higher-version-code>
```

For example, if the installed preview has version code `30000025`, build the local update with `-PversionCode=30000026` or higher. The GitHub preview workflow assigns these monotonically automatically; the override is only needed for local side-loading over an existing preview.

A production release build intentionally requires the release-signing environment variables documented in `../RELEASING.md`. Without them, release packaging fails clearly instead of producing an unsigned or debug-signed artifact.

## Preview channel

After native changes reach `main`, the **Android Preview Release** workflow builds the optimized preview APK and publishes it as a GitHub **Pre-release**. This is the normal installable testing channel before production releases.

Production remains separate and is published only from an explicit semantic version tag.

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

See `../RELEASING.md` for preview and production release instructions.