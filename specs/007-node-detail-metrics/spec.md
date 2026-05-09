# Feature Specification: Node Detail & Metrics

**Feature Branch**: `007-node-detail-metrics`  
**Created**: 2025-07-15  
**Status**: Migrated  
**Input**: Brownfield migration of existing `feature/node` detail screens, metrics charts, compass, and telemetry request actions (excluding node list layout covered by spec 002).

## Summary

The Node Detail & Metrics feature provides a comprehensive per-node inspection experience, allowing users to view a node's identity, hardware details, firmware status, telemetry data, position, and network diagnostics. It aggregates nine distinct metric log screens — device, environment, signal, power, host, pax, traceroute, neighbor info, and position — each with interactive Vico charts, time-frame filtering, CSV export, and card-to-chart synchronisation. A compass bottom-sheet offers bearing/distance guidance toward a target node using the phone's sensors.

## Goals

1. **G-001**: Provide a single, scrollable node detail screen showing identity (name, role, node ID, public key), signal metrics (SNR, RSSI), hops, uptime, and MQTT/PKC status.
2. **G-002**: Deliver nine dedicated metric log screens with interactive line charts (Vico), time-frame filtering (1h → all-time), and bi-directional chart↔card selection sync.
3. **G-003**: Support CSV export for device, environment, signal, power, and position metrics.
4. **G-004**: Enable on-demand telemetry requests (device, environment, signal, power, host, pax, air quality) and network diagnostics (traceroute, neighbor info) with cooldown-guarded buttons.
5. **G-005**: Provide compass-based bearing/distance guidance toward a target node with true-north correction, positional accuracy, and real-time heading updates.

## Non-Goals

- **NG-001**: Node list layout, sorting, filtering, and density settings (covered by spec 002 — `NodeItem`, `NodeItemCompact`, `NodeListDensity`, `NodeListHelp`, `NodeLayoutSettings`).
- **NG-002**: Channel/radio configuration UI (handled by `feature/settings`).
- **NG-003**: Map rendering implementation (provided by platform-specific `LocalInlineMapProvider` / `LocalTracerouteMapScreenProvider`).
- **NG-004**: Full firmware OTA update flow (covered by spec 006).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — View Node Details (Priority: P1)

A user taps a node in the node list to see its full identity, hardware details, signal quality, and firmware version on a single scrollable screen.

**Why this priority**: Core discovery — users need to inspect any node's metadata before taking further action.

**Independent Test**: Navigating to a node detail screen and verifying that all sections (details, device, notes, administration, firmware) render correctly with real or mock data.

**Acceptance Scenarios**:

1. **Given** a node with a valid position and PKC key, **When** the user navigates to the detail screen, **Then** the NodeDetailsSection displays short name, role, node ID, node number, last heard, hops away, user ID, uptime, SNR, RSSI, via MQTT status, and public key.
2. **Given** a node with known hardware, **When** the DeviceDetailsSection renders, **Then** it shows the hardware model image, display name, PlatformIO target, and support status.
3. **Given** a remote node with metadata, **When** the AdministrationSection renders, **Then** it shows session status (NoSession / Active / Stale), remote-admin button, refresh metadata, firmware edition, installed version, latest stable, and latest alpha with colour-coded version comparison.
4. **Given** a node with a mismatched encryption key, **When** the detail screen loads, **Then** the MismatchKeyWarning is displayed in the details section.

---

### User Story 2 — View & Export Device Metrics (Priority: P1)

A user views battery level, voltage, channel utilisation, air utilisation, and uptime over time as interactive charts and card lists, and optionally exports the data as CSV.

**Why this priority**: Battery and channel metrics are the most commonly monitored telemetry values.

**Independent Test**: Navigate to DeviceMetricsScreen, verify chart renders with 4 series (battery, voltage, ch util, air util), select a card to highlight the corresponding chart point, change the time frame, and export CSV.

**Acceptance Scenarios**:

1. **Given** device telemetry data for a node, **When** the user opens Device Metrics, **Then** a dual-axis Vico chart is shown (percent on left, voltage on right) with threshold line at 20% battery.
2. **Given** the user selects a metric card, **When** the card is clicked, **Then** the chart scrolls and highlights the corresponding data point, and vice-versa.
3. **Given** data spanning 8 days, **When** the TimeFrameSelector is shown, **Then** only time frames that fit the data range are enabled (1h, 24h, 1 week; not 2 weeks or 1 month).
4. **Given** device metric data, **When** the user taps the save icon, **Then** a CSV file is written with date, time, batteryLevel, voltage, channelUtilization, airUtilTx, and uptimeSeconds columns.

---

### User Story 3 — View Environment Metrics (Priority: P1)

A user views temperature, humidity, barometric pressure, soil metrics, wind, rainfall, IAQ, gas resistance, lux, UV lux, voltage, current, radiation, and one-wire temperature sensors.

**Why this priority**: Environment sensors are a primary use-case for Meshtastic sensor networks.

**Independent Test**: Navigate to EnvironmentMetricsScreen with a full set of environment telemetry and verify all sub-displays render; toggle Fahrenheit to verify temperature unit conversion.

**Acceptance Scenarios**:

1. **Given** environment telemetry with temperature data, **When** `isFahrenheit` is true, **Then** temperatures are converted from Celsius to Fahrenheit in both the chart and the cards.
2. **Given** environment telemetry with one-wire sensors, **When** the card renders, **Then** up to 8 one-wire temperature readings are displayed with individual color indicators.
3. **Given** environment data, **When** the user exports, **Then** the CSV includes all environment fields including the 8 one-wire columns.

---

### User Story 4 — View Signal Metrics (Priority: P2)

A user views RSSI and SNR for packets received from a node over time with a dual-axis chart and LoRa signal quality indicator.

**Why this priority**: Signal quality is critical for mesh network optimisation.

**Independent Test**: Open SignalMetricsScreen, verify RSSI (left axis) and SNR (right axis) chart rendering with LoraSignalIndicator in each card.

**Acceptance Scenarios**:

1. **Given** signal metrics from a node, **When** displayed, **Then** the chart shows RSSI on the left axis and SNR on the right axis with distinct colours and line styles (solid vs dashed).
2. **Given** a signal card, **When** rendered, **Then** it includes a LoraSignalIndicator widget showing signal quality derived from SNR and RSSI.

---

### User Story 5 — Traceroute & Network Diagnostics (Priority: P2)

A user runs a traceroute to a remote node to discover the mesh path, view forward/return hops, round-trip time, and optionally view results on a map.

**Why this priority**: Network path discovery is key for troubleshooting mesh connectivity.

**Independent Test**: Trigger a traceroute, wait for a response, verify the TracerouteLogScreen shows matched request/response pairs with hop counts and RTT, and tap "View on Map".

**Acceptance Scenarios**:

1. **Given** a traceroute request and matching response, **When** `resolveTraceroutePoints` runs, **Then** the result contains forward hops, return hops (if available), and round-trip seconds.
2. **Given** a traceroute point, **When** the user taps a card, **Then** a detail dialog shows annotated forward/return routes with colour-coded node names and a "View on Map" button.
3. **Given** no matching response, **When** the card renders, **Then** it shows "No response" with a PersonOff icon and null hop/RTT values.
4. **Given** traceroute results, **When** the user views the chart, **Then** forward hops (blue), return hops (green), and RTT (orange) are displayed as separate line series.

---

### User Story 6 — Neighbor Info (Priority: P2)

A user requests neighbor info from a node to see which nodes it can directly hear.

**Why this priority**: Neighbor info complements traceroute for mesh topology understanding.

**Independent Test**: Request neighbor info, verify NeighborInfoLogScreen shows annotated results with colour-coded signal quality.

**Acceptance Scenarios**:

1. **Given** a neighbor info response, **When** displayed, **Then** the result is shown with annotated, colour-coded neighbor entries.
2. **Given** a cooldown period on the request button, **When** the button was recently pressed, **Then** it is disabled until cooldown expires.

---

### User Story 7 — Power, Host, and Pax Metrics (Priority: P3)

A user views power channel voltage/current (up to 8 channels), host system load/memory/disk, and paxcount (BLE+WiFi device counts) on dedicated metric screens.

**Why this priority**: Specialised sensor data for advanced use cases.

**Independent Test**: Open each screen and verify chart/card rendering with time-frame filtering.

**Acceptance Scenarios**:

1. **Given** power metrics with 3 active channels, **When** PowerMetricsScreen opens, **Then** a channel selector chip row appears and the chart updates per selected channel.
2. **Given** host metrics, **When** HostMetricsLogScreen renders, **Then** load averages show coloured progress bars and free memory/disk are formatted with human-readable byte strings.
3. **Given** pax metrics, **When** PaxMetricsScreen renders, **Then** the chart shows three series (total, BLE, WiFi) and cards display total, BLE, WiFi, and uptime.

---

### User Story 8 — Position Log & Compass (Priority: P2)

A user views historical GPS positions on a map and in a card list, and opens a bearing compass toward a target node.

**Why this priority**: Position tracking is central to outdoor mesh deployments.

**Independent Test**: Open PositionLogScreen, verify map integration and card list; open compass bottom-sheet and verify heading, bearing, distance, and warning states.

**Acceptance Scenarios**:

1. **Given** position logs for a node, **When** the user opens Position Log, **Then** a map shows the node's track and a card list shows each position with coordinates, altitude, speed, heading, and satellite count.
2. **Given** a target node with a valid position, **When** the user opens the compass, **Then** heading, bearing, distance, and alignment indicator are shown with true-north correction applied.
3. **Given** no magnetometer sensor, **When** the compass opens, **Then** a `NO_MAGNETOMETER` warning is displayed.
4. **Given** position data, **When** the user taps the save icon, **Then** a CSV file includes latitude, longitude, altitude, satsInView, speed, and heading.

---

### User Story 9 — Node Management Actions (Priority: P2)

A user manages a node by sending direct messages, sharing its contact QR code, favoriting, muting, ignoring, or removing it from the local database.

**Why this priority**: Node management is a secondary but essential user flow from the detail screen.

**Independent Test**: From the detail screen, favorite/unfavorite a node, toggle ignore/mute, tap "Direct Message", share contact, and remove the node.

**Acceptance Scenarios**:

1. **Given** a remote node that is not effectively unmessageable, **When** the detail screen renders, **Then** a "Direct Message" button is shown.
2. **Given** the user taps the favorite toggle, **When** the action is dispatched, **Then** `NodeManagementActions.requestFavoriteNode` is called.
3. **Given** the user taps "Remove", **When** confirmed, **Then** the node is removed from the local database and the user is navigated back.

---

### User Story 10 — Remote Administration (Priority: P3)

A user opens remote admin for a node, with passkey session management and status feedback.

**Why this priority**: Advanced feature for power users managing remote mesh nodes.

**Independent Test**: Open remote admin on a connected node, verify session status transitions (NoSession → Active), and handle timeout/disconnection snackbars.

**Acceptance Scenarios**:

1. **Given** a connected radio, **When** the user taps Remote Admin, **Then** `ensureRemoteAdminSession` is called and on success, the app navigates to `SettingsRoute.Settings(destNum)`.
2. **Given** a disconnected radio, **When** remote admin is attempted, **Then** a snackbar shows "Connect radio for remote admin".
3. **Given** session timeout, **When** remote admin is attempted, **Then** a snackbar shows "Remote admin unreachable".

---

### Edge Cases

- What happens when a node has no telemetry data? → Metric screens show empty charts and empty card lists.
- What happens when environment metrics have `NaN` values? → Individual displays guard with `isNaN()` checks and skip rendering.
- What happens when `paxcount` has all-zero values? → `decodePaxFromLog` returns null, filtering out the entry.
- What happens when traceroute sub-second precision timestamps are used? → `timeSeconds` truncates to whole seconds to prevent Vico crashes.
- What happens when `soil_moisture` is `Int.MIN_VALUE`? → The sentinel value is filtered out in `SoilMetricsDisplay`.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| `NodeDetailScreen` | `feature/node/detail/NodeDetailScreens.kt` | Top-level detail scaffold with nav, overlays, and action routing |
| `NodeDetailContent` | `feature/node/detail/NodeDetailContent.kt` | Crossfade loading → scrollable detail list |
| `NodeDetailViewModel` | `feature/node/detail/NodeDetailViewModel.kt` | Coordinates node identity, metrics, session status |
| `MetricsViewModel` | `feature/node/metrics/MetricsViewModel.kt` | Manages metric data, time-frame filtering, CSV export, traceroute overlay cache |
| `BaseMetricScreen` | `feature/node/metrics/BaseMetricChart.kt` | Generic scaffold for all metric screens (AppBar, adaptive layout, chart↔list sync) |
| `GenericMetricChart` | `feature/node/metrics/BaseMetricChart.kt` | Vico CartesianChartHost wrapper with markers, FadingEdges, zoom |
| `DeviceMetricsScreen` | `feature/node/metrics/DeviceMetrics.kt` | Battery, voltage, channel util, air util chart + cards |
| `EnvironmentMetricsScreen` | `feature/node/metrics/EnvironmentMetrics.kt` | Full environment sensor display |
| `SignalMetricsScreen` | `feature/node/metrics/SignalMetrics.kt` | RSSI + SNR dual-axis chart |
| `PowerMetricsScreen` | `feature/node/metrics/PowerMetrics.kt` | Multi-channel power voltage/current |
| `TracerouteLogScreen` | `feature/node/metrics/TracerouteLog.kt` | Traceroute request/response pairing, hop chart, map integration |
| `PositionLogScreen` | `feature/node/metrics/PositionLogScreens.kt` | Position track map + card list |
| `HostMetricsLogScreen` | `feature/node/metrics/HostMetricsLog.kt` | Linux host load/memory/disk |
| `PaxMetricsScreen` | `feature/node/metrics/PaxMetrics.kt` | BLE + WiFi paxcount chart |
| `NeighborInfoLogScreen` | `feature/node/metrics/NeighborInfoLog.kt` | Neighbor discovery log |
| `CompassViewModel` | `feature/node/compass/CompassViewModel.kt` | Heading, bearing, distance, true-north correction |
| `NodeDetailsSection` | `feature/node/component/NodeDetailsSection.kt` | Identity card (name, role, ID, hops, signal, PKC) |
| `DeviceDetailsSection` | `feature/node/component/DeviceDetailsSection.kt` | Hardware model image, support status |
| `DeviceActions` | `feature/node/component/DeviceActions.kt` | DM, share, favorite, ignore, mute, remove |
| `TelemetricActionsSection` | `feature/node/component/TelemetricActionsSection.kt` | Telemetry request buttons + inline log navigation |
| `AdministrationSection` | `feature/node/component/AdministrationSection.kt` | Remote admin session + firmware version info |
| `TimeFrame` | `feature/node/model/TimeFrame.kt` | Enum of 1h → all-time windows with threshold calculation |
| `MetricsState` | `feature/node/model/MetricsState.kt` | Aggregate state for all metric types + hardware |
| `NodesNavigation` | `feature/node/navigation/NodesNavigation.kt` | Nav3 graph entries with ListDetail pane strategy |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display node identity (short name, role, node ID, node number, last heard, hops away, user ID, uptime) in a structured detail section.
- **FR-002**: System MUST display SNR and RSSI for directly-heard nodes (hops == 0 and not viaMqtt).
- **FR-003**: System MUST display public key as base64, with long-press to copy; display "Error" for all-zero 32-byte keys.
- **FR-004**: System MUST render nine distinct metric log screens (device, environment, signal, power, host, pax, traceroute, neighbor info, position).
- **FR-005**: Each metric screen MUST support time-frame filtering with `TimeFrame` enum (1h, 24h, 1 week, 2 weeks, 1 month, all time).
- **FR-006**: Time-frame options MUST be dynamically filtered based on the oldest available data point.
- **FR-007**: Metric charts MUST use Vico `CartesianChartHost` with dual-axis support and `FadingEdges`.
- **FR-008**: Selecting a chart point MUST scroll the card list to the matching item, and vice-versa.
- **FR-009**: System MUST support CSV export for device, environment, signal, power, and position metrics.
- **FR-010**: Telemetry request buttons MUST enforce cooldown periods to prevent spamming the mesh.
- **FR-011**: Traceroute results MUST be paired with requests by packet ID and display forward hops, return hops, and round-trip seconds.
- **FR-012**: Compass MUST apply true-north correction using magnetic declination from the phone's location.
- **FR-013**: Compass MUST calculate positional accuracy from GPS accuracy + DOP or precision bits.
- **FR-014**: Environment metrics MUST convert temperatures to Fahrenheit when `isFahrenheit` is true.
- **FR-015**: Remote admin MUST ensure a fresh session passkey before navigating to settings.
- **FR-016**: System MUST display hardware model image loaded from `flasher.meshtastic.org` with fallback placeholder.
- **FR-017**: Firmware version MUST be colour-coded (green = latest stable, yellow = between stable and alpha, orange = above alpha, red = below stable).
- **FR-018**: System MUST display device actions (DM, share contact, favorite, ignore, mute, remove) for remote nodes.

### Non-Functional Requirements

- **NFR-001**: All UI composables and business logic MUST reside in `commonMain` (KMP — Constitution §I, §III).
- **NFR-002**: Float values MUST be pre-formatted with `NumberFormatter.format()` / `MetricFormatter` (CMP constraint).
- **NFR-003**: Metric charts MUST use a minimum x-step of 60 seconds to prevent Vico slot-count explosion with irregular timestamps.
- **NFR-004**: Adaptive layout MUST switch between side-by-side (≥600dp) and stacked (< 600dp) chart/list arrangement.
- **NFR-005**: Chart expand/collapse toggle MUST animate with `AnimatedVisibility`.

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | All ~70 files in scope | All business logic, Compose UI, ViewModels, and navigation |
| `androidMain` | Platform `expect` implementations only | `CompassHeadingProvider`, `PhoneLocationProvider`, `MagneticFieldProvider` |
| `jvmMain` | Desktop `expect` implementations | Same three compass providers (stubs) |

## Design Standards Compliance

- [x] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [x] M3 component selection verified (SectionCard, ListItem, SwitchListItem, FilterChip, AssistChip, etc.)
- [x] Accessibility: TalkBack semantics on loading spinner, chart expand/collapse, all icon buttons
- [x] Typography: `titleMediumEmphasized` for card timestamps, M3 scale throughout

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed (public keys displayed to user, never transmitted)
- [x] No new network calls that transmit user data (hardware image fetched from public CDN)
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All nine metric log screens render without crashes for nodes with 0, 1, and 100+ data points.
- **SC-002**: Time-frame filtering correctly limits displayed data and enables/disables selector chips.
- **SC-003**: Chart↔card selection sync scrolls to the matching item within 300ms.
- **SC-004**: CSV export produces valid, importable files with correct column headers.
- **SC-005**: Traceroute request/response pairing correctly matches by packet ID with accurate hop counts and RTT.
- **SC-006**: Compass shows correct bearing ±1° when phone location and target position are both available.
- **SC-007**: Remote admin session handshake completes within 10 seconds or shows timeout snackbar.

## Assumptions

- All business logic and UI composables reside in `commonMain` source set.
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`.
- Icons use `MeshtasticIcons` (from `core/ui/icon/`).
- Float values pre-formatted with `NumberFormatter.format()` (CMP constraint).
- Vico charting library (Patrykandpatrick) is the standard for all metric graphs.
- Platform providers for compass (`CompassHeadingProvider`, `PhoneLocationProvider`, `MagneticFieldProvider`) have `expect`/`actual` implementations per target.
- `GetNodeDetailsUseCase` aggregates node identity, metrics state, and environment state into a single reactive flow.
- The traceroute map screen is provided via `LocalTracerouteMapScreenProvider` composition local.

