# Tasks: Map View

**Input**: Reverse-engineered from existing `feature/map/` module
**Prerequisites**: plan.md (required), spec.md (required)
**Tests**: Included — existing tests migrated; gap tasks added for missing coverage.
**Organization**: Tasks grouped by implementation phase. All existing work marked `[x]`; identified gaps marked `[ ]`.
**Status**: Migrated — all `[x]` tasks reflect code that already exists in the codebase.

## Format: `[MAP-TXXX] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4, US5)
- Include exact file paths in descriptions

## Path Conventions

- **KMP commonMain**: `feature/map/src/commonMain/kotlin/org/meshtastic/feature/map/`
- **KMP commonTest**: `feature/map/src/commonTest/kotlin/org/meshtastic/feature/map/`
- **Android source**: `feature/map/src/androidMain/kotlin/org/meshtastic/feature/map/`
- **Android tests**: `feature/map/src/androidUnitTestGoogle/kotlin/org/meshtastic/feature/map/`
- **Core deps**: `core/repository/`, `core/model/`, `core/ui/`

---

## Phase 1: Core ViewModel & Models

**Purpose**: Shared business logic for node data, waypoints, filters, traceroute resolution, and map layer models.

- [x] MAP-T001 [P] [US1] Create `BaseMapViewModel` in `feature/map/src/commonMain/.../BaseMapViewModel.kt` — shared ViewModel exposing `nodes`, `nodesWithPosition`, `myNodeInfo`, `ourNodeInfo`, `isConnected` flows from `NodeRepository` and `RadioController`. Filter ignored nodes from the `nodes` flow. (FR-001, FR-002, FR-003)
- [x] MAP-T002 [P] [US2] Implement `LastHeardFilter` enum in `BaseMapViewModel.kt` with entries `Any` (0s), `OneHour` (3600s), `EightHours` (28800s), `OneDay` (86400s), `TwoDays` (172800s). Include `fromSeconds()` companion factory defaulting to `Any` for unknown values. Wire `lastHeardFilter` and `lastHeardTrackFilter` state flows with `MapPrefs` persistence. (FR-004, FR-005)
- [x] MAP-T003 [P] [US3] Implement waypoint data flow in `BaseMapViewModel` — `waypoints: StateFlow<Map<Int, DataPacket>>` from `PacketRepository.getWaypoints()`, filtering expired waypoints using `nowSeconds`. Implement `deleteWaypoint(id)` and `sendWaypoint(wpt, contactKey)` methods using `safeLaunch` with `ioDispatcher`. (FR-006, FR-007, FR-008)
- [x] MAP-T004 [P] [US5] Implement map filter toggles in `BaseMapViewModel` — `showOnlyFavorites`, `showWaypointsOnMap`, `showPrecisionCircleOnMap` as `StateFlow<Boolean>` backed by `MapPrefs`. Combine all into `MapFilterState` data class via `mapFilterStateFlow`. (FR-005)
- [x] MAP-T005 [P] [US4] Implement `TracerouteNodeSelection` data class and `tracerouteNodeSelection()` top-level function in `BaseMapViewModel.kt` — resolve overlay node nums to displayable `Node` instances, prioritizing snapshot positions over live positions. Include convenience extension for `BaseMapViewModel`. (FR-009)
- [x] MAP-T006 [P] [US1] Create `SharedMapViewModel` in `feature/map/src/commonMain/.../SharedMapViewModel.kt` — Koin-injectable `@KoinViewModel` extending `BaseMapViewModel` with pass-through constructor. (FR-001)
- [x] MAP-T007 [P] [US1,US5] Create `MapLayerItem` data class and `LayerType` enum in `feature/map/src/commonMain/.../model/MapLayer.kt` — support KML and GeoJSON layer types with UUID-based IDs, visibility toggle, network flag, and refresh state. (FR-012)

**Dependencies**: None — all tasks are independent.  
**Checkpoint**: Core data layer complete. All flows, filters, and models ready for UI consumption.

---

## Phase 2: UI Components

**Purpose**: Map controls overlay and reusable button composables.

- [x] MAP-T008 [P] [US5] Create `MapButton` composable in `feature/map/src/commonMain/.../component/MapButton.kt` — `FilledIconButton` wrapper accepting `ImageVector`, `contentDescription`, `onClick`, optional `iconTint`. Uses `IconButtonDefaults.filledIconButtonColors()`. (NFR-001, NFR-005)
- [x] MAP-T009 [US5] Create `MapControlsOverlay` composable in `feature/map/src/commonMain/.../component/MapControlsOverlay.kt` — `HorizontalFloatingToolbar` (M3 Expressive) containing compass button, filter button with dropdown slot, map type slot, layers slot, optional refresh button with `CircularProgressIndicator`, and location tracking toggle. (FR-010, FR-011, NFR-001)
- [x] MAP-T010 [US5] Implement `CompassButton` private composable within `MapControlsOverlay.kt` — rotates icon by `-bearing` degrees, uses `StatusRed` when north-aligned, `primary` when following phone bearing. (FR-010)
- [x] MAP-T011 [US1] Create `MapScreen` composable in `feature/map/src/androidMain/.../MapScreen.kt` — `Scaffold` with `MainAppBar` showing connected node chip, delegating map content to `LocalMapViewProvider.current?.MapView()`. (FR-014, NFR-002)
- [x] MAP-T012 [P] [US1] Create `NodeMapViewModel` in `feature/map/src/commonMain/.../node/NodeMapViewModel.kt` — per-node map ViewModel with `destNum` from `SavedStateHandle`, `node` flow from `NodeRepository`, `positionLogs` flow from `MeshLogRepository` with time/coordinate deduplication. (FR-013)

**Dependencies**: Phase 1 (MAP-T001–MAP-T007) must complete first.  
**Checkpoint**: All UI components and ViewModels implemented.

---

## Phase 3: Navigation & DI

**Purpose**: Wire feature into app navigation graph and dependency injection.

- [x] MAP-T013 [US1] Create `MapNavigation.mapGraph()` in `feature/map/src/commonMain/.../navigation/MapNavigation.kt` — register `MapRoute.Map` entry using Navigation 3 `EntryProviderScope`, resolve map screen via `LocalMapMainScreenProvider`, navigate to `NodesRoute.NodeDetail` on node tap. (FR-014)
- [x] MAP-T014 [P] Create `FeatureMapModule` in `feature/map/src/commonMain/.../di/FeatureMapModule.kt` — Koin `@Module` with `@ComponentScan("org.meshtastic.feature.map")` for automatic ViewModel discovery.

**Dependencies**: Phase 2 (MAP-T011, MAP-T012) must complete first.  
**Checkpoint**: Feature fully wired into app navigation and DI.

---

## Phase 4: Testing

**Purpose**: Unit tests for ViewModels, models, and business logic. Includes existing tests and identified gaps.

- [x] MAP-T015 [P] [US1] Create `BaseMapViewModelTest` in `feature/map/src/commonTest/.../BaseMapViewModelTest.kt` — test initialization, `myNodeInfo` flow (starts null), `nodesWithPosition` flow (starts empty), `isConnected` flow (tracks `ConnectionState` changes), node repository integration. Uses Mokkery mocks for `MapPrefs` and `PacketRepository`, `FakeNodeRepository` and `FakeRadioController` for fakes. (SC-001)
- [x] MAP-T016 [P] [US2] Create `LastHeardFilterTest` in `feature/map/src/commonTest/.../LastHeardFilterTest.kt` — test `fromSeconds()` with all known values (0, 3600, 28800, 86400, 172800), unknown values (9999, -1, Long.MAX_VALUE default to `Any`), and `seconds` property round-trip. (SC-005)
- [x] MAP-T017 [P] [US4] Create `TracerouteNodeSelectionTest` in `feature/map/src/commonTest/.../TracerouteNodeSelectionTest.kt` — 8 test cases: null overlay returns all nodes, node lookup filters to valid positions, overlay with snapshot uses snapshot coordinates, snapshot node lookup, snapshot filters to overlay nodes, overlay without snapshot falls back to live nodes, empty overlay routes yield empty selection, getNodeOrFallback invocation verification. (SC-004)
- [x] MAP-T018 [P] [US5] Create `MapLayerTest` in `feature/map/src/commonTest/.../model/MapLayerTest.kt` — test `MapLayerItem` default values (auto-generated ID, null URI, visible=true, isNetwork=false, isRefreshing=false). (SC-008)
- [x] MAP-T019 [P] [US4] Create `TracerouteOverlayTest` in `feature/map/src/commonTest/.../model/TracerouteOverlayTest.kt` — test empty routes (`relatedNodeNums` empty, `hasRoutes` false) and populated routes (`relatedNodeNums` union, `hasRoutes` true).
- [x] MAP-T020 [P] [US5] Create `MapViewModelTest` in `feature/map/src/androidUnitTestGoogle/.../MapViewModelTest.kt` — Google-flavor tests: `getTileProvider` returns `UrlTileProvider` for remote config, `addNetworkMapLayer` detects GeoJSON by extension, KML default for other extensions, `setWaypointId` updates and clears value. Uses Robolectric. (SC-008)
- [x] MAP-T021 [P] [US5] Create `MBTilesProviderTest` in `feature/map/src/androidUnitTestGoogle/.../MBTilesProviderTest.kt` — test TMS y-coordinate translation (`y_tms = (1 << zoom) - 1 - y_google`), tile retrieval from SQLite database. Uses Robolectric with `TemporaryFolder`.

**Dependencies**: Phase 1–3 must complete first (tests exercise the full feature).  
**Checkpoint**: All existing tests passing. Gaps identified below.

---

## Phase 5: Gap Tasks (Not Yet Implemented) ⚠️

**Purpose**: Address identified coverage gaps in the existing implementation.

- [x] MAP-T022 [US3] **[GAP]** Add unit tests for waypoint expiration filtering logic in `BaseMapViewModel` — test that waypoints with `expire > nowSeconds` are included, `expire <= nowSeconds` are excluded, and `expire == 0` (never expires) are always included. File: `feature/map/src/commonTest/.../BaseMapViewModelTest.kt`. (SC-003)
- [ ] MAP-T023 [US1,US5] **[GAP]** Add Compose UI tests for `MapControlsOverlay` and `MapButton` composables — verify compass rotation, filter button click, location tracking toggle icon switch, refresh spinner visibility. File: `feature/map/src/commonTest/.../component/MapControlsOverlayTest.kt`. (NFR-001)

**Dependencies**: Phase 4 testing infrastructure.  
**Checkpoint**: Full test coverage achieved.

---

## Summary

| Phase | Tasks | Status |
|-------|-------|--------|
| Phase 1: Core ViewModel & Models | MAP-T001–MAP-T007 (7 tasks) | ✅ All complete |
| Phase 2: UI Components | MAP-T008–MAP-T012 (5 tasks) | ✅ All complete |
| Phase 3: Navigation & DI | MAP-T013–MAP-T014 (2 tasks) | ✅ All complete |
| Phase 4: Testing | MAP-T015–MAP-T021 (7 tasks) | ✅ All complete |
| Phase 5: Gap Tasks | MAP-T022–MAP-T023 (2 tasks) | ⚠️ Not started |
| **Total** | **23 tasks** | **21/23 complete** |

