# Pharmacy Pocket for Android

Pharmacy Pocket is now a fully native Android application written in Kotlin with Jetpack Compose.

The Android package remains com.aqsama.pharmacypocket, so this version can replace the previous Expo build. On first native launch it reads the existing Expo SQLite database from the same app-private location and migrates the Expo-backed preferences for currency, large text, and category definitions.

## Included features

- Fast offline medicine reference backed by SQLite
- Empty fresh installation with manual entry or JSON import
- Arabic and English search with Arabic diacritic and Alef normalization
- Category and subcategory filtering
- Custom categories with editable names, short labels, and colors
- Category color accents and subtle card tints
- Favorites and favorites-only filtering
- Default ordering by category size, then newest medicine first inside each category
- A-Z, Z-A, newest, oldest, price ascending, and price descending sorts
- Official and optional customer-requested prices
- Large-text mode
- Full medicine detail view with note, description, category, subcategory, and date added
- Add and edit medicine flows
- Configurable currency name, defaulting to IQD
- Native Android haptic feedback; no vibration API is used
- One-file JSON export and import
- Merge and replace import modes
- Compatibility with Pharmacy Pocket backup schema v1 and v2, including the original web/Expo backup format
- Preservation of favorites, custom category metadata, descriptions, timestamps, and imported section order
- Android document picker / document creator, with no storage permission required
- No React Native, Expo, JavaScript runtime, Metro, EAS, or web wrapper

## Data migration

Existing Expo installs used:

- files/SQLite/pharmacy-pocket.db for medicines
- files/SQLite/ExpoSQLiteStorage for Expo localStorage preferences

The native application deliberately keeps the same medicine database path. It also imports large-text, currency-name, and category-definitions-v1 from the Expo localStorage database once, then stores preferences in Android SharedPreferences.

Back up important data before installing development builds over a production installation.

## Build

Requirements:

- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0
- Gradle 9.6.0

From the repository root with Gradle 9.6 available on PATH:

    gradle test assembleDebug

The debug APK is created at:

    app/build/outputs/apk/debug/app-debug.apk

For the test-signed release APK used by GitHub prereleases:

    gradle assembleRelease

The release workflow intentionally uses development signing for direct testing. Use a private production signing key for Play Store or public production distribution.

## Continuous integration

Pull requests run JVM tests, compile the native app, verify APK signing, verify 16 KB zip alignment, and upload an installable APK artifact.

Successful builds on main publish a uniquely tagged prerelease APK.

## Architecture

The app is a single-activity Compose application. UI state is isolated from persistence through PharmacyRepository; database and import writes are serialized; JSON parsing and validation preserve the compatibility rules of the prior implementation.

No network connection is required for normal application use.
