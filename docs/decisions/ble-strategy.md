# Decision: BLE KMP Strategy

> Date: 2026-03-16 | Status: **Decided — Fully Migrated to Kable**

## Context

`core:ble` needed to support non-Android targets. Nordic's Kotlin-BLE-Library, while mature on Android and actively tested in the app, was primarily Android/iOS focused and lacked support for Desktop (JVM) targets. Kable natively supports all Kotlin Multiplatform targets (Android, Apple, Desktop/JVM, Web).

Initially, we implemented an **Interface-Driven "Nordic Hybrid" Abstraction** (keeping Nordic on Android behind `commonMain` interfaces) to wait and see if Nordic expanded their KMP support.

However, as Desktop integration advanced, we found the need for a unified BLE transport.

## Decision

**Migrate entirely to Kable:**

- We migrated all BLE transport logic across Android and Desktop to use Kable.
- The `commonMain` interfaces (`BleConnection`, `BleScanner`, `BleDevice`, `BluetoothRepository`, etc.) remain, but their core implementations (`KableBleConnection`, `KableBleScanner`) are now entirely shared in `commonMain`.
- The Android-specific Nordic dependencies (`no.nordicsemi.kotlin.ble:*`) and the Nordic DFU library were completely excised from the project.
- OTA Firmware updates on Android were successfully refactored to use the Kable-based `BleOtaTransport`.

## Consequences

- **Maximal Code Deduplication:** The BLE implementation is completely shared across Android and Desktop in `core:ble/commonMain`.
- **Future-Proofing:** Adding an `iosMain` target in the future will be trivial, as it can leverage the same shared Kable abstractions.
- **Lost Nordic Mocks:** Kable lacks the comprehensive mock infrastructure of the Nordic library. Consequently, several complex BLE OTA unit tests had to be deprecated. Re-establishing this test coverage using custom Kable fakes is an ongoing technical debt item.
