# Feature Specification: WiFi Provisioning (ESP32 SoftAP)

**Feature Branch**: `011-wifi-provisioning`  
**Created**: 2026-06-15  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `feature/wifi-provision` module

## Summary

WiFi Provisioning enables users to configure an ESP32-based Meshtastic device's WiFi connection over BLE using the nymea-networkmanager GATT profile. The app scans for a nearby nymea device, connects via BLE, retrieves visible WiFi networks, and sends SSID/password credentials to the device. On success, the device's assigned IP address is displayed along with SSH connection details for mPWRD-OS setup. All business logic and UI reside in `commonMain` following KMP conventions.

## Goals

1. **One-tap WiFi setup** — allow users to provision an ESP32 device's WiFi credentials via BLE without needing a serial console or web interface.
2. **Network discovery** — scan and display available WiFi networks from the device's perspective, deduplicated by SSID and sorted by signal strength.
3. **Secure credential transfer** — send SSID + password over BLE using the nymea JSON-over-BLE chunked protocol with `WITH_RESPONSE` writes.
4. **Post-provision guidance** — on successful provisioning, display the device's IP address, default SSH credentials, and a one-tap "Open SSH" action.
5. **Robust error handling** — surface typed errors (ConnectFailed, ScanFailed, ProvisionFailed) with human-readable messages mapped from nymea response codes.

## Non-Goals

- Provisioning via WiFi Direct, USB, or serial — BLE only.
- Managing saved WiFi connections on the device (forget, edit priority).
- WPA3/Enterprise authentication — only PSK and open networks.
- Hidden network provisioning via the UI (domain layer supports `CMD_CONNECT_HIDDEN` but UI does not expose it).
- iOS or Desktop platform-specific BLE implementations — the feature uses `core:ble` abstractions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Scan & Connect to BLE Device (Priority: P1)

A user opens the WiFi Provisioning screen. The app automatically scans for a nearby device advertising the nymea wireless GATT service. Once found, the device name is displayed with a confirmation prompt before proceeding.

**Why this priority**: BLE connection is the prerequisite for all other functionality. Without it, no provisioning can occur.

**Independent Test**: Can be fully tested by launching the screen and verifying the BLE scan → DeviceFound → confirmation transition. Delivers confidence that device discovery works.

**Acceptance Scenarios**:

1. **Given** Bluetooth is enabled and a nymea device is advertising, **When** the user opens the WiFi Provisioning screen, **Then** the app scans for up to 10 seconds and transitions to a "Device Found" confirmation showing the device name.
2. **Given** a device is found with no advertised name, **When** the device is discovered, **Then** the MAC address is displayed in place of the name.
3. **Given** Bluetooth is disabled or no device is advertising, **When** the scan timeout (10s) elapses, **Then** a `ConnectFailed` error is displayed and the screen returns to Idle state.
4. **Given** a BLE connection attempt throws an exception, **When** the error is caught, **Then** the error detail message is shown via Snackbar.

---

### User Story 2 — Discover Available WiFi Networks (Priority: P1)

After confirming the discovered device, the user taps "Scan Networks". The app sends a WiFi scan command to the device and displays the list of visible networks with signal strength and lock indicators.

**Why this priority**: Network discovery is the core UX — users need to see and select their WiFi network.

**Independent Test**: Connect to a BLE device, trigger network scan, and verify the network list populates with SSID, signal strength, and protection status.

**Acceptance Scenarios**:

1. **Given** a BLE connection is active, **When** the user taps "Scan Networks", **Then** the app sends CMD_SCAN (4) followed by CMD_GET_NETWORKS (0) and displays the results.
2. **Given** multiple access points with the same SSID, **When** the scan results are received, **Then** duplicates are merged keeping the entry with the strongest signal.
3. **Given** the scan returns an empty list, **When** results are displayed, **Then** a "No networks found" placeholder is shown.
4. **Given** the scan command returns a non-zero error code, **When** the error is received, **Then** a `ScanFailed` error is displayed via Snackbar.

---

### User Story 3 — Provision WiFi Credentials (Priority: P1)

The user selects a network (or types an SSID manually), enters a password, and taps "Apply". The app sends the credentials to the device and reports success or failure with an inline status card.

**Why this priority**: This is the primary action the feature exists to perform.

**Independent Test**: Connect, scan networks, select one, enter password, tap Apply, and verify the ProvisionStatusCard transitions from sending → success/failed.

**Acceptance Scenarios**:

1. **Given** a valid SSID and password are entered, **When** the user taps "Apply", **Then** CMD_CONNECT (1) is sent with the SSID and password and the status card shows "Sending credentials…".
2. **Given** the device responds with success (response code 0) and an IP address, **When** the response is received, **Then** the provision status is set to Success and the IP address is displayed.
3. **Given** the device responds with success but no IP in the payload, **When** the response is received, **Then** a fallback CMD_GET_CONNECTION (5) is sent to retrieve the IP address.
4. **Given** the device responds with a non-zero error code, **When** the response is received, **Then** the provision status is Failed with a mapped error message (e.g., "NetworkManager not available").
5. **Given** the SSID field is blank, **When** the user taps "Apply", **Then** the action is a no-op (button is disabled and provisionWifi guards against blank SSID).

---

### User Story 4 — Post-Provision Success Screen (Priority: P2)

After successful provisioning, the user sees a success screen with the device's IP address, default SSH credentials (username/password), SSH command, and a "Open SSH" button that launches an SSH URI.

**Why this priority**: Provides immediate next-step guidance, especially important for mPWRD-OS devices.

**Independent Test**: Simulate a successful provision and verify the success content displays IP, SSH command, copy buttons, and the Open SSH action.

**Acceptance Scenarios**:

1. **Given** provisioning succeeded with an IP address, **When** the success screen renders, **Then** the IP address, default username, default password, and SSH command are displayed with copy buttons.
2. **Given** provisioning succeeded but the IP address is unavailable, **When** the success screen renders, **Then** a fallback placeholder is shown and the "Open SSH" button is disabled.
3. **Given** the user taps "Done", **When** the action fires, **Then** the BLE connection is closed and the screen navigates back.

---

### User Story 5 — Disconnect & Cleanup (Priority: P3)

The user can cancel at any point. The app disconnects the BLE connection, resets the reassembler buffer, and cancels the service scope. ViewModel cleanup on `onCleared` also cancels the service.

**Why this priority**: Resource cleanup is essential but secondary to the core provisioning flow.

**Independent Test**: Connect to a device, then disconnect and verify the UI state resets to Idle with no leaked resources.

**Acceptance Scenarios**:

1. **Given** an active BLE connection, **When** the user taps "Cancel", **Then** `disconnect()` is called, the BLE connection is closed, and the UI state resets to initial.
2. **Given** the ViewModel is cleared (navigation away), **When** `onCleared` fires, **Then** `service.cancel()` is called to synchronously tear down the scope.

---

### Edge Cases

- What happens when the BLE connection drops mid-scan? The nymea response channel times out (15s) and a `ScanFailed` error is surfaced.
- What happens when the device reports an unknown error code (>7)? The error is mapped to "Unknown error (code N)".
- What happens when `scanNetworks()` is called without an active service? The ViewModel falls back to `connectToDevice()`.
- What happens with very long SSIDs? The UI handles text overflow via Material 3 `ListItem` which truncates with ellipsis.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| `WifiProvisionViewModel` | `feature/wifi-provision/…/WifiProvisionViewModel.kt` | State machine driving the UI through Idle → ConnectingBle → DeviceFound → LoadingNetworks → Connected → Provisioning phases |
| `NymeaWifiService` | `feature/wifi-provision/…/domain/NymeaWifiService.kt` | GATT client for the nymea-networkmanager profile: connect, scanNetworks, provision, close |
| `NymeaPacketCodec` | `feature/wifi-provision/…/domain/NymeaPacketCodec.kt` | Encode JSON → ≤20-byte BLE packets; Reassembler for inbound notification reassembly |
| `NymeaProtocol` | `feature/wifi-provision/…/domain/NymeaProtocol.kt` | kotlinx.serialization models for nymea JSON commands and responses |
| `NymeaBleConstants` | `feature/wifi-provision/…/NymeaBleConstants.kt` | GATT UUIDs, command codes, response codes, and timeout constants |
| `WifiNetwork` / `ProvisionResult` | `feature/wifi-provision/…/model/WifiNetwork.kt` | Domain models for scan results and provisioning outcomes |
| `WifiProvisionScreen` | `feature/wifi-provision/…/ui/WifiProvisionScreen.kt` | Main Compose screen with Crossfade phase transitions |
| `ProvisionStatusCard` | `feature/wifi-provision/…/ui/ProvisionStatusCard.kt` | Inline status card (sending/success/failed) using Material 3 color semantics |
| `WifiProvisionNavigation` | `feature/wifi-provision/…/navigation/WifiProvisionNavigation.kt` | Navigation 3 entry registration for graph and direct routes |
| `FeatureWifiProvisionModule` | `feature/wifi-provision/…/di/FeatureWifiProvisionModule.kt` | Koin DI module with component scan |
| `BleScanner` / `BleConnectionFactory` | `core/ble/` | Platform-abstracted BLE scanning and connection (reused from core) |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST scan for BLE devices advertising the nymea wireless service UUID (`e081fec0-…-f7fc`) with a 10-second timeout.
- **FR-002**: System MUST connect to the discovered device via BLE and subscribe to the Commander Response characteristic for notifications.
- **FR-003**: System MUST pause at a "Device Found" confirmation phase showing the device name (or MAC address) before proceeding.
- **FR-004**: System MUST send JSON commands chunked into ≤20-byte BLE packets with a newline (`\n`) terminator using `WITH_RESPONSE` write type.
- **FR-005**: System MUST reassemble inbound BLE notification packets into complete JSON responses using newline-terminated framing.
- **FR-006**: System MUST trigger a WiFi scan on the device (CMD_SCAN=4) and then fetch results (CMD_GET_NETWORKS=0).
- **FR-007**: System MUST deduplicate WiFi networks by SSID, keeping the strongest signal per SSID, and sort descending by signal strength.
- **FR-008**: System MUST send WiFi credentials via CMD_CONNECT (1) for visible networks or CMD_CONNECT_HIDDEN (2) for hidden networks.
- **FR-009**: System MUST map nymea response error codes (0–7) to human-readable error messages.
- **FR-010**: System MUST attempt a fallback CMD_GET_CONNECTION (5) to retrieve the IP address when the connect response payload lacks one.
- **FR-011**: System MUST display typed errors (`ConnectFailed`, `ScanFailed`, `ProvisionFailed`) via Snackbar with localized messages from string resources.
- **FR-012**: System MUST provide a post-provision success screen showing IP address, default SSH credentials, SSH command, copy buttons, and an "Open SSH" action.
- **FR-013**: System MUST disconnect and cancel the BLE service scope on user-initiated cancel or ViewModel cleanup.
- **FR-014**: System MUST support an optional `address` parameter for targeted BLE device connections (deep-link support).

### Non-Functional Requirements

- **NFR-001**: BLE scan timeout MUST NOT exceed 10 seconds; response timeout MUST NOT exceed 15 seconds.
- **NFR-002**: All UI composables and business logic MUST reside in `commonMain` source set (KMP Constitution §I, §III).
- **NFR-003**: String resources MUST use `stringResource(Res.string.*)` — no hardcoded user-facing text.
- **NFR-004**: Error handling MUST use `safeCatching {}` instead of `runCatching {}` (Constitution §VII).
- **NFR-005**: Password field MUST support visibility toggle and use `PasswordVisualTransformation`.
- **NFR-006**: Haptic feedback MUST fire on successful provisioning via `HapticFeedbackType.LongPress`.

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 11 new files (all feature code) | All business logic, domain, and UI per Constitution §I, §III |
| `commonTest` | 5 new test files | KMP `allTests` for domain + ViewModel |
| `androidMain` | None | No platform-specific code |
| `jvmMain` | None | No JVM-specific code |

## Design Standards Compliance

- [x] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [x] M3 component selection verified (ListItem, Card, OutlinedTextField, FilledTonalButton, LoadingIndicator, Snackbar)
- [x] Accessibility: TalkBack semantics on network rows (`clickable` with `onClickLabel`), password visibility toggle, icon `contentDescription`
- [x] Typography: `headlineSmallEmphasized`, `bodyLargeEmphasized`, `titleLargeEmphasized` for emphasis, M3 scale for hierarchy

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed — WiFi passwords are sent over BLE only, never logged
- [x] No new network calls that transmit user data — all communication is local BLE
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: User can provision a nymea-managed ESP32 device with WiFi credentials via BLE in under 30 seconds (connect + scan + provision).
- **SC-002**: Duplicate SSIDs from multi-AP environments are deduplicated — network list shows at most one entry per SSID.
- **SC-003**: All 7 nymea error codes (1–7) are mapped to distinct, human-readable error messages.
- **SC-004**: BLE packet codec round-trips correctly: encode → reassemble produces the original JSON for any message size.
- **SC-005**: ViewModel state machine transitions are verified by 15+ test cases covering all 6 phases and error paths.
- **SC-006**: Domain layer (NymeaWifiService, NymeaPacketCodec, NymeaProtocol) has 30+ unit tests with full coverage of command/response flows.
- **SC-007**: Post-provision success screen displays IP address and SSH details within 2 seconds of provisioning completion.
- **SC-008**: BLE resources are cleaned up on disconnect and ViewModel `onCleared` — no leaked coroutine scopes.

## Assumptions

- All business logic and UI composables reside in `commonMain` source set.
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`.
- Icons use `MeshtasticIcons` (from `core/ui/icon/`).
- The nymea-networkmanager BLE profile is available on target ESP32 devices running mPWRD-OS firmware.
- BLE MTU is assumed to be the minimum (20 bytes) — no MTU negotiation is performed.
- Default SSH credentials (username/password) for mPWRD-OS are provided via string resources.
- The `core:ble` module provides working `BleScanner` and `BleConnectionFactory` abstractions with fake implementations for testing.

