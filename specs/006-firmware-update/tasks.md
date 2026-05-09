# Tasks: Firmware Update (OTA / DFU)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Status**: Migrated  
**Task Prefix**: `FW-T`

> All tasks marked `[x]` were reverse-engineered from the existing implementation.  
> Tasks marked `[ ]` are identified gaps — work that should be done to improve the feature.

---

## Phase 1 — Core Models & Interfaces

- [x] **FW-T001**: Define `FirmwareArtifact` data class  
  `feature/firmware/src/commonMain/.../FirmwareArtifact.kt`  
  Platform-neutral handle for firmware files with `uri`, `fileName`, and `isTemporary` properties.

- [x] **FW-T002**: Define `FirmwareUpdateState` sealed interface  
  `feature/firmware/src/commonMain/.../FirmwareUpdateState.kt`  
  State machine: `Idle`, `Checking`, `Ready`, `Downloading`, `Processing`, `Updating`, `Verifying`, `VerificationFailed`, `Error`, `Success`, `AwaitingFileSave`.

- [x] **FW-T003**: Define `ProgressState` data class  
  `feature/firmware/src/commonMain/.../FirmwareUpdateState.kt`  
  Progress container with `message: UiText`, `progress: Float`, `details: String?`.

- [x] **FW-T004**: Define `FirmwareUpdateMethod` sealed class  
  `feature/firmware/src/commonMain/.../FirmwareUpdateViewModel.kt`  
  Transport mechanism enum: `Usb`, `Ble`, `Wifi`, `Unknown` — each with a `StringResource` description.

- [x] **FW-T005**: Define `FirmwareUpdateHandler` interface  
  `feature/firmware/src/commonMain/.../FirmwareUpdateHandler.kt`  
  Common `startUpdate()` contract for all handlers (release, hardware, target, state callback, optional URI).

- [x] **FW-T006**: Define `FirmwareUpdateManager` interface  
  `feature/firmware/src/commonMain/.../FirmwareUpdateManager.kt`  
  Routes update requests to the appropriate handler. `startUpdate()` returns a `FirmwareArtifact?` for cleanup.

- [x] **FW-T007**: Define `FirmwareUpdateActions` data class  
  `feature/firmware/src/commonMain/.../FirmwareUpdateActions.kt`  
  Lambda bundle for UI callbacks: `onReleaseTypeSelect`, `onStartUpdate`, `onPickFile`, `onSaveFile`, `onRetry`, `onCancel`, `onDone`, `onDismissBootloaderWarning`.

---

## Phase 2 — Firmware Retrieval

- [x] **FW-T008**: Define `FirmwareFileHandler` interface  
  `feature/firmware/src/commonMain/.../FirmwareFileHandler.kt`  
  Platform-abstracted file/network I/O: download, extract, copy, delete, zip operations.

- [x] **FW-T009**: Implement `isValidFirmwareFile()` utility  
  `feature/firmware/src/commonMain/.../FirmwareFileHandler.kt`  
  Filters firmware binaries from non-firmware artifacts (`littlefs-*`, `bleota*`, `mt-*`, `*.factory.*`).

- [x] **FW-T010**: Define `FirmwareManifest` and `FirmwareManifestFile` models  
  `feature/firmware/src/commonMain/.../FirmwareManifest.kt`  
  Kotlin model for `.mt.json` manifest files (kotlinx.serialization). Locates the `app0` OTA partition entry.

- [x] **FW-T011**: Implement `FirmwareRetriever`  
  `feature/firmware/src/commonMain/.../FirmwareRetriever.kt`  
  Multi-strategy firmware download: manifest → current naming → legacy naming → zip extraction. Supports `retrieveOtaFirmware()` (nRF52 DFU), `retrieveUsbFirmware()` (UF2), and `retrieveEsp32Firmware()` (ESP32 OTA).

- [x] **FW-T012**: Implement `AndroidFirmwareFileHandler`  
  `feature/firmware/src/androidMain/.../AndroidFirmwareFileHandler.kt`  
  Android-specific file I/O using ContentResolver and OkHttp.

- [x] **FW-T013**: Implement `JvmFirmwareFileHandler`  
  `feature/firmware/src/jvmMain/.../JvmFirmwareFileHandler.kt`  
  Desktop-specific file I/O using `java.io`.

---

## Phase 3 — OTA & DFU Protocols

### ESP32 Unified OTA

- [x] **FW-T014**: Define `UnifiedOtaProtocol` interface  
  `feature/firmware/src/commonMain/.../ota/UnifiedOtaProtocol.kt`  
  `connect()`, `startOta()`, `streamFirmware()`, `close()`. Shared by BLE and WiFi transports.

- [x] **FW-T015**: Define OTA commands, responses, and exceptions  
  `feature/firmware/src/commonMain/.../ota/UnifiedOtaProtocol.kt`  
  `OtaCommand.StartOta`, `OtaResponse` (Ok, Erasing, Ack, Error), `OtaProtocolException` hierarchy.

- [x] **FW-T016**: Implement `BleOtaTransport`  
  `feature/firmware/src/commonMain/.../ota/BleOtaTransport.kt`  
  BLE transport using Kable. Scans for OTA service UUID, connects, subscribes to notify characteristic, writes firmware in 512-byte chunks.

- [x] **FW-T017**: Implement `WifiOtaTransport`  
  `feature/firmware/src/commonMain/.../ota/WifiOtaTransport.kt`  
  WiFi/TCP transport using Ktor raw sockets on port 3232. 1024-byte chunks, no per-chunk ACK.

- [x] **FW-T018**: Implement `Esp32OtaUpdateHandler`  
  `feature/firmware/src/commonMain/.../ota/Esp32OtaUpdateHandler.kt`  
  Orchestrator: obtain firmware → compute SHA-256 → trigger OTA reboot → disconnect mesh → connect transport → stream → report success.

- [x] **FW-T019**: Implement `FirmwareHashUtil` (SHA-256 via Okio)  
  `feature/firmware/src/commonMain/.../ota/FirmwareHashUtil.kt`  
  `calculateSha256Bytes()` and `bytesToHex()` using `ByteString.sha256()`.

- [x] **FW-T020**: Implement `ThroughputTracker`  
  `feature/firmware/src/commonMain/.../ota/ThroughputTracker.kt`  
  Sliding-window throughput calculator with configurable window size and `TimeSource`.

- [x] **FW-T021**: Implement BLE scan support utilities  
  `feature/firmware/src/commonMain/.../ota/BleScanSupport.kt`  
  `calculateMacPlusOne()` for OTA/DFU address, `scanForBleDevice()` with retry logic.

### Nordic DFU (nRF52)

- [x] **FW-T022**: Define Secure DFU protocol models  
  `feature/firmware/src/commonMain/.../ota/dfu/SecureDfuProtocol.kt`  
  UUIDs, opcodes, result codes, extended errors, `DfuResponse` parsing, `DfuCrc32`, `DfuZipPackage`, `DfuManifest`, `DfuException` hierarchy.

- [x] **FW-T023**: Implement `SecureDfuTransport`  
  `feature/firmware/src/commonMain/.../ota/dfu/SecureDfuTransport.kt`  
  Full Nordic Secure DFU (FE59): buttonless trigger (Secure + Legacy fallback), DFU-mode connect, init packet transfer, firmware streaming with PRN flow control and CRC-32 validation, object resume, bytes-lost recovery.

- [x] **FW-T024**: Define Legacy DFU protocol models  
  `feature/firmware/src/commonMain/.../ota/dfu/LegacyDfuProtocol.kt`  
  Characteristic UUIDs, opcodes, status codes, `LegacyDfuResponse` parsing, payload builders, `LegacyDfuException` hierarchy.

- [x] **FW-T025**: Implement `LegacyDfuTransport`  
  `feature/firmware/src/commonMain/.../ota/dfu/LegacyDfuTransport.kt`  
  Full Nordic Legacy DFU (1530/Adafruit BLEDfu): DFU-mode connect, DFU version gate, init-packet bracket, firmware streaming with PRN, OTAFIX-2.1 high-MTU detection, connection-drop watcher.

- [x] **FW-T026**: Define `DfuUploadTransport` interface  
  `feature/firmware/src/commonMain/.../ota/dfu/DfuUploadTransport.kt`  
  Common upload surface: `connectToDfuMode()`, `transferInitPacket()`, `transferFirmware()`, `abort()`, `close()`.

- [x] **FW-T027**: Implement `DfuZipParser`  
  `feature/firmware/src/commonMain/.../ota/dfu/DfuZipParser.kt`  
  Parses pre-extracted zip entries into `DfuZipPackage` (manifest.json → .dat + .bin).

- [x] **FW-T028**: Implement `SecureDfuHandler`  
  `feature/firmware/src/commonMain/.../ota/dfu/SecureDfuHandler.kt`  
  nRF52 DFU orchestrator: obtain zip → extract .dat/.bin → disconnect mesh → trigger buttonless → detect protocol (Secure vs Legacy) → connect → transfer → validate → report success.

### USB / UF2

- [x] **FW-T029**: Implement `UsbUpdateHandler`  
  `feature/firmware/src/commonMain/.../UsbUpdateHandler.kt`  
  USB/UF2 handler delegating to `performUsbUpdate()`.

- [x] **FW-T030**: Implement `performUsbUpdate()` shared logic  
  `feature/firmware/src/commonMain/.../UsbUpdateSupport.kt`  
  Download UF2 → reboot to DFU → present `AwaitingFileSave` state. Handles both download and local-file paths.

- [x] **FW-T031**: Define `FirmwareUsbManager` interface  
  `feature/firmware/src/commonMain/.../FirmwareUsbManager.kt`  
  `deviceDetachFlow()` — emits when the USB device disconnects after flashing.

- [x] **FW-T032**: Implement platform USB managers  
  `feature/firmware/src/androidMain/.../AndroidFirmwareUsbManager.kt`  
  `feature/firmware/src/jvmMain/.../DesktopFirmwareUsbManager.kt`

---

## Phase 4 — ViewModel & UI

- [x] **FW-T033**: Implement `DefaultFirmwareUpdateManager`  
  `feature/firmware/src/commonMain/.../DefaultFirmwareUpdateManager.kt`  
  Handler router: BLE+ESP32→OTA, BLE+nRF52→DFU, TCP+ESP32→OTA, Serial+nRF52→USB. Target address resolution.

- [x] **FW-T034**: Implement `FirmwareUpdateViewModel`  
  `feature/firmware/src/commonMain/.../FirmwareUpdateViewModel.kt`  
  Orchestrates `checkForUpdates()`, `startUpdate()`, `startUpdateFromFile()`, `saveDfuFile()`, `cancelUpdate()`, `dismissBootloaderWarningForCurrentDevice()`. Post-update verification with 60-second timeout. Battery check (≤10%). Temp file cleanup via `ApplicationCoroutineScope` + `NonCancellable`.

- [x] **FW-T035**: Implement `FirmwareUpdateScreen`  
  `feature/firmware/src/commonMain/.../FirmwareUpdateScreen.kt`  
  Full CMP UI: `FirmwareUpdateScaffold`, `ReleaseTypeSelector`, `DeviceInfoCard`, `ReadyState`, `ProgressContent`, `VerifyingState`, `VerificationFailedState`, `ErrorState`, `SuccessState`, `AwaitingFileSaveState`, `DisclaimerDialog`, `BootloaderWarningCard`, `ChirpyCard`, `CyclingMessages`, `KeepScreenOn`, file picker and save launchers.

- [x] **FW-T036**: Implement `FirmwareNavigation`  
  `feature/firmware/src/commonMain/.../navigation/FirmwareNavigation.kt`  
  Navigation 3 `firmwareGraph()` registering `FirmwareRoute.FirmwareGraph` and `FirmwareRoute.FirmwareUpdate`.

- [x] **FW-T037**: Implement `FeatureFirmwareModule` (DI)  
  `feature/firmware/src/commonMain/.../di/FeatureFirmwareModule.kt`  
  Koin module with `@ComponentScan("org.meshtastic.feature.firmware")`.

---

## Phase 5 — Testing

- [x] **FW-T038**: ViewModel unit tests  
  `feature/firmware/src/commonTest/.../FirmwareUpdateViewModelTest.kt`  
  13 tests: initialization, release type switching, battery check, update success/error, cancel, bootloader warning, update method detection.

- [x] **FW-T039**: Integration tests  
  `feature/firmware/src/commonTest/.../FirmwareUpdateIntegrationTest.kt`  
  4 tests: end-to-end ViewModel state transitions with real ViewModel + fake/mock collaborators.

- [x] **FW-T040**: Handler routing tests  
  `feature/firmware/src/commonTest/.../DefaultFirmwareUpdateManagerTest.kt`  
  12 tests: BLE/Serial/TCP × ESP32/nRF52 routing, target resolution, error cases.

- [x] **FW-T041**: Firmware retriever tests  
  `feature/firmware/src/commonTest/.../CommonFirmwareRetrieverTest.kt`  
  11 tests: manifest resolution, current/legacy naming fallback, zip extraction, version stripping, platformioTarget vs hwModelSlug.

- [x] **FW-T042**: Firmware file validation tests  
  `feature/firmware/src/commonTest/.../IsValidFirmwareFileTest.kt`  
  13 tests: valid firmware names, exclusion patterns (littlefs, bleota, mt-, factory), wrong extension, target mismatch, edge cases.

- [x] **FW-T043**: State model tests  
  `feature/firmware/src/commonTest/.../FirmwareUpdateStateTest.kt`  
  4 tests: ProgressState defaults, stripFormatArgs variations.

- [x] **FW-T044**: Manifest deserialization tests  
  `feature/firmware/src/commonTest/.../FirmwareManifestTest.kt`

- [x] **FW-T045**: USB update flow tests  
  `feature/firmware/src/commonTest/.../CommonPerformUsbUpdateTest.kt`

- [x] **FW-T046**: BLE OTA transport tests  
  `feature/firmware/src/commonTest/.../ota/BleOtaTransportTest.kt`

- [x] **FW-T047**: BLE scan support tests  
  `feature/firmware/src/commonTest/.../ota/BleScanSupportTest.kt`  
  MAC+1 calculation, scan retry logic.

- [x] **FW-T048**: OTA response parsing tests  
  `feature/firmware/src/commonTest/.../ota/OtaResponseTest.kt`

- [x] **FW-T049**: Throughput tracker tests  
  `feature/firmware/src/commonTest/.../ota/ThroughputTrackerTest.kt`

- [x] **FW-T050**: SHA-256 hash tests  
  `feature/firmware/src/commonTest/.../ota/FirmwareHashUtilTest.kt`

- [x] **FW-T051**: DFU CRC-32 tests  
  `feature/firmware/src/commonTest/.../ota/dfu/DfuCrc32Test.kt`

- [x] **FW-T052**: DFU response parsing tests  
  `feature/firmware/src/commonTest/.../ota/dfu/DfuResponseTest.kt`

- [x] **FW-T053**: DFU zip parser tests  
  `feature/firmware/src/commonTest/.../ota/dfu/DfuZipParserTest.kt`

- [x] **FW-T054**: Legacy DFU protocol tests  
  `feature/firmware/src/commonTest/.../ota/dfu/LegacyDfuProtocolTest.kt`

- [x] **FW-T055**: Legacy DFU transport tests  
  `feature/firmware/src/commonTest/.../ota/dfu/LegacyDfuTransportTest.kt`

- [x] **FW-T056**: Secure DFU protocol tests  
  `feature/firmware/src/commonTest/.../ota/dfu/SecureDfuProtocolTest.kt`

- [x] **FW-T057**: Secure DFU transport tests  
  `feature/firmware/src/commonTest/.../ota/dfu/SecureDfuTransportTest.kt`

- [x] **FW-T058**: JVM ViewModel file tests  
  `feature/firmware/src/jvmTest/.../FirmwareUpdateViewModelFileTest.kt`

---

## Identified Gaps

- [ ] **FW-T059**: Add `WifiOtaTransport` unit tests  
  The WiFi/TCP OTA transport has no dedicated test coverage. Should test connection, command sending, response reading, firmware streaming, and error handling using a fake Ktor socket.

- [ ] **FW-T060**: Add `FirmwareUpdateScreen` composable/screenshot tests  
  No UI tests exist for the 889-line screen composable. Should test at minimum: Ready state rendering, progress state rendering, error state rendering, and success state rendering.

- [ ] **FW-T061**: Add `Esp32OtaUpdateHandler` unit tests  
  The ESP32 OTA handler orchestration logic (firmware retrieval → hash → reboot → connect → stream) has no isolated test. Currently only covered by proxy through integration tests.

---

## Summary

| Category | Count | Status |
|----------|-------|--------|
| Completed tasks | 58 | ✅ All done |
| Gap tasks | 3 | ⬜ Open |
| **Total** | **61** | — |

| Phase | Tasks | Status |
|-------|-------|--------|
| 1. Core Models & Interfaces | FW-T001–FW-T007 | ✅ Complete |
| 2. Firmware Retrieval | FW-T008–FW-T013 | ✅ Complete |
| 3. OTA & DFU Protocols | FW-T014–FW-T032 | ✅ Complete |
| 4. ViewModel & UI | FW-T033–FW-T037 | ✅ Complete |
| 5. Testing | FW-T038–FW-T058 | ✅ Complete |
| Gaps | FW-T059–FW-T061 | ⬜ Open |

