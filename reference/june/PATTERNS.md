# June-derived design and Compose patterns for Pharmacy Pocket

Reference repository: `DenserMeerkat/June`

Pinned commit: `89b7efdfac6fd51f58e957f50932840ad05ff6a7`

This is a design/engineering reference, not a requirement to reproduce June visually.

## UX concepts

### Primary actions should be obvious without reading

June frequently gives the dominant action a dedicated visual position instead of placing it among equal-weight text actions. Pharmacy Pocket should follow the same principle.

For Home, medicine creation, camera capture, save, and destructive confirmation, favor one unmistakable primary action and reduce competing controls.

### Context should modify the action, not multiply actions

June's Home FAB changes visual context rather than creating a row of several equally prominent buttons.

For Pharmacy Pocket, prefer a stable Add/Camera interaction whose label, icon, badge, or supporting action changes with context rather than adding more permanent buttons.

### Progressive disclosure

Secondary settings and editor details should be available without dominating the initial screen.

Pharmacy Pocket should keep:
- medicine name/category/price and Save immediately understandable;
- photos/codes/notes available nearby;
- advanced or infrequent fields behind expandable sections or secondary screens.

Do not hide guidance that is required to correctly enter a visible field.

## Compose patterns

### UI is a function of explicit state

Prefer:

```kotlin
Screen(state = state, onAction = viewModel::onAction)
```

over scattered local booleans that independently describe the same workflow.

For the medicine media flow, one state owner should determine whether the experience is:
- scanning,
- capturing,
- cropping,
- reviewing a code,
- saving,
- failed,
- or idle.

Avoid two independent sources of truth for the same photo/code.

### Lifecycle-aware collection

Use `collectAsStateWithLifecycle()` for UI-facing Flow/StateFlow values.

Long-running collectors tied to an Activity/Composable lifecycle should use lifecycle-aware scopes rather than permanent collection from composition.

### State belongs at the lowest shared owner

If two destinations need the same editor state, give them a shared ViewModel/back-stack owner instead of serializing large mutable payloads through navigation arguments.

This mirrors June's editor/media navigation approach and fits Pharmacy Pocket's crop/editor/detail flows.

## Screen composition

### App shell

Keep global concerns at the application shell:
- theme,
- layout direction,
- app-wide settings,
- navigation,
- startup handling.

Keep medicine-specific state out of the root app composable unless it truly spans destinations.

### Screen hierarchy

A well-composed screen should usually have:

1. clear top-level identity;
2. immediate primary content;
3. one dominant action;
4. compact secondary controls;
5. optional details below or behind progressive disclosure.

Do not make every section look like a separate floating card. Group related content and let spacing establish hierarchy.

## Navigation philosophy

June uses type-safe routes and centralized transition behavior.

For Pharmacy Pocket:

- prefer typed destinations over string route conventions;
- keep navigation arguments small and stable;
- pass identifiers rather than JPEG byte arrays or mutable editor structures;
- share a ViewModel/state owner where a multi-step flow is conceptually one task;
- use `launchSingleTop`/back-stack behavior deliberately;
- keep transitions subtle enough that navigation still feels immediate.

### Transition rule

Default navigation should be a short spatial transition plus fade, not a decorative animation.

Use stronger animation only when it communicates continuity, such as:
- expanding a selected medicine;
- camera -> crop;
- crop -> accepted media preview;
- filter selection;
- contextual FAB transformation.

## Animation and motion

June is a useful reference because its motion is state-driven.

Prefer:
- Material motion specs;
- `AnimatedContent` for content identity changes;
- `animateColorAsState` for contextual colors;
- `animateBounds`/lookahead only where spatial continuity matters;
- enter/exit animation for surfaces with a real appearance/disappearance state.

Avoid animating every layout change.

Motion must never delay Save, camera shutter, barcode confirmation, or list interaction.

## Spacing

Use a small consistent spacing scale instead of arbitrary per-screen values.

Recommended Pharmacy Pocket baseline:

- 4 dp: micro separation/badge offsets;
- 8 dp: related control spacing;
- 12 dp: compact internal grouping;
- 16 dp: normal screen/card padding;
- 20-24 dp: section separation;
- 32 dp+: only for deliberate large visual breaks.

The exact values may differ, but repeated components should share the same rhythm.

Use shape families consistently as well. Avoid a unique corner radius for every component.

## Material 3 usage

June uses Material 3 as a system rather than as a component catalog.

For Pharmacy Pocket:

- derive colors from `MaterialTheme.colorScheme`;
- derive typography from `MaterialTheme.typography`;
- use semantic surface/container roles instead of hand-picked unrelated colors;
- use filled/tonal/outlined emphasis according to action priority;
- support dark mode and system colors cleanly;
- consider Material Expressive APIs when they improve hierarchy or motion, but keep experimental APIs isolated so they can be replaced later.

Dynamic color should be an option, not a reason to lose Pharmacy Pocket's visual identity.

## State handling

### Prefer immutable screen state

A screen should receive one coherent state model rather than many unrelated mutable holders.

Example shape:

```kotlin
data class MedicineEditorUiState(
    val medicine: MedicineDraft,
    val media: MedicineMediaState,
    val validation: ValidationState,
    val isSaving: Boolean,
    val error: UiError? = null,
)
```

The exact model may vary. The important rule is that impossible combinations should be difficult to represent.

### Events instead of direct state mutation from UI

UI sends intentions such as:

```kotlin
onAction(MedicineEditorAction.Save)
onAction(MedicineEditorAction.OpenCamera)
onAction(MedicineEditorAction.AcceptCrop(...))
```

The state owner decides the transition.

This is especially important for asynchronous camera/gallery/database paths.

## Adaptive design

June's dependency catalog includes modern adaptive Material libraries, but not every installed dependency is proof that every adaptive API is used throughout the app.

For Pharmacy Pocket, adaptive design should focus first on real constraints:

- short phones;
- large font scale;
- keyboard visible;
- gesture navigation;
- RTL/Arabic;
- landscape;
- tablets/foldables if tested.

Rules:
- do not put critical confirmation buttons in a clipped non-scrollable camera overlay;
- pin or inset primary actions above system navigation when appropriate;
- let long/raw barcode or QR content wrap/scroll without pushing Save off-screen;
- use width constraints and responsive arrangements instead of fixed assumptions.

## Component architecture

Build reusable components when they encode a design or behavioral rule, not merely to reduce line count.

Good candidates in Pharmacy Pocket:

- app top bar;
- medicine card;
- contextual FAB cluster;
- category/subcategory selector;
- expandable editor section;
- media preview;
- camera control surface;
- scan confirmation surface;
- price field group;
- empty/error/loading surface;
- confirmation dialog.

Each component should have:
- a narrow responsibility;
- state passed in explicitly;
- callbacks for events;
- no hidden database ownership;
- semantics/content descriptions where interactive.

## What to adopt from June first

Highest-value improvements for Pharmacy Pocket:

1. Unify theme tokens and shape/spacing rhythm.
2. Make Home bottom actions/contextual FAB feel intentional rather than assembled.
3. Centralize navigation transitions.
4. Move more screens toward immutable UI state + events.
5. Reduce independent local state in editor/media flows.
6. Standardize expandable sections and dialogs.
7. Adopt lifecycle-aware state collection consistently.
8. Use contextual motion only where it explains state changes.
9. Review settings hierarchy using June's settings organization as a reference.
10. Keep common medicine actions reachable in one tap wherever practical.

## What not to copy

Do not copy:
- June's journal-specific domain model;
- tags/spaces/people concepts;
- its sync architecture unless Pharmacy Pocket needs equivalent sync;
- networking libraries without a concrete need;
- animation simply because June has it;
- visual complexity that increases taps or hides medication information.

The reference is successful only if Pharmacy Pocket becomes simpler, faster, more native, and more coherent.
