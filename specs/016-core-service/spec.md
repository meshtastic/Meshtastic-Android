# Feature Specification: Core Service (Mesh Service Bridge)

**Feature Branch**: `016-core-service`  
**Created**: 2026-07-27  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `core/service` module

## Summary

Core Service provides the mesh service lifecycle bridge between the platform layer and the KMP data layer. Its centerpiece is `MeshServiceOrchestrator` — a platform-agnostic orchestrator that extracts the startup wiring previously embedded in Android's `MeshService.onCreate()` into a reusable component usable on Android, Desktop, and iOS. The module also contains the Android foreground `MeshService` (AIDL binder, notifications, location, WorkManager workers), `ServiceRepositoryImpl` (reactive state for connection, errors, packets, service actions), `SharedRadioInterfaceService`, platform-specific location/file/notification services, and background workers (send message, mesh log cleanup, service keep-alive).

## Goals

1. **Platform-agnostic service lifecycle** — `MeshServiceOrchestrator` starts/stops the mesh service graph without Android dependencies.
2. **Android foreground service** — `MeshService` provides the required foreground notification, AIDL binder, and lifecycle hooks.
3. **Reactive service state** — `ServiceRepositoryImpl` exposes connection state, errors, packets, and service actions as KMP-compatible flows.
4. **Background workers** — WorkManager workers for message queuing, log cleanup, and service keep-alive on Android.
5. **Platform services** — location, file, and notification services with platform-specific implementations.

## Non-Goals

- Data layer implementation (repositories, managers) — handled by `core/data`.
- Transport layer (BLE, TCP, Serial) — handled by `core/network`.
- UI for service status — handled by feature modules.
- Push notification routing to individual messages — handled by `core/data` message processor.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Mesh Service Orchestrator Lifecycle (Priority: P1)

The orchestrator starts the mesh service graph: initializes the database, connects the radio, wires `FromRadio` data to the message processor, and dispatches service actions to the router. It stops by cancelling the scope and disconnecting.

**Why this priority**: The orchestrator is the entry point for the entire mesh service. Without it, nothing runs.

**Independent Test**: Can be tested with mock dependencies for radio, database, message processor.

**Acceptance Scenarios**:

1. **Given** the orchestrator is stopped, **When** `start()` is called, **Then** it initializes the per-device database, connects the radio, and begins processing `receivedData`.
2. **Given** `receivedData` emits bytes, **When** a FromRadio packet arrives, **Then** `messageProcessor.handleFromRadio(bytes, myNodeNum)` is called.
3. **Given** a service action is dispatched, **When** it arrives in `serviceRepository.serviceAction`, **Then** it is routed to `router.actionHandler.onServiceAction()` in a supervised coroutine.
4. **Given** `start()` is called while already running, **When** invoked, **Then** it is a no-op.
5. **Given** the orchestrator is running, **When** `stop()` is called, **Then** the scope is cancelled, the radio is disconnected, and TAK server is stopped.
6. **Given** a stale `receivedData` channel from a previous session, **When** `start()` is called, **Then** `resetReceivedBuffer()` drains the channel first.

---

### User Story 2 — ServiceRepositoryImpl Reactive State (Priority: P1)

`ServiceRepositoryImpl` manages all reactive state flows for the mesh service: connection state, error messages, mesh packets, client notifications, traceroute responses, and the service action channel.

**Why this priority**: All feature modules observe these flows for their UI state.

**Independent Test**: Fully testable — pure Kotlin flows with no platform dependencies.

**Acceptance Scenarios**:

1. **Given** the connection state changes, **When** `setConnectionState(Connected)` is called, **Then** `connectionState` StateFlow emits `Connected`.
2. **Given** an error occurs, **When** `setErrorMessage(msg, severity)` is called, **Then** the error flow emits the message.
3. **Given** a service action is dispatched, **When** `dispatchServiceAction(action)` is called, **Then** the action is received by the orchestrator's collector.
4. **Given** a traceroute response arrives, **When** `setTracerouteResponse(response)` is called, **Then** the response flow emits.

---

### User Story 3 — Android Foreground Service (Priority: P1, Android-only)

`MeshService` is an Android foreground service that hosts the orchestrator. It provides the required persistent notification, handles AIDL binding for external clients, and manages the service lifecycle.

**Why this priority**: Android requires a foreground service for persistent radio connections.

**Independent Test**: `IMeshServiceContractTest` validates the AIDL contract.

**Acceptance Scenarios**:

1. **Given** the service is started, **When** `onCreate()` fires, **Then** the foreground notification is posted and `orchestrator.start()` is called.
2. **Given** the service is destroyed, **When** `onDestroy()` fires, **Then** `orchestrator.stop()` is called and resources are cleaned up.
3. **Given** a client binds via AIDL, **When** `onBind()` is called, **Then** the `IMeshService.Stub` binder is returned.
4. **Given** an AIDL method throws, **When** caught, **Then** it is wrapped as a `RemoteException` via `toRemoteExceptions`.

---

### User Story 4 — Background Workers (Priority: P2, Android-only)

WorkManager workers handle background tasks: `SendMessageWorker` queues messages for reliable delivery, `MeshLogCleanupWorker` periodically prunes old logs, and `ServiceKeepAliveWorker` ensures the service stays alive.

**Why this priority**: Background task reliability directly affects message delivery and storage management.

**Independent Test**: `SendMessageWorkerTest` validates the worker logic.

**Acceptance Scenarios**:

1. **Given** a message is queued, **When** `SendMessageWorker` executes, **Then** the message is sent via `CommandSender` and the result is reported.
2. **Given** mesh logs exceed the retention limit, **When** `MeshLogCleanupWorker` runs, **Then** old logs are pruned.
3. **Given** the service is at risk of being killed, **When** `ServiceKeepAliveWorker` fires, **Then** the service is re-started.

---

### Edge Cases

- What happens when `stop()` is called while `start()` is initializing? The scope is cancelled, and the database/radio initialization coroutine is terminated.
- What happens when `radioInterfaceService.disconnect()` throws during stop? It's caught by `runCatching` in a detached coroutine.
- What happens when `orchestrator.start()` fails to connect the radio? The error is emitted via `radioInterfaceService.connectionError` and handled by the service repository.
- What happens when TAK server is enabled but the preferences change mid-session? The orchestrator starts/stops TAK integration reactively.

## Architecture

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| `MeshServiceOrchestrator` | `commonMain/.../MeshServiceOrchestrator.kt` (165 LOC) | KMP service lifecycle: start/stop, wire data flows |
| `ServiceRepositoryImpl` | `commonMain/.../ServiceRepositoryImpl.kt` (129 LOC) | Reactive state: connection, errors, packets, actions |
| `SharedRadioInterfaceService` | `commonMain/.../SharedRadioInterfaceService.kt` | Shared radio interface bridge |
| `DirectRadioControllerImpl` | `commonMain/.../DirectRadioControllerImpl.kt` | Direct radio control for KMP hosts |
| `CoreServiceModule` | `commonMain/.../di/CoreServiceModule.kt` | Koin DI: ServiceScope with SupervisorJob |
| `MeshService` | `androidMain/.../MeshService.kt` (406 LOC) | Android foreground service with AIDL binder |
| `AndroidServiceRepository` | `androidMain/.../AndroidServiceRepository.kt` | Extends ServiceRepositoryImpl with AIDL binding |
| `AndroidNotificationManager` | `androidMain/.../AndroidNotificationManager.kt` | Notification channels and builders |
| `MeshServiceNotificationsImpl` | `androidMain/.../MeshServiceNotificationsImpl.kt` | Service notification management |
| `AndroidLocationService` | `androidMain/.../AndroidLocationService.kt` | GPS location provider |
| `AndroidFileService` | `androidMain/.../AndroidFileService.kt` | File I/O for exports |
| `SendMessageWorker` | `androidMain/.../worker/SendMessageWorker.kt` | Background message delivery |
| `MeshLogCleanupWorker` | `androidMain/.../worker/MeshLogCleanupWorker.kt` | Log pruning worker |
| `ServiceKeepAliveWorker` | `androidMain/.../worker/ServiceKeepAliveWorker.kt` | Service persistence worker |
| `ServiceBroadcasts` | `androidMain/.../ServiceBroadcasts.kt` | Android broadcast intents |
| `MeshServiceStarter` | `androidMain/.../MeshServiceStarter.kt` | Service start helper |
| `BootCompleteReceiver` | `androidMain/.../BootCompleteReceiver.kt` | Auto-start on boot |
| `ReplyReceiver` | `androidMain/.../ReplyReceiver.kt` | Notification direct reply |
| `ReactionReceiver` | `androidMain/.../ReactionReceiver.kt` | Notification reaction |
| `MarkAsReadReceiver` | `androidMain/.../MarkAsReadReceiver.kt` | Notification mark-as-read |
| `JvmLocationService` | `jvmMain/.../JvmLocationService.kt` | Desktop location stub |
| `JvmFileService` | `jvmMain/.../JvmFileService.kt` | Desktop file I/O |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide `MeshServiceOrchestrator` with `start()` and `stop()` for platform-agnostic service lifecycle.
- **FR-002**: Orchestrator MUST initialize the per-device database before connecting the radio.
- **FR-003**: Orchestrator MUST drain the `receivedData` channel on each `start()` to prevent stale packet replay.
- **FR-004**: Orchestrator MUST forward `receivedData` to `messageProcessor.handleFromRadio()`.
- **FR-005**: Orchestrator MUST dispatch service actions in supervised coroutines (failure isolation).
- **FR-006**: Orchestrator MUST observe TAK server preference and start/stop integration reactively.
- **FR-007**: System MUST provide `ServiceRepositoryImpl` with StateFlows for connection state, errors, packets, notifications.
- **FR-008**: System MUST provide Android `MeshService` as a foreground service with persistent notification.
- **FR-009**: System MUST provide AIDL binder for external client integration via `IMeshService`.
- **FR-010**: System MUST provide `SendMessageWorker` for reliable background message delivery.
- **FR-011**: System MUST provide `MeshLogCleanupWorker` for periodic log pruning.
- **FR-012**: System MUST provide notification receivers for reply, reaction, and mark-as-read.
- **FR-013**: System MUST provide `BootCompleteReceiver` for auto-start on device boot.
- **FR-014**: System MUST provide `ServiceScope` (SupervisorJob + default dispatcher) via Koin.

### Non-Functional Requirements

- **NFR-001**: `MeshServiceOrchestrator` and `ServiceRepositoryImpl` MUST reside in `commonMain` (Constitution §I).
- **NFR-002**: Android-specific code (Service, Workers, Receivers, Notifications) MUST reside in `androidMain`.
- **NFR-003**: Orchestrator `stop()` MUST disconnect on a detached scope to avoid cancellation of the drain delay.
- **NFR-004**: AIDL exceptions MUST be wrapped via `toRemoteExceptions` to prevent caller crashes.
- **NFR-005**: Service coroutine scope MUST use `SupervisorJob` for failure isolation.

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 5 files (~600 LOC) | Orchestrator, ServiceRepositoryImpl, SharedRadioInterfaceService, DirectRadioControllerImpl, DI |
| `commonTest` | 1 file (~100 LOC) | MeshServiceOrchestratorTest |
| `androidMain` | 33 files (~4,500 LOC) | MeshService, notifications, workers, receivers, location, file, DI |
| `androidHostTest` | 7 files (~600 LOC) | Service, notification, worker, location, file tests |
| `jvmMain` | 2 files (~100 LOC) | JVM location and file stubs |

## Privacy Assessment

- [x] Location data is handled by platform services — not logged or transmitted beyond the radio
- [x] Message content is not logged in service layer
- [x] Notification content uses user-visible names only (no raw node numbers)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Orchestrator `start()` correctly initializes DB and connects radio in test environment.
- **SC-002**: Orchestrator `stop()` cancels scope and disconnects within 200ms grace window.
- **SC-003**: `receivedData` is drained on `start()` — no stale packets from previous session.
- **SC-004**: Service actions are isolated — one action failure does not terminate the collector.
- **SC-005**: `ServiceRepositoryImpl` correctly emits all state transitions.
- **SC-006**: Android `MeshService` posts foreground notification on API 34+ with correct type.
- **SC-007**: AIDL contract tests pass (`IMeshServiceContractTest`).
- **SC-008**: All 8 existing test files pass.

## Assumptions

- Android is the primary platform; Desktop/iOS use the orchestrator directly.
- `MeshService` requires `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission on Android 14+.
- TAK server integration is optional (gated by `TakPrefs.isTakServerEnabled`).
- AIDL interface (`IMeshService`) is deprecated but still required for backward compatibility.
- `ServiceScope` provided by Koin is shared across the orchestrator's lifetime.

