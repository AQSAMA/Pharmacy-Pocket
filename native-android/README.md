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

## Spreadsheet lists

Open **☰ > Import a spreadsheet** to create a separate offline list from XLSX,
UTF-8 CSV (comma, semicolon or tab separated), or a Google Sheets document link.
Private Google Sheets can be exported to XLSX and imported using the file picker;
link imports require “Anyone with the link” viewing access and respect the link's
`gid` worksheet. These are local copies, not live synchronization.

Choose the worksheet and header row, review suggested mappings, assign display
labels, and select up to six extra fields for cards. Map categories in order from
level 1 through level 4. Use **Custom field** to retain any other column, or **Skip
column** to omit it. Decimal prices and original text are preserved. Price mappings
accept nonnegative numbers with the selected **1,234.56** or **1.234,56** number
format, including Arabic numerals/separators; currency symbols should be kept in their own column. Review
errors before creating the list. Empty Name rows are counted and skipped; duplicate
names are retained as distinct entries.

Switch between **My medications** and imported lists from the side menu. Imported
items use the existing cards, favorites, sorting, normalized search, photo/code
capture, editor, details and Trash. **Tune** exposes deeper category filters.
The editor's **Imported fields** section contains fields omitted from the card.
Settings identify the selected list: categories, currency, JSON backups/imports and
Trash belong to that list; theme and text size are shared. Custom fields survive
editing, Trash/restore and JSON backup round trips. Restore an imported-list JSON
backup while that imported list is selected.

Limits: 32 MB source files, 128 MB expanded XLSX data, 64 worksheets, 100,000 rows
across the workbook, 128 columns and 2 million cells. A cell can contain up to 4,096
characters. XLSX formulas use stored values and are never executed. XML document
declarations (DTD) and custom entities are rejected. Interrupted creation is
reconciled on startup: committed lists are recovered and incomplete seeds discarded. Excel styling,
merged-cell expansion and date-format conversion are not imported.

To run the supplied workbook integration test without committing the source file:

```sh
PHARMACY_SPREADSHEET_FIXTURE=/path/to/products-price.xlsx gradle testDebugUnitTest
```
