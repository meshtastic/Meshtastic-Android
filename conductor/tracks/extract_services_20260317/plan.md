# Implementation Plan: Extract service/worker/radio files from `app`

## Phase 1: Preparation & Analysis [checkpoint: 72022ed]
- [x] Task: Identify all Android-specific classes to be moved (Services, WorkManager workers, Radio connections in `app`) [fd916e3]
    - [ ] Locate `Service` classes in `app/src/main/java/org/meshtastic/app`
    - [ ] Locate WorkManager `Worker` classes
    - [ ] Locate Radio connection classes
- [x] Task: Conductor - User Manual Verification 'Preparation & Analysis' (Protocol in workflow.md)

## Phase 2: Extraction to `core:service` [checkpoint: ff47af8]
- [x] Task: Setup `core:service` module for Android and Common targets (if not already fully configured) [a114084]
- [x] Task: Move Android `Service` implementations to `core:service/androidMain` [965def0]
    - [x] Move the files
    - [x] Update imports and Koin injections
- [x] Task: Abstract shared service logic into `core:service/commonMain` [a85e282]
    - [x] Write failing tests for abstracted shared logic (TDD Red)
    - [x] Extract interfaces and platform-agnostic logic (TDD Green)
    - [x] Refactor the implementations to use these shared abstractions
- [x] Task: Conductor - User Manual Verification 'Extraction to core:service' (Protocol in workflow.md)

## Phase 3: Extraction to `core:network`
- [~] Task: Move Radio connection and networking files from `app` to `core:network/androidMain`
    - [x] Move the files
    - [x] Update imports and Koin injections
- [ ] Task: Abstract shared radio/network logic into `core:network/commonMain`
    - [ ] Write failing tests for abstracted radio logic (TDD Red)
    - [ ] Extract platform-agnostic business logic (TDD Green)
    - [ ] Refactor implementations to use shared abstractions
- [ ] Task: Conductor - User Manual Verification 'Extraction to core:network' (Protocol in workflow.md)

## Phase 4: Desktop Integration
- [ ] Task: Integrate newly extracted shared abstractions into the `desktop` module
    - [ ] Implement desktop-specific actuals or Koin bindings for the shared interfaces
    - [ ] Wire up abstracted services/radio logic in desktop Koin graph
- [ ] Task: Conductor - User Manual Verification 'Desktop Integration' (Protocol in workflow.md)

## Phase 5: Verification & Cleanup
- [ ] Task: Build project and verify no regressions in background processing or radio connectivity
- [ ] Task: Verify test coverage (>80%) for all extracted and refactored code
- [ ] Task: Remove any lingering unused dependencies or dead code in `app`
- [ ] Task: Conductor - User Manual Verification 'Verification & Cleanup' (Protocol in workflow.md)