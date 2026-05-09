# Implementation Plan: Core BLE Abstraction

**Branch**: `013-core-ble` | **Date**: 2026-07-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/013-core-ble/spec.md`
**Status**: Migrated — all implementation complete, plan reverse-engineered from existing code.

## Summary

Core BLE wraps the Kable BLE library behind clean `commonMain` interfaces, providing device scanning, GATT connection management, characteristic I/O, exception classification, retry logic, and Meshtastic-specific BLE constants. Platform-specific code is minimal: Android (3 files), JVM (2 files), iOS (1 noop file).

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Kable (BLE), Koin 4.2+, kotlinx.coroutines, Kermit logging  
**Testing**: KMP `allTests` — 5 test files, ~300 LOC  
**Target Platform**: Android, Desktop (JVM), iOS  
**Constraints**: Kable types must not leak into public interfaces; all shared code in `commonMain`  
**Scale/Scope**: 22 commonMain files (~1,800 LOC), 6 platform files (~300 LOC), 5 test files (~300 LOC)

## Constitution Check

*GATE: All checks pass — existing production code reviewed against Constitution v1.2.2.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All interfaces in `commonMain`. Platform code minimal and correctly scoped. |
| II. Zero Lint Tolerance | ✅ PASS | `detekt-baseline.xml` present. `@Suppress` used sparingly. |
| III. Compose Multiplatform UI | N/A | No UI code. |
| IV. Privacy First | ✅ PASS | No PII logged. MAC addresses suppressed in release. |
| V. Design Standards Compliance | N/A | No UI code. |
| VI. Verify Before Push | ⚠️ PARTIAL | 5 test files cover utilities, but no integration test for `KableBleConnection`. |
| VII. Coroutine Safety | ✅ PASS | `retryBleOperation` re-throws `CancellationException`. Named dispatchers used. |
| VIII. Resource Discipline | N/A | No resources. |
| IX. Branch & Scope Hygiene | ✅ PASS | Module cleanly scoped to `org.meshtastic.core.ble`. |

**Gate Result**: ✅ All applicable principles satisfied (1 test coverage gap noted).

## Project Structure

```
core/ble/src/
├── commonMain/kotlin/org/meshtastic/core/ble/
│   ├── di/CoreBleModule.kt
│   ├── BleScanner.kt                    # Interface
│   ├── KableBleScanner.kt               # Kable implementation
│   ├── BleConnection.kt                 # Interface + BleService interface
│   ├── KableBleConnection.kt            # Kable implementation (276 LOC)
│   ├── ActiveBleConnection.kt           # Active-state tracking wrapper
│   ├── BleConnectionFactory.kt          # Factory interface
│   ├── KableBleConnectionFactory.kt     # Kable factory
│   ├── BleConnectionState.kt            # Sealed class
│   ├── KableStateMapping.kt             # Kable → BleConnectionState
│   ├── BleExceptionClassifier.kt        # Exception → BleExceptionInfo
│   ├── BleRetry.kt                      # Exponential backoff retry
│   ├── BleDevice.kt                     # Device representation
│   ├── MeshtasticBleDevice.kt           # Meshtastic-specific device
│   ├── MeshtasticRadioProfile.kt        # Profile interface
│   ├── KableMeshtasticRadioProfile.kt   # Kable profile implementation
│   ├── MeshtasticBleConstants.kt        # UUIDs and constants
│   ├── BluetoothRepository.kt           # BT adapter state interface
│   ├── BleLoggingConfig.kt              # Debug/Release logging
│   ├── KermitLogEngine.kt               # Kable → Kermit bridge
│   ├── BleServiceExtensions.kt          # Utility extensions
│   └── KablePlatformSetup.kt            # expect declaration
├── commonTest/kotlin/org/meshtastic/core/ble/
│   ├── BleExceptionClassifierTest.kt
│   ├── KableMeshtasticRadioProfileTest.kt
│   ├── KableStateMappingTest.kt
│   ├── DisconnectReasonTest.kt
│   └── BleRetryTest.kt
├── androidMain/ (3 files — BluetoothRepository, PlatformSetup, DI)
├── jvmMain/ (2 files — BluetoothRepository, PlatformSetup)
└── iosMain/ (1 file — NoopStubs)
```

## Implementation Phases

### Phase 1 — Interfaces & Constants (Complete)

Defined all `commonMain` interfaces (`BleScanner`, `BleConnection`, `BleService`, `BleConnectionFactory`, `BluetoothRepository`) and Meshtastic BLE constants.

### Phase 2 — Kable Implementations (Complete)

Built `KableBleScanner`, `KableBleConnection` (276 LOC), `KableBleConnectionFactory`, `KableBleService`, `KableStateMapping`. Implemented `BleExceptionClassifier` and `BleRetry`.

### Phase 3 — Platform Integration (Complete)

Platform-specific `BluetoothRepository` implementations (Android wraps `BluetoothAdapter`, JVM/iOS provide stubs). `KablePlatformSetup` expect/actual for platform scanner/peripheral configuration.

## Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| BLE library | Kable | Best KMP BLE library with coroutine-first API |
| Abstraction strategy | Interface wrapping | Prevents Kable type leakage; enables fake injection |
| Retry strategy | Exponential backoff + jitter | Prevents retry storms from synchronized BLE failures |
| Backoff cap | 2 seconds | Balances retry speed with BLE stack recovery time |
| Jitter range | ±25% | Decorrelates concurrent retries without excessive randomness |
| State mapping | Extension function on Kable `State` | Clean, testable, single point of conversion |
| Logging bridge | `KermitLogEngine` | Unifies Kable and app logging under Kermit |
| Debug logging | Verbose Kable Events in debug only | Prevents log spam in release; enables deep debugging |

## Gaps Identified

| Gap | Severity | Recommendation |
|-----|----------|----------------|
| No integration test for `KableBleConnection` | ⚠️ Medium | Add connected lifecycle test with mock Kable `Peripheral` |
| `KableBleScanner` has no unit test | ⚠️ Medium | Add test for scan flow emissions and timeout |
| `ActiveBleConnection` has no unit test | ⚠️ Low | Add test for active-state tracking behavior |
| No test for `KableBleConnectionFactory.create()` | ⚠️ Low | Verify factory produces correctly-scoped connections |
| iOS implementation is noop stubs | ℹ️ Info | Will need real implementation when iOS BLE stabilizes |

