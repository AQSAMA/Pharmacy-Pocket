# Pharmacy Pocket production releases

## 1. Preserve the Android signing identity

The production package is `com.aqsama.pharmacypocket`. Android accepts an in-place update only when the new APK is signed by the same certificate as the installed package and has a higher `versionCode`.

If you still have the keystore that signed the legacy Expo production APK, **reuse that keystore**. Do not generate a replacement key merely to complete this migration.

To inspect an existing keystore:

```sh
keytool -list -v -keystore /path/to/legacy-release.jks -alias YOUR_ALIAS
```

To inspect a previously distributed APK:

```sh
apksigner verify --verbose --print-certs /path/to/legacy.apk
```

Compare the SHA-256 certificate fingerprint before publishing the first native production release.

If the legacy signing key is unavailable, a newly generated key cannot update an installed legacy package in place. Users of that package must export a Pharmacy Pocket JSON backup before uninstalling it, install the newly signed native app, and import the backup.

## 2. Create a production keystore only when no reusable production key exists

Run this locally and let `keytool` prompt for the passwords. Do not put the passwords in shell history or repository files.

```sh
keytool -genkeypair -v \
  -keystore pharmacy-pocket-release.jks \
  -alias pharmacy-pocket \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Keep the keystore in a secure backup outside this repository.

Create a one-line Base64 representation for GitHub Actions:

```sh
base64 pharmacy-pocket-release.jks | tr -d '\n' > pharmacy-pocket-release.jks.b64
```

Get the certificate fingerprint:

```sh
keytool -list -v -keystore pharmacy-pocket-release.jks -alias pharmacy-pocket
```

## 3. Configure GitHub Actions secrets

Add these repository secrets:

- `ANDROID_KEYSTORE_BASE64` — contents of the one-line `.b64` file.
- `ANDROID_KEYSTORE_PASSWORD` — keystore password.
- `ANDROID_KEY_ALIAS` — signing-key alias.
- `ANDROID_KEY_PASSWORD` — key password; it may be the same as the keystore password.
- `ANDROID_SIGNING_CERT_SHA256` — expected SHA-256 certificate fingerprint. Colons and letter case are accepted.

The release workflow restores the keystore only inside the temporary GitHub runner, never prints secret values, verifies the alias, signs the APK/AAB, verifies the APK signature with `apksigner`, checks the APK certificate against `ANDROID_SIGNING_CERT_SHA256`, and removes the temporary keystore.

## 4. Versioning

`native-android/VERSION` is the production release version source and must contain exactly `MAJOR.MINOR.PATCH`.

The production Android version code is deterministic:

```text
versionCode = MAJOR * 10,000,000 + MINOR * 10,000 + PATCH
```

For example, `3.0.0` is `30,000,000`. Minor versions must be 0–999 and patches 0–9999.

Preview builds use the separate package `com.aqsama.pharmacypocket.native` and the repository's preview-only signing key. Their version codes are generated monotonically from preview publication order so a newly published preview can update the previous preview in place.

## 5. Preview releases

A push to `main` that changes the native Android app automatically runs **Android Preview Release** and publishes an optimized GitHub **Pre-release** APK.

The preview APK:

- is built with minification and resource shrinking;
- uses `com.aqsama.pharmacypocket.native`, so it can coexist with production;
- uses the stable preview-only signing key, so future previews can update it;
- is for testing and daily use before a production release, not a production signing identity.

Pull requests run **Android CI** and upload both `pharmacy-pocket-preview-<PR number>` and `pharmacy-pocket-debug-<PR number>` artifacts. Open the pull request's successful Android CI run and download the preview artifact for the optimized **Pharmacy Pocket Preview** APK, or the debug artifact for **Pharmacy Pocket Dev**. Both artifacts are retained for 14 days.

CI previews use the same preview package and signing key, with a version code above the latest published preview at build time. They can update that published preview in place; Dev is a separate app with separate data. CI artifacts do not create GitHub pre-releases. A newer installed CI preview may require downloading a newer build to avoid a version-code downgrade.

## 6. Publish a production release

1. Change `native-android/VERSION` in a pull request.
2. Let Android CI pass and merge that pull request to `main`.
3. Tag the exact merged commit and push the tag:

```sh
git checkout main
git pull --ff-only
git tag v3.0.0
git push origin v3.0.0
```

Use the version you actually placed in `VERSION`.

The production release workflow rejects malformed tags, a tag/version mismatch, tags that are not on `main`, and a version code that is not greater than already published strict `vMAJOR.MINOR.PATCH` releases. It then runs tests/lint, builds signed release APK and AAB files, verifies signing and alignment, writes SHA-256 checksums, and creates a normal GitHub Release.

Ordinary commits to `main` may publish **preview pre-releases**, but they never publish a **production release**. Production publishing still requires an explicit `vMAJOR.MINOR.PATCH` tag.
