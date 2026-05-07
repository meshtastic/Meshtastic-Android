# Tasks — Node List Layout

## Phase 0: Design Standards Gate (Blocking)

**Purpose**: Review Meshtastic design standards before shipping any new UI for node list density and settings.

- [ ] NL-T000 `[UI-GATE]` Review `.skills/design-standards/SKILL.md` and upstream Meshtastic design standards; record constraints for `NodeItemCompact`, `NodeLayoutSettings`, density picker, and `NodeListHelp` sheet styling.

**Dependencies**: None — this phase blocks all UI work.

---

## Phase 1: Preferences and Data Model

**Purpose**: Define the density enum and add all DataStore preference keys.

- [ ] NL-T001 [P] Create `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/model/NodeListDensity.kt` with `enum class NodeListDensity { COMPLETE, COMPACT }`.
- [ ] NL-T002 [P] Add `NodeListLayoutPreferences` enum or object in `core/prefs` defining all 10 DataStore keys (`nodeListDensity` + 9 compact toggles) with their defaults.
- [ ] NL-T003 Add DataStore preference accessors for all 10 keys in `UiPrefsImpl.kt` as `StateFlow<Boolean>` / `StateFlow<NodeListDensity>` with eager seeding.
- [ ] NL-T004 Add string resources for all toggle labels, help text, density option labels, and section headers to `strings.xml`. Run `python3 scripts/sort-strings.py`.

**Dependencies**: None — this phase can start immediately.
**Parallel**: NL-T001 and NL-T002 are independent of each other.

---

## Phase 2: Compact Row Composable

**Purpose**: Build the new `NodeItemCompact` composable with toggle-driven field visibility and adaptive circle sizing.

- [ ] NL-T010 [P] Create `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeItemCompact.kt` with the two-column layout structure (Column 1: circle + battery, Column 2: up to 3 rows).
- [ ] NL-T011 Implement Row 1 (always visible): lock/key icon, long name, favorite star.
- [ ] NL-T012 Implement Row 2 (toggle: `shouldShowLastHeard`): online/offline icon + timestamp via `LastHeardInfo`, with relative time support via `lastHeardIsRelative`.
- [ ] NL-T013 Implement Row 3 combined icons `Row` with `VerticalDivider` separators: Distance+Bearing, Hops Away, Signal, Channel, Device Role, Log Icons — each gated by its toggle and data condition.
- [ ] NL-T014 Implement adaptive circle sizing: `max(36.dp, min(70.dp, 24.dp × lineCount))` based on active row groups.
- [ ] NL-T015 Implement conditional battery rendering below circle (toggle: `shouldShowPower`).
- [ ] NL-T016 Add `Modifier.semantics` / `contentDescription` for full TalkBack coverage on compact rows.
- [ ] NL-T017 Add future date guard: hide last heard if timestamp is > 1 year in the future.

**Dependencies**: Requires Phase 1 (NL-T001–NL-T004).
**Parallel**: NL-T011–NL-T017 can be developed in parallel once NL-T010 scaffold exists.

---

## Phase 3: Complete Row Refactor

**Purpose**: Ensure the existing `NodeItem` serves cleanly as the Complete layout counterpart.

- [ ] NL-T020 Review existing `NodeItem.kt` and confirm it displays all fields unconditionally (no toggle logic). Refactor if needed to ensure clarity.
- [ ] NL-T021 Ensure `LoRaSignalStrengthMeter` gradient gauge is used for signal display in Complete mode (not just a colored icon).
- [ ] NL-T022 Ensure Complete rows have 3.dp top/bottom padding.
- [ ] NL-T023 Add `Modifier.semantics` / `contentDescription` for full TalkBack coverage on complete rows (if not already present).

**Dependencies**: None — can run in parallel with Phase 2.

---

## Phase 4: NodeList Density Switching

**Purpose**: Wire the density preference into the node list so it delegates to the correct row composable.

- [ ] NL-T030 Modify `NodeListViewModel` to expose `nodeListDensity: StateFlow<NodeListDensity>` and all 9 compact toggle flows.
- [ ] NL-T031 Modify `NodeListScreen` to collect density state and delegate to `NodeItem` (Complete) or `NodeItemCompact` (Compact) per row.
- [ ] NL-T032 Ensure `LazyColumn` uses stable `key` parameters for both layout variants.
- [ ] NL-T033 Verify smooth scrolling at 60fps with 200+ nodes in Compact mode.

**Dependencies**: Requires Phase 2 (NL-T010+) and Phase 3 (NL-T020+).

---

## Phase 5: Settings UI

**Purpose**: Build the Node Layout settings section with density picker, toggles, and live preview.

- [ ] NL-T040 [P] Create `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/app/NodeLayoutSettings.kt` with `SegmentedButton` for Complete/Compact density selection.
- [ ] NL-T041 Add 9 `Switch` composable toggles (ordered by layout position) that appear only when Compact is selected.
- [ ] NL-T042 Add descriptive text ("The Complete layout displays all available node data...") when Complete is selected.
- [ ] NL-T043 Implement "Relative Last Heard Time" toggle disabled state when "Last Heard Time" is off.
- [ ] NL-T044 Implement live preview composable below toggles using first node from Room KMP query (sorted by `lastHeard` descending), with placeholder when database is empty.
- [ ] NL-T045 Integrate `NodeLayoutSettings` into the existing App Settings screen in `feature/settings`.

**Dependencies**: Requires Phase 1 (NL-T002–NL-T003) for DataStore keys. Can start NL-T040 scaffold in parallel with Phase 2.

---

## Phase 6: Help Sheet

**Purpose**: Add the signal strength help documentation accessible from the node list.

- [ ] NL-T050 [P] Create `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeListHelp.kt` as a `ModalBottomSheet`.
- [ ] NL-T051 Add "Node Details" section with 4 signal strength entries: Good (green), Fair (yellow), Bad (orange), Very Bad (red) with MeshtasticIcons signal icons.
- [ ] NL-T052 Add gradient signal strength meter documentation entry with a mini gauge visual.
- [ ] NL-T053 Add help button (?) trigger to `NodeListScreen` that opens the help sheet.

**Dependencies**: None — can run in parallel with Phases 2–5.

---

## Phase 7: Testing and Verification

**Purpose**: Validate all requirements and ensure no regressions.

- [ ] NL-T060 Write unit tests for `NodeListDensity` enum and `lineCount` calculation logic.
- [ ] NL-T061 Write unit tests for DataStore preference defaults (all `true` except `lastHeardIsRelative`).
- [ ] NL-T062 Write unit tests for edge cases: future date filtering, channel 0 hiding, signal/hops mutual exclusivity, connected node distance exclusion, MQTT signal exclusion.
- [ ] NL-T063 Write Compose UI tests for `NodeItemCompact` with various toggle combinations.
- [ ] NL-T064 Write Compose UI tests for density switching in `NodeListScreen`.
- [ ] NL-T065 Run `./gradlew :feature:node:allTests :feature:settings:allTests` to validate.
- [ ] NL-T066 Run `./gradlew spotlessApply detekt assembleDebug test allTests` for full verification.

**Dependencies**: Requires Phases 1–6.

---

## Dependency Graph

```
Phase 1 (Preferences) ──┬──→ Phase 2 (Compact Row) ──┬──→ Phase 4 (Density Switching) ──→ Phase 7 (Testing)
                         │                             │
                         ├──→ Phase 3 (Complete Refactor)┘
                         │
                         └──→ Phase 5 (Settings UI) ──→ Phase 7
                         
Phase 6 (Help Sheet) ────────────────────────────────────→ Phase 7
```

## Task Summary

| Phase | Tasks | Parallel Opportunities |
|-------|-------|----------------------|
| 1. Preferences | 4 | NL-T001 ∥ NL-T002 |
| 2. Compact Row | 8 | NL-T011–NL-T017 after NL-T010 |
| 3. Complete Refactor | 4 | Entire phase ∥ Phase 2 |
| 4. Density Switching | 4 | Sequential |
| 5. Settings UI | 6 | NL-T040 scaffold ∥ Phase 2 |
| 6. Help Sheet | 4 | Entire phase ∥ Phases 2–5 |
| 7. Testing | 7 | NL-T060–NL-T064 parallelizable |
| **Total** | **37** | **21 can run in parallel** |
