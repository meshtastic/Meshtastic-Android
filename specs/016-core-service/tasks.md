# Tasks: Core Service (Mesh Service Bridge)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Status**: Migrated — all existing tasks marked complete. Gap tasks marked incomplete.  
**Task Prefix**: `SVC-T`

---

## Phase 1 — KMP Orchestrator

### SVC-T001: CoreServiceModule DI [x]

- **File**: `commonMain/.../di/CoreServiceModule.kt`
- `@ComponentScan("org.meshtastic.core.service")`.
- Provides `@Named("ServiceScope")` with `SupervisorJob + dispatchers.default`.
- **Test**: Module loads without error.

### SVC-T002: MeshServiceOrchestrator [x]

- **File**: `commonMain/.../MeshServiceOrchestrator.kt` (~165 LOC)
- `start()`: drain stale buffer, init DB, connect radio, wire `receivedData` → `messageProcessor`, wire `serviceAction` → `router.actionHandler`.
- `stop()`: stop TAK, disconnect on detached scope, cancel orchestrator scope.
- `isRunning`: scope?.isActive.
- TAK server reactive start/stop via `takPrefs.isTakServerEnabled`.
- **Test**: `MeshServiceOrchestratorTest.kt`.

### SVC-T003: ServiceRepositoryImpl [x]

- **File**: `commonMain/.../ServiceRepositoryImpl.kt` (~129 LOC)
- `connectionState: StateFlow<ConnectionState>`, `errorMessage`, `meshPacket`, `clientNotification`, `tracerouteResponse`, `serviceAction: Channel`.
- Platform-agnostic — no Android dependencies.
- **Test**: Verified via orchestrator test and `IMeshServiceContractTest`.

### SVC-T004: SharedRadioInterfaceService [x]

- **File**: `commonMain/.../SharedRadioInterfaceService.kt`
- Shared bridge between orchestrator and platform-specific radio interface.
- **Test**: Verified via integration.

### SVC-T005: DirectRadioControllerImpl [x]

- **File**: `commonMain/.../DirectRadioControllerImpl.kt`
- Direct radio control for KMP hosts (Desktop, iOS) that don't use AIDL.
- **Test**: Verified via Desktop app integration.

---

## Phase 2 — Android Service & Notifications

### SVC-T006: MeshService foreground service [x]

- **File**: `androidMain/.../MeshService.kt` (~406 LOC)
- Android `Service` with `startForeground()` on API 34+.
- AIDL `IMeshService.Stub` binder for external clients.
- Delegates lifecycle to `MeshServiceOrchestrator.start()/stop()`.
- `toRemoteExceptions` wrapper on all AIDL methods.
- **Test**: `IMeshServiceContractTest.kt`.

### SVC-T007: Android notification management [x]

- **Files**: `androidMain/.../AndroidNotificationManager.kt`, `MeshServiceNotificationsImpl.kt`, `NotificationChannels.kt`, `NotificationChannelMigration.kt`
- Notification channels: service, messages, alerts.
- Service notification with connection status.
- Message notifications with direct reply + reaction actions.
- Channel migration from legacy channel IDs.
- **Test**: `AndroidNotificationManagerTest.kt`, `MeshServiceNotificationsImplTest.kt`.

### SVC-T008: MeshServiceStarter + BootCompleteReceiver [x]

- **Files**: `androidMain/.../MeshServiceStarter.kt`, `BootCompleteReceiver.kt`
- `MeshServiceStarter`: helper for starting the foreground service.
- `BootCompleteReceiver`: auto-starts service on device boot.
- **Test**: Verified via Android integration.

### SVC-T009: AndroidServiceRepository [x]

- **File**: `androidMain/.../AndroidServiceRepository.kt`
- Extends `ServiceRepositoryImpl` with AIDL-specific binding logic.
- **Test**: Verified via `IMeshServiceContractTest.kt`.

### SVC-T010: ServiceClient + MeshServiceClient [x]

- **Files**: `androidMain/.../ServiceClient.kt`, `MeshServiceClient.kt`
- Client-side helpers for binding to `MeshService` and calling AIDL methods.
- **Test**: Verified via app integration.

### SVC-T011: ServiceBroadcasts [x]

- **File**: `androidMain/.../ServiceBroadcasts.kt`
- Android broadcast intents for mesh state changes.
- **Test**: `ServiceBroadcastsTest.kt`.

---

## Phase 3 — Workers & Receivers

### SVC-T012: SendMessageWorker [x]

- **File**: `androidMain/.../worker/SendMessageWorker.kt`
- WorkManager worker for reliable background message delivery.
- Retries with backoff on failure.
- **Test**: `SendMessageWorkerTest.kt`.

### SVC-T013: MeshLogCleanupWorker [x]

- **File**: `androidMain/.../worker/MeshLogCleanupWorker.kt`
- Periodic log pruning to prevent storage bloat.
- **Test**: Verified via Android integration.

### SVC-T014: ServiceKeepAliveWorker [x]

- **File**: `androidMain/.../worker/ServiceKeepAliveWorker.kt`
- Periodic worker to ensure service stays alive on aggressive OEMs.
- **Test**: Verified via Android integration.

### SVC-T015: Notification action receivers [x]

- **Files**: `androidMain/.../ReplyReceiver.kt`, `ReactionReceiver.kt`, `MarkAsReadReceiver.kt`
- Direct reply, emoji reaction, and mark-as-read from notification actions.
- **Test**: Verified via Android manual testing.

### SVC-T016: Android location service [x]

- **File**: `androidMain/.../AndroidLocationService.kt`
- GPS location provider for position reporting.
- **Test**: `AndroidLocationServiceTest.kt`.

### SVC-T017: AndroidMeshLocationManager [x]

- **File**: `androidMain/.../AndroidMeshLocationManager.kt`
- Manages mesh-based location updates and sharing.
- **Test**: Verified via integration.

### SVC-T018: AndroidFileService [x]

- **File**: `androidMain/.../AndroidFileService.kt`
- File I/O for data exports (CSV, JSON).
- **Test**: `AndroidFileServiceTest.kt`.

### SVC-T019: AndroidMeshWorkerManager [x]

- **File**: `androidMain/.../AndroidMeshWorkerManager.kt`
- Manages WorkManager worker scheduling and constraints.
- **Test**: Verified via integration.

### SVC-T020: JVM platform stubs [x]

- **Files**: `jvmMain/.../JvmLocationService.kt`, `JvmFileService.kt`
- Desktop stubs for location and file services.
- **Test**: Compilation verified.

---

## Gap Tasks (Incomplete)

### SVC-T021: Add ServiceRepositoryImpl unit tests [ ]

- **File to create**: `commonTest/.../ServiceRepositoryImplTest.kt`
- Test all state flow emissions: connection, errors, packets, actions, traceroute.
- **Priority**: Medium

### SVC-T022: Add DirectRadioControllerImpl tests [ ]

- **File to create**: `commonTest/.../DirectRadioControllerImplTest.kt`
- Test direct radio control operations (send, request config, disconnect).
- **Priority**: Medium

### SVC-T023: Add SharedRadioInterfaceService tests [ ]

- **File to create**: `commonTest/.../SharedRadioInterfaceServiceTest.kt`
- Test radio interface delegation and receivedData forwarding.
- **Priority**: Medium

### SVC-T024: Add TAK preference reactive test [ ]

- **File to extend**: `commonTest/.../MeshServiceOrchestratorTest.kt`
- Test that TAK integration starts/stops reactively on preference change.
- **Priority**: Low

