# Implementation Plan: Map View

**Branch**: `009-map-view` | **Date**: 2026-06-11 | **Spec**: `specs/009-map-view/spec.md`
**Input**: Feature specification from `/specs/009-map-view/spec.md`
**Note**: Brownfield migration — reverse-engineered from existing implementation.

## Summary

Map View provides an interactive map displaying mesh node positions, waypoints, traceroute overlays, and custom map layers. The shared `BaseMapViewModel` in `commonMain` manages node data flows, filter state, waypoint operations, and traceroute resolution. Platform-specific map rendering is delegated via composition locals (`LocalMapViewProvider`). The feature uses Koin for DI, Navigation 3 for routing, and Material 3 Expressive for the controls toolbar.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Compose Multiplatform, Material 3 Expressive, Koin 4.2+ (K2 Compiler Plugin), DataStore KMP, Navigation 3  
**Storage**: DataStore KMP for map preferences (filter, favorites, waypoints visibility, precision circles, map style)  
**Testing**: KMP `allTests` for `feature:map` commonTest; `testGoogleDebugUnitTest` for Android-specific tests  
**Target Platform**: Android, Desktop (JVM), iOS — all via `commonMain` (map rendering platform-specific)  
**Project Type**: Mobile/desktop app (Kotlin Multiplatform)  
**Performance Goals**: Smooth map rendering with 100+ node markers; filter state changes reflected within 500ms  
**Constraints**: All UI in `commonMain`; no `java.*`/`android.*` in common; CMP float pre-formatting via `NumberFormatter.format()`  
**Scale/Scope**: 8 commonMain files, 1 androidMain file, 5 commonTest files, 2 androidUnitTest files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All business logic in `commonMain`. `MapScreen.kt` in `androidMain` is a thin Scaffold host only. No `java.*`/`android.*` in common code. |
| II. Zero Lint Tolerance | ✅ PASS | `spotlessApply` + `detekt` pass. `detekt-baseline.xml` present for acknowledged suppressions. |
| III. Compose Multiplatform UI | ✅ PASS | Uses CMP composables (`HorizontalFloatingToolbar`, `FilledIconButton`, `Scaffold`). Map rendering delegated via composition local. |
| IV. Privacy First | ✅ PASS | No PII or location logging. Node positions from mesh, not phone GPS. Proto submodule read-only. |
| V. Design Standards Compliance | ✅ PASS | M3 Expressive toolbar, `MeshtasticIcons`, `stringResource()` for all labels. Content descriptions on all interactive elements. |
| VI. Verify Before Push | ✅ PASS | Full verification: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`. |
| VII. Coroutine Safety | ✅ PASS | Uses `safeLaunch {}` with project `ioDispatcher`. No `runCatching {}` or `Dispatchers.IO` in common code. |
| VIII. Resource Discipline | ✅ PASS | `stringResource(Res.string.*)`, `MeshtasticIcons.*` throughout. |
| IX. Branch & Scope Hygiene | ✅ PASS | Feature scoped to `feature/map` module with clear boundaries. |

**Gate Result**: ✅ All principles satisfied. No violations requiring justification.

## Project Structure

### Documentation (this feature)

```text
specs/009-map-view/
├── spec.md              # Feature specification (migrated)
├── plan.md              # This file (migrated)
└── tasks.md             # Task list (migrated)
```

### Source Code (repository root)

```text
feature/map/                              ← Primary changes
├── src/commonMain/kotlin/org/meshtastic/feature/map/
│   ├── BaseMapViewModel.kt               ← Shared ViewModel — nodes, waypoints, filters, traceroute
│   ├── SharedMapViewModel.kt             ← Koin-injectable ViewModel (extends BaseMapViewModel)
│   ├── component/
│   │   ├── MapButton.kt                  ← Reusable FilledIconButton for map controls
│   │   └── MapControlsOverlay.kt         ← M3 Expressive HorizontalFloatingToolbar
│   ├── di/
│   │   └── FeatureMapModule.kt           ← Koin module with @ComponentScan
│   ├── model/
│   │   └── MapLayer.kt                   ← MapLayerItem data class, LayerType enum
│   ├── navigation/
│   │   └── MapNavigation.kt              ← Navigation 3 graph entry for MapRoute
│   └── node/
│       └── NodeMapViewModel.kt           ← Per-node position history ViewModel
├── src/androidMain/kotlin/org/meshtastic/feature/map/
│   └── MapScreen.kt                      ← Android Scaffold host with LocalMapViewProvider
├── src/commonTest/kotlin/org/meshtastic/feature/map/
│   ├── BaseMapViewModelTest.kt           ← ViewModel initialization, connection state, node flow tests
│   ├── LastHeardFilterTest.kt            ← Filter enum round-trip and edge case tests
│   ├── TracerouteNodeSelectionTest.kt    ← Traceroute overlay resolution tests (8 test cases)
│   └── model/
│       ├── MapLayerTest.kt               ← MapLayerItem defaults test
│       └── TracerouteOverlayTest.kt      ← TracerouteOverlay route processing tests
└── src/androidUnitTestGoogle/kotlin/org/meshtastic/feature/map/
    ├── MapViewModelTest.kt               ← Google-flavor ViewModel tests (tile providers, layers, waypoints)
    └── MBTilesProviderTest.kt            ← MBTiles TMS coordinate translation test

core/repository/                          ← Dependencies (not modified)
├── MapPrefs                              ← DataStore-backed map preference interface
├── NodeRepository                        ← Node data access
└── PacketRepository                      ← Waypoint data access

core/model/                               ← Dependencies (not modified)
├── Node                                  ← Node data model
├── TracerouteOverlay                     ← Traceroute route data
├── DataPacket                            ← Waypoint container
└── RadioController                       ← Mesh radio interface
```

**Structure Decision**: The `feature/map` module follows the standard KMP feature module pattern. Business logic is in `commonMain`, platform-specific rendering is injected via composition locals. The `androidMain` source set contains only a thin `MapScreen` Scaffold host — actual map rendering (Google Maps / OSM) lives in build-flavor-specific modules outside this feature.

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/map` (commonMain) | Existing | 8 | Low |
| `feature/map` (androidMain) | Existing | 1 | Low |
| `core/repository` | Read-only dependency | 0 | None |
| `core/model` | Read-only dependency | 0 | None |
| `core/ui` | Read-only dependency | 0 | None |
| `core/resources` | Read-only dependency | 0 (strings already exist) | None |

## Integration Points

- **Navigation**: `MapNavigation.mapGraph()` registers `MapRoute.Map` entry, navigates to `NodesRoute.NodeDetail` on node tap.
- **DI**: `FeatureMapModule` uses Koin `@ComponentScan` to discover `SharedMapViewModel` and `NodeMapViewModel`.
- **Map Rendering**: `LocalMapViewProvider.current?.MapView()` injected by build-flavor modules (Google / F-Droid).
- **Map Screen Host**: `LocalMapMainScreenProvider.current` injected for the main map screen composable.
- **Preferences**: `MapPrefs` interface from `core/repository` backed by DataStore.
- **Radio**: `RadioController` for sending waypoints and generating packet IDs.

## Design Constraints

- All UI lives in `commonMain` — not platform-specific
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- Error handling uses `safeCatching {}` not `runCatching {}`
- Dispatchers via `org.meshtastic.core.common.util.ioDispatcher`
- Float values must be pre-formatted with `NumberFormatter.format()` (CMP constraint)
- Map rendering is platform-injected — `feature/map` has zero dependency on Google Maps SDK or OSM library

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| MapScreen in androidMain limits multiplatform reach | Medium | Medium | Thin host only; actual rendering via composition local. Desktop/iOS provide their own MapView implementations. |
| Missing Compose UI tests for controls overlay | Low | Low | Manual testing covers; unit tests cover ViewModel logic comprehensively. |
| Waypoint expiration edge cases (timezone, clock skew) | Low | Medium | Uses `nowSeconds` utility; expiration logic has clear boundary checks. |

## Phase Alignment with Tasks

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Core ViewModel & Models | Data layer and business logic | MAP-T001–MAP-T007 | None |
| 2. UI Components | Map controls and composables | MAP-T008–MAP-T012 | Phase 1 |
| 3. Navigation & DI | Routing and dependency injection | MAP-T013–MAP-T014 | Phase 2 |
| 4. Testing | Unit and integration tests | MAP-T015–MAP-T022 | Phase 1–3 |

### Critical Path

```
Phase 1 (ViewModel + Models) → Phase 2 (UI Components) → Phase 3 (Navigation + DI) → Phase 4 (Tests)
```

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |

