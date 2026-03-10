# Phase 8: `core:ble` KMP Abstraction

## Objective
Migrate `core:ble` from an Android-only library (`meshtastic.android.library`) to a Kotlin Multiplatform library (`meshtastic.kmp.library`). The goal is to provide a unified, platform-agnostic Bluetooth Low Energy (BLE) interface for the rest of the application (e.g., `core:domain`, `core:data`), while explicitly supporting future Desktop and Web targets.

## Strategy: The "Nordic Hybrid" Abstraction
We will use an Interface-Driven (Dependency Injection) approach rather than relying directly on Nordic's KMM library in `commonMain` or using raw `expect`/`actual` for the entire BLE stack.

Nordic's [KMM-BLE-Library](https://github.com/NordicSemiconductor/Kotlin-BLE-Library) provides excellent, battle-tested Coroutine/Flow APIs for Android and iOS. However, it **does not support Desktop (JVM/Windows/Linux/macOS) or Web (Wasm/JS)**. If we expose Nordic's classes directly in `commonMain`, the project will fail to compile for Desktop/Web targets.

To resolve this, we will build a custom abstraction layer:

### 1. The Common Interfaces (`commonMain`)
Define pure Kotlin interfaces and data classes representing BLE operations. The rest of the app will only know about these interfaces.
*   `BleScanner`: For discovering devices.
*   `BleDevice`: Represents a remote peripheral.
*   `BleConnectionManager`: Handles connect/disconnect, MTU negotiation, and characteristic read/write/subscribe operations.
*   *Note: No Nordic dependencies will exist in `commonMain`.*

### 2. The Android & iOS Implementations (`androidMain` & `iosMain`)
These source sets will depend on the Nordic `KMM-BLE-Library`. We will write concrete implementations of our common interfaces (e.g., `NordicBleConnectionManager`) that delegate operations to Nordic's `CentralManager` and `Peripheral` classes.

### 3. The Future Implementations (`desktopMain` / `webMain`)
By keeping `commonMain` free of Nordic dependencies, we reserve the ability to implement our BLE interfaces using other libraries (like [Kable](https://github.com/JuulLabs/kable) or Web Bluetooth APIs) on unsupported platforms without rewriting the core application logic.

## Execution Plan
1.  ✅ **Refactor Build Script:** Convert `core/ble/build.gradle.kts` to use the KMP plugin and define `commonMain` and `androidMain` source sets. Move Nordic dependencies to `androidMain`.
2.  ✅ **Define Abstractions:** Create pure Kotlin interfaces (`BleScanner`, `BleConnection`, etc.) in `commonMain`.
3.  ✅ **Implement Wrappers:** Move the existing Android-specific Nordic implementation into `androidMain` and adapt it to implement the new `commonMain` interfaces.
4.  ✅ **Update DI:** Adjust the Hilt/DI modules in `app` or `androidMain` to bind the Android-specific Nordic wrappers to the common interfaces.
5.  ✅ **Verify:** Ensure the Android app builds and tests pass, confirming the abstraction works correctly.

## Status: Completed
This phase was successfully executed. The Nordic SDK is now fully wrapped by common KMP interfaces (`BleDevice`, `BleScanner`, etc.). The DI modules have been relocated to the `app` module to accommodate Hilt limitations with KMP projects. All tests and integrations have been updated to use the new abstracted interfaces.