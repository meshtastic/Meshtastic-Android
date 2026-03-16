# Specification: Replace Nordic with Kable on Android (Deduplication Pass)

## Overview
This track executes a full migration of the Android application's BLE transport layer from the legacy Nordic Android Common Libraries to the multiplatform Kable library. Building upon the successful `MeshtasticRadioProfile` abstraction introduced for the Desktop target, this track aims to unify the BLE transport layer across all platforms (Android, Desktop, iOS) under a single KMP technology stack. Crucially, this pass focuses on **maximal code deduplication**, moving as much BLE logic as possible into `commonMain` to share it across all targets, including OTA firmware update logic.

## Functional Requirements
- **Kable Integration:** Implement the `MeshtasticRadioProfile` using Kable for the `androidMain` source set, replacing the existing Nordic implementation.
- **Maximal Deduplication:** Refactor the existing Kable `jvmMain` implementation and the new `androidMain` implementation to extract common connection management, scanning logic, and characteristic observation into `core:ble/commonMain`. 
- **OTA Firmware Updates:** Migrate the Android OTA firmware update logic (currently handled by `NordicDfuHandler`) to use the new Kable/KMP abstraction.
- **Full Migration:** The Android app must exclusively use the new Kable backend for all BLE operations (scanning, connecting, data transfer, firmware updates).
- **Deprecation/Removal:** Remove all dependencies on the Nordic Android Common Libraries and Nordic DFU libraries from the project configuration (`build.gradle.kts`, version catalogs).
- **Feature Parity:** The new Kable implementation on Android must maintain full feature parity with the previous Nordic implementation, including connection stability, MTU negotiation, and data throughput.

## Non-Functional Requirements
- **Expanded Testing:** Adapt existing Android BLE tests to use Kable fakes and write new `commonMain` tests to expand test coverage for the shared KMP BLE abstraction.
- **Architecture:** Maintain strict adherence to the MVI/UDF patterns and the pure KMP DI architecture (Koin annotations).

## Acceptance Criteria
- [ ] Kable backend is fully implemented for Android (`androidMain`).
- [ ] Nordic Android Common Libraries and DFU dependencies are completely removed from the project.
- [ ] Android application successfully scans, connects, and transfers data via BLE using Kable.
- [ ] BLE logic (connection state, profile mapping, retry logic) is heavily deduplicated into `core:ble/commonMain`.
- [ ] OTA firmware update logic is successfully migrated to use the Kable backend.
- [ ] Existing BLE tests are updated or replaced, and all test suites pass.
- [ ] New KMP BLE tests are added, improving overall test coverage.

## Out of Scope
- Migrating USB or TCP network transports.