# Feature Specification: Core BLE Abstraction

**Feature Branch**: `013-core-ble`  
**Created**: 2026-07-27  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `core/ble` module

## Summary

Core BLE provides a platform-agnostic Bluetooth Low Energy abstraction layer for Meshtastic-Android. It wraps the Kable library behind clean `commonMain` interfaces (`BleScanner`, `BleConnection`, `BleConnectionFactory`, `BleService`, `BluetoothRepository`) so that consumers (`core/network`, `feature/wifi-provision`, `feature/device-connections`) never depend on Kable directly. The module handles device scanning, GATT connection lifecycle, characteristic read/write/observe, connection state mapping, exception classification, retry with exponential backoff, and Meshtastic-specific BLE constants (service UUID, characteristic UUIDs). Platform-specific implementations exist in `androidMain` (Android `BluetoothAdapter` integration), `jvmMain` (desktop stubs), and `iosMain` (noop stubs).

## Goals

1. **Platform abstraction** — isolate Kable (and platform BLE APIs) behind `commonMain` interfaces so transport consumers don't import Kable types.
2. **Reliable connections** — provide exponential-backoff retry, structured exception classification, and connection state mapping for robust BLE operations.
3. **Meshtastic radio profile** — encapsulate Meshtastic-specific GATT service/characteristic UUIDs and MTU constants.
4. **Reusable scanning** — provide a generic `BleScanner` that supports service UUID filtering, address-based targeting, and timeout-based scan windows.
5. **Testable** — enable consumers to inject `BleConnectionFactory` and `BleScanner` fakes for unit testing without real hardware.

## Non-Goals

- Transport-level framing or protobuf encoding — handled by `core/network`.
- WiFi provisioning protocol (nymea) — handled by `feature/wifi-provision` (uses `BleConnection` + `BleService`).
- MQTT or TCP connectivity — this module is BLE-only.
- Android runtime permission management — handled by the app module.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Scan for Meshtastic BLE Devices (Priority: P1)

The BLE scanner discovers nearby Meshtastic devices advertising the Meshtastic service UUID. Results are emitted as a cold `Flow<BleDevice>` that terminates after the scan timeout.

**Why this priority**: Device discovery is the prerequisite for all BLE connectivity.

**Independent Test**: Can be unit-tested with Kable scanner mocks.

**Acceptance Scenarios**:

1. **Given** Bluetooth is enabled, **When** `scan(timeout, serviceUuid)` is called, **Then** a flow of `BleDevice` objects is emitted for each matching advertisement.
2. **Given** a specific MAC address is provided, **When** `scan(timeout, address = "AA:BB:CC")` is called, **Then** only the matching device is emitted.
3. **Given** the scan timeout elapses, **When** no more devices are found, **Then** the flow completes normally.
4. **Given** Bluetooth is disabled, **When** scan is attempted, **Then** the flow terminates with an appropriate exception.

---

### User Story 2 — Connect to a BLE Device (Priority: P1)

`BleConnection` manages the GATT connection lifecycle. Consumers call `connect(device)` or `connectAndAwait(device, timeout)` and observe `connectionState: StateFlow<BleConnectionState>` for state transitions.

**Why this priority**: Active BLE connection is required for all radio communication.

**Independent Test**: Connection state transitions can be tested by observing the `connectionState` flow.

**Acceptance Scenarios**:

1. **Given** a valid `BleDevice`, **When** `connect(device)` is called, **Then** `connectionState` transitions from `Disconnected` → `Connecting` → `Connected`.
2. **Given** `connectAndAwait(device, 30s)` is called, **When** connection succeeds within timeout, **Then** it returns `BleConnectionState.Connected`.
3. **Given** the timeout elapses before connection, **When** `connectAndAwait` returns, **Then** it returns the current disconnected state.
4. **Given** the remote device disconnects, **When** the GATT disconnection event occurs, **Then** `connectionState` transitions to `Disconnected` with a `DisconnectReason`.

---

### User Story 3 — Read/Write/Observe GATT Characteristics (Priority: P1)

Within a connected `BleService` profile scope, consumers can observe notifications, read values, and write data to characteristics using the `BleService` interface.

**Why this priority**: All mesh data exchange happens through characteristic read/write/observe.

**Independent Test**: Can be validated by writing to a characteristic and observing the echo.

**Acceptance Scenarios**:

1. **Given** an active `BleService` for the Meshtastic service UUID, **When** `observe(fromRadio)` is called, **Then** a flow of `ByteArray` notifications is emitted.
2. **Given** `observe(characteristic, onSubscription)` is used, **When** CCCD write completes, **Then** `onSubscription` is invoked before the first notification.
3. **Given** a payload to send, **When** `write(toRadio, data, WITH_RESPONSE)` is called, **Then** the data is written with write-with-response semantics.
4. **Given** the connection drops during a write, **When** the exception is caught, **Then** `classifyBleException()` returns a `BleExceptionInfo` with a meaningful message.

---

### User Story 4 — BLE Operation Retry with Backoff (Priority: P2)

The `retryBleOperation` utility retries transient BLE failures with bounded exponential backoff and jitter to avoid retry storms.

**Why this priority**: BLE operations are inherently unreliable. Retry logic is essential for production stability.

**Independent Test**: Fully testable in isolation with simulated failures.

**Acceptance Scenarios**:

1. **Given** a BLE operation fails on the first attempt, **When** retry is invoked with `count=3`, **Then** it retries up to 2 more times with increasing delay.
2. **Given** all 3 attempts fail, **When** the last attempt throws, **Then** the exception is propagated to the caller.
3. **Given** a `CancellationException` is thrown, **When** caught by retry, **Then** it is immediately re-thrown (structured concurrency preserved).
4. **Given** backoff delay exceeds `MAX_RETRY_DELAY_MS` (2s), **When** calculated, **Then** the delay is capped at 2s with ±25% jitter.

---

### Edge Cases

- What happens when `maximumWriteValueLength()` returns null? The caller falls back to `DEFAULT_BLE_WRITE_VALUE_LENGTH` (20 bytes).
- What happens when `requestHighConnectionPriority()` is called on a non-Android platform? It returns `false` (default implementation).
- What happens when a GATT status error with an unknown code is classified? `BleExceptionInfo` is returned with the raw status code.
- What happens when multiple `observe()` collectors exist on the same characteristic? Each gets an independent flow backed by the same Kable observation.

## Architecture

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| `BleScanner` | `BleScanner.kt` | Interface: scans for BLE devices with timeout & filtering |
| `KableBleScanner` | `KableBleScanner.kt` | Kable-backed scanner implementation |
| `BleConnection` | `BleConnection.kt` | Interface: GATT connection lifecycle, state, characteristic access |
| `KableBleConnection` | `KableBleConnection.kt` | Kable `Peripheral`-backed connection (276 LOC) |
| `ActiveBleConnection` | `ActiveBleConnection.kt` | Connection wrapper with active-state tracking |
| `BleConnectionFactory` | `BleConnectionFactory.kt` | Factory interface for creating `BleConnection` instances |
| `KableBleConnectionFactory` | `KableBleConnectionFactory.kt` | Kable-backed factory |
| `BleService` | `BleConnection.kt` | Interface: characteristic observe/read/write within a GATT profile |
| `KableBleService` | `KableBleConnection.kt` | Kable `Peripheral`-backed service implementation |
| `BleConnectionState` | `BleConnectionState.kt` | Sealed class: Connected / Disconnected(reason) / Connecting |
| `KableStateMapping` | `KableStateMapping.kt` | Maps Kable `State` → `BleConnectionState` |
| `BleExceptionClassifier` | `BleExceptionClassifier.kt` | Classifies Kable exceptions into `BleExceptionInfo` |
| `BleRetry` | `BleRetry.kt` | Exponential backoff retry with jitter |
| `MeshtasticRadioProfile` | `MeshtasticRadioProfile.kt` | Meshtastic service/characteristic UUID profile |
| `KableMeshtasticRadioProfile` | `KableMeshtasticRadioProfile.kt` | Kable-specific profile implementation |
| `MeshtasticBleConstants` | `MeshtasticBleConstants.kt` | Service UUID, characteristic UUIDs, MTU constants |
| `MeshtasticBleDevice` | `MeshtasticBleDevice.kt` | Meshtastic-specific BLE device wrapper |
| `BleDevice` | `BleDevice.kt` | Platform-agnostic BLE device representation |
| `BluetoothRepository` | `BluetoothRepository.kt` | Bluetooth adapter state (enabled/disabled) |
| `BleLoggingConfig` | `BleLoggingConfig.kt` | Debug vs release logging configuration |
| `KermitLogEngine` | `KermitLogEngine.kt` | Bridges Kable logging to Kermit |
| `BleServiceExtensions` | `BleServiceExtensions.kt` | Extension functions for `BleService` |
| `CoreBleModule` | `di/CoreBleModule.kt` | Koin DI module |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a `BleScanner` interface that emits `Flow<BleDevice>` from a time-bounded scan.
- **FR-002**: System MUST support scan filtering by service UUID and/or MAC address.
- **FR-003**: System MUST provide a `BleConnection` interface with `connect`, `connectAndAwait`, `disconnect`, and `connectionState` flow.
- **FR-004**: System MUST provide a `BleService` interface with `observe`, `read`, `write`, `hasCharacteristic`, and `preferredWriteType`.
- **FR-005**: System MUST support `observe(characteristic, onSubscription)` to execute a callback after CCCD write completes.
- **FR-006**: System MUST provide a `BleConnectionFactory` for creating `BleConnection` instances scoped to a `CoroutineScope`.
- **FR-007**: System MUST classify Kable exceptions (`GattStatusException`, `NotConnectedException`, `GattRequestRejectedException`, `UnmetRequirementException`) into `BleExceptionInfo`.
- **FR-008**: System MUST provide `retryBleOperation` with configurable count, initial delay, exponential backoff (factor 2), 2s cap, and ±25% jitter.
- **FR-009**: System MUST map Kable `State` values to `BleConnectionState` (Connected, Connecting, Disconnected with reason).
- **FR-010**: System MUST define Meshtastic GATT constants: service UUID (`0xfeb8`), `FromRadio`, `ToRadio`, `FromNum`, `LogRadio` characteristic UUIDs.
- **FR-011**: System MUST provide `requestHighConnectionPriority()` with platform-specific implementation on Android (default `false` on other platforms).
- **FR-012**: System MUST bridge Kable logging to Kermit via `KermitLogEngine`, with verbose logging in debug builds only.

### Non-Functional Requirements

- **NFR-001**: All interfaces and shared logic MUST reside in `commonMain` (Constitution §I).
- **NFR-002**: Kable types (`Peripheral`, `Scanner`, `State`) MUST NOT leak into public API surfaces.
- **NFR-003**: `retryBleOperation` MUST re-throw `CancellationException` immediately (Constitution §VII).
- **NFR-004**: BLE logging MUST be single-line format for logcat/grep friendliness.
- **NFR-005**: Default write value length MUST be 20 bytes (23-byte ATT MTU minus 3-byte header).

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 22 files (~1,800 LOC) | All interfaces, Kable implementations, constants, retry logic |
| `commonTest` | 5 files (~300 LOC) | Tests for exception classifier, state mapping, radio profile, retry, disconnect reason |
| `androidMain` | 3 files (~200 LOC) | `AndroidBluetoothRepository`, platform-specific `KablePlatformSetup`, Android DI module |
| `jvmMain` | 2 files (~80 LOC) | Desktop `KableBluetoothRepository`, `KablePlatformSetup` |
| `iosMain` | 1 file (~20 LOC) | Noop stubs |

## Privacy Assessment

- [x] No PII logged — BLE device addresses are not logged in release builds
- [x] No user data transmitted — BLE module handles raw byte transport only
- [x] Proto submodule not modified

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `BleExceptionClassifier` correctly classifies all 4 Kable exception types into `BleExceptionInfo`.
- **SC-002**: `KableStateMapping` maps all Kable `State` values to the correct `BleConnectionState`.
- **SC-003**: `retryBleOperation` retries exactly `count-1` times on transient failures, with delays capped at 2s.
- **SC-004**: `KableMeshtasticRadioProfile` correctly resolves all 4 Meshtastic characteristic UUIDs.
- **SC-005**: Disconnect reason mapping produces meaningful human-readable messages for all known Kable disconnect states.
- **SC-006**: All 5 existing test files pass with `allTests` target.
- **SC-007**: BLE module compiles for all 3 targets (Android, JVM, iOS) with no platform leaks.
- **SC-008**: Debug builds produce verbose Kable logs; release builds produce quiet logs.

## Assumptions

- Kable library is the sole BLE implementation backing — no fallback to raw platform APIs.
- Consumers inject `BleConnectionFactory`/`BleScanner` via Koin; fakes are available in `core/testing`.
- MTU negotiation is not performed — the module assumes minimum 20-byte write value length.
- iOS implementation is currently noop stubs (iOS BLE support is pending full Kable iOS stabilization).
- `BleLoggingConfig` is provided via Koin based on `BuildConfigProvider.isDebug`.

