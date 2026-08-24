# Data Model: Air Quality Telemetry Display

## Entities

### AirQualityMetrics (Proto — read-only upstream)

Source: `core/proto/src/main/proto/meshtastic/telemetry.proto` (field 4 of `Telemetry` oneof)

| Field | Type | Unit | Display | Description |
|-------|------|------|---------|-------------|
| `pm10_standard` | uint32 | µg/m³ | Primary | PM1.0 standard concentration |
| `pm25_standard` | uint32 | µg/m³ | Primary | PM2.5 standard concentration |
| `pm100_standard` | uint32 | µg/m³ | Primary | PM10.0 standard concentration |
| `pm10_environmental` | uint32 | µg/m³ | CSV-only | PM1.0 environmental concentration |
| `pm25_environmental` | uint32 | µg/m³ | CSV-only | PM2.5 environmental concentration |
| `pm100_environmental` | uint32 | µg/m³ | CSV-only | PM10.0 environmental concentration |
| `particles_03um` | uint32 | #/0.1L | CSV-only | 0.3µm particle count |
| `particles_05um` | uint32 | #/0.1L | CSV-only | 0.5µm particle count |
| `particles_10um` | uint32 | #/0.1L | CSV-only | 1.0µm particle count |
| `particles_25um` | uint32 | #/0.1L | CSV-only | 2.5µm particle count |
| `particles_50um` | uint32 | #/0.1L | CSV-only | 5.0µm particle count |
| `particles_100um` | uint32 | #/0.1L | CSV-only | 10.0µm particle count |
| `co2` | uint32 | ppm | Primary | CO₂ concentration (color-coded) |
| `co2_temperature` | float | °C | CSV-only | CO₂ sensor temperature |
| `co2_humidity` | float | %RH | CSV-only | CO₂ sensor relative humidity |
| `form_formaldehyde` | float | ppb | CSV-only | Formaldehyde concentration |
| `form_humidity` | float | %RH | CSV-only | Formaldehyde sensor humidity |
| `form_temperature` | float | °C | CSV-only | Formaldehyde sensor temperature |
| `pm40_standard` | uint32 | µg/m³ | CSV-only | PM4.0 standard concentration |
| `particles_40um` | uint32 | #/0.1L | CSV-only | 4.0µm particle count |
| `pm_temperature` | float | °C | CSV-only | PM sensor temperature |
| `pm_humidity` | float | %RH | CSV-only | PM sensor humidity |
| `pm_voc_idx` | float | ppb | CSV-only | VOC index |
| `pm_nox_idx` | float | ppb | CSV-only | NOx index |
| `particles_tps` | float | µm | CSV-only | Typical particle size |

### Node (Domain Model)

File: `core/model/src/commonMain/kotlin/org/meshtastic/core/model/Node.kt`

**New fields:**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `airQualityMetrics` | `AirQualityMetrics` | `AirQualityMetrics()` | Latest air quality readings |

**New computed properties:**

| Property | Type | Logic |
|----------|------|-------|
| `hasAirQualityMetrics` | `Boolean` | `airQualityMetrics != AirQualityMetrics()` |

### NodeEntity (Database Entity)

File: `core/database/src/commonMain/kotlin/org/meshtastic/core/database/entity/NodeEntity.kt`

**New column:**

| Column | Affinity | Type | Default | Description |
|--------|----------|------|---------|-------------|
| `air_quality_metrics` | BLOB | `Telemetry` | `Telemetry()` | Serialized Telemetry proto containing air_quality_metrics oneof |

**New accessor property:**

```kotlin
val airQualityMetrics: AirQualityMetrics?
    get() = airQualityTelemetry.air_quality_metrics
```

### Database Migration

| From | To | Type | Change |
|------|-----|------|--------|
| 38 | 39 | Auto-migration | Add nullable `air_quality_metrics` BLOB column to `node_entity` table |

## Relationships

```
Telemetry Proto (oneof)
  └── AirQualityMetrics (field 4)

MeshPacket
  → TelemetryPacketHandlerImpl (decode)
    → Node.airQualityMetrics (in-memory state)
    → NodeEntity.air_quality_metrics (persisted BLOB)

Node
  ├── hasAirQualityMetrics → drives info card visibility
  └── airQualityMetrics → feeds info card values + log screen history
```

## Enumerations

### Co2Severity

New utility in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/Co2Severity.kt`

| Level | Range (ppm) | Color Token | Label |
|-------|-------------|-------------|-------|
| GOOD | 0–1000 | M3 tertiary/green | Good |
| STUFFY | 1000–2000 | M3 secondary/yellow | Stuffy |
| POOR | 2000–5000 | Custom warning/orange | Poor |
| UNSAFE | 5000–30000 | M3 error/red | Unsafe |
| EVACUATE | 30000+ | M3 error/red + emphasis | Evacuate |

### LogsType.AIR_QUALITY

New enum entry in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/model/LogsType.kt`

```kotlin
AIR_QUALITY(Res.string.air_quality_metrics_log, MeshtasticIcons.AirQuality, { NodeDetailRoute.AirQualityMetrics(it) })
```

### AirQuality (Chart Metric Enum)

New enum for selectable chart metrics in the Air Quality log screen:

| Entry | Label | Unit | Proto Field |
|-------|-------|------|-------------|
| PM1_0 | PM1.0 | µg/m³ | `pm10_standard` |
| PM2_5 | PM2.5 | µg/m³ | `pm25_standard` |
| PM10 | PM10 | µg/m³ | `pm100_standard` |
| CO2 | CO₂ | ppm | `co2` |

## Validation Rules

- Zero/null proto field values → field is "not reported" → hide from info cards
- CO₂ color severity only applied when `co2 > 0`
- Float fields (temperatures, VOC, NOx) pre-formatted with `NumberFormatter.format()` before display
- No upper-bound validation on sensor values (raw display per spec non-goals)

## State Transitions

No complex state machine. The data flow is unidirectional:

```
Packet received → Node updated → UI recomposes
```

The only "state" is presence/absence of data:
- No telemetry → no info cards shown, empty state on log screen
- Telemetry received → info cards visible, log entries populated
