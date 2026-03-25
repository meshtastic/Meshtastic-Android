# Implementation Plan: KMP Testing Best Practices & Coverage Alignment

## Phase 1: Foundation & core:testing Expansion [checkpoint: 2a63b07]
Standardize and expand the shared testing infrastructure.

- [x] Task: Audit `core:testing` for existing fakes and identify gaps based on common repository/manager interfaces. 1b9dfab
- [x] Task: Implement `FakeNodeRepository` in `core:testing` (if missing or incomplete). 8022439
- [x] Task: Implement `FakeMeshService` and `FakeRadioInterface` fakes in `core:testing`. fa81ce0
- [x] Task: Implement `FakeSettingsRepository` and `FakeLocationRepository`. f1d3bfc
- [x] Task: Standardize `BaseFake` or similar utility for consistent state management in fakes. 58e85f5
- [x] Task: Conductor - User Manual Verification 'Phase 1: Foundation & core:testing Expansion' (Protocol in workflow.md)

## Phase 2: core Modules Alignment
Migrate and improve coverage for core business logic modules.

- [x] Task: Align `core:domain`: Migrate tests to `commonTest`, use fakes, target 80% coverage. af753e3
- [x] Task: Align `core:repository`: Migrate tests to `commonTest`, target 80% coverage. 59273d7
- [~] Task: Align `core:data`: Migrate tests to `commonTest`, target 80% coverage (Best effort reached ~25%). 082fda5
- [x] Task: Align `core:database` & `core:datastore`: Ensure Room/DataStore tests are in `commonTest` using KMP abstractions. 082fda5
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