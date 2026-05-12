# Feature Specification: Firmware Update (OTA / DFU)

**Feature Branch**: `006-firmware-update`  
**Created**: 2026-07-15  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `feature/firmware` module

## Summary

Firmware Update provides end-to-end over-the-air (OTA) and USB firmware flashing for all Meshtastic device families. The feature auto-detects the connected device's hardware model and active transport (BLE, WiFi/TCP, USB/Serial), downloads the correct firmware binary from the Meshtastic release infrastructure, and executes the appropriate update protocol: ESP32 Unified OTA (BLE or WiFi), Nordic Secure DFU (nRF52), Nordic Legacy DFU / Adafruit BLEDfu (nRF52), or UF2 USB Mass Storage (nRF52/RP2040). All business logic, protocol implementations, and Compose Multiplatform UI reside in `commonMain`; only file I/O and USB manager adapters live in platform source sets.

## Goals

1. Allow users to flash stable, alpha, or local firmware files to any connected Meshtastic device with a single tap.
2. Support all three transport mechanisms — BLE DFU, WiFi/TCP OTA, and USB UF2 — with automatic routing based on connection type and device architecture.
3. Display real-time download and upload progress with throughput metrics (KiB/s, ETA).
4. Verify the device reconnects after flashing and report success or verification failure.
5. Provide safety guardrails: battery level checks, bootloader upgrade warnings, disclaimer dialogs, and screen-on locks during transfer.

## Non-Goals

- Bootloader upgrade itself — the feature warns about outdated bootloaders and links to documentation, but does not perform the upgrade.
- ESP32 firmware update over USB/Serial — explicitly unsupported; shown as `Unknown` update method.
- Firmware building or custom build pipelines — the feature only consumes published release artifacts.
- Multi-device batch updates — only the currently connected device can be updated.
- iOS platform support — `FirmwareFileHandler` and `FirmwareUsbManager` have no `iosMain` implementations yet.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Flash Stable Firmware via BLE (Priority: P1)

A user with a BLE-connected nRF52 device navigates to the Firmware Update screen, sees the currently installed version and the latest stable release, taps "Update via BLE", acknowledges the disclaimer, and watches the firmware download and flash to the device. After the device reboots, the app verifies reconnection and shows a success screen.

**Why this priority**: BLE + nRF52 is the most common device/transport combination in the field. This is the primary update path for RAK4631, T114, and similar boards.

**Independent Test**: Can be fully tested by connecting to any nRF52 device over BLE. Delivers the core value of keeping devices on the latest firmware.

**Acceptance Scenarios**:

1. **Given** a BLE-connected nRF52 device on firmware 2.4.0, **When** the user opens the Firmware Update screen, **Then** the screen shows the device name, current version (2.4.0), latest stable release, and an "Update via BLE" button.
2. **Given** the user taps "Update via BLE" and confirms the disclaimer, **When** the download completes and DFU begins, **Then** a progress bar shows upload percentage, speed (KiB/s), and ETA.
3. **Given** the DFU transfer completes successfully, **When** the device reboots, **Then** the app enters "Verifying" state, reconnects within 60 seconds, and shows "Success".
4. **Given** the device does not reconnect within the 60-second timeout, **Then** the app shows "Verification Failed" with Retry and Done options.

---

### User Story 2 — Flash ESP32 Firmware via BLE OTA (Priority: P1)

A user with a BLE-connected ESP32 device (e.g., Heltec V3, T-Deck) updates firmware using the ESP32 Unified OTA protocol over BLE. The app downloads the correct `.bin` file (resolved via `.mt.json` manifest or filename heuristics), triggers an OTA reboot, reconnects to the device in OTA mode at MAC+1, streams the firmware with SHA-256 verification, and confirms success.

**Why this priority**: ESP32 is the second major architecture family, and BLE is the primary transport for mobile users.

**Independent Test**: Connect to any ESP32-based device over BLE to test the full OTA flow.

**Acceptance Scenarios**:

1. **Given** a BLE-connected ESP32-S3 device, **When** the user initiates an update, **Then** the app resolves the firmware binary via the `.mt.json` manifest (or falls back through naming heuristics).
2. **Given** the firmware is downloaded, **When** the OTA reboot is triggered, **Then** the app disconnects the mesh service, scans for the device at the original or MAC+1 address, and connects to the OTA service.
3. **Given** a successful OTA connection, **When** the firmware is streamed, **Then** the device validates the SHA-256 hash and responds with "OK".
4. **Given** the device rejects the hash, **Then** the app shows a "Hash Rejected" error with guidance.

---

### User Story 3 — Flash ESP32 Firmware via WiFi/TCP OTA (Priority: P2)

A user with a TCP-connected ESP32 device updates firmware using the ESP32 Unified OTA protocol over a raw TCP socket. The flow is identical to BLE OTA but uses Ktor raw sockets on port 3232 with larger chunk sizes (1024 bytes vs 512 for BLE).

**Why this priority**: WiFi OTA is faster than BLE and preferred by power users with network-connected devices, but is less common than BLE.

**Independent Test**: Connect to any ESP32 device via TCP/WiFi to test the WiFi OTA flow.

**Acceptance Scenarios**:

1. **Given** a TCP-connected ESP32 device at `192.168.1.100`, **When** the user initiates a firmware update, **Then** the app connects to the device on port 3232 via Ktor raw sockets.
2. **Given** a successful TCP connection, **When** the firmware is streamed, **Then** the transfer uses 1024-byte chunks and completes without per-packet ACK overhead.
3. **Given** the device verifies the hash after transfer, **When** "OK" is received, **Then** the app transitions to Success state.

---

### User Story 4 — Flash nRF52/RP2040 Firmware via USB (Priority: P2)

A user with a USB/Serial-connected nRF52 or RP2040 device updates firmware by downloading the `.uf2` file, rebooting the device into DFU bootloader mode, and saving the UF2 to the device's virtual mass storage. The app handles the download, triggers `rebootToDfu`, and prompts the user to save the file.

**Why this priority**: USB is the only update path for devices without BLE (e.g., desktop-connected RP2040 boards).

**Independent Test**: Connect any nRF52/RP2040 device via USB serial to test the UF2 save flow.

**Acceptance Scenarios**:

1. **Given** a serial-connected nRF52 device, **When** the user initiates a firmware update, **Then** the app downloads the `.uf2` file and shows a "Rebooting" state.
2. **Given** the device reboots into DFU mode, **When** the UF2 file is ready, **Then** the app presents an `AwaitingFileSave` dialog with instructions.
3. **Given** the user saves the UF2 file to the device, **When** the device detaches and reboots, **Then** the app verifies reconnection.

---

### User Story 5 — Flash Local Firmware File (Priority: P2)

A user selects "Local File" as the release type, picks a firmware file (`.zip` for BLE DFU, `.bin` for ESP32 OTA, `.uf2` for USB) from device storage, and the app applies it using the appropriate handler. This supports beta testers and developers with custom builds.

**Why this priority**: Essential for development and testing, but not used by mainstream users.

**Independent Test**: Pick a local firmware file and apply it to any connected device.

**Acceptance Scenarios**:

1. **Given** the user selects the "Local File" tab, **When** they tap "Select File", **Then** a file picker opens accepting `*/*`.
2. **Given** a local `.zip` file is selected for a BLE nRF52 device, **When** the file is processed, **Then** the app extracts the DFU package and begins the transfer.
3. **Given** a local `.bin` file is selected for a BLE ESP32 device with a valid Bluetooth address, **When** the file is processed, **Then** the app imports it and starts the OTA update with a synthetic `LOCAL` release.
4. **Given** a BLE ESP32 update from file but the Bluetooth address is invalid, **Then** the app shows a "No device" error.

---

### User Story 6 — Bootloader Warning & Safety Guards (Priority: P3)

Users with devices that require a bootloader upgrade before OTA (flagged via `requiresBootloaderUpgradeForOta`) see a prominent warning card with a "Learn More" link and a "Don't show again" dismissal option. Additionally, firmware updates are blocked if battery level is ≤10%, the screen stays on during transfers, and a back-navigation confirmation dialog prevents accidental cancellation.

**Why this priority**: Safety features that prevent bricked devices and failed updates — important but secondary to the core update flow.

**Independent Test**: Connect a flagged nRF52 device (e.g., RAK4631) over BLE to verify the warning card appears and can be dismissed.

**Acceptance Scenarios**:

1. **Given** a BLE-connected device with `requiresBootloaderUpgradeForOta = true`, **When** the user opens the Firmware Update screen, **Then** a red warning card is displayed with the device name and a "Learn More" link.
2. **Given** the user taps "Don't show again", **Then** the warning is dismissed for that device address and does not reappear.
3. **Given** a device with battery level at 5%, **When** the user taps "Update", **Then** the app shows a "Battery low" error and does not start the update.
4. **Given** a firmware transfer is in progress (Downloading/Processing/Updating/Verifying), **When** the user presses back, **Then** a confirmation dialog appears instead of navigating away.

---

### Edge Cases

- What happens when the device disconnects mid-transfer? → BLE transports detect link drops via connection state watchers and surface `ConnectionFailed` / `TransferFailed` errors.
- What happens when the firmware hash is rejected by the device? → The `HashRejected` OTA exception is caught and a specific "Hash Rejected" error message is shown.
- What happens when the DFU zip is malformed? → `parseDfuZipEntries` throws `DfuException.InvalidPackage` with a descriptive message (missing manifest, missing bin/dat).
- What happens when no matching firmware file is found in the release? → The retriever returns `null`, and the handler shows a "Firmware not found for [device]" error.
- What happens when the Legacy DFU bootloader is too old (SDK ≤ 6)? → `LegacyDfuException.UnsupportedBootloader` is thrown with guidance to update the bootloader.
- What happens when BLE packets are lost during Secure DFU? → The transport detects bytes-lost via CRC checksum, tightens PRN to 1, and resends the lost portion.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| `FirmwareUpdateScreen` | `feature/firmware/FirmwareUpdateScreen.kt` | CMP UI — state-driven scaffold with progress, error, success, and file-save composables |
| `FirmwareUpdateViewModel` | `feature/firmware/FirmwareUpdateViewModel.kt` | Coordinates release checking, update execution, post-update verification, and temp file cleanup |
| `FirmwareUpdateManager` | `feature/firmware/FirmwareUpdateManager.kt` | Interface routing update requests to the correct handler based on connection type + architecture |
| `DefaultFirmwareUpdateManager` | `feature/firmware/DefaultFirmwareUpdateManager.kt` | Routes to `SecureDfuHandler`, `Esp32OtaUpdateHandler`, or `UsbUpdateHandler` |
| `FirmwareRetriever` | `feature/firmware/FirmwareRetriever.kt` | Downloads firmware via manifest resolution, filename heuristics, or zip extraction fallback |
| `FirmwareFileHandler` | `feature/firmware/FirmwareFileHandler.kt` | Platform-abstracted file/network I/O interface (androidMain / jvmMain implementations) |
| `Esp32OtaUpdateHandler` | `feature/firmware/ota/Esp32OtaUpdateHandler.kt` | ESP32 OTA orchestrator — triggers reboot, connects transport, streams firmware |
| `BleOtaTransport` | `feature/firmware/ota/BleOtaTransport.kt` | BLE transport for ESP32 Unified OTA protocol using Kable |
| `WifiOtaTransport` | `feature/firmware/ota/WifiOtaTransport.kt` | WiFi/TCP transport for ESP32 Unified OTA using Ktor raw sockets |
| `SecureDfuHandler` | `feature/firmware/ota/dfu/SecureDfuHandler.kt` | nRF52 DFU orchestrator — auto-detects Secure vs Legacy bootloader protocol |
| `SecureDfuTransport` | `feature/firmware/ota/dfu/SecureDfuTransport.kt` | Nordic Secure DFU (FE59) BLE transport with object-transfer, CRC-32, and PRN flow control |
| `LegacyDfuTransport` | `feature/firmware/ota/dfu/LegacyDfuTransport.kt` | Nordic Legacy DFU (1530) / Adafruit BLEDfu transport with PRN and OTAFIX-2.1 high-MTU support |
| `UsbUpdateHandler` | `feature/firmware/UsbUpdateHandler.kt` | USB/UF2 update handler — downloads UF2, reboots to DFU, presents save dialog |
| `ThroughputTracker` | `feature/firmware/ota/ThroughputTracker.kt` | Sliding-window throughput calculator for real-time speed/ETA display |
| `FirmwareHashUtil` | `feature/firmware/ota/FirmwareHashUtil.kt` | SHA-256 hashing via Okio for firmware integrity verification |
| `DfuZipParser` | `feature/firmware/ota/dfu/DfuZipParser.kt` | Parses Nordic DFU zip packages (manifest.json → .dat + .bin) |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST auto-detect the connected device's hardware model via `DeviceHardwareRepository` and resolve the correct firmware binary.
- **FR-002**: System MUST support three release channels: Stable, Alpha, and Local File, selectable via a segmented button row.
- **FR-003**: System MUST route firmware updates to the correct handler based on connection type (BLE → DFU/OTA, TCP → WiFi OTA, Serial → USB UF2) and architecture (ESP32 → OTA, nRF52 → DFU/USB).
- **FR-004**: System MUST download firmware from the Meshtastic GitHub release infrastructure, resolving via `.mt.json` manifest first, then filename heuristics, then zip extraction as fallback.
- **FR-005**: System MUST compute SHA-256 of ESP32 firmware and include it in the OTA handshake for device-side verification.
- **FR-006**: System MUST compute running CRC-32 checksums during Secure DFU transfers and validate against device-reported values at PRN intervals.
- **FR-007**: System MUST support both Nordic Secure DFU (service `FE59`) and Nordic Legacy DFU / Adafruit BLEDfu (service `1530`) protocols, auto-detecting which the bootloader speaks.
- **FR-008**: System MUST support buttonless DFU trigger for both Secure and Legacy services, with fallback from Secure to Legacy when FE59 is not exposed.
- **FR-009**: System MUST display download and upload progress with percentage, throughput (KiB/s), and ETA.
- **FR-010**: System MUST verify the device reconnects after flashing within a 60-second timeout.
- **FR-011**: System MUST block firmware updates when battery level is ≤ 10%.
- **FR-012**: System MUST show a bootloader upgrade warning card for devices with `requiresBootloaderUpgradeForOta = true` over BLE, dismissable per device address.
- **FR-013**: System MUST show a disclaimer dialog before starting any update, with a disconnect warning and "I know what I'm doing" confirmation.
- **FR-014**: System MUST keep the screen on during active transfer states (Downloading, Processing, Updating, Verifying).
- **FR-015**: System MUST clean up temporary firmware files on ViewModel destruction (via `ApplicationCoroutineScope` + `NonCancellable`).
- **FR-016**: System MUST support resume for Secure DFU — if the device already has partial data with a matching CRC, skip to the next object boundary.
- **FR-017**: System MUST handle OTAFIX-2.1+ bootloaders by detecting the `_DFU` advertising name suffix and using high-MTU packets (up to 244 bytes) for Legacy DFU.
- **FR-018**: System MUST handle bytes-lost during Secure DFU by tightening PRN to 1 and resending the lost portion.

### Non-Functional Requirements

- **NFR-001**: All protocol implementations and UI composables MUST reside in `commonMain` — no `android.*` or `java.*` imports.
- **NFR-002**: BLE OTA chunk size MUST be 512 bytes; WiFi OTA chunk size MUST be 1024 bytes for optimal throughput.
- **NFR-003**: Connection timeouts MUST be ≤ 15 seconds; command timeouts ≤ 30 seconds; erasing timeouts ≤ 60 seconds.
- **NFR-004**: The feature MUST use `safeCatching {}` (not `runCatching {}`) and project `ioDispatcher` (not `Dispatchers.IO`) per constitution.

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 30 source files (~5,697 lines) | All business logic, protocols, and UI |
| `androidMain` | 2 files (`AndroidFirmwareFileHandler`, `AndroidFirmwareUsbManager`) | Platform file I/O and USB device detection |
| `jvmMain` | 2 files (`JvmFirmwareFileHandler`, `DesktopFirmwareUsbManager`) | Desktop file I/O and stub USB manager |
| `commonTest` | 21 test files (~4,602 lines) | Protocol, retriever, ViewModel, state, and integration tests |
| `jvmTest` | 1 file (`FirmwareUpdateViewModelFileTest`) | JVM-specific ViewModel file operation tests |

## Design Standards Compliance

- [x] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [x] M3 component selection verified — `SegmentedButton`, `ElevatedCard`, `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`, `MeshtasticDialog`
- [x] Accessibility: haptic feedback on update/success actions, descriptive content descriptions on icons
- [x] Typography: `headlineSmall` for device name, `titleMedium` for status messages, `bodyMedium`/`bodySmall` for details

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed — firmware hashes are logged but are not user data
- [x] No new network calls that transmit user data — only firmware downloads from public GitHub release URLs
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Firmware updates succeed end-to-end for all supported architectures (ESP32, nRF52, RP2040) across all transport types (BLE, WiFi, USB).
- **SC-002**: ESP32 firmware resolution correctly uses `.mt.json` manifest when available and falls back gracefully through 3 additional strategies.
- **SC-003**: Nordic DFU handler auto-detects Secure vs Legacy protocol and routes to the correct transport.
- **SC-004**: Post-update device verification detects reconnection within 60 seconds or reports verification failure.
- **SC-005**: Battery check prevents updates at ≤ 10% charge.
- **SC-006**: Bootloader warning is shown for flagged devices over BLE and persists dismissal per address.
- **SC-007**: Temporary firmware files are cleaned up on ViewModel destruction and on init.
- **SC-008**: All 21 test files (4,602 lines) pass in `allTests`.
- **SC-009**: Upload progress displays accurate throughput (KiB/s) and ETA using sliding-window tracker.
- **SC-010**: Screen stays on during active transfer states and back-navigation shows confirmation dialog.

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` (from `core/ui/icon/`)
- Float values pre-formatted with `NumberFormatter.format()` (CMP constraint)
- Device hardware metadata (architecture, platformioTarget, bootloaderInfoUrl) is available from `DeviceHardwareRepository`
- BLE abstraction layer (`core/ble`) provides `BleScanner`, `BleConnectionFactory`, and `BleConnection` interfaces
- The Meshtastic firmware release infrastructure serves files at `https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-{version}/`
- DFU zip packages follow the Nordic DFU format: `manifest.json` + `.dat` + `.bin`
- ESP32 devices advertise at MAC+1 in OTA mode; nRF52 devices advertise at MAC+1 in DFU mode

