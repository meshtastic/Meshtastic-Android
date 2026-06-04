# Research: Air Quality Telemetry Display

## R1: Telemetry Packet Handling Pattern

**Decision**: Add `air_quality_metrics` oneof handling to `TelemetryPacketHandlerImpl` following the exact pattern used for `environment_metrics` and `power_metrics`.

**Rationale**: The handler at `core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/TelemetryPacketHandlerImpl.kt` already pattern-matches on the Telemetry oneof variants. The `air_quality_metrics` variant (field 4 in the Telemetry proto) is not yet handled — it simply falls through. Adding a branch that copies the metrics to the Node model is trivial and consistent.

**Alternatives considered**:
- Separate handler class → rejected: adds indirection for a single oneof branch; other metrics don't do this.

## R2: Database Storage Strategy

**Decision**: Add a new BLOB column `air_quality_metrics` (type `Telemetry`) to `NodeEntity`, auto-migrating from version 38 to 39.

**Rationale**: Environment (`environment_metrics`) and Power (`power_metrics`) use the same pattern — store the full `Telemetry` proto as a binary BLOB. Room KMP auto-migration handles new nullable columns cleanly (existing rows get null/default). The `NodeEntity` accessor property unwraps the oneof for type-safe access.

**Alternatives considered**:
- Individual columns per metric field → rejected: 25 fields in `AirQualityMetrics` makes this unwieldy; BLOB serialization is proven.
- Shared column with environment → rejected: different telemetry type, different update cadence, violates existing separation pattern.

## R3: Node Model Extension

**Decision**: Add `airQualityMetrics: AirQualityMetrics = AirQualityMetrics()` field and `hasAirQualityMetrics` boolean accessor to the `Node` data class.

**Rationale**: Mirrors `environmentMetrics`/`hasEnvironmentMetrics` pattern exactly. The `has*` accessor compares against the default empty instance to determine if data is present.

**Alternatives considered**: None — this is the established pattern.

## R4: CO₂ Severity Color Thresholds

**Decision**: Create a `Co2Severity` enum/utility in `core:ui` that maps CO₂ ppm to M3-compatible color tokens:
- Good: 400–1000 ppm → `Color.Green` / M3 tertiary
- Stuffy: 1000–2000 ppm → `Color.Yellow` / M3 secondary
- Poor: 2000–5000 ppm → `Color.Orange` / custom warning token
- Unsafe: 5000+ ppm → `Color.Red` / M3 error
- Evacuate: 30000+ ppm → `Color.Red` + bold / M3 error with emphasis

**Rationale**: Per design/issues/53 (Oscar's recommendation). Using M3-compatible color tokens ensures theme consistency across light/dark modes. Existing `IndoorAirQuality.kt` in `core/ui/component/` provides a precedent for threshold-based coloring (IAQ severity levels).

**Alternatives considered**:
- Hardcoded hex colors → rejected: breaks M3 theming and dark mode.
- Reuse IAQ severity → rejected: different scale (IAQ 0-500 vs CO₂ 400-40000 ppm), different semantics.

## R5: Chart Rendering Style

**Decision**: Use thin-line charts via Vico library with dot marker visible only at the selected/cursor position. No persistent markers on data points.

**Rationale**: Per design/issues/53 recommendation. The existing chart infrastructure in `BaseMetricScreen` already uses Vico for line charts. The thin-line-only style may differ from current Environment charts (which may show dots) — this feature follows the updated design guidance.

**Alternatives considered**:
- Thick lines with dots at every point → rejected: explicitly against design guidance ("avoid clutter").
- Bar charts for PM data → rejected: line charts show temporal trends better for continuous monitoring.

## R6: Navigation Integration

**Decision**: Add `NodeDetailRoute.AirQualityMetrics(destNum: Int)` as a new serializable data class in the `NodeDetailRoute` sealed interface. Register in `NodesNavigation.kt` via `addNodeDetailScreenComposable`.

**Rationale**: Exact pattern used by all other metric routes (Device, Environment, Power, Signal, etc.). The `LogsType.AIR_QUALITY` enum entry drives the navigation.

**Alternatives considered**: None — established pattern is clear.

## R7: CSV Export Column Set

**Decision**: Export ALL proto fields as CSV columns, including secondary fields (particle counts, VOC, NOx, formaldehyde, co-read temp/humidity). Headers:
```
"date","time","pm10_standard","pm25_standard","pm100_standard","pm10_environmental","pm25_environmental","pm100_environmental","particles_03um","particles_05um","particles_10um","particles_25um","particles_50um","particles_100um","co2","co2_temperature","co2_humidity","form_formaldehyde","form_humidity","form_temperature","pm40_standard","particles_40um","pm_temperature","pm_humidity","pm_voc_idx","pm_nox_idx","particles_tps"
```

**Rationale**: FR-008 requires all available proto fields. External analysis tools benefit from complete data. Empty/zero fields exported as empty cells per spec edge cases.

**Alternatives considered**:
- Only export displayed (primary) fields → rejected: spec explicitly requires all proto fields for external analysis use case.

## R8: Info Card Display Logic

**Decision**: Display info cards for PM1.0 (standard), PM2.5 (standard), PM10 (standard), and CO₂ when non-zero. Use `VectorMetricInfo` pattern from `EnvironmentMetrics.kt` component. Hide cards for zero/null values per existing convention.

**Rationale**: FR-003 specifies standard concentrations as primary display metrics. The existing `EnvironmentMetrics.kt` component pattern (build info cards list, filter nulls/NaN/zero) is well-established.

**Alternatives considered**:
- Show all 25 fields on info cards → rejected: overwhelming; design guidance says PM+CO₂ are primary.
- Show environmental concentrations instead of standard → rejected: spec explicitly calls for standard concentrations.

## R9: Icon Selection

**Decision**: Use existing `MeshtasticIcons.AirQuality` (maps to `ic_air` drawable) for the info card and log type entry.

**Rationale**: Icon already exists in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/icon/Telemetry.kt` — no new vector asset needed.

**Alternatives considered**: None — purpose-built icon already available.

## R10: Telemetry Request Button

**Decision**: No new work needed for the request button on the **node detail screen** — `TelemetricActionsSection` already includes it (line 179/181) and `CommandSenderImpl` already encodes the request (line 303). However, the **response path** is entirely missing — `TelemetryPacketHandlerImpl` does not yet handle the `air_quality_metrics` oneof, so responses are silently dropped.

**Rationale**: Verified in codebase:
- Request UI: `TelemetricActionsSection.kt` line 181 → `NodeMenuAction.RequestTelemetry(it, TelemetryType.AIR_QUALITY)`
- Request encoding: `CommandSenderImpl.kt` line 303 → constructs `Telemetry(air_quality_metrics = AirQualityMetrics())`
- Response handling: `TelemetryPacketHandlerImpl.kt` — the `when` block only handles `device_metrics`, `environment_metrics`, and `power_metrics`; `air_quality_metrics` falls through unhandled

The critical gap is in the handler. Without R1 (adding the oneof branch), the request button does nothing visible.

**Alternatives considered**: N/A — infrastructure exists, just needs the response path wired up.

## R11: Log Screen Request Action Button

**Decision**: The Air Quality log screen must include a "Request" action button via `BaseMetricScreen`'s `onRequestTelemetry` callback, calling `viewModel.requestTelemetry(TelemetryType.AIR_QUALITY)`.

**Rationale**: Environment metrics log screen already does this (line 96 of `EnvironmentMetrics.kt`):
```kotlin
onRequestTelemetry = { viewModel.requestTelemetry(TelemetryType.ENVIRONMENT) },
```
The `MetricsViewModel.requestTelemetry()` method already exists and supports `TelemetryType.AIR_QUALITY` — it delegates to `CommandSender`. The only work is wiring the callback in the new Air Quality log screen composable.

**Alternatives considered**:
- Omit request button from log screen → rejected: inconsistent with Environment/Power patterns, and users may want to refresh data while reviewing history.

## R12: End-to-End Request→Response→Display Flow

**Decision**: Document and verify the complete loop:

1. **User taps "Request Air-Quality Metrics"** (node detail OR log screen)
2. **`MetricsViewModel.requestTelemetry(TelemetryType.AIR_QUALITY)`** → delegates to `CommandSender`
3. **`CommandSenderImpl`** constructs `AdminMessage` with `Telemetry(air_quality_metrics = AirQualityMetrics())` and sends via mesh
4. **Node responds** with a `Telemetry` packet containing populated `air_quality_metrics`
5. **`TelemetryPacketHandlerImpl`** decodes the response, matches `air_quality_metrics != null`, calls `nextNode = nextNode.copy(airQualityMetrics = airQuality)` **(NEW CODE)**
6. **`NodeManager`** persists updated Node → `NodeEntity.air_quality_metrics` BLOB column **(NEW COLUMN)**
7. **UI recomposes** — info cards and log screen observe Node state via Flow

**Rationale**: This is the exact same flow used by Environment metrics. The only missing pieces are step 5 (handler branch) and step 6 (database column) — both addressed by R1 and R2.

**Alternatives considered**: None — this is the established unidirectional data flow.
