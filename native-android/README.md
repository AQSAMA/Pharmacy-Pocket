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

Use the Rows, Columns and Preview tabs. Choose the worksheet, any header row by its
spreadsheet number, and the first/last data rows. Review suggested mappings, assign display
labels, and select up to six extra fields for cards. Map categories in order from
level 1 through level 4. Use **Custom field** to retain any other column, or **Skip
column** to omit it. Decimal prices and original text are preserved. Price mappings
accept nonnegative numbers with the selected **1,234.56** or **1.234,56** number
format, including Arabic numerals/separators; currency symbols should be kept in their own column. Review
errors before creating the list. Empty Name rows are counted and skipped; duplicate
names are retained as distinct entries.

Reopen **☰ > Import settings** for the selected list to change its name, row range,
column roles, labels and card fields. Imports retain a compressed local source table;
skipped columns remain recoverable. Earlier imports can be configured from their saved
fields, although previously skipped source columns cannot be recovered. Applying changes
preserves local field edits, favorites, codes and photos by source row ID. Excluded active
rows move to Trash; expanding the selection does not silently restore Trash or recreate
moved/permanently deleted rows. Settings and source tables are local to the installation;
JSON backups preserve medication fields but do not embed the spreadsheet source table.

Switch between **My medications** and imported lists from the side menu. Imported
items use the existing cards, favorites, sorting, normalized search, photo/code
capture, editor, details and Trash. **Tune** exposes deeper category filters.
The editor's **Imported fields** section contains fields omitted from the card.
The prepared search index stays in memory while visiting details, so returning to a
large unchanged list does not prepare it again. Switching lists releases that index.

From an imported medicine's details, choose **Move to My medications**. Set a common
name, your pharmacy price and a main-list category. Its main card shows the common name,
original name underneath (scientific name if the names match), and your chosen price.
All source fields/prices/currency, notes, photo, codes and favorite status remain in the
medicine's details and JSON backups. Source details stay separate from your editable
main-list price. Package-code conflicts block the move without changing either list;
a successful move places the original row in the source list's Trash.
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

## Custom fields, nested folders, and imported actions

In any medicine editor, open **Custom fields & card layout**. Add a label and value,
choose a color (or leave it empty for the theme color), and turn **Show on card** on
or off. Hold a field to drag it between **Above name**, **Below price**, and
**Card bottom**, or reorder it among the other fields. The editor lifts the dragged
field, highlights its target, and scrolls near the list edges. Arrow buttons and
position chips provide alternatives to dragging. Save the medicine to persist the
layout. Source fields can also be edited and styled here; source price fields stay
separate from your own main-list price. Colors apply to cards; details use the theme
colors for long-form reading. Field order, color, and placement survive JSON backups,
Trash/restore, and import-settings updates for retained fields.

In **Settings > Categories**, create or edit a category and choose its **Parent
folder**. Folders can contain further folders without a fixed depth limit (up to
256 category definitions per list). A medicine belongs to its selected folder;
browsing a parent includes medicines from all descendants. Existing subcategories
and mapped spreadsheet category levels appear as children without rewriting existing
records. Repeated folder names in different branches remain separate. Cyclic or
missing parents are rejected.

Choose a navigation style in **Settings > Category navigation**:

- **Breadcrumbs**: a compact path and horizontally scrolling children; the default.
- **Folders**: two-column folder tiles and medicine counts.
- **Tree**: expand/collapse branches and select a folder.
- **Columns**: adjacent independently scrolling folder levels.
- **Floating explorer**: a compact path with a bottom sheet for browsing the tree.

All five views share the same hierarchy and selection behavior. The choice is saved
per list. The breadcrumb path lets you return to any ancestor or All.

In an imported medication’s details, open **Copy, move or merge**:

- **Copy** to My medications or another imported list. The source stays available.
- **Move** to either destination. The source goes to its list’s Trash and is not
  silently recreated when import settings change.
- **Merge** into an explicitly selected existing medication in My medications.
  Review the common name and both prices that will be retained. Original/scientific
  names, source prices, notes, description, extra fields, photo and package codes are
  brought in. The destination’s ID, category, favorite and date added stay unchanged.
  Existing personal custom fields are retained. An imported photo replaces the
  destination photo when available; otherwise the existing photo stays. Source notes
  and description replace those destination fields, including empty values. The source
  stays in place by default; optionally move it to Trash after merging.

Code conflicts or more than 20 combined codes block the operation without modifying
records. Transfers use the existing attached-database transaction for medication,
photo and source-Trash writes. Original source price text and currency remain in
fields rather than becoming your editable main-list price.
