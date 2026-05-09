# Implementation Plan: Core Service (Mesh Service Bridge)

**Branch**: `016-core-service` | **Date**: 2026-07-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/016-core-service/spec.md`
**Status**: Migrated — all implementation complete, plan reverse-engineered from existing code.

## Summary

Core Service bridges the platform layer with the KMP data layer. The `MeshServiceOrchestrator` (165 LOC) manages the start/stop lifecycle in `commonMain`, while `MeshService` (406 LOC) provides the Android foreground service host. The module is heavily platform-split: 5 commonMain files vs 33 androidMain files.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Koin 4.2+, kotlinx.coroutines, Kermit, WorkManager (Android), AndroidX Core (Android)  
**Testing**: 1 commonTest + 7 androidHostTest files, ~700 LOC  
**Target Platform**: Android (primary), Desktop (JVM), iOS (future)  
**Constraints**: Orchestrator in `commonMain`; all Android service/worker/receiver code in `androidMain`  
**Scale/Scope**: 5 commonMain files (~600 LOC), 33 androidMain files (~4,500 LOC), 8 test files (~700 LOC)

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | Orchestrator in `commonMain`. Android code correctly in `androidMain`. |
| II. Zero Lint Tolerance | ✅ PASS | `detekt-baseline.xml` present. `@Suppress("LargeClass")` on `MeshService`. |
| VII. Coroutine Safety | ✅ PASS | SupervisorJob in orchestrator scope. `handledLaunch` for service actions. Detached scope for disconnect. |
| IX. Branch & Scope Hygiene | ✅ PASS | Module scoped to `org.meshtastic.core.service`. |

**Gate Result**: ✅ All applicable principles satisfied.

## Project Structure

```
core/service/src/
├── commonMain/ (5 files — orchestrator, service repo, radio interface, DI)
├── commonTest/ (1 file — MeshServiceOrchestratorTest)
├── androidMain/ (33 files — MeshService, notifications, workers, receivers, etc.)
├── androidHostTest/ (7 files — service, notification, worker, location, file tests)
└── jvmMain/ (2 files — JVM location + file stubs)
```

## Implementation Phases

### Phase 1 — KMP Orchestrator (Complete)

`MeshServiceOrchestrator` with `start()`/`stop()` lifecycle, `ServiceRepositoryImpl` reactive state, `CoreServiceModule` DI.

### Phase 2 — Android Service & Notifications (Complete)

`MeshService` foreground service, AIDL binder, `AndroidNotificationManager`, `MeshServiceNotificationsImpl`, notification channel migration.

### Phase 3 — Workers & Receivers (Complete)

`SendMessageWorker`, `MeshLogCleanupWorker`, `ServiceKeepAliveWorker`. Broadcast receivers: `BootCompleteReceiver`, `ReplyReceiver`, `ReactionReceiver`, `MarkAsReadReceiver`.

## Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Architecture | Orchestrator (common) + Service host (Android) | Enables Desktop/iOS to use the same lifecycle |
| Scope per start | Fresh `CoroutineScope` per `start()` | Clean teardown; no collector accumulation across cycles |
| Action isolation | `handledLaunch` per service action | One failed action doesn't terminate the action collector |
| Disconnect on stop | Detached scope with `SupervisorJob` | Allows disconnect drain without scope cancellation |
| Buffer drain | `resetReceivedBuffer()` on start | Prevents stale packet replay from previous session |
| TAK integration | Reactive on/off via preference Flow | No restart required to toggle TAK server |
| Notification type | `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Required by Android 14+ for BLE services |
| AIDL wrapping | `toRemoteExceptions` | Prevents caller crashes from unhandled exceptions |
| Background workers | WorkManager (Android only) | Reliable scheduling with system-managed lifecycle |

## Gaps Identified

| Gap | Severity | Recommendation |
|-----|----------|----------------|
| `DirectRadioControllerImpl` has no test | ⚠️ Medium | Add test for direct radio control operations |
| `SharedRadioInterfaceService` has no test | ⚠️ Medium | Add test for radio interface delegation |
| Only 1 commonTest for entire module | ⚠️ Medium | Add commonTest for `ServiceRepositoryImpl` state transitions |
| `AndroidServiceRepository` AIDL binding not unit tested | ⚠️ Low | AIDL binding validated via `IMeshServiceContractTest` |
| No test for TAK preference reactive start/stop | ⚠️ Low | Add orchestrator test with TAK pref flow |
| `NotificationChannelMigration` has no test | ⚠️ Low | Add test for channel migration logic |

