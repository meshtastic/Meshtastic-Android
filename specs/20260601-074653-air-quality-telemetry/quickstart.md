# Quickstart: Air Quality Telemetry Display

## Prerequisites

- Kotlin 2.3+ / JDK 21
- Android Studio with KMP plugin or IntelliJ with Compose Multiplatform
- Project builds successfully: `./gradlew assembleDebug`
- Proto submodule initialized: `git submodule update --init`

## Build & Verify

```bash
# Full build
./gradlew assembleDebug

# Lint + format
./gradlew spotlessApply spotlessCheck detekt

# Unit tests for touched modules
./gradlew :core:model:test :core:data:test :core:database:test :feature:node:test
```

## Key Files to Modify

### 1. Node Model (`core:model`)
```
core/model/src/commonMain/kotlin/org/meshtastic/core/model/Node.kt
```
Add `airQualityMetrics` field and `hasAirQualityMetrics` computed property.

### 2. Telemetry Handler (`core:data`)
```
core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/TelemetryPacketHandlerImpl.kt
```
Add `air_quality_metrics` branch to the telemetry oneof `when` block.

### 3. Database Entity (`core:database`)
```
core/database/src/commonMain/kotlin/org/meshtastic/core/database/entity/NodeEntity.kt
core/database/src/commonMain/kotlin/org/meshtastic/core/database/MeshtasticDatabase.kt
```
Add BLOB column + bump version to 39.

### 4. Navigation Route (`core:navigation`)
```
core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt
```
Add `NodeDetailRoute.AirQualityMetrics(destNum: Int)`.

### 5. Info Cards (`feature:node`)
```
feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/AirQualityMetrics.kt
```
New composable building `VectorMetricInfo` list from `AirQualityMetrics` proto.

### 6. Log Screen (`feature:node`)
```
feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/AirQualityMetrics.kt
```
New composable using `BaseMetricScreen` with chart + history + export.

### 7. LogsType Enum
```
feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/model/LogsType.kt
```
Add `AIR_QUALITY` entry.

### 8. String Resources
```
core/resources/src/commonMain/composeResources/values/strings.xml
```
Add air quality labels, then run `python3 scripts/sort-strings.py`.

## Testing Approach

1. **Unit test** the CO₂ severity threshold mapping
2. **Unit test** the info card list builder (given metrics, expect correct card output)
3. **Unit test** CSV export column generation
4. **Integration test** telemetry packet handler correctly updates Node state
5. **Screenshot test** (if applicable) info cards and log screen composables

## Patterns to Follow

| Pattern | Reference File |
|---------|---------------|
| Info cards | `feature/node/src/commonMain/.../component/EnvironmentMetrics.kt` |
| Log screen | `feature/node/src/commonMain/.../metrics/EnvironmentMetrics.kt` |
| CSV export | `MetricsViewModel.kt` → `saveEnvironmentMetricsCSV()` |
| Route registration | `NodesNavigation.kt` → `NodeDetailRoute.EnvironmentMetrics::class` |
| Database column | `NodeEntity.kt` → `environment_metrics` BLOB column |
| Icon usage | `core/ui/.../icon/Telemetry.kt` → `MeshtasticIcons.AirQuality` |
