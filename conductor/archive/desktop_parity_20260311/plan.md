# Implementation Plan

## Phase 1: Navigation Parity [checkpoint: 5b8e194]
- [x] Task: Extract shared navigation contracts f7e0c2e
    - [x] Define shared top-level destinations and route metadata in `core:navigation`.
    - [x] Update Android `TopLevelDestination` to use the shared contract.
    - [x] Update Desktop `DesktopDestination` to use the shared contract.
    - [x] Add parity tests for navigation routing.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Navigation Parity' (Protocol in workflow.md)

## Phase 2: DI Parity [checkpoint: 5bdc099]
- [x] Task: Migrate Desktop Koin Modules 93fd600
    - [x] Configure KSP for the JVM target in necessary modules.
    - [x] Ensure Koin annotations are processed for Desktop.
    - [x] Replace manual ViewModel wiring in `DesktopKoinModule` with generated modules.
- [x] Task: Conductor - User Manual Verification 'Phase 2: DI Parity' (Protocol in workflow.md)

## Phase 3: Connections Parity [checkpoint: 4be5732]
- [x] Task: Create `feature:connections` module 242faa6
    - [x] Set up the KMP module structure with `commonMain`, `androidMain`, and `jvmMain` (or `desktopMain`).
    - [x] Move device discovery UI and ViewModels from `app` and `desktop` into the new module.
    - [x] Consolidate the Connections UI into a shared screen in `feature:connections`.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Connections Parity' (Protocol in workflow.md)

## Phase 4: UI/Feature Parity [checkpoint: e83a07a]
- [x] Task: Implement missing Map and Chart features on Desktop 128ee3b
    - [x] Evaluate and implement a KMP-friendly mapping library or placeholder for Desktop.
    - [x] Refactor Vico charts or provide a KMP charting alternative/placeholder for Desktop.
- [x] Task: Refinement - Connections UI and Messaging Parity c98db4f
    - [x] Hide unsupported transports (BLE/USB) on Desktop via BuildUtils proxy.
    - [x] Update message titles to resolve channel names for broadcasts.
    - [x] Add snackbar for no-op gaps (delivery info).
    - [x] Shared AnimatedConnectionsNavIcon for "blinky light" parity.
    - *Note: Connection type filtering is currently hardcoded via BuildUtils.sdkInt. This should be refactored to use dynamic transport discovery once the 'Extract hardware transport' track is complete.*
- [x] Task: Conductor - User Manual Verification 'Phase 4: UI/Feature Parity' (Protocol in workflow.md) e83a07a

## Phase 5: Multi-Target Hardening [checkpoint: 91784a9]
- [x] Task: Clean up remaining platform-specific leaks f5f1e29
    - [x] Ensure `commonMain` is free of any `java.*` dependencies.
    - [x] Verify test suite passes on both Android and Desktop JVM targets.
- [x] Task: Conductor - User Manual Verification 'Phase 5: Multi-Target Hardening' (Protocol in workflow.md) 91784a9