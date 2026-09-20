# Pharmacy Pocket for Android

A fast, offline medicine price reference built with Expo and React Native.

## Included

- 113 supplied medicines with Arabic names preserved
- Arabic and English search
- Swipe, category strip, and previous/next category controls
- Official and customer-requested prices in IQD
- Large-text and customer price views
- Add, edit, favorite, JSON backup, and restore
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

The included GitHub workflow also builds a preview APK and attaches it to the `v1.0.0` GitHub Release. That build does not require an Expo account. It uses development signing and is intended for direct testing; use EAS signing before a public or Play Store release.

## Release notes

The first native release stores data only on the phone. It does not use the private web app's cloud database because native clients do not receive the hosted Site's ChatGPT authentication session. Back up the list from Settings before clearing app data or changing phones.
