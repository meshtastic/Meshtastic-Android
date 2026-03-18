# Implementation Plan: Extract remaining background services and workers from app module

## Phase 1: Preparation & Location Manager Abstraction [checkpoint: 57052fc]
- [x] Task: Review current implementations in `app/src/main/kotlin/org/meshtastic/app/service/AndroidMeshLocationManager.kt` and `app/src/main/kotlin/org/meshtastic/app/MeshServiceClient.kt`.
- [x] Task: Create KMP shared interface or base class in `core:service/commonMain` for the Location Manager if applicable, aligning with KMP best practices.
- [x] Task: Relocate `AndroidMeshLocationManager.kt` and `MeshServiceClient.kt` to `core:service/src/androidMain/...`.
- [x] Task: Update package declarations and resolve broken imports in the app module.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Preparation & Location Manager Abstraction' (Protocol in workflow.md)

## Phase 2: Message Queue Abstraction [checkpoint: dda10b4]
- [x] Task: Review `app/src/main/kotlin/org/meshtastic/app/messaging/domain/worker/WorkManagerMessageQueue.kt`.
- [x] Task: Identify opportunities to extract non-Android specific queue logic to `feature:messaging/commonMain`.
- [x] Task: Relocate `WorkManagerMessageQueue.kt` to `feature:messaging/src/androidMain/...`.
- [x] Task: Update package declarations and resolve broken imports.
- [x] Task: Conductor - User Manual Verification 'Phase 2: Message Queue Abstraction' (Protocol in workflow.md)

## Phase 3: Widget Extraction
- [~] Task: Review the contents of `app/src/main/kotlin/org/meshtastic/app/widget/`.
- [ ] Task: Decide whether to move widgets to an existing module (e.g. `core:ui` or `feature:node`) or create a new `feature:widget` module.
- [ ] Task: Relocate `LocalStatsWidget.kt`, `LocalStatsWidgetReceiver.kt`, `LocalStatsWidgetState.kt`, `RefreshLocalStatsAction.kt`, and `AndroidAppWidgetUpdater.kt`.
- [ ] Task: Relocate necessary widget resources, strings, and AndroidManifest declarations.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Widget Extraction' (Protocol in workflow.md)

## Phase 4: Dependency Injection Refactoring
- [ ] Task: Review `app/src/main/kotlin/org/meshtastic/app/MainKoinModule.kt` and `di/AppKoinModule.kt`.
- [ ] Task: Move DI bindings for the relocated classes to their new respective modules (e.g., `ServiceKoinModule`, `MessagingKoinModule`).
- [ ] Task: Ensure the root app module's DI configuration successfully includes the feature and core Koin modules.
- [ ] Task: Run Android instrumented/unit tests to verify graph compilation.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Dependency Injection Refactoring' (Protocol in workflow.md)