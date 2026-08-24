# UI Contracts: Air Quality Telemetry

This feature is internal to the mobile app (no public API, library, or external service interface). The contracts below define the UI component interfaces for implementation consistency.

## Info Card Contract

### AirQualityInfoCards

**Input**: `Node` with `hasAirQualityMetrics == true`

**Output**: List of `VectorMetricInfo` items for rendering via existing `InfoCard` composable

**Card set** (shown when value > 0):

| Card | Label String | Value Format | Unit | Icon |
|------|-------------|--------------|------|------|
| PM1.0 | `Res.string.pm1_0` | Integer | µg/m³ | `MeshtasticIcons.AirQuality` |
| PM2.5 | `Res.string.pm2_5` | Integer | µg/m³ | `MeshtasticIcons.AirQuality` |
| PM10 | `Res.string.pm10` | Integer | µg/m³ | `MeshtasticIcons.AirQuality` |
| CO₂ | `Res.string.co2` | Integer | ppm | `MeshtasticIcons.AirQuality` |

**CO₂ special behavior**: Value text color determined by `Co2Severity.fromPpm(value)`.

## Log Screen Contract

### AirQualityMetricsScreen

**Route**: `NodeDetailRoute.AirQualityMetrics(destNum: Int)`

**Composable signature**:
```kotlin
@Composable
fun AirQualityMetricsScreen(
    nodeNum: Int,
    modifier: Modifier = Modifier,
)
```

**Delegates to**: `BaseMetricScreen` with:
- `metricsState`: `AirQualityMetricsState` (implements existing metric state interface)
- `chartContent`: Thin-line Vico chart with `AirQuality` enum for series selection
- `historyContent`: LazyColumn of timestamped metric cards
- `exportAction`: `saveAirQualityMetricsCSV()` from `MetricsViewModel`
- `timeFrameSelector`: Reuses existing time frame filter UI
- `onRequestTelemetry`: `{ viewModel.requestTelemetry(TelemetryType.AIR_QUALITY) }` — renders a "Request" FAB/button allowing users to manually fetch fresh readings from the node

## Request→Response→Display Contract

### Full Loop

```
┌─────────────────────────────────────────────────────────────────┐
│ User taps "Request Air-Quality Metrics"                         │
│   (node detail TelemetricActionsSection OR log screen button)   │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ MetricsViewModel.requestTelemetry(TelemetryType.AIR_QUALITY)    │
│   → CommandSender.requestTelemetry(destNum, AIR_QUALITY)        │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ CommandSenderImpl encodes AdminMessage with                      │
│   Telemetry(air_quality_metrics = AirQualityMetrics())          │
│   → sends via MeshProtos.ToRadio                                │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼ (mesh radio)
┌─────────────────────────────────────────────────────────────────┐
│ Remote node responds: Telemetry packet with populated           │
│   air_quality_metrics field                                     │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ TelemetryPacketHandlerImpl.handle()                             │
│   → air_quality_metrics != null branch (NEW)                    │
│   → nextNode = nextNode.copy(airQualityMetrics = airQuality)    │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ NodeManager persists → NodeEntity.air_quality_metrics (BLOB)    │
│ Node state Flow emits update                                    │
└─────────────────────────┬───────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ UI recomposes:                                                  │
│   • Info cards show updated PM/CO₂ values                       │
│   • Log screen appends new history entry                        │
│   • Chart adds new data point                                   │
└─────────────────────────────────────────────────────────────────┘
```

**Existing (no changes needed)**:
- `TelemetricActionsSection.kt` line 181 — button UI
- `CommandSenderImpl.kt` line 303 — request encoding
- `MetricsViewModel.requestTelemetry()` — method already handles any `TelemetryType`

**New code required**:
- `TelemetryPacketHandlerImpl.kt` — add `air_quality_metrics` branch to `when` block
- Air Quality log screen — wire `onRequestTelemetry` callback to `BaseMetricScreen`

## CSV Export Contract

### Column Format

```csv
"date","time","pm10_standard","pm25_standard","pm100_standard","pm10_environmental","pm25_environmental","pm100_environmental","particles_03um","particles_05um","particles_10um","particles_25um","particles_50um","particles_100um","co2","co2_temperature","co2_humidity","form_formaldehyde","form_humidity","form_temperature","pm40_standard","particles_40um","pm_temperature","pm_humidity","pm_voc_idx","pm_nox_idx","particles_tps"
```

- Date format: locale-aware via `epochSeconds` → `exportCsv` helper
- Missing/zero fields: empty string in CSV cell
- Float fields: raw numeric (no formatting applied to CSV output)

## Navigation Contract

### Entry Points

1. **LogsType list** → `LogsType.AIR_QUALITY` entry visible when `node.hasAirQualityMetrics`
2. **Route** → `NodeDetailRoute.AirQualityMetrics(destNum)` registered in `NodesNavigation.kt`
3. **Back navigation** → `NavigationBackHandler` returns to node detail screen
