# Implementation Plan: Firmware Update (OTA / DFU)

**Branch**: `006-firmware-update` | **Date**: 2026-07-15 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/006-firmware-update/spec.md`

**Note**: This plan was reverse-engineered from the existing `feature/firmware` module as part of a brownfield migration.

## Summary

The Firmware Update feature provides a complete firmware flashing pipeline for Meshtastic devices across three transport mechanisms (BLE, WiFi/TCP, USB) and three device architectures (ESP32, nRF52, RP2040). The implementation uses a handler-router pattern (`FirmwareUpdateManager` → transport-specific handlers) with platform-abstracted file I/O (`FirmwareFileHandler`) and a state-machine-driven Compose Multiplatform UI. All protocol implementations (ESP32 Unified OTA, Nordic Secure DFU, Nordic Legacy DFU) are pure Kotlin in `commonMain`.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Compose Multiplatform, Material 3 Adaptive + Expressive, Koin 4.2+ (K2 Compiler Plugin), Ktor (raw sockets for WiFi OTA), Okio (SHA-256 hashing), Kable (BLE via `core/ble`), Coil 3 (device images), mikepenz/multiplatform-markdown-renderer (release notes)  
**Storage**: `BootloaderWarningDataSource` (DataStore KMP) for bootloader dismissal persistence  
**Testing**: KMP `allTests` — 21 test files in `commonTest`, 1 in `jvmTest`. Uses Mokkery for mocks, `FakeNodeRepository` / `FakeRadioController` for fakes.  
**Target Platform**: Android, Desktop (JVM) — all via `commonMain`  
**Performance Goals**: Real-time throughput tracking via sliding-window `ThroughputTracker`; BLE DFU throughput ~1-12 KiB/s (20-244 byte packets); WiFi OTA ~50-100 KiB/s (1024-byte chunks)  
**Constraints**: All UI in `commonMain`; no `java.*`/`android.*` in common; CMP float pre-formatting via `NumberFormatter.format()`; `safeCatching {}` not `runCatching {}`  
**Scale/Scope**: 30 source files, 2 platform files (androidMain), 2 platform files (jvmMain), 22 test files

## Constitution Check

*GATE: All principles verified against existing implementation.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All 30 source files in `commonMain`. Platform code limited to `FirmwareFileHandler` impls and `FirmwareUsbManager` impls. |
| II. Zero Lint Tolerance | ✅ PASS | `spotlessApply` + `detekt` pass. `@Suppress` annotations documented where needed (e.g., `LongParameterList`, `MagicNumber`). |
| III. Compose Multiplatform UI | ✅ PASS | CMP composables throughout. `NumberFormatter.format()` used for throughput display. Navigation 3 patterns via `firmwareGraph()`. |
| IV. Privacy First | ✅ PASS | No PII logged. Firmware hashes are ephemeral. Network calls only to public GitHub URLs. |
| V. Design Standards Compliance | ✅ PASS | M3 Expressive APIs (`CircularWavyProgressIndicator`, `LinearWavyProgressIndicator`, `ButtonDefaults.LargeContainerHeight`). `MeshtasticIcons` exclusively. |
| VI. Verify Before Push | ✅ PASS | Full verification pipeline passes: `spotlessApply spotlessCheck detekt assembleDebug test allTests`. |
| VII. Coroutine Safety | ✅ PASS | `safeCatching {}` used consistently. `ioDispatcher` from project utils. `NonCancellable` for cleanup in `onCleared()`. |
| VIII. Resource Discipline | ✅ PASS | All strings via `stringResource(Res.string.*)`. Icons from `MeshtasticIcons`. |
| IX. Branch & Scope Hygiene | ✅ PASS | Feature module is self-contained with clean dependency boundaries. |

**Gate Result**: ✅ All principles satisfied

## Project Structure

### Documentation (this feature)

```text
specs/006-firmware-update/
├── spec.md              # Feature specification (migrated)
├── plan.md              # This file (migrated)
└── tasks.md             # Task breakdown (migrated)
```

### Source Code (repository root)

```text
feature/firmware/
├── src/commonMain/kotlin/org/meshtastic/feature/firmware/
│   ├── DefaultFirmwareUpdateManager.kt    ← Handler router (BLE/WiFi/USB × ESP32/nRF52)
│   ├── FirmwareArtifact.kt                ← Platform-neutral firmware file handle
│   ├── FirmwareFileHandler.kt             ← Platform I/O interface + isValidFirmwareFile()
│   ├── FirmwareManifest.kt                ← .mt.json manifest model (kotlinx.serialization)
│   ├── FirmwareRetriever.kt               ← Multi-strategy firmware download (manifest → heuristics → zip)
│   ├── FirmwareUpdateActions.kt           ← UI callback holder (lambda bundle)
│   ├── FirmwareUpdateHandler.kt           ← Common handler interface
│   ├── FirmwareUpdateManager.kt           ← Manager interface
│   ├── FirmwareUpdateScreen.kt            ← CMP UI (889 lines — scaffold, progress, error, success)
│   ├── FirmwareUpdateState.kt             ← Sealed state machine (Idle→Checking→Ready→...→Success)
│   ├── FirmwareUpdateViewModel.kt         ← ViewModel (update orchestration, verification, cleanup)
│   ├── FirmwareUsbManager.kt              ← USB detach flow interface
│   ├── UsbUpdateHandler.kt                ← USB/UF2 handler
│   ├── UsbUpdateSupport.kt               ← Shared USB update logic (top-level function)
│   ├── di/
│   │   └── FeatureFirmwareModule.kt       ← Koin DI module (@ComponentScan)
│   ├── navigation/
│   │   └── FirmwareNavigation.kt          ← Navigation 3 entry provider
│   └── ota/
│       ├── BleOtaTransport.kt             ← BLE transport for ESP32 Unified OTA
│       ├── BleScanSupport.kt              ← BLE scan helpers + MAC+1 calculation
│       ├── Esp32OtaUpdateHandler.kt       ← ESP32 OTA orchestrator (BLE + WiFi)
│       ├── FirmwareHashUtil.kt            ← SHA-256 via Okio
│       ├── ThroughputTracker.kt           ← Sliding-window speed calculator
│       ├── UnifiedOtaProtocol.kt          ← OTA command/response/exception models
│       ├── WifiOtaTransport.kt            ← WiFi/TCP transport via Ktor raw sockets
│       └── dfu/
│           ├── DfuUploadTransport.kt      ← Common DFU upload interface
│           ├── DfuZipParser.kt            ← Nordic DFU zip → DfuZipPackage
│           ├── LegacyDfuProtocol.kt       ← Legacy DFU opcodes, responses, payloads
│           ├── LegacyDfuTransport.kt      ← Legacy DFU BLE transport (Adafruit BLEDfu)
│           ├── SecureDfuHandler.kt         ← nRF52 DFU orchestrator (Secure + Legacy auto-detect)
│           ├── SecureDfuProtocol.kt        ← Secure DFU opcodes, responses, CRC-32, manifest
│           └── SecureDfuTransport.kt       ← Secure DFU BLE transport (FE59)

├── src/androidMain/kotlin/org/meshtastic/feature/firmware/
│   ├── AndroidFirmwareFileHandler.kt      ← Android file I/O (ContentResolver, OkHttp)
│   └── AndroidFirmwareUsbManager.kt       ← Android USB device detach flow

├── src/jvmMain/kotlin/org/meshtastic/feature/firmware/
│   ├── JvmFirmwareFileHandler.kt          ← Desktop file I/O (java.io)
│   └── DesktopFirmwareUsbManager.kt       ← Desktop USB stub

├── src/commonTest/kotlin/org/meshtastic/feature/firmware/
│   ├── CommonFirmwareRetrieverTest.kt     ← ESP32 manifest/heuristic resolution (abstract)
│   ├── CommonPerformUsbUpdateTest.kt      ← USB update flow tests (abstract)
│   ├── DefaultFirmwareUpdateManagerTest.kt ← Handler routing tests
│   ├── FirmwareManifestTest.kt            ← Manifest deserialization
│   ├── FirmwareUpdateIntegrationTest.kt   ← End-to-end ViewModel integration
│   ├── FirmwareUpdateStateTest.kt         ← ProgressState + stripFormatArgs
│   ├── FirmwareUpdateViewModelTest.kt     ← ViewModel unit tests
│   ├── IsValidFirmwareFileTest.kt         ← Firmware filename validation
│   ├── TestApplicationCoroutineScope.kt   ← Test helper
│   └── ota/
│       ├── BleOtaTransportTest.kt         ← BLE OTA transport tests
│       ├── BleScanSupportTest.kt          ← MAC+1 calculation tests
│       ├── FirmwareHashUtilTest.kt        ← SHA-256 tests
│       ├── OtaResponseTest.kt             ← OTA response parsing
│       ├── ThroughputTrackerTest.kt       ← Throughput calculation tests
│       └── dfu/
│           ├── DfuCrc32Test.kt            ← CRC-32 tests
│           ├── DfuResponseTest.kt         ← DFU response parsing
│           ├── DfuZipParserTest.kt        ← DFU zip parsing
│           ├── LegacyDfuProtocolTest.kt   ← Legacy DFU protocol tests
│           ├── LegacyDfuTransportTest.kt  ← Legacy DFU transport tests
│           ├── SecureDfuProtocolTest.kt    ← Secure DFU protocol tests
│           └── SecureDfuTransportTest.kt   ← Secure DFU transport tests

└── src/jvmTest/kotlin/org/meshtastic/feature/firmware/
    └── FirmwareUpdateViewModelFileTest.kt ← JVM-specific file operation tests
```

**Structure Decision**: The feature follows the established `feature/*` module pattern. The `ota/` and `ota/dfu/` sub-packages organize protocol implementations by transport family. All protocol models, commands, and responses are co-located with their transport implementations for cohesion.

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/firmware` | All New | 35 source + 22 test | Low (isolated module) |
| `core/ble` | Dependency | 0 (uses existing interfaces) | Low |
| `core/model` | Dependency | 0 (uses `DeviceHardware`, `RadioController`) | Low |
| `core/database` | Dependency | 0 (uses `FirmwareRelease` entity) | Low |
| `core/resources` | Modify | 1 file (strings.xml — ~50 firmware_update_* strings) | Low |

## Integration Points

- **Navigation**: `FirmwareNavigation.firmwareGraph()` registers `FirmwareRoute.FirmwareGraph` and `FirmwareRoute.FirmwareUpdate` entries into Navigation 3.
- **DI**: `FeatureFirmwareModule` uses `@ComponentScan` to auto-register all `@Single` and `@KoinViewModel` annotated classes.
- **Radio Controller**: Uses `RadioController.setDeviceAddress("n")` to disconnect mesh service before OTA, `rebootToDfu()` / `requestRebootOta()` to trigger device reboot.
- **DataStore**: `BootloaderWarningDataSource` persists per-device dismissal of bootloader upgrade warnings.
- **BLE**: Depends on `core/ble` abstractions (`BleScanner`, `BleConnectionFactory`, `BleConnection`) for all BLE operations.

## Design Constraints

- All UI lives in `commonMain` — not platform-specific
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- Error handling uses `safeCatching {}` not `runCatching {}`
- Dispatchers via `org.meshtastic.core.common.util.ioDispatcher`
- Float values must be pre-formatted with `NumberFormatter.format()` (CMP constraint)
- Legacy DFU packet size defaults to 20 bytes for safety; OTAFIX-2.1+ devices use negotiated MTU up to 244 bytes
- ESP32 OTA requires mesh service disconnect before transport connection (GATT exclusivity)

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| BLE link drops mid-DFU transfer | Medium | High | Connection-state watchers cancel streaming immediately; abort sent to device; retry logic in SecureDfuTransport |
| Bootloader protocol mismatch (Secure vs Legacy) | Low | High | Auto-detection via BLE scan for Legacy service UUID before connecting |
| Firmware hash rejected by ESP32 | Low | Medium | Specific `HashRejected` exception with user-facing error message |
| UF2 file save fails on Android | Low | Medium | Save dialog with retry; `AwaitingFileSave` state persists until user acts |
| OTAFIX-2.1 high-MTU packets overrun non-OTAFIX bootloaders | Low | Critical | Name-suffix detection (`_DFU`) gates high-MTU; defaults to safe 20-byte packets |

## Phase Alignment with Tasks

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Core Models & Interfaces | Data types, state machine, handler interface | FW-T001–FW-T006 | None |
| 2. Firmware Retrieval | Download, manifest resolution, zip extraction | FW-T007–FW-T012 | Phase 1 |
| 3. OTA Protocols | ESP32 OTA (BLE + WiFi), Nordic DFU (Secure + Legacy), USB | FW-T013–FW-T028 | Phase 2 |
| 4. ViewModel & UI | Screen composables, ViewModel orchestration, navigation | FW-T029–FW-T037 | Phase 3 |
| 5. Testing & Polish | Unit tests, integration tests, DI module | FW-T038–FW-T042 | All prior phases |

### Critical Path

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |

