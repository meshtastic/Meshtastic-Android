# Tasks: WiFi Provisioning (ESP32 SoftAP)

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)  
**Status**: Migrated — all tasks reflect implemented code  
**Prefix**: WFP-T

---

## Phase 1 — Domain Layer

### WFP-T001: Define BLE constants and protocol parameters
- [x] Create `NymeaBleConstants.kt` with GATT UUIDs (Wireless Service, Commander, Response, Connection Status, Network Service)
- [x] Define command codes: `CMD_GET_NETWORKS` (0), `CMD_CONNECT` (1), `CMD_CONNECT_HIDDEN` (2), `CMD_SCAN` (4), `CMD_GET_CONNECTION` (5)
- [x] Define response error codes (0–7) for success, invalid command, invalid parameter, etc.
- [x] Define timing constants: `SCAN_TIMEOUT` (10s), `RESPONSE_TIMEOUT` (15s), `CONNECTION_INFO_TIMEOUT` (2s), `SUBSCRIPTION_SETTLE` (300ms)
- **File**: `feature/wifi-provision/src/commonMain/…/NymeaBleConstants.kt`

### WFP-T002: Implement BLE packet codec (encode + reassemble)
- [x] Implement `NymeaPacketCodec.encode()` — split JSON + `\n` terminator into ≤20-byte packets
- [x] Implement `NymeaPacketCodec.Reassembler` — stateful reassembler that buffers partial notifications and emits complete JSON on `\n`
- [x] Implement `Reassembler.reset()` for cleanup
- **File**: `feature/wifi-provision/src/commonMain/…/domain/NymeaPacketCodec.kt`

### WFP-T003: Define nymea JSON protocol models
- [x] Create `NymeaSimpleCommand` (`@Serializable`, `@SerialName("c")`) for parameter-less commands
- [x] Create `NymeaConnectParams` with `ssid` (`"e"`) and `password` (`"p"`) fields
- [x] Create `NymeaConnectCommand` with nested `NymeaConnectParams`
- [x] Create `NymeaResponse` with `command`, `responseCode`, optional `connectionInfo`
- [x] Create `NymeaNetworkEntry` with `ssid`, `bssid`, `signalStrength`, `protection`
- [x] Create `NymeaNetworksResponse` with network list payload
- [x] Create `NymeaConnectionInfo` with `ssid`, `bssid`, `signalStrength`, `protection`, `ipAddress`
- [x] Configure shared `NymeaJson` codec with `ignoreUnknownKeys`, `isLenient`
- **File**: `feature/wifi-provision/src/commonMain/…/domain/NymeaProtocol.kt`

### WFP-T004: Define domain models
- [x] Create `WifiNetwork` data class with `ssid`, `bssid`, `signalStrength`, `isProtected`
- [x] Create `ProvisionResult` sealed interface with `Success` (optional `ipAddress`) and `Failure` (errorCode, message)
- **File**: `feature/wifi-provision/src/commonMain/…/model/WifiNetwork.kt`

### WFP-T005: Implement NymeaWifiService GATT client
- [x] Implement `connect()` — scan for nymea device, BLE connect, discover wireless service, subscribe to response characteristic
- [x] Implement `scanNetworks()` — send CMD_SCAN, wait for ack, send CMD_GET_NETWORKS, parse response into `List<WifiNetwork>`
- [x] Implement `provision()` — send CMD_CONNECT or CMD_CONNECT_HIDDEN with SSID/password, parse response into `ProvisionResult`
- [x] Implement `fetchConnectionIpAddress()` — fallback CMD_GET_CONNECTION with short timeout
- [x] Implement `sendCommand()` — encode JSON, write packets with `WITH_RESPONSE`
- [x] Implement `waitForResponse()` — await on response channel with timeout
- [x] Implement `nymeaErrorMessage()` — map error codes 1–7 to human-readable strings
- [x] Implement `close()` (suspend) and `cancel()` (synchronous) for resource cleanup
- **File**: `feature/wifi-provision/src/commonMain/…/domain/NymeaWifiService.kt`

### WFP-T006: Define typed error categories
- [x] Create `WifiProvisionError` sealed interface with `detail: String`
- [x] Implement `ConnectFailed`, `ScanFailed`, `ProvisionFailed` subtypes
- **File**: `feature/wifi-provision/src/commonMain/…/WifiProvisionViewModel.kt` (top-level declarations)

---

## Phase 2 — ViewModel

### WFP-T007: Define UI state model
- [x] Create `WifiProvisionUiState` data class with `phase`, `networks`, `error`, `deviceName`, `ipAddress`, `provisionStatus`
- [x] Define `Phase` enum: `Idle`, `ConnectingBle`, `DeviceFound`, `LoadingNetworks`, `Connected`, `Provisioning`
- [x] Define `ProvisionStatus` enum: `Idle`, `Success`, `Failed`
- **File**: `feature/wifi-provision/src/commonMain/…/WifiProvisionViewModel.kt`

### WFP-T008: Implement WifiProvisionViewModel
- [x] Create `@KoinViewModel` class with `BleScanner`, `BleConnectionFactory`, `CoroutineDispatchers` injection
- [x] Expose `uiState: StateFlow<WifiProvisionUiState>` via `MutableStateFlow`
- [x] Implement lazy `NymeaWifiService` creation per session
- **File**: `feature/wifi-provision/src/commonMain/…/WifiProvisionViewModel.kt`

### WFP-T009: Implement ViewModel actions
- [x] Implement `connectToDevice(address?)` — scan → connect → DeviceFound, with ConnectFailed error path
- [x] Implement `scanNetworks()` — auto-reconnect if no service, delegate to `loadNetworks()`
- [x] Implement `provisionWifi(ssid, password)` — guard blank SSID, send credentials, map Success/Failure result
- [x] Implement `disconnect()` — close service, reset state
- [x] Implement `onCleared()` — synchronous `service.cancel()`
- **File**: `feature/wifi-provision/src/commonMain/…/WifiProvisionViewModel.kt`

### WFP-T010: Implement SSID deduplication
- [x] Implement `deduplicateBySsid()` — group by SSID, keep strongest signal, sort descending
- [x] Mark as `internal` companion function for testability
- **File**: `feature/wifi-provision/src/commonMain/…/WifiProvisionViewModel.kt`

---

## Phase 3 — UI Layer

### WFP-T011: Implement main WiFi Provisioning screen
- [x] Create `WifiProvisionScreen` composable with `Scaffold`, `CenterAlignedTopAppBar`, `SnackbarHost`
- [x] Implement `Crossfade` transitions between `ScreenKey` states (ConnectingBle, DeviceFound, LoadingNetworks, Connected)
- [x] Add `LinearProgressIndicator` for loading phases
- [x] Wire `LaunchedEffect` for auto-connect on screen entry and error-to-Snackbar display
- **File**: `feature/wifi-provision/src/commonMain/…/ui/WifiProvisionScreen.kt`

### WFP-T012: Implement phase sub-composables
- [x] `ScanningBleContent` — centered `LoadingIndicator` with scanning message
- [x] `DeviceFoundContent` — Bluetooth icon, device name, "Scan Networks" / "Cancel" buttons
- [x] `ScanningNetworksContent` — centered `LoadingIndicator` with WiFi scanning message
- [x] `ConnectedContent` — scan button, network list (LazyColumn in Card), SSID/password fields, Apply/Cancel buttons
- [x] `ProvisionSuccessContent` — check icon, IP address, SSH credentials card, Open SSH button, Done button
- [x] `NetworkRow` — ListItem with WiFi icon, signal strength, lock indicator, selection highlight
- **File**: `feature/wifi-provision/src/commonMain/…/ui/WifiProvisionScreen.kt`

### WFP-T013: Implement ProvisionStatusCard
- [x] Create `ProvisionStatusCard` composable with M3 color semantics (secondary=sending, primary=success, error=failed)
- [x] Implement `StatusIcon` with `LoadingIndicator` / `Success` / `Error` icons
- [x] Implement `statusText` with localized strings
- **File**: `feature/wifi-provision/src/commonMain/…/ui/ProvisionStatusCard.kt`

### WFP-T014: Implement mPWRD disclaimer banner
- [x] Create `MpwrdDisclaimerBanner` with mPWRD logo image and `AutoLinkText` disclaimer
- **File**: `feature/wifi-provision/src/commonMain/…/ui/WifiProvisionScreen.kt`

### WFP-T015: Add Compose previews
- [x] Create preview composables for all phases: scanning BLE, device found (with/without name), scanning networks, connected (with networks, empty, scanning, provisioning, success, failed), edge cases (long SSID, many networks), standalone components
- **File**: `feature/wifi-provision/src/commonMain/…/ui/WifiProvisionPreviews.kt`

### WFP-T016: Wire navigation and DI
- [x] Create `wifiProvisionGraph()` extension function registering `WifiProvisionGraph` and `WifiProvision` nav entries
- [x] Create `FeatureWifiProvisionModule` with `@Module` and `@ComponentScan`
- **Files**: `feature/wifi-provision/src/commonMain/…/navigation/WifiProvisionNavigation.kt`, `…/di/FeatureWifiProvisionModule.kt`

---

## Phase 4 — Testing

### WFP-T017: Test NymeaPacketCodec
- [x] Test encode appends newline terminator
- [x] Test short message fits in single packet
- [x] Test long message splits across multiple packets
- [x] Test boundary conditions (exactly fills packet, one byte over)
- [x] Test empty string encoding
- [x] Test custom maxPacketSize
- [x] Test Reassembler single feed, buffered partial, multi-chunk completion
- [x] Test Reassembler sequential messages and reset
- [x] Test encode → reassemble round-trip at default and small packet sizes
- **File**: `feature/wifi-provision/src/commonTest/…/domain/NymeaPacketCodecTest.kt` — 12 test cases

### WFP-T018: Test NymeaProtocol serialization
- [x] Test NymeaSimpleCommand compact JSON serialization and round-trip
- [x] Test NymeaConnectCommand with nested params, empty password, round-trip
- [x] Test NymeaResponse deserialization for success, error codes, connection info payload, unknown keys
- [x] Test NymeaNetworksResponse with network list, empty list, default field values
- **File**: `feature/wifi-provision/src/commonTest/…/domain/NymeaProtocolTest.kt` — 11 test cases

### WFP-T019: Test NymeaWifiService
- [x] Test connect succeeds and returns device name / address
- [x] Test connect fails on BLE connection failure and exception
- [x] Test scanNetworks returns parsed network list and empty list
- [x] Test scanNetworks fails on error response code
- [x] Test scanNetworks sends correct BLE commands with WITH_RESPONSE write type
- [x] Test provision returns Success on code 0 with IP, and Failure on non-zero codes
- [x] Test provision falls back to GetConnection for IP when payload is empty
- [x] Test provision sends CMD_CONNECT vs CMD_CONNECT_HIDDEN
- [x] Test provision maps all 7 known error codes
- [x] Test close disconnects BLE
- **File**: `feature/wifi-provision/src/commonTest/…/domain/NymeaWifiServiceTest.kt` — 14 test cases

### WFP-T020: Test SSID deduplication
- [x] Test empty list returns empty
- [x] Test single network unchanged
- [x] Test duplicate SSIDs keep strongest signal
- [x] Test mixed duplicates and unique networks
- [x] Test result sorted by signal strength descending
- [x] Test preserves isProtected from strongest entry
- **File**: `feature/wifi-provision/src/commonTest/…/DeduplicateBySsidTest.kt` — 6 test cases

### WFP-T021: Test WifiProvisionViewModel
- [x] Test initial state is Idle with empty data
- [x] Test connectToDevice transitions: ConnectingBle → DeviceFound on success
- [x] Test connectToDevice uses address when name is null
- [x] Test connectToDevice sets ConnectFailed error on failure and exception
- [x] Test scanNetworks transitions: LoadingNetworks → Connected with deduplicated results
- [x] Test scanNetworks reconnects if no service exists
- [x] Test provisionWifi transitions: Provisioning → Connected with Success/Failed status
- [x] Test provisionWifi ignores blank SSID and no-ops when service is null
- [x] Test disconnect resets state and calls BLE disconnect
- **File**: `feature/wifi-provision/src/commonTest/…/WifiProvisionViewModelTest.kt` — 13 test cases

### WFP-T022: Add string resources
- [x] Add all `wifi_provision_*` string resources to `core/resources/src/commonMain/composeResources/values/strings.xml`
- [x] Run `sort-strings.py` to maintain alphabetical order

---

## Identified Gaps (not yet implemented)

### WFP-T023: Expose hidden network provisioning in UI
- [x] Add a "Hidden Network" toggle or option in `ConnectedContent` that sets `hidden = true` when calling `provisionWifi`
- [ ] **[DEFERRED]** Domain layer already supports `CMD_CONNECT_HIDDEN` (2) — only UI wiring needed — *Deferred: already implemented in WFP-T023; this sub-task is redundant.*
- **Priority**: Low — niche use case

### WFP-T024: Add retry mechanism for BLE scan timeout
- [x] When BLE scan times out (10s), offer a "Retry" button instead of requiring the user to navigate back and re-enter
- [ ] **[DEFERRED]** Consider exponential backoff or a manual retry count limit — *Deferred: enhancement — current retry UX is sufficient for v1.*
- **Priority**: Medium — improves UX for unreliable BLE environments

### WFP-T025: Add Compose UI tests
- [ ] **[DEFERRED]** Add `@Test` composable tests for `WifiProvisionScreen` phase transitions (ConnectingBle → DeviceFound → Connected) — *Deferred: requires Compose UI test infrastructure.*
- [ ] **[DEFERRED]** Add interaction tests for network selection, SSID/password input, Apply button enable/disable — *Deferred: requires Compose UI test infrastructure.*
- [ ] **[DEFERRED]** Add snapshot or screenshot tests for `ProvisionStatusCard` states — *Deferred: requires Compose UI test infrastructure.*
- **Priority**: Medium — domain and ViewModel well-tested, but UI layer lacks automated verification

---

## Summary

| Category | Total | Completed | Gaps |
|----------|-------|-----------|------|
| Domain Layer | 6 | 6 ✅ | 0 |
| ViewModel | 4 | 4 ✅ | 0 |
| UI Layer | 6 | 6 ✅ | 0 |
| Testing | 6 | 6 ✅ | 0 |
| Gaps | 3 | 0 | 3 ⚠️ |
| **Total** | **25** | **22** | **3** |

