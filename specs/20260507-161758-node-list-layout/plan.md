# Implementation Plan: Node List Layout

**Branch**: `002-node-list-layout` | **Date**: 2026-05-07 | **Spec**: `specs/002-node-list-layout/spec.md`
**Input**: Feature specification from `/specs/002-node-list-layout/spec.md`

## Summary

Add a density-switching system to the existing node list in `feature/node/`. Users choose between a **Complete** layout (all fields shown) and a **Compact** layout (user-configurable field toggles with adaptive sizing). Preferences persist via DataStore in `core:prefs`. A help bottom sheet documents signal strength color semantics. The feature is entirely `commonMain` — no platform-specific code required.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Compose Multiplatform, Material 3 Adaptive, Koin 4.2+ (K2 Compiler Plugin), Room KMP, DataStore KMP  
**Storage**: DataStore via `core:prefs` for toggle/density state; Room KMP via `core:database` (read-only for layout)  
**Testing**: KMP `allTests` for `feature:node`, `feature:settings`, `core:prefs`; Compose UI tests  
**Target Platform**: Android, Desktop (JVM), iOS — all via `commonMain`  
**Project Type**: Mobile/desktop app (Kotlin Multiplatform)  
**Performance Goals**: 60fps scrolling with 200+ compact nodes in `LazyColumn`  
**Constraints**: All UI in `commonMain`; no `java.*`/`android.*` in common; CMP float pre-formatting via `NumberFormatter.format()`  
**Scale/Scope**: 5 new files, ~6 modified files across `feature/node`, `core/prefs`, `core/resources`, `feature/settings`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All code in `commonMain`. No `java.*`/`android.*` imports. Uses DataStore KMP, Room KMP, Koin 4.2+. |
| II. Zero Lint Tolerance | ✅ PASS | `spotlessApply` + `detekt` required before merge (NL-T047). |
| III. Compose Multiplatform UI | ✅ PASS | Uses JetBrains Compose Multiplatform composables. Floats pre-formatted via `NumberFormatter.format()`. No Android-only Compose APIs. |
| IV. Privacy First | ✅ PASS | Feature is read-only display of existing node data. No new PII logging, no network calls, no crypto key exposure. |
| V. Design Standards Compliance | ✅ PASS | Phase 1 (NL-T001) gates all UI work on design standards review. New composables reviewed against upstream standards. |
| VI. Verify Before Push | ✅ PASS | Full verification via `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests` required (NL-T047). |

**Gate Result**: ✅ All six principles satisfied. No violations requiring justification.

## Project Structure

### Documentation (this feature)

```text
specs/002-node-list-layout/
├── plan.md              # This file
├── research.md          # Phase 0 output — 5 research decisions
├── data-model.md        # Phase 1 output — preference keys, density enum, node fields
├── quickstart.md        # Phase 1 output — bootstrap and development guide
└── tasks.md             # Phase 2 output — 47 tasks across 7 phases
```

### Source Code (repository root)

```text
feature/node/                          ← Primary changes
├── src/commonMain/kotlin/org/meshtastic/feature/node/
│   ├── component/
│   │   ├── NodeItem.kt                ← Existing — refactor to "Complete" role
│   │   ├── NodeItemCompact.kt         ← NEW — compact row composable
│   │   └── NodeListHelp.kt            ← NEW — help bottom sheet
│   ├── list/
│   │   ├── NodeListScreen.kt          ← Modify — density-aware delegation
│   │   ├── NodeListViewModel.kt       ← Modify — expose density + toggle state
│   │   └── NodeFilterPreferences.kt   ← Modify — add layout preferences
│   └── model/
│       └── NodeListDensity.kt         ← NEW — enum COMPLETE / COMPACT

core/prefs/                            ← Preference keys
├── src/commonMain/kotlin/org/meshtastic/core/prefs/
│   └── ui/UiPrefsImpl.kt             ← Modify — add layout DataStore keys

feature/settings/                      ← Settings UI
├── src/commonMain/kotlin/org/meshtastic/feature/settings/
│   └── NodeLayoutSettings.kt          ← NEW — settings section composable

core/ui/                               ← Shared components (existing, no new files expected)
├── src/commonMain/kotlin/org/meshtastic/core/ui/component/
│   ├── NodeChip.kt                    ← Existing — short-name avatar chip
│   ├── LoraSignalIndicator.kt         ← Existing — signal quality (Snr, Rssi, NodeSignalQuality)
│   ├── MaterialBatteryInfo.kt         ← Existing — battery indicator
│   ├── NodeKeyStatusIcon.kt           ← Existing — PKC/key status icon
│   ├── HopsInfo.kt                    ← Existing — hop count chip
│   ├── DistanceInfo.kt                ← Existing — distance + bearing chip
│   └── LastHeardInfo.kt               ← Existing — last heard timestamp chip

core/resources/
└── src/commonMain/composeResources/values/strings.xml  ← Add toggle labels, help text
```

**Structure Decision**: This feature modifies existing modules (`feature/node`, `core/prefs`, `feature/settings`) and adds new composable files within them. No new Gradle modules are created. This preserves the existing KMP module architecture.

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/node` | New + Modify | 7 files (3 new, 4 modified) | Medium — core feature changes |
| `core/prefs` | New + Modify | 2 files (1 new, 1 modified) | Low — additive preference keys |
| `feature/settings` | New | 1 file (NodeLayoutSettings.kt) | Low — new standalone section |
| `core/ui` | None | 0 — uses existing composables only | None |
| `core/resources` | Modify | 1 file (strings.xml) | Low — additive string resources |

## Integration Points

### Preference Keys

Add to `core:prefs` DataStore:

| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `nodeListDensity` | `String` (enum name) | `"COMPLETE"` | Active density mode |
| `shouldShowPower` | `Boolean` | `true` | Compact: battery visibility |
| `shouldShowLastHeard` | `Boolean` | `true` | Compact: last heard row |
| `lastHeardIsRelative` | `Boolean` | `false` | Compact: relative vs absolute time |
| `shouldShowLocation` | `Boolean` | `true` | Compact: distance + bearing |
| `shouldShowHops` | `Boolean` | `true` | Compact: hop count |
| `shouldShowSignal` | `Boolean` | `true` | Compact: signal strength |
| `shouldShowChannel` | `Boolean` | `true` | Compact: channel number |
| `shouldShowRole` | `Boolean` | `true` | Compact: device role icons |
| `shouldShowTelemetry` | `Boolean` | `true` | Compact: log/telemetry icons |

### Navigation

No new routes needed. The settings section is embedded within the existing `SettingsRoute.AppSettings` screen. The help sheet is a `ModalBottomSheet` triggered from `NodeListScreen`.

### Data Flow

1. User selects density in Settings → DataStore write
2. `NodeListViewModel` collects density + toggle StateFlows
3. `NodeListScreen` delegates to `NodeItem` or `NodeItemCompact` based on density
4. `NodeItemCompact` reads toggle state to conditionally render rows
5. `lineCount` derived from toggles drives adaptive chip sizing: `max(36.dp, min(70.dp, 24.dp × lineCount))`

## Design Constraints

- All UI lives in `commonMain` — layout density is not platform-specific
- Existing `NodeItem` composable is refactored to serve as the Complete layout with no toggle logic
- `NodeItemCompact` is a new composable, not a modified version of `NodeItem`
- The live preview in Settings reuses the same composables with a sample node from the database
- All strings added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- `determineSignalQuality(snr, rssi)` function (in `LoraSignalIndicator.kt`) must be accessible from both layout variants
- Float values must be pre-formatted with `NumberFormatter.format()` before display (CMP constraint)
- The short-name avatar is `NodeChip` (Card-based chip in `core:ui`), not a circle composable
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Error handling uses `safeCatching {}` not `runCatching {}`
- Dispatchers via `org.meshtastic.core.common.util.ioDispatcher`

### Material 3 & Expressive Constraints

- Use `SwitchPreference` (`core:ui`) for settings toggles — not raw `Switch`. Provides M3 `ListItem` integration and `toggleable` semantics
- Use `titleMediumEmphasized` (M3 Expressive) for node names in both layouts for consistency
- Help button must use `IconButton` (not `Icon` + `clickable`) for 48dp minimum touch target
- `VerticalDivider` in compact Row 3 must use `Modifier.fillMaxHeight()` inside a `Row(Modifier.height(IntrinsicSize.Min))` — not hardcoded height
- Compact 2.dp spacing is an intentional M3 deviation documented in spec (FR-026/FR-027)

### Accessibility Constraints

- Both layouts MUST use `Modifier.semantics(mergeDescendants = true)` on the outer Card to aggregate child elements into a single TalkBack focus stop
- Clickable rows MUST declare `role = Role.Button` for TalkBack "double tap to activate" announcement
- Signal icons MUST include quality-level `contentDescription` (WCAG 1.4.1 — no color-only information)
- Chip sizing MUST use `Modifier.defaultMinSize()` instead of hard `Modifier.size()` to grow with system font scaling
- **NL-T006 is HIGH priority** — the existing `NodeItem` has zero row-level semantics, causing 8-12 separate TalkBack focus stops per node

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Scroll performance degrades with 200+ compact rows | Low | High | Use stable `key` in `LazyColumn`, avoid unnecessary recompositions via `derivedStateOf` (NL-T018, NL-T038) |
| Toggle state out of sync between Settings and NodeList | Low | Medium | Both screens observe the same DataStore flows — single source of truth |
| Existing `NodeItem` refactor breaks current behavior | Medium | High | Validate Complete layout semantics before modifying (NL-T007, NL-T008) |
| Live preview renders incorrectly without database nodes | Low | Low | Show placeholder text when no nodes exist (NL-T030) |
| Design standards non-compliance | Low | Medium | Phase 1 gates all UI work on standards review (NL-T001) |
| String resource conflicts | Low | Low | Run `python3 scripts/sort-strings.py` after adding strings (NL-T005) |
| TalkBack regression in existing NodeItem | High | High | Existing `NodeItem` has no row-level semantics merge (8-12 focus stops per row). NL-T006 is HIGH priority to fix before shipping compact variant. |
| Font scaling clips compact chip text | Medium | Medium | Use `defaultMinSize()` not hard `size()` for adaptive growth (NL-T032) |

## Phase Alignment with Tasks

The implementation is structured across 7 phases (47 tasks) as defined in `tasks.md`:

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Setup | Design gate, density enum, DataStore keys, strings | NL-T001–T005 | None — NL-T001 blocks all UI |
| 2. Foundational | NodeItem a11y fix, ViewModel wiring | NL-T006–T009 | Phase 1 |
| 3. US1 — Density Switch | Compact scaffold, settings picker, list wiring | NL-T010–T018 | Phase 2 |
| 4. US2 — Field Toggles | 9 compact toggles, all conditional fields | NL-T019–T030 | Phase 3 |
| 5. US3 — Adaptive Sizing | lineCount + adaptive chip sizing | NL-T031–T033 | Phase 4 |
| 6. US4 — Help Sheet | Signal strength documentation | NL-T034–T037 | Phase 1 (parallel with 3–5) |
| 7. Polish | Performance, edge cases, tests, verification | NL-T038–T047 | Phases 3–6 |

### Critical Path

```
Phase 1 → Phase 2 → Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3) → Phase 7
```

Phase 6 (US4) runs in parallel off the critical path, converging at Phase 7 (Polish).

## Complexity Tracking

> No constitution violations detected. This table is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |
