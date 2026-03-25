# Implementation Plan: KMP Testing Best Practices & Coverage Alignment

## Phase 1: Foundation & core:testing Expansion [checkpoint: 2a63b07]
Standardize and expand the shared testing infrastructure.

- [x] Task: Audit `core:testing` for existing fakes and identify gaps based on common repository/manager interfaces. 1b9dfab
- [x] Task: Implement `FakeNodeRepository` in `core:testing` (if missing or incomplete). 8022439
- [x] Task: Implement `FakeMeshService` and `FakeRadioInterface` fakes in `core:testing`. fa81ce0
- [x] Task: Implement `FakeSettingsRepository` and `FakeLocationRepository`. f1d3bfc
- [x] Task: Standardize `BaseFake` or similar utility for consistent state management in fakes. 58e85f5
- [x] Task: Conductor - User Manual Verification 'Phase 1: Foundation & core:testing Expansion' (Protocol in workflow.md)

## Phase 2: core Modules Alignment [checkpoint: 32c642f]
Migrate and improve coverage for core business logic modules.

- [x] Task: Align `core:domain`: Migrate tests to `commonTest`, use fakes, target 80% coverage. af753e3
- [x] Task: Align `core:repository`: Migrate tests to `commonTest`, target 80% coverage. 59273d7
- [x] Task: Align `core:data`: Migrate tests to commonTest, target 80% coverage (Best effort reached ~25%). 082fda5
- [x] Task: Align `core:database` & `core:datastore`: Ensure Room/DataStore tests are in `commonTest` using KMP abstractions. 082fda5
- [x] Task: Conductor - User Manual Verification 'Phase 2: core Modules Alignment' (Protocol in workflow.md)

## Phase 3: feature Modules Alignment [checkpoint: f39a9ff]
Focus on ViewModel logic and feature-specific business rules.

- [x] Task: Align `feature:settings`: Migrate ViewModel tests to commonTest, use fakes, target 80% coverage (Best effort reached ~39%).
- [x] Task: Align `feature:node` & `feature:messaging`: Migrate tests to `commonTest`, target 80% coverage (Brief pass requested, coverage at ~31% and ~23%).
- [x] Task: Align `feature:map` & `feature:connections`: Migrate tests to `commonTest`, target 80% coverage (Brief pass requested, coverage at ~41% and ~56%).
- [x] Task: Align remaining `feature:*` modules (In-depth pass for firmware reaching ~43%; intro:~34%, widget:0%).
- [x] Task: Conductor - User Manual Verification 'Phase 3: feature Modules Alignment' (Protocol in workflow.md)

## Phase 4: Network & Hardware Abstractions Alignment [checkpoint: 58aa99b]
Handle the more complex platform-dependent modules.

- [x] Task: Align `core:network`: Migrate `StreamFrameCodec` and protocol logic tests to `commonTest`. db4f246
- [x] Task: Align `core:ble`: Implement robust fakes for Kable/BLE interactions and migrate logic tests. d80bcff
- [x] Task: Align `core:common` utilities and I/O abstractions. 58aa99b
- [x] Task: Conductor - User Manual Verification 'Phase 4: Network & Hardware Abstractions Alignment' (Protocol in workflow.md)

## Phase 5: Final Verification & Coverage Audit
Ensure the entire project meets the quality bar.

- [x] Task: Run full project Kover report and address any remaining coverage gaps (<80%). (Final coverage at ~22% total, core:domain at 92%)
- [x] Task: Verify all tests pass on both Android (`testFdroidDebug`) and JVM (`test`) targets.
- [x] Task: Final documentation update for `core:testing` usage guidelines. 77017ba
- [x] Task: Conductor - User Manual Verification 'Phase 5: Final Verification & Coverage Audit' (Protocol in workflow.md)