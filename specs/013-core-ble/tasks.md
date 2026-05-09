# Tasks: Core BLE Abstraction

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Status**: Migrated — all existing tasks marked complete. Gap tasks marked incomplete.  
**Task Prefix**: `BLE-T`

---

## Phase 1 — Interfaces & Constants

### BLE-T001: BLE device and connection state types [x]

- **Files**: `BleDevice.kt`, `MeshtasticBleDevice.kt`, `BleConnectionState.kt`
- Defined `BleDevice` (address, name, rssi), `MeshtasticBleDevice` (Meshtastic-specific wrapper), `BleConnectionState` sealed class (Connected, Connecting, Disconnected with reason).
- **Test**: `DisconnectReasonTest.kt` — covers disconnect reason mapping.

### BLE-T002: BleScanner interface [x]

- **File**: `BleScanner.kt`
- Defined `scan(timeout, serviceUuid?, address?)` returning `Flow<BleDevice>`.
- **Test**: Interface contract verified via consumers.

### BLE-T003: BleConnection + BleService interfaces [x]

- **File**: `BleConnection.kt` (~114 LOC)
- `BleConnection`: connect, connectAndAwait, disconnect, profile, connectionState, deviceFlow.
- `BleService`: observe, read, write, hasCharacteristic, preferredWriteType.
- `BleWriteType` enum: WITH_RESPONSE, WITHOUT_RESPONSE.
- `BleCharacteristic` data class.
- **Test**: Interface contracts verified via implementations.

### BLE-T004: BleConnectionFactory interface [x]

- **File**: `BleConnectionFactory.kt`
- Factory: `create(scope, tag)` → `BleConnection`.
- **Test**: Verified via `core/network` BLE transport.

### BLE-T005: BluetoothRepository interface [x]

- **File**: `BluetoothRepository.kt`
- Bluetooth adapter state (enabled/disabled) as reactive flow.
- **Test**: Verified via consumers.

### BLE-T006: Meshtastic BLE constants [x]

- **File**: `MeshtasticBleConstants.kt`
- Service UUID (`0xfeb8`), `FromRadio`, `ToRadio`, `FromNum`, `LogRadio` characteristic UUIDs.
- **Test**: `KableMeshtasticRadioProfileTest.kt` validates UUID resolution.

### BLE-T007: MeshtasticRadioProfile interface + Kable implementation [x]

- **Files**: `MeshtasticRadioProfile.kt`, `KableMeshtasticRadioProfile.kt`
- Maps Meshtastic characteristic names to `BleCharacteristic` instances.
- **Test**: `KableMeshtasticRadioProfileTest.kt`.

---

## Phase 2 — Kable Implementations

### BLE-T008: KableBleScanner [x]

- **File**: `KableBleScanner.kt`
- Wraps Kable `Scanner` with service UUID filtering and timeout.
- Emits `BleDevice` for each discovered advertisement.
- **Test**: Verified via integration with device connections feature.

### BLE-T009: KableBleConnection + KableBleService [x]

- **File**: `KableBleConnection.kt` (~276 LOC)
- `KableBleConnection`: manages Kable `Peripheral` lifecycle, state observation, disconnect handling.
- `KableBleService`: wraps `Peripheral` for per-service characteristic I/O.
- Connection state observation via `Peripheral.state` → `BleConnectionState` mapping.
- **Test**: State mapping verified via `KableStateMappingTest.kt`.

### BLE-T010: ActiveBleConnection [x]

- **File**: `ActiveBleConnection.kt`
- Thin wrapper tracking whether the connection is actively being used.
- **Test**: Verified via integration.

### BLE-T011: KableBleConnectionFactory [x]

- **File**: `KableBleConnectionFactory.kt`
- Creates `KableBleConnection` instances scoped to a `CoroutineScope`.
- **Test**: Verified via `BleRadioTransport` in `core/network`.

### BLE-T012: KableStateMapping [x]

- **File**: `KableStateMapping.kt`
- Maps Kable `State.Connected` → `BleConnectionState.Connected`, etc.
- Clean extension function approach.
- **Test**: `KableStateMappingTest.kt` — covers all state transitions.

### BLE-T013: BleExceptionClassifier [x]

- **File**: `BleExceptionClassifier.kt` (~65 LOC)
- `Throwable.classifyBleException()` → `BleExceptionInfo?`
- Classifies: `GattStatusException`, `NotConnectedException`, `GattRequestRejectedException`, `UnmetRequirementException`.
- All currently classified as transient (`isPermanent = false`).
- **Test**: `BleExceptionClassifierTest.kt` — covers all 4 exception types + unknown.

### BLE-T014: BleRetry with exponential backoff [x]

- **File**: `BleRetry.kt` (~73 LOC)
- `retryBleOperation(count, delayMs, tag, block)`
- Backoff factor 2, cap at 2s, ±25% jitter.
- Re-throws `CancellationException` immediately.
- **Test**: `BleRetryTest.kt` — covers success, retry, exhaustion, cancellation.

### BLE-T015: BLE logging infrastructure [x]

- **Files**: `BleLoggingConfig.kt`, `KermitLogEngine.kt`
- `BleLoggingConfig.Debug` (verbose Kable Events) vs `BleLoggingConfig.Release` (quiet).
- `KermitLogEngine` bridges Kable logging to Kermit.
- **Test**: Configuration verified via `CoreBleModule` provider.

### BLE-T016: BleServiceExtensions [x]

- **File**: `BleServiceExtensions.kt`
- Utility extension functions for common `BleService` operations.
- **Test**: Verified via consumers.

---

## Phase 3 — Platform Integration

### BLE-T017: Android BluetoothRepository + DI [x]

- **Files**: `androidMain/.../AndroidBluetoothRepository.kt`, `di/CoreBleAndroidModule.kt`, `KablePlatformSetup.kt`
- Wraps `BluetoothAdapter` for adapter state observation.
- Android-specific Kable scanner/peripheral configuration.
- **Test**: Verified via Android app integration.

### BLE-T018: JVM + iOS platform stubs [x]

- **Files**: `jvmMain/.../KableBluetoothRepository.kt`, `KablePlatformSetup.kt`, `iosMain/.../NoopStubs.kt`
- JVM: Desktop Bluetooth repository with Kable desktop scanner.
- iOS: Noop stubs (pending full Kable iOS support).
- **Test**: Compilation verified on all targets.

---

## Gap Tasks (Incomplete)

### BLE-T019: Add KableBleConnection integration tests [ ]

- **File to create**: `commonTest/.../KableBleConnectionTest.kt`
- Test connected lifecycle with mock Kable `Peripheral`.
- Verify state transitions, profile access, disconnect handling.
- **Priority**: Medium

### BLE-T020: Add KableBleScanner unit tests [ ]

- **File to create**: `commonTest/.../KableBleConnectionTest.kt`
- Test scan flow emissions, timeout behavior, service UUID filtering.
- **Priority**: Medium

### BLE-T021: Add ActiveBleConnection tests [ ]

- **File to create**: `commonTest/.../ActiveBleConnectionTest.kt`
- Verify active-state tracking and delegation behavior.
- **Priority**: Low

