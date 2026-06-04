# Feature Specification: Air Quality Telemetry Display

**Feature Branch**: `20260601-074653-air-quality-telemetry`
**Created**: 2025-06-01
**Status**: Draft
**Input**: User description: "Display raw air quality / particulate sensor data from the AirQualityMetrics proto message on node detail info cards and in a dedicated metrics log screen with history, graphing, and CSV export — matching the existing patterns for Environment and Power metrics."
**Cross-Platform Spec**: https://github.com/meshtastic/design/issues/51, https://github.com/meshtastic/design/issues/53

## Summary

Add support for displaying air quality and particulate sensor telemetry data received from nodes equipped with air quality sensors (SEN5X, PMSA003I, SCD30, SCD4X). The feature focuses on the primary displayable metrics — PM1.0, PM2.5, PM10 (standard concentrations) and CO₂ — with CO₂ presented using color-coded severity thresholds per upstream design guidance. Secondary fields (particle counts, VOC index, NOx index, formaldehyde) are stored and exportable but given less visual prominence. The feature provides node detail info cards for at-a-glance status and a dedicated metrics log screen with timestamped history, thin-line charting, and CSV export, following the established patterns used by Environment and Power metrics.

### Upstream Design Decisions (design/issues/51 + design/issues/53)

Per Oscar (@oscgonfer) from the Meshtastic design team:

- **PM data** (PM1.0, PM2.5, PM10) — useful as raw µg/m³ values. Primary display metrics.
- **CO₂** — useful as raw ppm. Display with color-coded thresholds:
  - Good: 400–1000 ppm
  - Stuffy: 1000–2000 ppm
  - Poor: 2000–5000 ppm
  - Unsafe (8h work): 5000+ ppm
  - Evacuate: 30000–40000+ ppm
- **Gas resistance (Ohms)** — low value as raw display; only useful after IAQ processing. Not included in air quality display (IAQ already shown in Environment metrics).
- **Chart style** — thin lines only; dot marker shown only at the selected/cursor position to avoid clutter.
- **Telemetry category** — Air Quality is distinct from Environment/Weather. PM and chemical pollutants are its domain.

## Goals

1. Display the most recent air quality readings on the node detail info card so users can quickly assess current conditions without navigating away
2. Provide a dedicated Air Quality metrics log screen with historical readings, selectable line charts, and time frame filtering for trend analysis
3. Enable CSV export of air quality data for external analysis and reporting
4. Persist air quality telemetry to the local database so readings survive app restarts and are available for historical review
5. Integrate seamlessly into existing telemetry navigation and UI patterns so users experience consistent behavior across all metric types

## Non-Goals

- Calculating or displaying derived air quality indices (AQI) — only raw sensor values are shown (with CO₂ threshold coloring as the one exception per design guidance)
- Displaying raw gas resistance — this is handled by the existing IAQ display in Environment metrics
- Configuring air quality sensor hardware settings from the app
- Setting alert thresholds or push notifications for unhealthy readings
- Aggregating air quality data from multiple nodes into a combined view
- Displaying air quality data on the map layer
- Modifying the proto definitions (upstream read-only)
- Processing or displaying data best sent via MQTT for external analysis (per design/issues/51)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Current Air Quality Readings (Priority: P1)

A user with an air quality sensor-equipped node wants to see the latest PM2.5, PM10, and CO₂ values at a glance on the node detail screen without extra navigation.

**Why this priority**: This is the most common interaction — users check current conditions frequently and need immediate visibility of key readings.

**Independent Test**: Can be fully tested by receiving a single air quality telemetry packet and verifying the info cards render correct values on the node detail screen.

**Acceptance Scenarios**:

1. **Given** a node has received air quality telemetry, **When** the user views the node detail screen, **Then** info cards display the latest PM1.0, PM2.5, PM10 (standard concentrations) and CO₂ with appropriate labels and units (µg/m³ for PM, ppm for CO₂)
2. **Given** a node has received air quality telemetry with CO₂ data, **When** the CO₂ info card is displayed, **Then** the value is color-coded according to severity thresholds (Good ≤1000, Stuffy 1000–2000, Poor 2000–5000, Unsafe 5000+, Evacuate 30000+)
3. **Given** a node has never received air quality telemetry, **When** the user views the node detail screen, **Then** no air quality info cards are shown
4. **Given** a node receives updated air quality telemetry while the detail screen is open, **When** the new packet arrives, **Then** the info cards update to reflect the latest values

---

### User Story 2 - Browse Air Quality History (Priority: P2)

A user wants to review historical air quality readings to understand how conditions changed throughout the day — for example, checking if PM2.5 spiked after a nearby event.

**Why this priority**: Historical context transforms raw numbers into actionable insight; this is the primary reason users track metrics over time.

**Independent Test**: Can be fully tested by populating telemetry history and verifying the log screen shows timestamped cards in chronological order with correct values.

**Acceptance Scenarios**:

1. **Given** multiple air quality telemetry readings exist for a node, **When** the user navigates to the Air Quality metrics log, **Then** timestamped history cards are displayed in reverse-chronological order showing key metric values
2. **Given** the user selects a time frame filter, **When** the filter is applied, **Then** only readings within the selected time frame are displayed
3. **Given** no air quality telemetry exists for a node, **When** the user navigates to the Air Quality metrics log, **Then** an appropriate empty state is shown

---

### User Story 3 - Graph Air Quality Trends (Priority: P2)

A user wants to visually identify trends and correlations in air quality data by viewing line charts of selected metrics over time.

**Why this priority**: Graphing enables pattern recognition (e.g., daily PM cycles) that raw numbers alone cannot convey.

**Independent Test**: Can be fully tested by populating telemetry history and verifying chart renders with correct data points and legend entries.

**Acceptance Scenarios**:

1. **Given** air quality history exists, **When** the user views the chart on the Air Quality metrics log, **Then** thin line charts (no large dot markers) plot the selected metrics over time with a legend identifying each series
2. **Given** the user taps a point on the chart, **When** the selection is made, **Then** a single dot marker appears at the selected position and the corresponding history card is highlighted/scrolled to
3. **Given** some metric values are zero or absent for certain readings, **When** the chart renders, **Then** those data points are omitted gracefully without breaking the chart line

---

### User Story 4 - Export Air Quality Data (Priority: P3)

A user wants to export air quality readings to CSV for external analysis, regulatory reporting, or sharing with environmental agencies.

**Why this priority**: Export enables integration with external tools but is a secondary workflow compared to in-app viewing.

**Independent Test**: Can be fully tested by triggering CSV export and verifying the file contains correct headers and values matching the displayed history.

**Acceptance Scenarios**:

1. **Given** air quality history exists, **When** the user taps the export action, **Then** a CSV file is generated containing all displayed readings with appropriate column headers
2. **Given** a time frame filter is active, **When** the user exports, **Then** only the filtered readings are included in the CSV
3. **Given** some readings have partial data (e.g., only PM values, no CO₂), **When** CSV is exported, **Then** missing values are represented as empty cells

---

### User Story 5 - Navigate to Air Quality Metrics Log (Priority: P1)

A user sees air quality info cards on the node detail screen and wants to drill into the full history and charts.

**Why this priority**: Navigation is foundational — without it, the log screen is inaccessible.

**Independent Test**: Can be fully tested by verifying the Air Quality entry appears in the logs list and navigation leads to the correct screen.

**Acceptance Scenarios**:

1. **Given** a node has air quality telemetry, **When** the user views available metric logs for the node, **Then** an "Air Quality" option is listed with appropriate icon
2. **Given** the user selects the Air Quality log entry, **When** navigation occurs, **Then** the Air Quality metrics log screen opens for that node

---

### Edge Cases

- What happens when only a subset of air quality fields are populated (e.g., PM-only sensor with no CO₂)? Only populated fields are displayed; empty/zero fields are hidden.
- What happens when a sensor reports unrealistic values (e.g., PM2.5 = 0 from a fresh boot)? Zero values are displayed as-is since the app shows raw sensor data without validation.
- What happens when the device receives air quality telemetry from a very old firmware version that has fewer fields? Newer fields default to zero/absent per proto semantics and are simply not displayed.
- What happens when thousands of air quality readings accumulate? The same pagination/scrolling approach used by other metric logs applies (LazyColumn with efficient composable reuse).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST store the latest AirQualityMetrics on the Node model when received via telemetry
- **FR-002**: System MUST persist air quality telemetry to the database so data survives app restarts
- **FR-003**: System MUST display info cards for PM1.0, PM2.5, PM10 (standard concentrations) and CO₂ on the node detail screen when non-zero values are present
- **FR-004**: System MUST color-code the CO₂ display value using severity thresholds: Good (0–1000 ppm), Stuffy (1000–2000 ppm), Poor (2000–5000 ppm), Unsafe (5000–30000 ppm), Evacuate (30000+ ppm). Values below outdoor ambient (~420 ppm) are still categorized as Good.
- **FR-005**: System MUST provide a dedicated Air Quality metrics log screen accessible from the node detail logs list
- **FR-006**: System MUST display timestamped history cards on the Air Quality log screen showing PM and CO₂ values
- **FR-007**: System MUST render thin-line charts (dot marker only at selection point) for air quality metrics over time
- **FR-008**: System MUST support CSV export of displayed air quality readings with all available proto fields as columns (including secondary fields: particle counts, VOC index, NOx index, formaldehyde, co-read temperature/humidity)
- **FR-009**: System MUST support time frame filtering on the Air Quality log screen
- **FR-010**: System MUST handle partial data gracefully — only display fields that have meaningful non-zero values
- **FR-011**: System MUST handle the telemetry packet for `Telemetry.air_quality_metrics` oneof variant in the packet handler
- **FR-012**: System MUST include a database migration adding the air quality metrics column
- **FR-013**: System MUST NOT display raw gas_resistance in the Air Quality screen (IAQ is already shown in Environment metrics)

### Non-Functional Requirements

- **NFR-001**: Air quality info cards render within the same frame budget as existing Environment info cards (no perceptible additional lag)
- **NFR-002**: Chart rendering with 1,000+ data points remains smooth and scrollable
- **NFR-003**: All new UI composables and business logic reside in the `commonMain` source set for cross-platform compatibility

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| Node model | `core/model/` | Store `airQualityMetrics` field and `hasAirQualityMetrics` accessor |
| TelemetryPacketHandlerImpl | `core/data/` | Handle `air_quality_metrics` oneof variant |
| NodeEntity | `core/database/` | Persist air quality telemetry as BLOB column |
| Database Migration | `core/database/` | Add `air_quality_metrics` column to NodeEntity |
| AirQualityMetrics info cards | `feature/node/component/` | Display current readings on node detail |
| AirQualityMetrics log screen | `feature/node/metrics/` | History, chart, CSV export |
| LogsType.AIR_QUALITY | `feature/node/model/` | Enum entry for navigation |
| NodeDetailRoute.AirQualityMetrics | `core/navigation/` | Route definition |
| MetricsViewModel extensions | `feature/node/` | Air quality graphing data and CSV export logic |

### Data Flow

```
MeshPacket (air_quality_metrics)
  → TelemetryPacketHandlerImpl (decode + update Node)
  → NodeManager (persist to NodeEntity via database)
  → UI observes Node state
    → Info Cards (node detail screen)
    → Metrics Log Screen (history + chart + export)
```

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | New composables, model updates, packet handler logic, navigation route, database migration | All business logic and UI per Constitution I, III |
| `androidMain` | None | No platform-specific code needed |
| `jvmMain` | None | No platform-specific code needed |

## Design Standards Compliance

- [ ] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [ ] M3 component selection verified — uses existing `InfoCard`, `SelectableMetricCard`, `BaseMetricScreen` composables
- [ ] Accessibility: TalkBack semantics on info cards, adequate touch targets, units included in content descriptions
- [ ] Typography: Consistent with existing metric cards (`labelSmall` for labels, `labelLarge` for values)

## Privacy Assessment

- [ ] No PII, location data, or cryptographic keys logged or exposed — only raw sensor numerics
- [ ] No new network calls that transmit user data — reads from existing mesh telemetry only
- [ ] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can view current air quality readings on the node detail screen within 1 second of receiving telemetry
- **SC-002**: Users can access and browse full air quality history for any node with fewer than 3 taps from the node detail screen
- **SC-003**: Exported CSV files contain all air quality fields with correct headers and are importable by standard spreadsheet applications
- **SC-004**: Air quality metrics persist across app restarts with zero data loss
- **SC-005**: The Air Quality log screen supports the same time frame filters and chart interactions available on the Environment metrics log screen

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` (from `core/ui/icon/`) — a new air quality icon vector may be needed
- Float values pre-formatted with `NumberFormatter.format()` (CMP constraint)
- The `AirQualityMetrics` proto message is already available in the proto submodule and need not be modified
- The existing `BaseMetricScreen` composable framework is reused for the log screen (chart + list + export pattern)
- The telemetry request button for AIR_QUALITY already exists in `TelemetricActionsSection` and `CommandSenderImpl`
- Database migration follows the sequential numbering pattern established by prior migrations
- Zero-value fields from proto deserialization are treated as "not reported" and hidden from display (consistent with Environment metrics behavior)
- Primary display metrics: PM1.0, PM2.5, PM10 (standard concentrations in µg/m³) and CO₂ (ppm)
- Secondary metrics stored and exported but not prominently displayed: particle counts (particles/0.1L), VOC index (ppb), NOx index (ppb), formaldehyde, co-read temperature/humidity
- CO₂ threshold colors follow Oscar's guidance from design/issues/53 and use M3-compatible color tokens
- Chart rendering uses thin lines per design/issues/53 recommendation to avoid clutter; existing Environment metrics charts may use a different dot style — this feature follows the updated guidance
- Gas resistance is intentionally excluded from this feature; it is already surfaced as IAQ in the Environment metrics display
