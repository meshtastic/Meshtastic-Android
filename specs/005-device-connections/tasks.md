# Tasks: Device Connections

**Input**: Design documents from `/specs/005-device-connections/`
**Prerequisites**: plan.md (required), spec.md (required for user stories)
**Status**: Migrated — all implemented tasks marked `[x]`; gap tasks marked `[ ]`

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **KMP commonMain**: `feature/connections/src/commonMain/kotlin/org/meshtastic/feature/connections/`
- **androidMain**: `feature/connections/src/androidMain/kotlin/org/meshtastic/feature/connections/`
- **jvmMain**: `feature/connections/src/jvmMain/kotlin/org/meshtastic/feature/connections/`
- **Tests (KMP)**: `feature/connections/src/commonTest/kotlin/org/meshtastic/feature/connections/`
- **Resources**: `core/resources/src/commonMain/composeResources/values/strings.xml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Build configuration, constants, DI module, and navigation registration.

- [x] DC-T001 [P] Configure `build.gradle.kts` with `meshtastic.kmp.feature` plugin, `commonMain` dependencies (`core:common`, `core:data`, `core:database`, `core:datastore`, `core:di`, `core:domain`, `core:model`, `core:navigation`, `core:prefs`, `core:proto`, `core:resources`, `core:service`, `core:ui`, `core:ble`, `core:network`, `feature:settings`), and `androidMain` dependency (`usb-serial-android`).
- [x] DC-T002 [P] Create `Constants.kt` — define `NO_DEVICE_SELECTED`, `TCP_DEVICE_PREFIX`, `MOCK_DEVICE_PREFIX`, `BLE_DEVICE_PREFIX` sentinel constants.
- [x] DC-T003 [P] Create `di/FeatureConnectionsModule.kt` — Koin `@Module` with `@ComponentScan("org.meshtastic.feature.connections")`.
- [x] DC-T004 [P] Create `navigation/ConnectionsNavigation.kt` — Navigation 3 `connectionsGraph()` with entries for `ConnectionsRoute.ConnectionsGraph` and `ConnectionsRoute.Connections`.

**Dependencies**: None.
**Checkpoint**: Module builds, DI wired, navigation registered.

---

## Phase 2: Models & Domain Logic

**Purpose**: Data models, use case interfaces, platform implementations, and TCP discovery helpers.

- [x] DC-T005 [P] [US1/US2/US4] Create `model/DeviceListEntry.kt` — sealed class with `Ble`, `Usb`, `Tcp`, `Mock` subtypes. Include `UsbDeviceData` interface, `fullAddress`/`address` properties, `getMeshtasticShortName()` extension.
- [x] DC-T006 [P] [US1/US2/US4] Create `model/DiscoveredDevices.kt` — data class aggregating `bleDevices`, `usbDevices`, `discoveredTcpDevices`, `recentTcpDevices`. Define `GetDiscoveredDevicesUseCase` interface.
- [x] DC-T007 [P] [US4] Create `domain/usecase/UsbScanner.kt` — interface for platform USB device enumeration.
- [x] DC-T008 [P] [US2] Create `domain/usecase/TcpDiscoveryHelpers.kt` — shared helpers: `processTcpServices()`, `matchDiscoveredTcpNodes()`, `buildRecentTcpEntries()`, `findNodeByNameSuffix()`.
- [x] DC-T009 [US2/US4] Create `domain/usecase/CommonGetDiscoveredDevicesUseCase.kt` — platform-agnostic implementation combining TCP, USB, and mock devices via `combine()`. Not `@Single` annotated (platform overrides provide canonical binding).
- [x] DC-T010 [US1/US4] Create `androidMain/.../AndroidGetDiscoveredDevicesUseCase.kt` — Android-specific: bonded BLE filtering via `BluetoothRepository.state`, USB via `UsbRepository.serialDevices`, node matching by MAC suffix. `@Single(binds = [GetDiscoveredDevicesUseCase::class])`.
- [x] DC-T011 [P] Create `jvmMain/.../JvmGetDiscoveredDevicesUseCase.kt`, `JvmUsbScanner.kt`, `JvmUsbDeviceData.kt` — JVM stubs wrapping `CommonGetDiscoveredDevicesUseCase`.

**Dependencies**: Phase 1 must complete first.
**Checkpoint**: All data models and use cases ready — ViewModel and UI can begin.

---

## Phase 3: User Story 1 — BLE Discovery & Connection (Priority: P1) 🎯 MVP

**Goal**: Users can scan for BLE devices, see bonded + unbonded list sorted by name/discovery-order, and connect with a single tap.

**Independent Test**: Start BLE scan → verify devices appear with RSSI → tap bonded device → connection initiates.

### Implementation

- [x] DC-T012 [US1] Create `ScannerViewModel.kt` — platform-neutral ViewModel: BLE scan (`startBleScan`/`stopBleScan`/`toggleBleScan`), `scannedBleDevices` map, `discoveryOrder` list, `bleDevicesForUi` StateFlow combining bonded + scanned with stable sort, `onSelected()` routing, `changeDeviceAddress()`, `disconnect()`, connection progress text, mock transport toggle.
- [x] DC-T013 [US1] Create `androidMain/.../AndroidScannerViewModel.kt` — Android override: `requestBonding()` via `bluetoothRepository.bond()` with `SecurityException` + generic exception handling + "bond state 11" special case. `requestPermission()` via `usbRepository.requestPermission()`. `@KoinViewModel(binds = [ScannerViewModel::class])`.
- [x] DC-T014 [P] [US1] Create `jvmMain/.../JvmScannerViewModel.kt` — JVM override: direct GATT connect without explicit bonding.
- [x] DC-T015 [US1] Create `ui/components/DeviceListItem.kt` — device row composable: transport-appropriate icon (`Bluetooth`/`BluetoothConnected`/`BluetoothSearching`/`Usb`/`Wifi`/`Add`), `NodeChip` headline when node matched, throttled RSSI display (2s interval), `RadioButton` trailing content, `selectable`/`combinedClickable` with `Role.RadioButton` + `onClickLabel`.
- [x] DC-T016 [US1] Create `ui/components/DeviceList.kt` — unified `LazyColumn` with `bluetoothSection()`: `DeviceSectionHeader` with scan toggle, `DeviceCard` items with `animateItem()`, inline empty state (`SectionEmptyState`).

**Dependencies**: Phase 2 must complete first.
**Checkpoint**: BLE discovery and connection works end-to-end.

---

## Phase 4: User Story 2/3 — TCP/Network Discovery & Manual Add (Priority: P2)

**Goal**: Users can discover TCP devices via NSD/mDNS, see recent TCP addresses, and manually add devices by IP.

**Independent Test**: Enable network scan → verify NSD devices appear → add manual IP → device selected.

### Implementation

- [x] DC-T017 [US2] Extend `DeviceList.kt` `networkSection()` — discovered TCP items, recent TCP sub-section via `recentNetworkSection()`, "Add network device manually" tonal button, scan toggle in header.
- [x] DC-T018 [US3] Implement `AddDeviceDialog` in `DeviceList.kt` — `ModalBottomSheet` with address (`OutlinedTextField`, `KeyboardType.Uri`) + port (`KeyboardType.Decimal`, default `4403`) fields. Validation via `isValidAddress()`. Non-default port appended as `address:port`.
- [x] DC-T019 [US2] Implement NSD gating in `ScannerViewModel.kt` — `_isNetworkScanning` flag, `gatedResolvedList` via `flatMapLatest`, `toggleNetworkScan()` + `persistNetworkAutoScanIntent()`. Android 15+ `ACCESS_LOCAL_NETWORK` handled in `ConnectionsScreen.kt` via `rememberRequestLocalNetworkPermission`.

**Dependencies**: Phase 2 use cases + Phase 3 DeviceList scaffold.
**Checkpoint**: TCP discovery (NSD + recent + manual) works end-to-end.

---

## Phase 5: User Story 4 — USB/Serial Connection (Priority: P3)

**Goal**: Users can see connected USB devices and connect with permission grant.

**Independent Test**: Plug in USB device → verify it appears in USB section → tap to connect.

### Implementation

- [x] DC-T020 [US4] Extend `DeviceList.kt` `usbSection()` — USB device items with section header, inline empty state.
- [x] DC-T021 [P] [US4] Create `androidMain/.../model/AndroidUsbDeviceData.kt` — wrapper for `UsbSerialDriver` implementing `UsbDeviceData` interface.

**Dependencies**: Phase 2 use cases.
**Checkpoint**: USB enumeration and permission-gated connection works.

---

## Phase 6: User Story 5 — Connection Status & Disconnect (Priority: P1)

**Goal**: Users see animated connection state card (NO_DEVICE → CONNECTING → CONNECTED) with node info, battery, RSSI, firmware, and disconnect button.

**Independent Test**: Connect device → verify card transitions → tap disconnect → card resets.

### Implementation

- [x] DC-T022 [US5] Create `ui/ConnectionsScreen.kt` — top-level Composable: `Scaffold` with `MainAppBar`, `AdaptiveTwoPane`, `AnimatedContent` with `fadeIn togetherWith fadeOut` for 3 states (`ConnectionUiState` enum). Auto-start BLE scan via `LaunchedEffect(bleAutoScan)`, auto-start NSD via `DisposableEffect`. Region warning card below status card.
- [x] DC-T023 [US5] Create `ui/components/CurrentlyConnectedInfo.kt` — connected card: `MaterialBatteryInfo`, `Rssi` with polling loop (`withTimeout(1.seconds)`, `delay(2.seconds)`), `NodeChip` with click-to-navigate, firmware version text, `DisconnectButton`.
- [x] DC-T024 [US5] Create `ui/components/ConnectingDeviceInfo.kt` — connecting card: `CircularProgressIndicator`, device name + address, status label from `ConnectionStatus` enum with progress text fallback, `DisconnectButton`.
- [x] DC-T025 [P] [US5] Create `ui/components/DisconnectButton.kt` — full-width `OutlinedButton` with `error` color tint.

**Dependencies**: Phase 3 (ViewModel wired).
**Checkpoint**: Connection lifecycle UI works end-to-end.

---

## Phase 7: User Story 6 — Transport Filter Chips (Priority: P3)

**Goal**: Users can toggle BLE/Network/USB section visibility via filter chips; preferences persist.

**Independent Test**: Toggle each chip → verify section hides/shows → restart → verify state restored.

### Implementation

- [x] DC-T026 [US6] Create `ui/components/TransportFilterChips.kt` — `FilterChip` row for BLE, Network, USB with `MeshtasticIcons` leading icons. Wired to `UiPrefs.showBleTransport` / `showNetworkTransport` / `showUsbTransport`.
- [x] DC-T027 [US6] Wire filter chips in `ConnectionsScreen.kt` — read state from `ScannerViewModel`, pass toggles to `TransportFilterChips`, gate `DeviceList` sections on `showBleSection` / `showNetworkSection` / `showUsbSection`.

**Dependencies**: Phase 3 (DeviceList + ViewModel).
**Checkpoint**: Transport filtering with persistence works.

---

## Phase 8: Tests, Shared Components & Verification

**Purpose**: Unit tests, shared UI components, lint, and final verification.

### Shared Components

- [x] DC-T028 [P] Create `ui/components/ConnectionActionButton.kt` — shared icon+label button with 4 styles (Filled, Tonal, Outlined, Text) + `ConnectionActionButtonStyle.kt` enum. Used by scan toggles, add-device button.
- [x] DC-T029 [P] Create `ui/components/DeviceSectionHeader.kt` — section header with `titleSmall` label, optional `LinearProgressIndicator`, trailing composable slot.
- [x] DC-T030 [P] Create `ui/components/EmptyStateContent.kt` — full-page empty state composable (icon + text + optional action).

### Tests (Implemented)

- [x] DC-T031 Write `ScannerViewModelTest.kt` — 11 tests covering: initialization, connection progress updates, BLE scan start/stop, device address change, USB device updates, network scan toggle, NSD gating (empty when not scanning, populates when active), BLE sort order (bonded-first then discovery-order, RSSI no-reorder), stop-scan preserves discovered list.
- [x] DC-T032 Write `CommonGetDiscoveredDevicesUseCaseTest.kt` — 10 tests covering: empty state, recent address sort, mock toggle, node matching by suffix, no-match without database, reactive node updates, discovered TCP from NSD, discovered TCP node matching, empty resolved list, mock in empty state.
- [x] DC-T033 Write `TcpDiscoveryHelpersTest.kt` — 10 tests covering: `processTcpServices` (shortname+id, default name, recent name priority, no-duplicate-id, sort order), `matchDiscoveredTcpNodes` (node match, no-database), `buildRecentTcpEntries` (filter discovered, suffix match, sort), `findNodeByNameSuffix` (no-database, match, short-suffix rejection).

### Verification

- [x] DC-T034 Run `./gradlew :feature:connections:allTests` — 26 tests pass.
- [x] DC-T035 Run `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests` — green.

### Gap Tasks (Not Yet Implemented) ⚠️

- [ ] DC-T036 `[GAP]` [US1] Write `AndroidScannerViewModelTest` in `feature/connections/src/androidTest/` — test `requestBonding()` success/failure paths, `requestPermission()` USB flow, `SecurityException` handling, "bond state 11" special case. *Rationale: Android-specific bonding and permission logic has no test coverage.*
- [ ] DC-T037 `[GAP]` [US1/US5] Write Compose UI tests for `ConnectionsScreen` in `feature/connections/src/commonTest/` — test `AnimatedContent` state transitions (NO_DEVICE → CONNECTING → CONNECTED), transport chip toggles, device card selection. *Rationale: All existing tests are ViewModel/use-case level; no UI-layer test coverage.*
- [x] DC-T038 `[GAP]` Add KDoc to `ConnectionActionButtonStyle.kt` — document each enum value (`Filled`, `Tonal`, `Outlined`, `Text`) with usage context. *Rationale: Only enum in the module without documentation.*

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Models & Domain (Phase 2)**: Depends on Phase 1
- **US1 — BLE (Phase 3)**: Depends on Phase 2 — **critical path**
- **US2/US3 — TCP (Phase 4)**: Depends on Phase 2 + Phase 3 scaffold
- **US4 — USB (Phase 5)**: Depends on Phase 2
- **US5 — Status (Phase 6)**: Depends on Phase 3
- **US6 — Filters (Phase 7)**: Depends on Phase 3
- **Tests (Phase 8)**: Depends on all prior phases

### Critical Path

```
Phase 1 → Phase 2 → Phase 3 (BLE/ViewModel) → Phase 6 (Status) → Phase 8 (Tests)
```

### Parallel Opportunities

```
Phase 4 (TCP) ∥ Phase 5 (USB) ∥ Phase 7 (Filters) — all depend on Phase 2/3 but are independent of each other
DC-T028/T029/T030 (shared components) — independent, parallelizable
```

---

## Implementation Strategy

### Status: Complete (Migrated)

All 35 implementation tasks are complete. 3 gap tasks identified for future work:

1. **DC-T036**: Android-specific ViewModel tests (bonding/permissions)
2. **DC-T037**: Compose UI tests (screen state transitions)
3. **DC-T038**: KDoc for `ConnectionActionButtonStyle`

### Recommended Follow-Up

- Use `/speckit.specify` to create a follow-up spec for gap tasks
- Use `/speckit.bugfix.report` if bonding edge cases surface in production

