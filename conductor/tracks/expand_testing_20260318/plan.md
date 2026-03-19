# Implementation Plan: Expand Testing Coverage

## Phase 1: Baseline Measurement [checkpoint: 6d9ad46]
- [x] Task: Execute `./gradlew koverLog` and record current project test coverage. 8bdd673a1
- [x] Task: Conductor - User Manual Verification 'Phase 1: Baseline Measurement' (Protocol in workflow.md) 6d9ad468c

## Phase 2: Feature ViewModel Migration to Turbine [checkpoint: 61b9595]
- [x] Task: Refactor `MetricsViewModelTest` to use `Turbine` and `Mokkery` in `commonTest`. 79e059286
- [x] Task: Refactor `MessageViewModelTest` to use `Turbine` and `Mokkery` in `commonTest`. b45697b53
- [x] Task: Refactor `RadioConfigViewModelTest` to use `Turbine` and `Mokkery` in `commonTest`. 33e10fc6c
- [x] Task: Refactor `NodeListViewModelTest` to use `Turbine` and `Mokkery` in `commonTest`. 33e10fc6c
- [x] Task: Refactor remaining `feature` ViewModels to use `Turbine` and `Mokkery`. 33e10fc6c
- [x] Task: Conductor - User Manual Verification 'Phase 2: Feature ViewModel Migration to Turbine' (Protocol in workflow.md) 61b959506

## Phase 3: Property-Based Parsing Tests (Kotest) [checkpoint: cb71c85]
- [x] Task: Add `Kotest` property-based tests for `StreamFrameCodec` in `core:network`. 2c8fd6a8f
- [x] Task: Add `Kotest` property-based tests for `PacketHandler` implementations in `core:data`. 7d56c3fef
- [x] Task: Add `Kotest` property-based tests for `TcpTransport` and/or `SerialTransport` in `core:network`. 2fd68d67e
- [x] Task: Conductor - User Manual Verification 'Phase 3: Property-Based Parsing Tests (Kotest)' (Protocol in workflow.md) cb71c8588

## Phase 4: Domain Logic Gap Fill
- [x] Task: Identify and fill testing gaps in `core:domain` use cases not fully covered during the initial Mokkery migration. 7b815130f
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Domain Logic Gap Fill' (Protocol in workflow.md)

## Phase 5: Final Measurement & Verification
- [ ] Task: Execute full test suite (`./gradlew test`) to ensure stability.
- [ ] Task: Execute `./gradlew koverLog` to generate and document the final coverage metrics.
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Final Measurement & Verification' (Protocol in workflow.md)

## Phase 6: Documentation and Wrap-up
- [ ] Task: Review previous steps and update project documentation (e.g., `README.md`, testing guides).
- [ ] Task: Conductor - User Manual Verification 'Phase 6: Documentation and Wrap-up' (Protocol in workflow.md)