# Implementation Plan: Air Quality Telemetry Display

**Branch**: `20260601-074653-air-quality-telemetry` | **Date**: 2025-06-01 | **Spec**: `specs/20260601-074653-air-quality-telemetry/spec.md`

**Input**: Feature specification from `specs/20260601-074653-air-quality-telemetry/spec.md`

## Summary

Display air quality telemetry (PM1.0, PM2.5, PM10, CO₂) from the `AirQualityMetrics` proto message on node detail info cards with CO₂ severity color-coding, and provide a dedicated metrics log screen with history, thin-line charting, and CSV export. The full request→response→display loop must work end-to-end: request infrastructure already exists (button in `TelemetricActionsSection`, encoding in `CommandSenderImpl`), but the **response** path is missing — `TelemetryPacketHandlerImpl` must handle the `air_quality_metrics` oneof to store data on the Node model, triggering UI updates. The log screen includes its own "Request" action button via `BaseMetricScreen`'s `onRequestTelemetry` callback. Implementation follows the established Environment/Power metrics patterns: BLOB-persisted `Telemetry` proto in `NodeEntity`, oneof handling in `TelemetryPacketHandlerImpl`, `BaseMetricScreen` composable for the log, and metric-specific CSV export in `MetricsViewModel`.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21

**Primary Dependencies**: Compose Multiplatform (UI), Room KMP (database), Koin 4.2+ (DI), Wire/protobuf (proto), Vico (charts), Okio (filesystem/CSV)

**Storage**: Room KMP — new BLOB column `air_quality_metrics` on `NodeEntity` storing serialized `Telemetry` proto (same pattern as `environment_metrics` and `power_metrics` columns)

**Testing**: `./gradlew :core:model:test :core:data:test :feature:node:test` for unit tests; Compose screenshot tests for UI verification

**Target Platform**: Android (minSdk 24) + Compose Desktop (JVM)

**Project Type**: Mobile app (KMP multi-target)

**Performance Goals**: Info cards render within same frame budget as Environment cards; chart smooth with 1,000+ data points (NFR-001, NFR-002)

**Constraints**: All business logic and UI in `commonMain`; no `java.*`/`android.*` imports in common code; read-only proto submodule

**Scale/Scope**: 1 new database column, 1 new navigation route, ~3 new composable files, ~1 new ViewModel extension, database migration 38→39

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Kotlin Multiplatform Core**: ✅ All new code resides in `commonMain` source sets across `core:model`, `core:data`, `core:database`, `core:navigation`, `core:ui`, `core:resources`, and `feature:node`. No platform-specific (`androidMain`/`desktopMain`) code required — existing BLOB serialization, Room KMP, and Compose Multiplatform patterns handle all platform concerns.

- **II. Zero Lint Tolerance**: ✅ Will run:
  ```
  ./gradlew spotlessApply spotlessCheck detekt
  ```
  Scoped module tests: `:core:model:test :core:data:test :core:database:test :feature:node:test`

- **III. Compose Multiplatform UI**: ✅ All UI uses Compose Multiplatform composables (`BaseMetricScreen`, `InfoCard`, `SelectableMetricCard`). Float values pre-formatted with `NumberFormatter.format()`. Navigation via `MeshtasticNavDisplay` using serializable `NodeDetailRoute.AirQualityMetrics` route. No Jetpack-only APIs.

- **IV. Privacy First**: ✅ Only raw sensor numerics displayed/stored. No PII, location, or crypto keys involved. Proto submodule (`core/proto`) not modified — `AirQualityMetrics` message already exists in upstream proto.

- **V. Design Standards Compliance**: ✅ Cross-platform design specs referenced: `meshtastic/design/issues/51` and `meshtastic/design/issues/53`. Chart style (thin lines, dot only at selection) per Oscar's guidance. UI reuses existing metric card patterns already validated against design standards.

- **VI. Verify Before Push**: ✅ Local verification:
  ```
  ./gradlew spotlessApply spotlessCheck detekt :core:model:test :core:data:test :core:database:test :feature:node:test
  ```
  Post-push: `gh pr checks <PR>` or `gh run list --branch 20260601-074653-air-quality-telemetry --limit 5`

## Project Structure

### Documentation (this feature)

```text
specs/20260601-074653-air-quality-telemetry/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (internal UI contracts)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
core/
├── model/src/commonMain/kotlin/org/meshtastic/core/model/
│   └── Node.kt                          # Add airQualityMetrics field + hasAirQualityMetrics
├── data/src/commonMain/kotlin/org/meshtastic/core/data/manager/
│   └── TelemetryPacketHandlerImpl.kt    # Handle air_quality_metrics oneof (response path)
├── database/src/commonMain/kotlin/org/meshtastic/core/database/
│   ├── entity/NodeEntity.kt             # Add air_quality_metrics BLOB column + accessor
│   └── MeshtasticDatabase.kt            # Bump version 38→39 (auto-migration)
├── navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/
│   └── Routes.kt                        # Add NodeDetailRoute.AirQualityMetrics
├── ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/
│   └── Co2Severity.kt                   # CO₂ threshold color utility (new)
└── resources/src/commonMain/composeResources/values/
    └── strings.xml                      # Add air quality string resources

feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/
├── component/
│   └── AirQualityMetrics.kt             # Info card composable (new)
├── metrics/
│   └── AirQualityMetrics.kt             # Log screen + chart + request button (new)
├── model/
│   └── LogsType.kt                      # Add AIR_QUALITY enum entry
├── detail/
│   └── NodesNavigation.kt               # Register AirQualityMetrics route
└── MetricsViewModel.kt                  # Add air quality CSV export + chart state + requestTelemetry(AIR_QUALITY)
```

**Structure Decision**: KMP multi-module mobile app structure. New code distributed across existing `core:*` and `feature:node` modules following the established Environment/Power metrics pattern. No new modules required.

## Complexity Tracking

> No constitution violations. All gates pass without exception.
