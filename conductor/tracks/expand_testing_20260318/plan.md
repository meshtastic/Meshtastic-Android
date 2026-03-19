# Implementation Plan: Expand Testing Coverage

## Phase 1: Baseline Measurement
- [x] Task: Execute `./gradlew koverLog` and record current project test coverage. 8bdd673a1
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Baseline Measurement' (Protocol in workflow.md)

## Phase 2: Feature ViewModel Migration to Turbine
- [ ] Task: Refactor `MetricsViewModelTest` to use `Turbine` and `Mokkery` in `commonTest`.
- [ ] Task: Refactor `MessageViewModelTest` to use `Turbine` and `Mokkery` in `commonTest`.
- [ ] Task: Refactor `RadioConfigViewModelTest` to use `Turbine` and `Mokkery` in `commonTest`.
- [ ] Task: Refactor `NodeListViewModelTest` to use `Turbine` and `Mokkery` in `commonTest`.
- [ ] Task: Refactor remaining `feature` ViewModels to use `Turbine` and `Mokkery`.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Feature ViewModel Migration to Turbine' (Protocol in workflow.md)

## Phase 3: Property-Based Parsing Tests (Kotest)
- [ ] Task: Add `Kotest` property-based tests for `StreamFrameCodec` in `core:network`.
- [ ] Task: Add `Kotest` property-based tests for `PacketHandler` implementations in `core:data`.
- [ ] Task: Add `Kotest` property-based tests for `TcpTransport` and/or `SerialTransport` in `core:network`.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Property-Based Parsing Tests (Kotest)' (Protocol in workflow.md)

## Phase 4: Domain Logic Gap Fill
- [ ] Task: Identify and fill testing gaps in `core:domain` use cases not fully covered during the initial Mokkery migration.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Domain Logic Gap Fill' (Protocol in workflow.md)

## Phase 5: Final Measurement & Verification
- [ ] Task: Execute full test suite (`./gradlew test`) to ensure stability.
- [ ] Task: Execute `./gradlew koverLog` to generate and document the final coverage metrics.
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Final Measurement & Verification' (Protocol in workflow.md)

## Phase 6: Documentation and Wrap-up
- [ ] Task: Review previous steps and update project documentation (e.g., `README.md`, testing guides).
- [ ] Task: Conductor - User Manual Verification 'Phase 6: Documentation and Wrap-up' (Protocol in workflow.md)