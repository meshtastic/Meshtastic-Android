# Specification: Extract remaining background services and workers from app module

## Overview
The primary goal of this track is to continue the app module thinning effort by extracting the remaining Android-specific background services, workers, and widgets from the `app` module into appropriate core or feature modules. Where possible, business logic from these components should be abstracted and moved to `commonMain` to support KMP targets. This will leave the app module as a thin entry point shell.

## Functional Requirements
- **Core Services:** Extract `AndroidMeshLocationManager.kt` and `MeshServiceClient.kt` to `core:service/androidMain`. Refactor underlying logic to `core:service/commonMain` where applicable.
- **Messaging Workers:** Extract `WorkManagerMessageQueue.kt` to `feature:messaging/androidMain`. Analyze logic for potential `commonMain` abstraction.
- **Widgets:** Extract the `LocalStatsWidget` implementation to a new or existing appropriate feature module (e.g. `feature:widget/androidMain`) following KMP feature module conventions.
- **Dependency Injection:** Update the DI graph (`MainKoinModule.kt` / `AppKoinModule.kt`) to resolve these implementations from their new module locations using Koin compiler plugin annotations where applicable.

## Non-Functional Requirements
- **Testability:** Existing tests related to these services and workers should pass after relocation.
- **Maintainability:** The extraction must preserve all existing app functionality, including background synchronization, location tracking, and widget updates.

## Acceptance Criteria
- [ ] `AndroidMeshLocationManager.kt` and `MeshServiceClient.kt` are successfully moved to `core:service`.
- [ ] `WorkManagerMessageQueue.kt` is successfully moved to `feature:messaging`.
- [ ] App Widgets are extracted out of the `app` module into an appropriate feature module.
- [ ] Any logic that can be abstracted to `commonMain` has been extracted and shared.
- [ ] `MainKoinModule.kt` is refactored, and DI wires everything correctly.
- [ ] The Android app compiles and runs successfully, with background tasks and widgets working identically to the previous implementation.