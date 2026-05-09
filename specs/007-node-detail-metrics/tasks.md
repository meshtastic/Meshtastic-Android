# Tasks: Node Detail & Metrics

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Status**: Migrated  
**Prefix**: NDM-T | **Date**: 2025-07-15

> All tasks marked `[x]` reflect existing, shipped implementation.  
> Tasks marked `[ ]` are identified **gaps** — code without tests, missing error handling, or areas for improvement.

---

## Phase 1 — Data Layer & Models

### NDM-T001: MetricsState data class ✅
- [x] Create `MetricsState` data class aggregating device, signal, power, host, traceroute, neighbor, position, and pax metrics
- [x] Include `hasXxxMetrics()` convenience methods
- [x] Include `oldestTimestampSeconds()` for time-frame availability
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/model/MetricsState.kt`

### NDM-T002: TimeFrame enum ✅
- [x] Define `TimeFrame` enum with entries: ONE_HOUR, TWENTY_FOUR_HOURS, SEVEN_DAYS, TWO_WEEKS, ONE_MONTH, ALL_TIME
- [x] Implement `timeThreshold()` and `isAvailable()` methods
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/model/TimeFrame.kt`

### NDM-T003: LogsType enum ✅
- [x] Define `LogsType` enum with 9 entries mapping to route factories, icons, and title resources
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/model/LogsType.kt`

### NDM-T003a: NodeDetailAction sealed interface ✅
- [x] Define sealed action types: Navigate, TriggerServiceAction, HandleNodeMenuAction, OpenRemoteAdmin, RefreshMetadata, ShareContact, OpenCompass
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/model/NodeDetailAction.kt`

### NDM-T003b: EnvironmentMetricsState ✅
- [x] Create `EnvironmentMetricsState` with graphing data extraction and Fahrenheit conversion support
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/EnvironmentMetricsState.kt`

---

## Phase 2 — Node Detail Screen

### NDM-T004: NodeDetailViewModel ✅
- [x] Build ViewModel combining node identity, metrics state, session status, and cooldown timestamps
- [x] Reactive `uiState` flow via `combine` + `flatMapLatest` over active node ID
- [x] `handleNodeMenuAction` for remove, ignore, mute, favorite, request telemetry, traceroute
- [x] `openRemoteAdmin` with session handshake and snackbar feedback
- [x] `refreshMetadata`, `setNodeNotes`, `getDirectMessageRoute`
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/detail/NodeDetailViewModel.kt`

### NDM-T005: NodeDetailScreens (Scaffold + Overlays) ✅
- [x] `NodeDetailScreen` composable with LaunchedEffect for nodeId start and navigation events
- [x] `NodeDetailScaffold` with MainAppBar, overlay state management
- [x] `NodeDetailOverlays` for SharedContact dialog, FirmwareRelease bottom sheet, Compass bottom sheet
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/detail/NodeDetailScreens.kt`

### NDM-T006: NodeDetailContent ✅
- [x] `Crossfade` between loading spinner and `NodeDetailList`
- [x] `NodeDetailList` as LazyColumn with NodeDetailsSection, DeviceActions, DeviceDetailsSection, NotesSection, AdministrationSection
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/detail/NodeDetailContent.kt`

### NDM-T007: NodeDetailsSection (Identity Card) ✅
- [x] Display name, role, node ID, node number, last heard, hops, user ID, uptime
- [x] Signal row (SNR + RSSI) for direct-heard nodes
- [x] MQTT + manual verification row
- [x] Public key display with base64 encoding and copy-on-long-press
- [x] MismatchKeyWarning for encryption key errors
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeDetailsSection.kt`

### NDM-T008: DeviceDetailsSection ✅
- [x] Hardware model image from CDN with Coil3 async loading and fallback
- [x] Hardware display name with optional PlatformIO target
- [x] Support status (officially supported vs community supported)
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/DeviceDetailsSection.kt`

### NDM-T009: DeviceActions ✅
- [x] Primary row: Direct Message button, Share Contact button, Favorite toggle
- [x] Management: Ignore switch, Mute switch, Remove action
- [x] `isEffectivelyUnmessageable` check to hide DM button
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/DeviceActions.kt`

### NDM-T010: AdministrationSection + FirmwareSection ✅
- [x] Remote admin with session status chip (NoSession, Active, Stale)
- [x] Progress indicator during session handshake
- [x] Refresh metadata button
- [x] Firmware edition, installed version, latest stable, latest alpha with colour-coded version comparison
- [x] Firmware release info bottom sheet
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/AdministrationSection.kt`

### NDM-T010a: TelemetricActionsSection ✅
- [x] Build 11 telemetric feature rows (user info, traceroute, neighbor info, signal, device, environment, air quality, power, host, pax, position)
- [x] Log navigation buttons with tooltip
- [x] Cooldown-guarded request buttons
- [x] Inline content for environment metrics, power metrics, and position (map + compass)
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/TelemetricActionsSection.kt`

### NDM-T010b: NotesSection ✅
- [x] Editable notes section with save callback
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NotesSection.kt`

---

## Phase 3 — Chart Infrastructure

### NDM-T011: BaseMetricScreen template ✅
- [x] `BaseMetricScreen` generic scaffold with AppBar (export, expand/collapse, info, refresh), controlPart, chartPart, listPart
- [x] Bi-directional chart↔card selection sync via `selectedX` + `animateScrollToItem`/`animateScroll`
- [x] `AdaptiveMetricLayout` with responsive split at 600dp
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/BaseMetricChart.kt`

### NDM-T012: GenericMetricChart ✅
- [x] Vico `CartesianChartHost` wrapper with multi-layer support, dual axes, markers, FadingEdges, zoom
- [x] `MarkerVisibilityListener` for point selection
- [x] Minimum x-step of 60 seconds to prevent slot-count explosion
- [x] `MetricChartScaffold` with `CartesianChartModelProducer` + Legend
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/BaseMetricChart.kt`

### NDM-T013: ChartStyling + CommonCharts ✅
- [x] Line style factories: `createBoldLine`, `createSubtleLine`, `createDashedLine`, `createGradientLine`, `createStyledLine`
- [x] Marker value formatter with colour-based label routing
- [x] Threshold line decoration (`rememberThresholdLine`)
- [x] Bottom time axis with `MetricFormatter`
- **Files**: `feature/node/metrics/ChartStyling.kt`, `feature/node/metrics/CommonCharts.kt`

### NDM-T013a: TimeFrameSelector ✅
- [x] Horizontal chip row for time-frame selection with dynamic availability
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/TimeFrameSelector.kt`

### NDM-T013b: MetricLogComponents (shared primitives) ✅
- [x] `MetricIndicator`, `MetricValueRow`, `SelectableMetricCard`, `Legend`, `LegendInfoDialog`, `DeleteItem`
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/MetricLogComponents.kt`

---

## Phase 4 — Metric Screens

### NDM-T014: DeviceMetricsScreen ✅
- [x] Dual-axis chart: battery + ch util + air util (left, 0–100%), voltage (right)
- [x] 20% battery threshold line
- [x] Dynamic legend filtering based on available data
- [x] `DeviceMetricsCard` with battery info, channel/air util, uptime
- [x] CSV export via `saveDeviceMetricsCSV`
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/DeviceMetrics.kt`

### NDM-T015: EnvironmentMetricsScreen ✅
- [x] Full environment chart with multi-series environment data
- [x] `EnvironmentMetricsCard` with sub-displays: temperature, humidity, pressure, soil, gas, IAQ, lux, UV, voltage, current, radiation, wind, rainfall, one-wire (up to 8)
- [x] Fahrenheit conversion in `filteredEnvironmentMetrics`
- [x] CSV export via `saveEnvironmentMetricsCSV` with 8 one-wire columns
- **Files**: `feature/node/metrics/EnvironmentMetrics.kt`, `feature/node/metrics/EnvironmentCharts.kt`

### NDM-T016: SignalMetricsScreen ✅
- [x] Dual-axis chart: RSSI (left, solid blue line) + SNR (right, dashed green line)
- [x] `SignalMetricsCard` with RSSI, SNR values and `LoraSignalIndicator`
- [x] CSV export via `saveSignalMetricsCSV`
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/SignalMetrics.kt`

### NDM-T017: PowerMetricsScreen ✅
- [x] Per-channel chart with `FilterChip` channel selector (up to 8 channels)
- [x] Dual-axis: current (left) + voltage (right)
- [x] `PowerMetricsCard` with per-channel voltage/current rows (up to 3 rows of 3)
- [x] CSV export via `savePowerMetricsCSV`
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/PowerMetrics.kt`

### NDM-T018: TracerouteLogScreen ✅
- [x] `resolveTraceroutePoints` pairing requests with responses by packet ID
- [x] `TracerouteMetricsChart` with forward hops (blue), return hops (green), RTT (orange)
- [x] `TracerouteCard` with route summary text, hop counts, RTT
- [x] `showTracerouteDetail` dialog with annotated route + "View on Map" button
- [x] Map availability validation before navigation
- **Files**: `feature/node/metrics/TracerouteLog.kt`, `feature/node/metrics/TracerouteChart.kt`

### NDM-T019: PositionLogScreen ✅
- [x] Track map via `LocalNodeTrackMapProvider` with selected position highlighting
- [x] `PositionCard` with coordinates, altitude, speed, heading, satellite count
- [x] Position request + clear buttons
- [x] CSV export via `savePositionCSV`
- **Files**: `feature/node/metrics/PositionLogScreens.kt`, `feature/node/metrics/PositionLogComponents.kt`

### NDM-T020: HostMetricsLogScreen ✅
- [x] `HostMetricsChart` with load averages (1/5/15) and optional free memory series
- [x] `HostMetricsCard` with uptime, free memory, disk free (up to 3 partitions), load averages with coloured progress bars, user_string
- [x] `formatBytes` helper with KB/MB/GB formatting
- **Files**: `feature/node/metrics/HostMetricsLog.kt`, `feature/node/metrics/HostMetricsChart.kt`

### NDM-T021: PaxMetricsScreen ✅
- [x] Three-series chart: total (gray), BLE (purple), WiFi (orange)
- [x] `PaxMetricsItem` with total, BLE, WiFi counts and uptime
- [x] `decodePaxFromLog` with binary proto, Base64, and hex fallback paths
- [x] Empty state message when no pax data
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/PaxMetrics.kt`

### NDM-T022: NeighborInfoLogScreen ✅
- [x] Request/response pairing by packet ID
- [x] Annotated neighbour info with colour-coded signal quality
- [x] Cooldown-guarded refresh button
- [x] Long-press to delete log entry
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/NeighborInfoLog.kt`

---

## Phase 5 — Compass

### NDM-T023: CompassViewModel ✅
- [x] Heading from `CompassHeadingProvider`, location from `PhoneLocationProvider`
- [x] True-north correction via `MagneticFieldProvider.getDeclination`
- [x] Bearing, distance, alignment detection (within 5°)
- [x] Positional accuracy from GPS accuracy × DOP or precision bits
- [x] Angular error calculation
- [x] Warning states: NO_MAGNETOMETER, NO_LOCATION_PERMISSION, LOCATION_DISABLED, NO_LOCATION_FIX
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/compass/CompassViewModel.kt`

### NDM-T024: CompassBottomSheet ✅
- [x] Compass sheet composable with heading ring, bearing pointer, distance, accuracy
- [x] Request location permission / open location settings callbacks
- [x] Lifecycle-aware start/stop via `DisposableEffect`
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/CompassBottomSheet.kt`

---

## Phase 6 — Navigation

### NDM-T025: NodesNavigation graph ✅
- [x] Nav3 `entry<T>` declarations for `NodesRoute.NodeDetail`, all 9 `NodeDetailRoute.*` screens, and `TracerouteMap`
- [x] `ListDetailSceneStrategy` pane metadata (listPane, detailPane, extraPane)
- [x] `NodeDetailScreen` enum mapping route classes to screen composables
- [x] `MetricsViewModel` scoped per `destNum` with `@InjectedParam`
- **File**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/navigation/NodesNavigation.kt`

---

## Phase 7 — Testing

### NDM-T026: NodeDetailViewModelTest ✅
- [x] Test initialization
- [x] Test `uiState` emits updates from use case
- [x] Test `handleNodeMenuAction` delegates Mute to `nodeManagementActions`
- [x] Test `handleNodeMenuAction` delegates TraceRoute to `nodeRequestActions`
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/detail/NodeDetailViewModelTest.kt`

### NDM-T027: MetricsViewModelTest ✅
- [x] Test initialization
- [x] Test `state` reflects updates from `getNodeDetailsUseCase`
- [x] Test `availableTimeFrames` filters based on oldest data
- [x] Test `savePositionCSV` writes correct header and coordinate data
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/metrics/MetricsViewModelTest.kt`

### NDM-T028: TracerouteChartTest ✅
- [x] `matchesRequestToResult` — pairs by packet ID
- [x] `computesForwardHops` — 2 intermediate → 2 hops
- [x] `directRoute_yieldsZeroHops` — no intermediates → 0 hops
- [x] `computesRoundTripSeconds` — 3.5s RTT calculation
- [x] `noMatchingResult_yieldsNulls` — mismatched ID → all nulls
- [x] `emptyInputs_returnsEmpty`
- [x] `multipleRequests_preservesOrder`
- [x] `emptyRouteBack_yieldsNullReturnHops`
- [x] `timeSeconds_truncatesSubSecondPrecision`
- [x] `returnHops_computedWhenRouteBackAvailable`
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/metrics/TracerouteChartTest.kt`

### NDM-T029: TimeFrameTest ✅
- [x] `timeThreshold` for all entries
- [x] `isAvailable` with boundary, just-under, and data-range checks
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/model/TimeFrameTest.kt`

### NDM-T030: DecodePaxFromLogTest ✅
- [x] Binary proto valid decode, want_response filter, all-zero filter, wrong portnum
- [x] Base64 fallback valid decode
- [x] Invalid raw message and empty log return null
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/metrics/DecodePaxFromLogTest.kt`

### NDM-T031: EnvironmentMetricsStateTest ✅
- [x] Graphing data time range extraction
- [x] Zero temperature handled as valid (not filtered)
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/metrics/EnvironmentMetricsStateTest.kt`

### NDM-T032: FormatBytesTest ✅
- [x] Zero, small values, KB/MB/GB boundaries, decimals, negative, custom decimal places
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/metrics/FormatBytesTest.kt`

### NDM-T033: CompassViewModelTest ✅
- [x] Compass state updates (heading, bearing, distance)
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/compass/CompassViewModelTest.kt`

### NDM-T034: HandleNodeActionTest ✅
- [x] Action routing dispatch tests
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/detail/HandleNodeActionTest.kt`

### NDM-T035: NodeManagementActionsTest ✅
- [x] Favorite, mute, ignore, remove action delegation tests
- **File**: `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/detail/NodeManagementActionsTest.kt`

---

## Identified Gaps

### NDM-T100: Missing — MetricsViewModel CSV export tests for device/environment/signal/power
- [ ] Add unit tests for `saveDeviceMetricsCSV`, `saveEnvironmentMetricsCSV`, `saveSignalMetricsCSV`, `savePowerMetricsCSV` verifying correct column headers and data formatting
- **Rationale**: Only `savePositionCSV` has a test; the other four export methods are untested.
- **Priority**: Medium

### NDM-T101: Missing — HostMetricsLogScreen chart+card test coverage
- [ ] Add unit tests for `HostMetricsChart` data model and `formatBytes` edge cases (exact boundaries)
- **Rationale**: `formatBytes` is tested but chart data transformation and card selection sync are not.
- **Priority**: Low

### NDM-T102: Missing — Compass accuracy edge cases
- [ ] Add tests for `calculatePositionalAccuracyMeters` with various DOP combinations (PDOP-only, HDOP+VDOP, HDOP-only, precision-bits-only, and none)
- [ ] Add test for `calculateAngularError` when distance is zero
- **Rationale**: `CompassViewModelTest` exists but accuracy calculation branch coverage is not verified.
- **Priority**: Medium

### NDM-T103: Missing — Environment NaN guard tests
- [ ] Add tests verifying that `NaN` temperature, humidity, and pressure values are correctly filtered (not rendered, not charted)
- **Rationale**: The code has `isNaN()` guards but no tests validate them.
- **Priority**: Low

### NDM-T104: Missing — Remote admin session timeout testing
- [ ] Add `NodeDetailViewModelTest` coverage for `openRemoteAdmin` with `Disconnected` and `Timeout` session results
- **Rationale**: Only `Mute` and `TraceRoute` actions are tested; session error paths are untested.
- **Priority**: Medium

### NDM-T105: Missing — Adaptive layout breakpoint test
- [ ] Add UI test or screenshot test verifying `AdaptiveMetricLayout` switches from Column to Row at 600dp
- **Rationale**: Responsive layout is untested.
- **Priority**: Low

---

## Summary

| Category | Total | Complete | Gaps |
|----------|-------|----------|------|
| Data Layer | 5 | 5 | 0 |
| Detail Screen | 8 | 8 | 0 |
| Chart Infrastructure | 5 | 5 | 0 |
| Metric Screens | 9 | 9 | 0 |
| Compass | 2 | 2 | 0 |
| Navigation | 1 | 1 | 0 |
| Testing | 10 | 10 | 0 |
| **Gaps** | 6 | 0 | **6** |
| **Total** | **46** | **40** | **6** |

