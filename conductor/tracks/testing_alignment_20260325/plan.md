# Implementation Plan: KMP Testing Best Practices & Coverage Alignment

## Phase 1: Foundation & core:testing Expansion
Standardize and expand the shared testing infrastructure.

- [x] Task: Audit `core:testing` for existing fakes and identify gaps based on common repository/manager interfaces. 1b9dfab
- [ ] Task: Implement `FakeNodeRepository` in `core:testing` (if missing or incomplete).
- [ ] Task: Implement `FakeMeshService` and `FakeRadioInterface` fakes in `core:testing`.
- [ ] Task: Implement `FakeSettingsRepository` and `FakeLocationRepository`.
- [ ] Task: Standardize `BaseFake` or similar utility for consistent state management in fakes.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Foundation & core:testing Expansion' (Protocol in workflow.md)

## Phase 2: core Modules Alignment
Migrate and improve coverage for core business logic modules.

- [ ] Task: Align `core:domain`: Migrate tests to `commonTest`, use fakes, target 80% coverage.
- [ ] Task: Align `core:repository`: Migrate tests to `commonTest`, target 80% coverage.
- [ ] Task: Align `core:data`: Migrate tests to `commonTest`, target 80% coverage.
- [ ] Task: Align `core:database` & `core:datastore`: Ensure Room/DataStore tests are in `commonTest` using KMP abstractions.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: core Modules Alignment' (Protocol in workflow.md)

## Phase 3: feature Modules Alignment
Focus on ViewModel logic and feature-specific business rules.

- [ ] Task: Align `feature:settings`: Migrate ViewModel tests to `commonTest`, use fakes, target 80% coverage.
- [ ] Task: Align `feature:node` & `feature:messaging`: Migrate tests to `commonTest`, target 80% coverage.
- [ ] Task: Align `feature:map` & `feature:connections`: Migrate tests to `commonTest`, target 80% coverage.
- [ ] Task: Align remaining `feature:*` modules.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: feature Modules Alignment' (Protocol in workflow.md)

## Phase 4: Network & Hardware Abstractions Alignment
Handle the more complex platform-dependent modules.

- [ ] Task: Align `core:network`: Migrate `StreamFrameCodec` and protocol logic tests to `commonTest`.
- [ ] Task: Align `core:ble`: Implement robust fakes for Kable/BLE interactions and migrate logic tests.
- [ ] Task: Align `core:common` utilities and I/O abstractions.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Network & Hardware Abstractions Alignment' (Protocol in workflow.md)

## Phase 5: Final Verification & Coverage Audit
Ensure the entire project meets the quality bar.

- [ ] Task: Run full project Kover report and address any remaining coverage gaps (<80%).
- [ ] Task: Verify all tests pass on both Android (`testFdroidDebug`) and JVM (`test`) targets.
- [ ] Task: Final documentation update for `core:testing` usage guidelines.
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Final Verification & Coverage Audit' (Protocol in workflow.md)