# June reference

This directory defines **June** as the primary external Android UI and Compose architecture reference for Pharmacy Pocket.

Upstream repository: https://github.com/DenserMeerkat/June

Pinned reference commit:

```
89b7efdfac6fd51f58e957f50932840ad05ff6a7
```

Upstream license: **GPL-3.0**

## Why this is a reference instead of a vendored second Android project

Pharmacy Pocket should remain a single Android application. Keeping an entire second Gradle project in the repository would make code search, automated review, dependency analysis, and agent context noisier and can cause tools to reason about the wrong `HomeScreen`, theme, navigation graph, or data layer.

The pinned upstream source should be consulted directly when implementing or redesigning Pharmacy Pocket. This directory records exactly what to study and how to translate it into Pharmacy Pocket.

## Reference priorities

Use June especially for:

1. Material 3 / Material Expressive composition.
2. Motion and state-driven transitions.
3. Screen hierarchy and visual density.
4. Bottom navigation and contextual FAB behavior.
5. Theme, typography, dynamic color, AMOLED handling.
6. Navigation structure and transitions.
7. Lifecycle-aware state collection.
8. Reusable component boundaries.
9. Editor and settings screen organization.
10. Clean separation between UI, state, domain, and persistence.

Do **not** reshape Pharmacy Pocket into a journal application or copy June screens blindly. Preserve Pharmacy Pocket's medicine-focused information architecture and workflows.

## Most useful upstream files

### App shell and navigation

- `app/src/main/java/com/denser/june/presentation/JuneApp.kt`
- `app/src/main/java/com/denser/june/presentation/navigation/JuneNavHost.kt`

Study these for lifecycle-aware collection, CompositionLocal usage, route ownership, navigation state, and restrained transitions.

### Theme and Material system

- `app/src/main/java/com/denser/june/presentation/theme/JuneTheme.kt`
- `app/src/main/java/com/denser/june/presentation/screens/settings/tiles/PaletteSelectionSettingsItem.kt`

Study dynamic Material colors, typography, AMOLED mode, system-bar appearance, and expressive Material APIs.

### Home composition and motion

- `app/src/main/java/com/denser/june/presentation/screens/home/HomeScreen.kt`
- `app/src/main/java/com/denser/june/presentation/screens/home/components/HomeBottombar.kt`

The bottom bar is a particularly useful reference for contextual actions, animated state changes, `AnimatedContent`, `animateBounds`, `LookaheadScope`, Material motion specs, and keeping primary actions visually obvious.

### Shared components

- `app/src/main/java/com/denser/june/presentation/components/`

Useful examples include dialogs, fullscreen surfaces, placeholders, media UI, search/filter components, and stateful controls.

### ViewModel/state patterns

- `app/src/main/java/com/denser/june/presentation/screens/home/`
- `app/src/main/java/com/denser/june/presentation/screens/settings/SettingsVM.kt`
- `app/src/main/java/com/denser/june/presentation/screens/home/journals/JournalsVM.kt`

Use these to study immutable screen state, Flow/StateFlow updates, and lifecycle-aware UI observation.

### Core/data architecture

- `core/src/main/java/com/denser/june/core/`
- `core/src/main/java/com/denser/june/core/data/database/`
- `core/src/main/java/com/denser/june/core/di/CoreModule.kt`

The goal is not to copy June's domain model. Use the separation and ownership rules as architectural references.

### Dependency catalog

- `gradle/libs.versions.toml`

Useful for seeing the AndroidX/Compose stack used by the pinned June version. Do not add a dependency to Pharmacy Pocket only because June uses it; add it only when Pharmacy Pocket has a concrete need.

## When implementing UI work

Before redesigning a significant Pharmacy Pocket screen:

1. Identify the closest June interaction or component.
2. Read the relevant June source at the pinned commit.
3. Extract the interaction principle, component boundaries, state transitions, spacing rhythm, and motion behavior.
4. Re-implement those principles around Pharmacy Pocket's own state and domain model.
5. Prefer official Material 3 APIs where possible.
6. Keep accessibility, Arabic/RTL, large text, short displays, and Android gesture insets first-class requirements.
7. Verify that visual polish does not add extra taps to common medicine workflows.

See `PATTERNS.md` for the concrete design rules derived from June.
