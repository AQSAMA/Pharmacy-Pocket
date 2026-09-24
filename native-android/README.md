# Pharmacy Pocket Native

This folder contains the Kotlin + Jetpack Compose reimplementation of Pharmacy Pocket.

## Install identities

The project intentionally has two flavors:

- `preview`: `com.aqsama.pharmacypocket.native`, shown as **Pharmacy Pocket Native**. This can be installed beside the Expo app and is the flavor used for development and GitHub prereleases.
- `production`: `com.aqsama.pharmacypocket`, shown as **Pharmacy Pocket**. This is reserved for the eventual replacement of the legacy app.

The Kotlin namespace remains `com.aqsama.pharmacypocket`; only the install-time application ID differs.

## Data during side-by-side testing

Android isolates private app storage by application ID. The preview app therefore cannot read the legacy Expo app's private SQLite files directly. Use Pharmacy Pocket JSON export/import to copy real data between the two apps while testing.

The production flavor keeps the legacy application ID and retains the direct Expo SQLite/preferences migration code for the final cutover. An in-place Android update will also require compatible signing with the installed legacy app.

## Included features

- Native Kotlin + Jetpack Compose UI
- SQLite offline storage
- Arabic/English normalized search
- Categories, subcategories and customizable colors
- Favorites and all sorting modes
- Official and optional customer-requested prices
- Large-text mode
- Add/edit/detail flows, descriptions and date added
- Configurable currency
- Native Android haptic feedback
- JSON backup schema v1/v2 compatibility
- Merge and replace imports
- Android document picker/creator APIs

## Build

Requirements: JDK 17, Android SDK 37.0, Build Tools 36.0.0 and Gradle 9.6.

From this folder:

    gradle testPreviewDebugUnitTest assemblePreviewRelease

Preview APK:

    app/build/outputs/apk/preview/release/app-preview-release.apk

Preview builds use the repository's intentionally public **preview-only** signing key so successive side-loaded native previews can update one another without losing the preview app's private data. This key provides no production authenticity and must never be used for the final Pharmacy Pocket package.

A preview release:

    gradle assemblePreviewRelease

Production successor build:

    gradle assembleProductionRelease

Do not distribute the production flavor as an update to an installed legacy app until signing compatibility has been deliberately configured.
