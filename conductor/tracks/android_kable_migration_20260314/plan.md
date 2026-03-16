# Implementation Plan: Replace Nordic with Kable on Android (Deduplication Pass)

## Phase 1: Deduplicate Kable Abstractions into `commonMain` [checkpoint: 709f6e3]
- [x] Task: Extract common Kable state mapping logic from jvmMain to commonMain 10cdd16
    - [x] Create `commonMain` tests for `BleConnectionState` mapping using Kable `State`
    - [x] Move `KableMeshtasticRadioProfile` and `KableBleConnection` logic that doesn't depend on platform specifics to `commonMain`
- [x] Task: Implement common Kable `Scanner` and `Peripheral` wrappers 2691d70
    - [x] Extract generic connection lifecycle (connect, reconnect, close) to `commonMain` using Kable's `Peripheral` interface
- [x] Task: Conductor - User Manual Verification 'Phase 1: Deduplicate Kable Abstractions into commonMain' (Protocol in workflow.md) 709f6e3

## Phase 2: Implement Kable Backend for Android (`androidMain`) [checkpoint: 12217de]
- [x] Task: Add Kable dependency to Android source set in `core:ble/build.gradle.kts` 011d619
- [x] Task: Implement Android-specific `BleConnectionFactory` and `BleScanner` using the deduplicated `commonMain` logic 589ee93
    - [x] Write failing integration tests for Android Kable scanner (using fakes/mocks)
    - [x] Implement `KableBleScanner` for `androidMain`
    - [x] Write failing integration tests for Android Kable connection (using fakes/mocks)
    - [x] Implement `KableBleConnection` for `androidMain` (handling Android-specific MTU requests if necessary)
- [x] Task: Conductor - User Manual Verification 'Phase 2: Implement Kable Backend for Android' (Protocol in workflow.md) 12217de

## Phase 3: Migrate OTA Firmware Update Logic [checkpoint: 663c8e2]
- [x] Task: Deprecate `NordicDfuHandler` and replace with Kable-based DFU 06fe4f5
    - [x] Write failing tests for Kable DFU integration
    - [x] Implement new DFU handler in `feature:firmware` using `MeshtasticRadioProfile` / Kable abstraction
- [x] Task: Conductor - User Manual Verification 'Phase 3: Migrate OTA Firmware Update Logic' (Protocol in workflow.md) 663c8e2

## Phase 4: Wire Kable into Android App and Remove Nordic [checkpoint: ebe1617]
- [x] Task: Deprecate and remove `NordicBleInterface` and `AndroidBleConnection` ebe1617
    - [x] Remove `NordicAndroidCommonLibraries` and `NordicDfuLibrary` from `gradle/libs.versions.toml` and build files
    - [x] Delete `NordicBleInterface.kt` and associated Nordic-specific radio implementations
- [x] Task: Wire new `androidMain` Kable implementation into the Koin DI graph ebe1617
    - [x] Update `AndroidRadioControllerImpl` or DI modules to provide the new Kable `BleConnectionFactory` and `BleScanner`
- [x] Task: Conductor - User Manual Verification 'Phase 4: Wire Kable into Android App and Remove Nordic' (Protocol in workflow.md) ebe1617

## Phase 5: Final Testing and Integration
- [ ] Task: Update Android `app` UI tests and BLE unit tests to use Kable fakes
    - [ ] Fix any failing tests related to the Nordic removal
- [ ] Task: Manual end-to-end verification
    - [ ] Build and run the Android app, verify BLE scanning, connecting, and messaging
    - [ ] Verify OTA updates work via BLE
    - [ ] Verify the Desktop app still functions correctly
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Final Testing and Integration' (Protocol in workflow.md)