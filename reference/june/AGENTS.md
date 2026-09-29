# Agent instructions for the June reference

This directory documents an external reference project.

Primary reference:
- Repository: `DenserMeerkat/June`
- Commit: `89b7efdfac6fd51f58e957f50932840ad05ff6a7`
- License: GPL-3.0

When a task concerns Pharmacy Pocket UI, Compose architecture, navigation, motion, theming, screen composition, or state ownership:

1. Read `reference/june/README.md` and `reference/june/PATTERNS.md`.
2. When useful, inspect the relevant file directly in June at the pinned commit.
3. Adapt the idea to Pharmacy Pocket's domain instead of mechanically copying a whole screen.
4. Production code remains under `native-android/`.
5. Do not add June as a Gradle module or make Pharmacy Pocket build against June.
6. Do not treat June's dependency choices as automatic requirements.
7. Keep Pharmacy Pocket's established constraints: fast one-tap workflows, phone-first usability, Arabic/RTL, large text, short screens, and offline-first local data.
8. For camera/photo/barcode work, preserve one explicit state owner and an end-to-end persistence path.
9. Prefer small focused PRs and do not modify `main` directly.

If source is copied from June rather than independently reimplemented, preserve the applicable GPL-3.0 notices and attribution.
