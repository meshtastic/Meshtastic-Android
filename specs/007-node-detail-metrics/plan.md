# Implementation Plan: Node Detail & Metrics

**Branch**: `007-node-detail-metrics` | **Date**: 2025-07-15 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/007-node-detail-metrics/spec.md`

**Note**: This is a brownfield migration — all implementation is complete. This plan documents the architecture as-built.

## Summary

The Node Detail & Metrics feature provides per-node inspection with nine metric log screens, interactive Vico charts, time-frame filtering, CSV export, compass-based navigation, and remote administration. All code resides in `commonMain` using Compose Multiplatform, Koin DI, and Navigation 3 with Material 3 Adaptive ListDetailSceneStrategy.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Compose Multiplatform, Material 3 Adaptive, Koin 4.2+ (K2 Compiler Plugin), Vico (Patrykandpatrick) charting, Coil 3 (async image), Okio (CSV/Base64)  
**Storage**: Room KMP for mesh logs and nodes, DataStore KMP for user preferences (display units, Fahrenheit)  
**Testing**: KMP `allTests` for `feature:node` — Mokkery mocking, Turbine flow testing  
**Target Platform**: Android, Desktop (JVM), iOS — all via `commonMain`  
**Project Type**: Mobile/desktop app (Kotlin Multiplatform)  
**Performance Goals**: 60fps chart scrolling, <100ms chart↔card sync  
**Constraints**: All UI in `commonMain`; no `java.*`/`android.*` in common; CMP float pre-formatting via `NumberFormatter.format()` / `MetricFormatter`  
**Scale/Scope**: ~70 source files, ~13,000 lines (feature/node commonMain, excluding list layout); ~14 test files, ~2,000 test lines

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All code in `commonMain`. Compass providers use `expect`/`actual`. No `java.*`/`android.*` imports in common. |
| II. Zero Lint Tolerance | ✅ PASS | `spotlessApply` + `detekt` pass. Suppressions documented (`MagicNumber`, `LongMethod`, `CyclomaticComplexMethod`). |
| III. Compose Multiplatform UI | ✅ PASS | CMP composables throughout. `NumberFormatter.format()` for all floats. Navigation 3 with `ListDetailSceneStrategy`. |
| IV. Privacy First | ✅ PASS | No PII logging. Public keys displayed but never transmitted. Hardware images fetched from public CDN. |
| V. Design Standards Compliance | ✅ PASS | M3 components: `SectionCard`, `ListItem`, `SwitchListItem`, `FilterChip`, `AssistChip`. TalkBack semantics on key elements. |
| VI. Verify Before Push | ✅ PASS | Full verification pipeline: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`. |
| VII. Coroutine Safety | ✅ PASS | `safeLaunch {}` used throughout. `dispatchers.io` (project injected), not `Dispatchers.IO`. |
| VIII. Resource Discipline | ✅ PASS | `stringResource(Res.string.key)` everywhere. `MeshtasticIcons` for all icons. |
| IX. Branch & Scope Hygiene | ✅ PASS | Feature scoped to `feature/node` module with well-defined sub-packages. |

**Gate Result**: ✅ All principles satisfied

## Project Structure

### Documentation (this feature)

```text
specs/007-node-detail-metrics/
├── spec.md               # Feature specification (migrated)
├── plan.md               # This file (migrated)
└── tasks.md              # Task breakdown (migrated)
```

### Source Code (repository root)

```text
feature/node/
├── src/commonMain/kotlin/org/meshtastic/feature/node/
│   ├── compass/
│   │   ├── CompassHeadingProvider.kt       ← expect declaration
│   │   ├── CompassUiState.kt              ← Compass UI state model
│   │   ├── CompassViewModel.kt            ← Heading, bearing, distance, true-north
│   │   ├── MagneticFieldProvider.kt        ← expect declaration
│   │   └── PhoneLocationProvider.kt        ← expect declaration
│   ├── component/
│   │   ├── AdministrationSection.kt        ← Remote admin + firmware section
│   │   ├── ChannelInfo.kt                  ← Channel display
│   │   ├── CompassBottomSheet.kt           ← Compass sheet composable
│   │   ├── CooldownOutlinedIconButton.kt   ← Request cooldown button
│   │   ├── DeviceActions.kt                ← DM, share, favorite, ignore, mute, remove
│   │   ├── DeviceDetailsSection.kt         ← Hardware model + support status
│   │   ├── DistanceInfo.kt                 ← Distance display
│   │   ├── ElevationInfo.kt                ← Altitude display
│   │   ├── EnvironmentMetrics.kt           ← Inline env metrics summary
│   │   ├── FirmwareReleaseSheetContent.kt  ← Firmware release bottom sheet
│   │   ├── HopsInfo.kt                     ← Hop count display
│   │   ├── IconInfo.kt                     ← Reusable icon+text pair
│   │   ├── InfoCard.kt                     ← Section card wrapper
│   │   ├── LastHeardInfo.kt                ← Last heard display
│   │   ├── LinkedCoordinatesItem.kt        ← Clickable coordinates
│   │   ├── NodeContextMenu.kt             ← Context menu
│   │   ├── NodeDetailComponentPreviews.kt  ← Preview composables
│   │   ├── NodeDetailComponents.kt         ← Shared UI primitives
│   │   ├── NodeDetailsSection.kt           ← Identity card
│   │   ├── NodeMenuAction.kt               ← Sealed action types
│   │   ├── NodeStatusIcons.kt              ← Status indicators
│   │   ├── NotesSection.kt                 ← Editable notes
│   │   ├── PositionSection.kt              ← Inline map + compass button
│   │   ├── PowerMetrics.kt                 ← Inline power summary
│   │   ├── SatelliteCountInfo.kt           ← Satellite count
│   │   ├── TelemetricActionsSection.kt     ← Telemetry feature rows
│   │   └── TelemetryInfo.kt               ← Telemetry display
│   ├── detail/
│   │   ├── CommonNodeRequestActions.kt     ← Shared request action impls
│   │   ├── HandleNodeAction.kt             ← Action dispatch router
│   │   ├── NodeDetailActions.kt            ← Coordinated action facade
│   │   ├── NodeDetailContent.kt            ← Crossfade + LazyColumn detail
│   │   ├── NodeDetailPreviews.kt           ← Preview composables
│   │   ├── NodeDetailScreens.kt            ← Scaffold + overlay management
│   │   ├── NodeDetailViewModel.kt          ← Node detail ViewModel
│   │   ├── NodeManagementActions.kt        ← CRUD node actions
│   │   └── NodeRequestActions.kt           ← Telemetry/traceroute requests
│   ├── di/
│   │   └── FeatureNodeModule.kt            ← Koin module
│   ├── domain/usecase/
│   │   ├── CommonGetNodeDetailsUseCase.kt  ← Shared use case impl
│   │   ├── GetFilteredNodesUseCase.kt      ← Node list filtering (spec 002)
│   │   └── GetNodeDetailsUseCase.kt        ← Node detail aggregator
│   ├── metrics/
│   │   ├── BaseMetricChart.kt              ← GenericMetricChart, BaseMetricScreen, AdaptiveLayout
│   │   ├── ChartStyling.kt                 ← Line styles, markers, threshold lines
│   │   ├── CommonCharts.kt                 ← Shared chart helpers (time axis, scroll)
│   │   ├── DeviceMetrics.kt                ← Device metric screen + chart + card
│   │   ├── EnvironmentCharts.kt            ← Environment multi-series chart
│   │   ├── EnvironmentMetrics.kt           ← Environment metric screen + card
│   │   ├── EnvironmentMetricsState.kt      ← Environment graphing data model
│   │   ├── HardwareModelExtensions.kt      ← Safe hardware model number lookup
│   │   ├── HostMetricsChart.kt             ← Host metrics chart
│   │   ├── HostMetricsLog.kt               ← Host metrics screen + card
│   │   ├── MetricLogComponents.kt          ← Shared metric UI (indicators, legends, dialogs)
│   │   ├── MetricsViewModel.kt             ← Central metrics ViewModel
│   │   ├── NeighborInfoLog.kt              ← Neighbor info log screen
│   │   ├── PaxMetrics.kt                   ← Pax metrics screen + chart + card
│   │   ├── PositionLogComponents.kt        ← Position card composable
│   │   ├── PositionLogScreens.kt           ← Position log screen
│   │   ├── PowerMetrics.kt                 ← Power metrics screen + chart + card
│   │   ├── SignalMetrics.kt                ← Signal metrics screen + chart + card
│   │   ├── TimeFrameSelector.kt            ← Time-frame chip selector
│   │   ├── TracerouteChart.kt              ← Traceroute chart + data model
│   │   └── TracerouteLog.kt                ← Traceroute log screen + card
│   ├── model/
│   │   ├── IsEffectivelyUnmessageable.kt   ← Node messaging capability check
│   │   ├── LogsType.kt                     ← Log type enum with routes
│   │   ├── MetricInfo.kt                   ← Metric display info
│   │   ├── MetricsState.kt                 ← Aggregate metric state
│   │   ├── NodeDetailAction.kt             ← Sealed detail actions
│   │   └── TimeFrame.kt                    ← Time window enum
│   └── navigation/
│       ├── AdaptiveNodeListScreen.kt       ← Adaptive list/detail (spec 002)
│       └── NodesNavigation.kt              ← Nav3 graph entries
├── src/commonTest/kotlin/org/meshtastic/feature/node/
│   ├── compass/CompassViewModelTest.kt
│   ├── detail/HandleNodeActionTest.kt
│   ├── detail/NodeDetailViewModelTest.kt
│   ├── detail/NodeManagementActionsTest.kt
│   ├── domain/usecase/GetFilteredNodesUseCaseTest.kt
│   ├── list/NodeListViewModelTest.kt
│   ├── metrics/DecodePaxFromLogTest.kt
│   ├── metrics/EnvironmentMetricsForGraphingTest.kt
│   ├── metrics/EnvironmentMetricsStateTest.kt
│   ├── metrics/FormatBytesTest.kt
│   ├── metrics/HardwareModelSafeNumberTest.kt
│   ├── metrics/MetricsViewModelTest.kt
│   ├── metrics/TracerouteChartTest.kt
│   └── model/TimeFrameTest.kt
```

**Structure Decision**: All code lives in `feature/node` module, organised by concern (detail, metrics, compass, component, model, navigation). Metrics screens share `BaseMetricScreen` and `GenericMetricChart` to avoid duplication.

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/node` | Primary | ~70 source + ~14 test | Low (self-contained) |
| `core/model` | Read-only dep | 0 modified | None |
| `core/ui` | Read-only dep | 0 modified | None |
| `core/resources` | String additions | strings.xml | Low |
| `core/navigation` | Route definitions used | 0 modified | None |
| `core/database` | Entities read | 0 modified | None |
| `core/repository` | Repositories injected | 0 modified | None |

## Integration Points

- **Navigation**: `NodesNavigation.nodesGraph()` registers all routes using Navigation 3 `entry<T>` with `ListDetailSceneStrategy` pane metadata.
- **DI**: `FeatureNodeModule` provides `NodeDetailViewModel`, `MetricsViewModel`, `CompassViewModel` via `@KoinViewModel`.
- **DataStore**: Display units and Fahrenheit preference read from user settings DataStore.
- **Room**: Mesh logs, node DB, and traceroute snapshot positions queried via repositories.
- **Service**: `ServiceRepository` provides live traceroute responses and accepts `ServiceAction` commands.

## Design Constraints

- All UI lives in `commonMain` — not platform-specific
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- Error handling uses `safeLaunch {}` / `safeCatching {}` not `runCatching {}`
- Dispatchers via `org.meshtastic.core.di.CoroutineDispatchers` (injected)
- Float values must be pre-formatted with `NumberFormatter.format()` / `MetricFormatter` (CMP constraint)
- Vico chart x-step minimum of 60 seconds to prevent slot-count explosion

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Vico chart performance with 1000+ data points | Low | Medium | Time-frame filtering limits visible data; x-step floor prevents micro-slotting |
| Compass heading drift without magnetometer | Medium | Low | `NO_MAGNETOMETER` warning displayed to user |
| Traceroute map unavailability (no positioned nodes) | Medium | Low | `evaluateTracerouteMapAvailability` checks before navigation, shows error dialog |

## Phase Alignment with Tasks

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Data Layer | Models + state | NDM-T001–T003 | None |
| 2. Detail Screen | Node detail sections | NDM-T004–T010 | Phase 1 |
| 3. Chart Infrastructure | BaseMetricScreen + Vico | NDM-T011–T013 | Phase 1 |
| 4. Metric Screens | Nine individual screens | NDM-T014–T022 | Phase 3 |
| 5. Compass | Heading + bearing | NDM-T023–T024 | Phase 1 |
| 6. Navigation | Nav3 graph | NDM-T025 | Phase 2, 4 |
| 7. Testing | Unit + ViewModel tests | NDM-T026–T033 | All prior |

### Critical Path

```
Phase 1 → Phase 2 + Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |

