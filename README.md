# Pharmacy Pocket

This repository temporarily contains both Android implementations of Pharmacy Pocket while the native rewrite matures.

## Applications

| Folder | Stack | Android application ID | Role |
| --- | --- | --- | --- |
| `legacy-expo/` | Expo + React Native | `com.aqsama.pharmacypocket` | Current/reference application |
| `native-android/` preview flavor | Kotlin + Jetpack Compose | `com.aqsama.pharmacypocket.native` | Side-by-side native testing |
| `native-android/` production flavor | Kotlin + Jetpack Compose | `com.aqsama.pharmacypocket` | Reserved for eventual cutover |

The Expo app and native preview can be installed on the same Android device at the same time.

## Migration strategy

During the maturation period, the two apps are independent installations. Exchange data through the shared Pharmacy Pocket JSON backup format.

When the native application is approved, the production flavor can take over the original application ID. Before using it as an in-place update, Android signing compatibility with the installed legacy app must be configured and verified.

## Repository layout

    Pharmacy-Pocket/
    ├── legacy-expo/
    ├── native-android/
    ├── .github/workflows/
    ├── .gitignore
    └── README.md

CI is path-scoped: changes to one application build and test that application without unnecessarily building the other.
