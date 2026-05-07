# Requirements Quality Checklist — Node List Layout

Use this checklist to review the specification before implementation starts.

## Scope and User Value

- [ ] The spec clearly describes the user problem (large meshes require denser node lists).
- [ ] The feature scope is limited to layout density switching, compact toggles, adaptive sizing, and help documentation.
- [ ] Out-of-scope items are implicitly bounded (no new data model, no filter changes, no navigation changes).

## User Stories

- [ ] Four user stories are present and prioritized P1–P3.
- [ ] Each user story is independently testable.
- [ ] Each user story explains why the priority matters.
- [ ] Acceptance scenarios cover success and edge cases.

## Architecture Fit

- [ ] The spec uses Compose Multiplatform + Material 3 terminology.
- [ ] The spec uses Navigation 3 patterns where applicable.
- [ ] All business logic and UI reside in `commonMain`.
- [ ] No platform-specific code is required for this feature.
- [ ] The feature modifies existing modules (`feature/node`, `feature/settings`, `core/prefs`) rather than creating unnecessary new modules.

## Preferences and Persistence

- [ ] All 10 DataStore keys are documented with names, types, and defaults.
- [ ] The density enum is persisted as a string (enum name) with a safe fallback.
- [ ] Toggle defaults are specified (all `true` except `lastHeardIsRelative`).
- [ ] Preference key naming uses an enum or constant to prevent string drift.

## Layout Requirements

- [ ] The compact layout structure (2-column, 3-row) is clearly documented.
- [ ] The adaptive circle sizing formula is specified with min/max bounds.
- [ ] All 9 compact toggles are listed with their data conditions.
- [ ] The "Relative Last Heard Time" disabled state dependency is documented.
- [ ] Row rendering order matches the toggle order in settings.
- [ ] Complete layout is documented as unconditional (no toggles).

## Edge Cases

- [ ] All compact toggles disabled → only name row + minimum circle.
- [ ] Missing data for toggled-on field → field absent, no placeholder.
- [ ] Signal/Hops mutual exclusivity → documented and tested.
- [ ] Channel 0 → hidden regardless of toggle.
- [ ] Connected node → distance excluded.
- [ ] MQTT nodes → signal excluded.
- [ ] Future dates → last heard hidden (> 1 year guard).

## Accessibility

- [ ] TalkBack semantics are required for both layouts (FR-025).
- [ ] Content descriptions cover all visible fields.

## Resource Conventions

- [ ] String resources planned for `core/resources/.../values/strings.xml`.
- [ ] Icons reference `MeshtasticIcons`.
- [ ] All framework references are specific to the Meshtastic-Android KMP stack.

## Testing and Delivery

- [ ] Unit tests for preference defaults and `lineCount` calculation.
- [ ] Compose UI tests for both layout variants.
- [ ] Full verification command documented: `./gradlew spotlessApply detekt assembleDebug test allTests`.
