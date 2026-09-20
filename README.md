# Pharmacy Pocket for Android

A fast, offline medicine price reference built with Expo and React Native.

## Included

- Empty on a fresh installation, ready for an import or manual entry
- Arabic and English search
- Swipe, category strip, and previous/next category controls
- Official and customer-requested prices in IQD
- Configurable currency name, with IQD as the default
- Large-text and customer price views
- Full-card tap targets, floating medicine details, and longer descriptions
- Add, edit, favorite, and one-file JSON import/export
- Imports the original web app's v1 backups; v2 exports remain importable by the web app
- Merge or exactly replace medicines while preserving category-section order
- On-device SQLite storage; no connection is required after installation

## Test in Expo Go

```bash
npm install
npx expo start
```

Scan the QR code with Expo Go. An Expo account is not required for this local workflow.

## Build an installable APK with EAS

Sign in once, then start the preview build:

```bash
npx eas-cli@latest login
npx eas-cli@latest build --platform android --profile preview
```

The preview profile produces an APK suitable for direct Android installation. EAS manages the signing key so later builds can update the installed app.

The GitHub workflows build smaller ARM64 preview APKs without an Expo account. Every pull request receives a temporary downloadable artifact, and every successful build on `main` publishes a uniquely numbered GitHub prerelease. ARM64 covers modern Android phones while omitting emulator-only CPU libraries that made the universal APK unnecessarily large. These builds use development signing and are intended for direct testing; use EAS signing before a public or Play Store release.

## Release notes

The first native release stores data only on the phone. It does not use the private web app's cloud database because native clients do not receive the hosted Site's ChatGPT authentication session. Back up the list from Settings before clearing app data or changing phones.
