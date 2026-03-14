# Implementation Plan: Desktop BLE Enablement via Kable

## Phase 1: Define `MeshtasticRadioProfile` Abstraction [checkpoint: 1206e87]
- [x] Task: Define `MeshtasticRadioProfile` interface in `core:ble/commonMain` eaa623a
    - [ ] Write tests for expected profile behavior (e.g., state flow emission) using a simple fake
    - [ ] Implement `MeshtasticRadioProfile` interface, data classes for states, and configuration
- [x] Task: Conductor - User Manual Verification 'Phase 1: Define `MeshtasticRadioProfile` Abstraction' (Protocol in workflow.md) 1206e87

## Phase 2: Refactor Nordic Implementation to use Abstraction [checkpoint: dc700a5]
- [x] Task: Implement `MeshtasticRadioProfile` in the existing Nordic implementation (`androidMain`) 83a8a9b
    - [ ] Write/adapt existing Android tests to verify `MeshtasticRadioProfile` adherence
    - [ ] Implement wrapper/adapter for Nordic classes to fulfill `MeshtasticRadioProfile`
- [x] Task: Decouple app-level BLE transport from Nordic types 2dfedde
    - [ ] Write tests to ensure BLE transport only relies on `MeshtasticRadioProfile`
    - [ ] Refactor transport layer (e.g., `NordicBleInterface` usages) to use the new profile interface
- [x] Task: Conductor - User Manual Verification 'Phase 2: Refactor Nordic Implementation to use Abstraction' (Protocol in workflow.md) dc700a5

## Phase 3: Implement Kable Backend for Desktop [checkpoint: ed2a459]
- [x] Task: Setup Kable dependencies for `jvmMain` in `core:ble` b152eff
    - [ ] Update `build.gradle.kts` to include Kable dependency for Desktop
- [x] Task: Implement Kable `MeshtasticRadioProfile` backend (`jvmMain`) fa5cc82
    - [ ] Write `commonMain` unit tests with Kable fakes to verify scanning, connection, and read/write operations
    - [ ] Implement Kable scanning logic
    - [ ] Implement Kable connection and characteristic management
    - [ ] Implement Kable read/write data transfer logic
- [x] Task: Conductor - User Manual Verification 'Phase 3: Implement Kable Backend for Desktop' (Protocol in workflow.md) ed2a459

## Phase 4: Integration and Final Testing [checkpoint: af6d3b3]
- [x] Task: Integrate Kable backend into Desktop app DI graph 28afcad
    - [ ] Wire up the Kable implementation in `desktop` module DI
- [x] Task: End-to-end verification 84aae75
    - [ ] Verify Android app still compiles and connects using Nordic
    - [ ] Verify Desktop app compiles and connects using Kable
- [x] Task: Conductor - User Manual Verification 'Phase 4: Integration and Final Testing' (Protocol in workflow.md) af6d3b3

## Phase: Review Fixes
- [x] Task: Apply review suggestions b36da82
