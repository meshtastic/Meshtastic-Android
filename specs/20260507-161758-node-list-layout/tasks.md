# Tasks: Node List Layout

**Input**: Design documents from `/specs/002-node-list-layout/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, m3-accessibility-audit.md
**Tests**: Included — spec explicitly defines test scenarios per user story and plan references Phase 7 testing.
**Organization**: Tasks grouped by user story (US1–US4) with shared infrastructure in Setup/Foundational phases.

## Format: `[NL-TXXX] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **KMP commonMain**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/`
- **Core prefs**: `core/prefs/src/commonMain/kotlin/org/meshtastic/core/prefs/`
- **Core UI**: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/`
- **Settings**: `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/`
- **Resources**: `core/resources/src/commonMain/composeResources/values/strings.xml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Design gate, density enum, DataStore preference keys, and string resources required by all user stories.

- [x] NL-T001 `[UI-GATE]` Review `.skills/design-standards/SKILL.md` and upstream Meshtastic design standards; record constraints for `NodeItemCompact`, `NodeLayoutSettings`, density picker, and `NodeListHelp` sheet styling. This phase blocks all UI work.
- [x] NL-T002 [P] Create `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/model/NodeListDensity.kt` with `enum class NodeListDensity { COMPLETE, COMPACT }` (FR-001, FR-002).
- [x] NL-T003 [P] Create `NodeListLayoutPreferences` enum in `core/prefs/src/commonMain/kotlin/org/meshtastic/core/prefs/ui/NodeListLayoutPreferences.kt` defining all 10 DataStore keys (`nodeListDensity` + 9 compact toggles) with their defaults per data-model.md (NFR-003).
- [x] NL-T004 Add DataStore preference accessors for all 10 keys in `core/prefs/src/commonMain/kotlin/org/meshtastic/core/prefs/ui/UiPrefsImpl.kt` — density as `StateFlow<NodeListDensity>` (fallback to `COMPLETE` for invalid values), 9 toggles as `StateFlow<Boolean>` with eager seeding via `SharingStarted.Eagerly` (FR-002, FR-004, FR-005).
- [x] NL-T005 Add string resources for all toggle labels, density option labels ("Complete", "Compact"), settings section header ("Node Layout"), help sheet text, signal quality labels, and complete-mode descriptive text to `core/resources/src/commonMain/composeResources/values/strings.xml`. Run `python3 scripts/sort-strings.py` after.

**Dependencies**: NL-T001 blocks all UI phases (2+). NL-T002 and NL-T003 are independent. NL-T004 depends on NL-T002 + NL-T003.
**Checkpoint**: Preference infrastructure ready — all user stories can now begin.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Accessibility fix for existing `NodeItem` and ViewModel wiring that MUST complete before any user story can ship.

**⚠️ CRITICAL**: The existing `NodeItem` has zero row-level semantics — TalkBack reads 8–12 separate focus stops per node row. This is a HIGH priority fix (audit §2.1).

- [x] NL-T006 **[HIGH]** Add `Modifier.semantics(mergeDescendants = true)` with a composed `contentDescription` (aggregating name, connection status, favorite, last heard, online/offline, role, hops, battery, distance, heading, signal strength) and `role = Role.Button` on the outer `Card` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeItem.kt` (FR-025, audit §2.1, §2.3). Extract a `buildNodeDescription()` helper for reuse by `NodeItemCompact`.
- [x] NL-T007 Ensure `NodeItem` uses `titleMediumEmphasized` for node names (already at line 423 — verify no regressions) and confirm Complete rows have 3.dp top/bottom padding (FR-027). Adjust `Column` padding from 12.dp if needed to meet the 3.dp outer spec.
- [x] NL-T008 Ensure `NodeItem` uses `LoraSignalIndicator` / `NodeSignalQuality` composables for signal display in Complete mode — quality icon + SNR/RSSI text with quality color, not just a colored icon (FR-022). Verify existing `NodeSignalRow` at line 250 matches spec.
- [x] NL-T009 Modify `NodeListViewModel` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListViewModel.kt` to expose `nodeListDensity: StateFlow<NodeListDensity>` and all 9 compact toggle `StateFlow<Boolean>` values from `UiPrefsImpl` (FR-002, FR-004).

**Dependencies**: Phase 1 (NL-T002–NL-T004) must complete first.
**Checkpoint**: Foundation ready — existing NodeItem is accessible, ViewModel exposes density state.

---

## Phase 3: User Story 1 — Switch Between Complete and Compact Density (Priority: P1) 🎯 MVP

**Goal**: Users can switch between Complete and Compact density modes via Settings and see the node list re-render with the correct row style.

**Independent Test**: Open Settings > Node Layout, toggle between Complete and Compact, navigate to the Nodes tab, verify the list renders with the correct row style. Relaunch app and verify density persists.

### Implementation for User Story 1

- [x] NL-T010 [P] [US1] Create `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeItemCompact.kt` with the two-column `Row` layout scaffold: Column 1 (fixed width: `NodeChip` + optional battery), Column 2 (`Modifier.weight(1f)`: `Column(verticalArrangement = spacedBy(2.dp))`) (FR-009, FR-026, FR-027).
- [x] NL-T011 [US1] Implement Row 1 (always visible) in `NodeItemCompact.kt`: `NodeKeyStatusIcon` (PKC/key status), long name using `titleMediumEmphasized` (M3 Expressive), and favorite star icon. Row is non-toggleable (FR-012, FR-025).
- [x] NL-T012 [US1] Add `Modifier.semantics(mergeDescendants = true)` on the outer `Card` in `NodeItemCompact.kt` with composed `contentDescription` (reuse `buildNodeDescription()` from NL-T006) and `role = Role.Button` for TalkBack (FR-025, audit §2.1, §2.3).
- [x] NL-T013 [US1] Add compact row padding: 2.dp top/bottom on outer `Column` (FR-027 — intentional M3 deviation for density).
- [x] NL-T014 [P] [US1] Create `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/NodeLayoutSettings.kt` with `SingleChoiceSegmentedButtonRow` + `SegmentedButton` for Complete/Compact density selection. Write selected density to DataStore (FR-001, FR-002).
- [x] NL-T015 [US1] Add descriptive text in `NodeLayoutSettings.kt`: "The Complete layout displays all available node data. Fields with no data are automatically hidden." — shown when Complete is selected (FR-007).
- [x] NL-T016 [US1] Integrate `NodeLayoutSettings` into the existing App Settings screen in `feature/settings/` (R-005 — embedded section, no new navigation route).
- [x] NL-T017 [US1] Modify `NodeListScreen` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListScreen.kt` to collect `nodeListDensity` from ViewModel and delegate to `NodeItem` (Complete) or `NodeItemCompact` (Compact) per row (FR-009).
- [x] NL-T018 [US1] Ensure `LazyColumn` in `NodeListScreen.kt` uses stable `key = { it.num }` for both layout variants (already present at line 187 — verify no regression) (NFR-004).

**Checkpoint**: User Story 1 complete — density switching works end-to-end. Compact shows name-only rows, Complete shows existing full layout. Both persist across app restarts.

---

## Phase 4: User Story 2 — Configure Compact Layout Fields (Priority: P1)

**Goal**: Users can toggle individual data fields in the compact layout via Settings, and the live preview + node list update in real time.

**Independent Test**: Switch to Compact, disable all toggles one by one, verify each field disappears from both the preview and the node list. Re-enable them and verify they reappear.

### Implementation for User Story 2

- [x] NL-T019 [US2] Implement Row 2 (toggle: `shouldShowLastHeard`) in `NodeItemCompact.kt`: online/offline icon (green checkmark / orange moon) + timestamp via `LastHeardInfo`, with relative time support via `lastHeardIsRelative`. Guard with future date filter (> 1 year) (FR-013, FR-028).
- [x] NL-T020 [US2] Implement Row 3 combined metrics in `NodeItemCompact.kt` as `FlowRow(horizontalArrangement = Arrangement.SpaceBetween)` spanning full card width — no separators (FR-014, FR-033). Render in order: Distance+Bearing, Hops Away, Signal, Channel, Device Role, Log Icons — each gated by its toggle AND data conditions.
- [x] NL-T021 [US2] Implement Distance+Bearing rendering in Row 3: gate on `shouldShowLocation` toggle + node has positions + node is not connected node + valid location data for both user and node. Use `NumberFormatter.format()` for float values (FR-015, FR-028).
- [x] NL-T022 [US2] Implement Hops Away rendering in Row 3: gate on `shouldShowHops` toggle + `node.hopsAway > 0` (FR-016).
- [x] NL-T023 [US2] Implement Signal rendering in Row 3: gate on `shouldShowSignal` toggle + `node.hopsAway == 0` + `node.snr != 0` + `!node.viaMqtt`. Icon color via `determineSignalQuality(snr, rssi)`. **MUST** include `contentDescription = stringResource(quality.nameRes)` (e.g., "Signal: Good") for WCAG 1.4.1 — no color-only information (FR-017, audit §2.6).
- [x] NL-T024 [US2] Implement Channel rendering in Row 3: gate on `shouldShowChannel` toggle + `node.channel > 0` (FR-018).
- [x] NL-T025 [US2] Implement Device Role rendering in Row 3: gate on `shouldShowRole` toggle. Show role's `MeshtasticIcons` icon + conditional unmessagable, store-and-forward, and MQTT icons (FR-019).
- [x] NL-T026 [US2] Implement Log Icons rendering in Row 3: gate on `shouldShowTelemetry` toggle + node has at least one of: positions, environment metrics, detection sensor metrics, or trace routes. Show device metrics, positions (mappin), environment, detection sensor, trace routes (signpost) icons from `MeshtasticIcons` (FR-020).
- [x] NL-T027 [US2] Implement conditional battery rendering below `NodeChip` in Column 1: gate on `shouldShowPower` toggle + `node.batteryLevel != null` (FR-003 toggle order, spec §Toggle Reference).
- [x] NL-T028 [US2] Add 9 `SwitchPreference` toggles (from `core:ui`, NOT raw `Switch`) in `NodeLayoutSettings.kt`, ordered by layout position: Power, Last Heard Time, Relative Last Heard Time, Distance and Bearing, Hops Away, Signal (Direct Only), Channel, Device Role, Log Icons. Show only when Compact is selected (FR-003, audit §1.1).
- [x] NL-T029 [US2] Implement "Relative Last Heard Time" toggle disabled state (`enabled = false`) when "Last Heard Time" is toggled off in `NodeLayoutSettings.kt` (FR-006).
- [x] NL-T030 [US2] Implement live preview composable in `NodeLayoutSettings.kt` below toggles: query first node from Room KMP sorted by `lastHeard` descending, render via `NodeItem` or `NodeItemCompact` based on current density + toggle state using `collectAsState()`. Show placeholder text when database is empty (FR-008).

**Checkpoint**: User Story 2 complete — all 9 toggles control compact field visibility, live preview updates in real time, toggle states persist across app launches.

---

## Phase 5: User Story 3 — Adaptive Chip Sizing (Priority: P2)

**Goal**: The `NodeChip` in compact mode scales proportionally based on the number of active row groups.

**Independent Test**: Disable all optional rows (last heard + combined row), verify the chip shrinks to 36.dp minimum. Enable all rows, verify it grows to 70.dp maximum.

### Implementation for User Story 3

- [x] NL-T031 [US3] Implement `lineCount` computed property in `NodeItemCompact.kt`: count active row groups (1 base + 1 if `shouldShowLastHeard` + 1 if any combined-row toggle is enabled). Derive from toggle state, NOT actual data presence (R-003, data-model.md §Adaptive Chip Sizing).
- [x] NL-T032 [US3] Implement adaptive chip sizing in `NodeItemCompact.kt`: `max(36.dp, min(70.dp, 24.dp × lineCount))`. Use `Modifier.defaultMinSize()` (not hard `Modifier.size()`) to allow growth with system font scaling > 100% (FR-011, audit §2.8).
- [x] NL-T033 [US3] Ensure `NodeChip` always renders as a `NodeChip` composable at all sizes — maintaining consistent M3 `Card` styling (FR-010).

**Checkpoint**: User Story 3 complete — chip scales smoothly across 36.dp/48.dp/70.dp sizes based on toggle configuration.

---

## Phase 6: User Story 4 — Signal Strength Help Documentation (Priority: P3)

**Goal**: Users can tap a help button on the node list to see a documented legend of signal quality colors and the LoraSignalIndicator composable.

**Independent Test**: Open node list, tap help icon, scroll to "Node Details" section, verify 4 signal quality entries (Good/Fair/Bad/None) + LoraSignalIndicator entry are present with correct colors and descriptions.

### Implementation for User Story 4

- [x] NL-T034 [P] [US4] Create `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeListHelp.kt` as a `ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = true)` (FR-023, audit §1.1).
- [x] NL-T035 [US4] Add "Node Details" section with 4 signal quality entries: Good (green, SNR > −7 dB, RSSI > −115 dBm), Fair (yellow, SNR > −12 dB, RSSI > −120 dBm), Bad (orange, SNR > −18 dB, RSSI > −125 dBm), None (red, below all thresholds). Use `Quality` enum drawables from `LoraSignalIndicator.kt` (FR-023).
- [x] NL-T036 [US4] Add `LoraSignalIndicator` composable documentation entry in `NodeListHelp.kt` showing the quality icon + description explaining how SNR and RSSI combine into a quality level (Complete layout only) (FR-024).
- [x] NL-T037 [US4] Add help `IconButton` (NOT raw `Icon` + `clickable`) trigger to `NodeListScreen.kt` that opens the help sheet via state. Use M3 `IconButton` for built-in 48dp minimum touch target (audit §2.5).

**Checkpoint**: User Story 4 complete — signal help is discoverable and documents all quality levels.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Performance validation, edge case hardening, and verification across all stories.

- [x] NL-T038 [P] Verify smooth scrolling at 60fps with 200+ nodes in Compact mode. Use `derivedStateOf` for computed states to avoid unnecessary recompositions (NFR-002).
- [x] NL-T039 [P] Ensure all float values in both layouts use `NumberFormatter.format()` before display — distance, SNR, voltage, etc. (FR-028, Constitution §III).
- [x] NL-T040 Validate edge cases in `NodeItemCompact.kt`: all-toggles-disabled state (name row + 36.dp chip only, battery hidden), missing data (field absent, no placeholder), signal/hops mutual exclusivity, channel 0 hiding, connected node distance exclusion, MQTT signal exclusion, future date guard (spec §Edge Cases).
- [x] NL-T041 [P] Write unit tests for `NodeListDensity` enum, `lineCount` calculation logic (1/2/3 row cases), and invalid density string fallback to `COMPLETE` in `feature/node/src/commonTest/`.
- [x] NL-T042 [P] Write unit tests for DataStore preference defaults (all `true` except `lastHeardIsRelative` = `false`) in `core/prefs/src/commonTest/`.
- [x] NL-T043 [P] Write unit tests for edge cases: future date filtering (> 1 year), channel 0 hiding, signal/hops mutual exclusivity (`hopsAway == 0` vs `hopsAway > 0`), connected node distance exclusion, MQTT signal exclusion (`viaMqtt == true`) in `feature/node/src/commonTest/`.
- [x] NL-T044 [P] Write Compose UI tests for `NodeItemCompact` with various toggle combinations (all on, all off, partial) in `feature/node/src/commonTest/`.
- [x] NL-T045 [P] Write Compose UI tests for density switching in `NodeListScreen` (Complete → Compact → Complete round-trip) in `feature/node/src/commonTest/`.
- [x] NL-T046 Run `./gradlew :feature:node:allTests :feature:settings:allTests :core:prefs:allTests` to validate module tests.
- [x] NL-T047 Run `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests` for full verification (Constitution §II, §VI).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — NL-T001 blocks all UI. NL-T002 ∥ NL-T003, then NL-T004.
- **Phase 2 (Foundational)**: Depends on Phase 1 (NL-T002–NL-T004). BLOCKS all user stories.
- **Phase 3 (US1 — Density Switching)**: Depends on Phase 2. NL-T010 ∥ NL-T014 (different files).
- **Phase 4 (US2 — Field Toggles)**: Depends on US1 (`NodeItemCompact` scaffold + Settings UI).
- **Phase 5 (US3 — Adaptive Sizing)**: Depends on US2 (requires toggle logic to compute `lineCount`).
- **Phase 6 (US4 — Help Sheet)**: Can start after Phase 1 — independent of Phases 3–5. NL-T034 can run in parallel with any UI phase.
- **Phase 7 (Polish)**: Depends on Phases 3–6.

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational (Phase 2) — no dependencies on other stories
- **US2 (P1)**: Depends on US1 (`NodeItemCompact` scaffold and `NodeLayoutSettings` must exist)
- **US3 (P2)**: Depends on US2 (toggle logic needed for `lineCount` derivation)
- **US4 (P3)**: Independent — can start after Phase 1, no dependencies on US1–US3

### Critical Path

```
Phase 1 → Phase 2 → Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3) → Phase 8 (M3 Expressive) → Phase 7
```

### Parallel Opportunities

```
Phase 6 (US4) runs in parallel with Phases 3–5
NL-T002 ∥ NL-T003 (Setup)
NL-T010 ∥ NL-T014 (US1 — compact scaffold ∥ settings scaffold)
NL-T034 ∥ any Phase 3–5 task (US4 — help sheet)
NL-T041 ∥ NL-T042 ∥ NL-T043 ∥ NL-T044 ∥ NL-T045 (Phase 7 tests)
```

---

## Parallel Example: User Story 1

```bash
# Launch independent scaffolds together:
NL-T010: "Create NodeItemCompact.kt scaffold with two-column Row layout"
NL-T014: "Create NodeLayoutSettings.kt with SegmentedButton density picker"

# After scaffolds complete, sequential within US1:
NL-T011 → NL-T012 → NL-T013 (compact row details)
NL-T015 → NL-T016 (settings integration)
NL-T017 → NL-T018 (node list wiring)
```

---

## Dependency Graph

```
Phase 1 (Setup)
  ├──→ Phase 2 (Foundational: NodeItem a11y + ViewModel) ──→ Phase 3 (US1: Density Switch)
  │                                                            └──→ Phase 4 (US2: Field Toggles)
  │                                                                   └──→ Phase 5 (US3: Adaptive Sizing)
  │                                                                          └──→ Phase 8 (M3 Expressive)
  │                                                                                 └──→ Phase 7 (Polish)
  └──→ Phase 6 (US4: Help Sheet) ──────────────────────────────────────────────────────→ Phase 7 (Polish)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (design gate + preferences + strings)
2. Complete Phase 2: Foundational (NodeItem TalkBack fix + ViewModel density exposure)
3. Complete Phase 3: User Story 1 (density switching end-to-end)
4. **STOP and VALIDATE**: Toggle density, verify list renders correctly, verify persistence
5. Ship as MVP — users can switch to Compact (name-only rows for now)

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready
2. Phase 3: US1 → Density switching works → **MVP shippable**
3. Phase 4: US2 → All 9 field toggles work → Full compact experience
4. Phase 5: US3 → Adaptive chip sizing → Visual polish
5. Phase 6: US4 → Help documentation → Feature complete
6. Phase 8: M3 Expressive → Neutral bg, color border, glow animation → Design-standards compliant
7. Phase 7 → Tests + verification → Merge-ready

### Parallel Team Strategy

With multiple developers:

1. All complete Phase 1 + Phase 2 together
2. Once Foundational is done:
   - Developer A: US1 (Phase 3) → US2 (Phase 4) → US3 (Phase 5) *(critical path)*
   - Developer B: US4 (Phase 6) *(independent, can start after Phase 1)*
3. Both converge at Phase 7 (Testing)

---

## Phase 8: M3 Expressive Card Redesign (FR-029 – FR-034, NFR-005)

**Purpose**: Align card styling with Design Standards v1.4 §1, add M3 Expressive motion personality via packet-received glow animation, replace alpha-based text emphasis with semantic color roles, and finalize layout structure per cross-platform spec.

**Prerequisites**: Phases 3–5 complete (card scaffold exists, toggles work, chip sizing works).

- [x] NL-T048 [P] **Remove node-color card background tinting** from both `NodeItemCompact` (`core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/NodeItemCompact.kt`) and `NodeItem` (`core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/NodeItem.kt`). Replace `CardDefaults.cardColors().copy(containerColor = Color(it).copy(alpha))` with `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)`. Remove `ACTIVE_ALPHA` / `INACTIVE_ALPHA` constants. (FR-029)

- [x] NL-T049 [P] **Add node-color `BorderStroke`** to both card composables. Border uses `BorderStroke(1.5.dp, Color(node.colors.second).copy(alpha = if (isActive) 0.5f else 0.2f))`. Pass to `Card(border = ...)`. (FR-030)

- [x] NL-T050 **Implement packet-received glow animation** in a shared `NodeCardGlow` composable or modifier extension in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/`. Uses `remember { Animatable(0f) }` + `LaunchedEffect(node.lastHeard)` to trigger bloom (`fastSpatialSpec`) → decay (`slowSpatialSpec`). Applies `Modifier.shadow(elevation = 8.dp * glowAlpha, shape = cardShape, ambientColor = nodeColor.copy(alpha = glowAlpha), spotColor = nodeColor.copy(alpha = glowAlpha))`. Integrate into both `NodeItem` and `NodeItemCompact` outer Card modifier. (FR-031, NFR-005)

- [x] NL-T051 [P] **Replace alpha-based text emphasis with M3 color roles** across both layouts. Audit and replace all instances of `contentColor.copy(alpha = 0.7f)`, `contentColor.copy(alpha = 0.55f)`, `contentColor.copy(alpha = 0.65f)` etc. with:
  - Primary text → `MaterialTheme.colorScheme.onSurface`
  - Secondary text/values → `MaterialTheme.colorScheme.onSurfaceVariant`
  - Tertiary/metadata → `MaterialTheme.colorScheme.outline`
  (FR-032)

- [x] NL-T052 **Restore two-column layout in compact mode** (regression fix). During Phase 4–5 iteration, the compact layout was refactored to inline NodeChip into the name row. Restructure back to `Row { Column1(chip + battery), Column2(weight=1f, rows) }` per spec FR-009. Battery moves from health row to below chip in Column 1. Content rows span Column 2 only. Verify adaptive chip sizing (NL-T032) still applies after restructure. (FR-009, FR-011)

- [x] NL-T053 [P] **Implement bearing as rotated MapCompass icon**. Use `MeshtasticIcons.MapCompass` with `Modifier.rotate(bearingDegrees.toFloat())` for direction indicator in both compact (Row 3, after distance) and complete (battery/position row). Bearing is null if either node lacks valid position. (FR-034)

- [x] NL-T054 [P] **Add `HorizontalDivider` before footer in Complete mode**. Use `HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))` between the metrics section and the footer (hw model + role + node ID). (Layout structure — Complete)

- [x] NL-T055 **Performance validation of glow animation**. Benchmark scrolling with 200+ nodes in `LazyColumn` while glow animations fire. Verify no frame drops (use `FrameMetrics` or Compose `recomposition highlighter`). If degraded: move shadow to `graphicsLayer` draw phase, or debounce rapid `lastHeard` updates. (NFR-005)

**Dependencies**: NL-T048 and NL-T049 are parallel (both modify card colors/border independently). NL-T050 depends on NL-T048 (needs neutral background to see glow). NL-T051 is independent (text colors, no card dependency). NL-T052 depends on NL-T048 (layout restructure after color change). NL-T053 depends on NL-T052 (chip sizing needs two-column layout). NL-T054 is independent. NL-T055 depends on NL-T050 (tests the glow).

**Checkpoint**: Cards use neutral backgrounds with node-color borders, glow pulses on packet received, text uses semantic M3 roles, layout matches cross-platform spec.
