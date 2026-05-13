# Implementation Plan — Local Mesh Discovery

## Overview

Implement Local Mesh Discovery as a new KMP feature module under `feature/discovery/`. The module owns the shared scan state machine, persistence-facing repository, summary UI, and map/topology presentation models. Android and Desktop hosts provide thin platform integrations for maps, export, and (on supported Google-flavor Android devices) Gemini Nano.

## Technical Context

| Area | Choice |
|---|---|
| Language | Kotlin 2.3+ |
| UI | Compose Multiplatform + Material 3 Adaptive |
| Navigation | JetBrains Navigation 3 typed `NavKey` routes |
| DI | Koin 4.2+ K2 compiler plugin (`@Module`, `@ComponentScan`, `@KoinViewModel`) |
| Persistence | Room KMP in `core:database` |
| Radio config mutation | Existing admin/config path reused from `feature:settings` |
| Packet capture | Existing mesh packet pipeline / repositories / handlers |
| Preferences | DataStore via `core:prefs` + `core:repository` interfaces |
| Maps | CompositionLocal provider pattern (`MapViewProvider`, inline/overlay locals) |
| AI | Gemini Nano via Google AI Edge SDK on supported Google flavor Android; deterministic fallback everywhere else |
| Logging | Kermit `Logger` + optional MeshLog-backed debugging where appropriate |
| Build | Gradle Kotlin DSL + convention plugins in `build-logic/` |

## Module Structure

```text
feature/discovery/
├── build.gradle.kts
└── src/
    ├── commonMain/
    │   └── kotlin/org/meshtastic/feature/discovery/
    │       ├── di/
    │       │   └── FeatureDiscoveryModule.kt
    │       ├── navigation/
    │       │   └── DiscoveryNavigation.kt
    │       ├── model/
    │       │   ├── DiscoverySession.kt
    │       │   ├── DiscoveryPresetSummary.kt
    │       │   └── DiscoveryNodeObservation.kt
    │       ├── repository/
    │       │   └── DiscoveryRepository.kt
    │       ├── scan/
    │       │   ├── DiscoveryScanCoordinator.kt
    │       │   ├── DiscoveryScanState.kt
    │       │   ├── DiscoveryPacketCollector.kt
    │       │   └── DiscoveryRankingEngine.kt
    │       ├── ui/
    │       │   ├── LocalMeshDiscoveryScreen.kt
    │       │   ├── DiscoveryHistoryScreen.kt
    │       │   ├── DiscoverySummaryScreen.kt
    │       │   └── DiscoveryMapScreen.kt
    │       └── DiscoveryViewModel.kt
    ├── androidMain/
    │   └── kotlin/org/meshtastic/feature/discovery/
    │       ├── ai/AndroidDiscoveryRecommendationEngine.kt
    │       ├── export/AndroidDiscoveryExporter.kt
    │       └── map/AndroidDiscoveryMapBindings.kt
    ├── jvmMain/
    │   └── kotlin/org/meshtastic/feature/discovery/
    │       ├── export/DesktopDiscoveryExporter.kt
    │       └── map/DesktopDiscoveryMapBindings.kt
    └── commonTest/
        └── kotlin/org/meshtastic/feature/discovery/
            ├── DiscoveryScanCoordinatorTest.kt
            ├── DiscoveryRankingEngineTest.kt
            └── DiscoveryViewModelTest.kt
```

## Project Structure Changes Outside the Module

| Area | Planned Change |
|---|---|
| `settings.gradle.kts` | Include `:feature:discovery` |
| `app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt` | Include `FeatureDiscoveryModule` |
| `desktop/src/main/kotlin/org/meshtastic/desktop/di/DesktopKoinModule.kt` | Include generated discovery Koin module |
| `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt` | Add typed discovery settings routes |
| `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/DeepLinkRouter.kt` | Add `/settings/local-mesh-discovery` mapping + optional compatibility alias |
| `core/navigation/src/commonTest/kotlin/org/meshtastic/core/navigation/DeepLinkRouterTest.kt` | Add discovery deep-link coverage |
| `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/RadioConfig.kt` or related settings screen | Add entry point under Advanced section |
| `core/database/src/commonMain/kotlin/org/meshtastic/core/database/MeshtasticDatabase.kt` | Register new discovery entities / DAOs and bump schema version |
| `core/resources/src/commonMain/composeResources/values/strings.xml` | Add all discovery UI strings |
| `app` / `desktop` map bindings | Provide discovery map adapter if a specialized provider is needed |

## Build-Logic and Gradle Plan

### Module build script

Start from the `feature/map` pattern and extend only as needed:

```kotlin
plugins {
    alias(libs.plugins.meshtastic.kmp.feature)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}
```

### Expected dependencies

`commonMain` likely needs:

- `projects.core.common`
- `projects.core.data`
- `projects.core.database`
- `projects.core.di`
- `projects.core.model`
- `projects.core.navigation`
- `projects.core.prefs`
- `projects.core.repository`
- `projects.core.resources`
- `projects.core.ui`
- `libs.jetbrains.navigation3.ui`
- `libs.kotlinx.collections.immutable` (if chip/filter state mirrors other features)

`androidMain` may add the Android AI/export integration dependency set. Keep flavor- or provider-specific dependencies out of `commonMain`.

### Convention reminders

- Keep shared business logic in `commonMain`.
- Do not import Android framework APIs in shared code.
- Use shared strings from `core/resources`.
- Use `MeshtasticIcons` for all new icons.
- Use `safeCatching {}` in coroutine code where failures are expected.

## Navigation Integration Plan

### Route shape

Use the Settings family for external entry, with optional detail routes for history:

- `SettingsRoute.LocalMeshDiscovery`
- `SettingsRoute.LocalMeshDiscoverySession(sessionId: String)`

### Graph integration

1. Add typed routes in `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`.
2. Register route slug(s) in `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/DeepLinkRouter.kt`.
3. Add discovery entries inside `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt` or call into a discovery-owned extension from there.
4. Add a settings list item under the existing **Advanced** section.

### Deep-link shape

- Canonical: `meshtastic://meshtastic/settings/local-mesh-discovery`
- Compatibility alias: `meshtastic://meshtastic/settings/localMeshDiscovery`
- Session detail: `meshtastic://meshtastic/settings/local-mesh-discovery/session/{sessionId}`

## Persistence Plan

### Database integration

- Add `DiscoverySessionEntity`, `DiscoveryPresetResultEntity`, and `DiscoveredNodeEntity` to `core:database`.
- Add DAOs and accessors to `MeshtasticDatabase`.
- Bump Room schema to the next available version.
- Add common DAO tests plus migration coverage.

### Repository boundary

Implement a discovery repository layer that:

- starts and finalizes sessions
- records preset transitions
- aggregates incoming packet observations into per-preset metrics
- exposes `Flow` APIs for history and detail screens

This repository can live in `feature/discovery` if the scope remains feature-local, or move to `core:data` if broader reuse appears.

## Scan Engine Plan

### Orchestration responsibilities

`DiscoveryScanCoordinator` in `commonMain` should:

1. validate selected presets and dwell time
2. resolve hardware gating
3. snapshot the current home preset
4. create a session row and per-preset placeholder rows
5. dispatch preset changes through the existing config path
6. wait for reconnect stability
7. start / pause / resume dwell timing
8. collect metrics during the dwell window
9. restore the home preset after completion / stop / failure
10. finalize summary + recommendation fields

### Dependencies the coordinator will need

- radio config mutation interface
- connection-state flow
- packet / node / neighbor info sources
- clock / timer abstraction for tests
- discovery repository
- optional recommendation engine

## Map Visualization Plan

### Shared side

In `commonMain`, build:

- preset filter state
- mapped/unmapped node counts
- node marker presentation models
- topology edge models derived from neighbor info
- selected node detail model

### Platform side

- Android should reuse existing map provider infrastructure and only add discovery-specific overlays if needed.
- Desktop should either wire a provider or explicitly use the same placeholder/list fallback pattern seen elsewhere.
- No map SDK types should leak into shared code.

## AI Recommendation Plan

### Shared contract

```text
interface DiscoveryRecommendationEngine {
    suspend fun recommend(summary: DiscoverySessionSummary): DiscoveryRecommendationResult
}
```

### Implementations

- `RuleBasedDiscoveryRecommendationEngine` in `commonMain` (always available)
- `AndroidDiscoveryRecommendationEngine` in `androidMain` (Google flavor only, Gemini Nano capable devices only)
- No-op / fallback binding on unsupported targets

> **Cross-spec note (F3):** Feature 003 (App Documentation) defines a parallel `AIDocAssistant` interface with the same platform-gating and fallback pattern. The two abstractions are intentionally separate because their prompts, result types, and domain contexts differ significantly. If a third AI-powered feature is added, a shared `core:ai` capability-check and session-factory module should be extracted to avoid further duplication.

### Prompting strategy

Pass only compact structured metrics into the AI layer:

- preset order and names
- counts and rankings
- noteworthy caveats (failed reconnect, partial dwell, no neighbor info)
- best/worst metrics

Do not pass raw packet payload dumps.

## Preferences Plan

Add a discovery prefs contract to `core:repository` and `core:prefs` for lightweight UI defaults such as:

- last dwell duration
- last selected preset set
- last-used map filter
- whether AI expansion is enabled / preferred
- whether topology overlay defaults on

Long-lived session history belongs in Room, not DataStore.

## Testing Strategy

### Module-level validation

- `:feature:discovery:allTests` for shared logic and ViewModels
- `:core:database:allTests` for DAO logic if schema work lands there
- `:app:testFdroidDebugUnitTest` and/or `:app:testGoogleDebugUnitTest` when Android route wiring or export integration changes
- `./gradlew kmpSmokeCompile` after route / source-set wiring

### Test focus areas

- scan state machine transitions
- reconnect pause/resume behavior
- partial cancellation and home-preset restore logic
- ranking heuristic tie-breakers
- AI fallback behavior
- Room relation loading and cascade deletion
- deep-link routing

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Radio reconnect takes longer than expected | Keep reconnect timeout configurable and lean on existing transport behavior rather than custom retries. |
| Hardware capability data is incomplete | Default 2.4 GHz presets to disabled when capability cannot be verified. |
| AI path is unavailable on most devices | Treat AI as an optional enhancement; deterministic summary remains first-class. |
| Map overlays become platform-specific too early | Keep all overlay calculations in `commonMain` and push only rendering to platform code. |
| Session rows grow large over time | Normalize tables and store only aggregate/session reconstruction data, not every raw packet. |

## Delivery Order

1. Set up the feature module, navigation entry, and DI wiring.
2. Land Room entities / DAOs / migration.
3. Implement the scan coordinator and persistence.
4. Add map + summary UI.
5. Add history and export.
6. Add optional Gemini Nano integration.

This order keeps the feature demonstrable early and ensures the optional AI work cannot block the main diagnostic experience.
