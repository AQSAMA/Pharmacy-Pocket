# Pharmacy Pocket 2.5 prerelease

## Changes

- Full-screen medicine details, with the half-height sheet and its animation removed.
- Native vertical list scrolling without a competing screen-wide category gesture.
- Section headings are ordinary rows, with the list kept clear of system bars and the bottom controls.
- Stable favorite callbacks and row object references; favorites no longer reload every medicine.
- Serialized storage operations, transactional merges, handled favorite/preference errors, and a retry screen for database initialization failures.
- Price rows can wrap on narrow screens; empty or unsafe prices cannot be saved.
- Android version 2.5.0, version code 25; same application ID and database.

## Validation and limitations

Run npm test and npm run typecheck. GitHub Actions builds a standalone ARM64 release APK with its JavaScript bundled; no development server or Expo Go is needed.

This environment has no Android emulator or physical device. Native crash reproduction and scrolling frame-rate measurements have not been performed. The storage and navigation changes address observed code risks, not a confirmed diagnosis of every reported crash.

Before installing, export a JSON backup. Install over the current app without uninstalling. If Android reports a signing mismatch, stop rather than clearing app data.

Device acceptance checks: rapid up/down flings through all sections, large text and Android font scaling, Arabic/English search, repeated open/back, edit/save, rapid favorite toggles, import merge/replace, force-stop/relaunch, and airplane-mode startup.
