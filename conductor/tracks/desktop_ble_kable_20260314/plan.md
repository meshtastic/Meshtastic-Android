# Implementation Plan: Desktop BLE Enablement via Kable

## Phase 1: Define `MeshtasticRadioProfile` Abstraction
- [ ] Task: Define `MeshtasticRadioProfile` interface in `core:ble/commonMain`
    - [ ] Write tests for expected profile behavior (e.g., state flow emission) using a simple fake
    - [ ] Implement `MeshtasticRadioProfile` interface, data classes for states, and configuration
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Define `MeshtasticRadioProfile` Abstraction' (Protocol in workflow.md)

## Phase 2: Refactor Nordic Implementation to use Abstraction
- [ ] Task: Implement `MeshtasticRadioProfile` in the existing Nordic implementation (`androidMain`)
    - [ ] Write/adapt existing Android tests to verify `MeshtasticRadioProfile` adherence
    - [ ] Implement wrapper/adapter for Nordic classes to fulfill `MeshtasticRadioProfile`
- [ ] Task: Decouple app-level BLE transport from Nordic types
    - [ ] Write tests to ensure BLE transport only relies on `MeshtasticRadioProfile`
    - [ ] Refactor transport layer (e.g., `NordicBleInterface` usages) to use the new profile interface
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Refactor Nordic Implementation to use Abstraction' (Protocol in workflow.md)

## Phase 3: Implement Kable Backend for Desktop
- [ ] Task: Setup Kable dependencies for `jvmMain` in `core:ble`
    - [ ] Update `build.gradle.kts` to include Kable dependency for Desktop
- [ ] Task: Implement Kable `MeshtasticRadioProfile` backend (`jvmMain`)
    - [ ] Write `commonMain` unit tests with Kable fakes to verify scanning, connection, and read/write operations
    - [ ] Implement Kable scanning logic
    - [ ] Implement Kable connection and characteristic management
    - [ ] Implement Kable read/write data transfer logic
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Implement Kable Backend for Desktop' (Protocol in workflow.md)

## Phase 4: Integration and Final Testing
- [ ] Task: Integrate Kable backend into Desktop app DI graph
    - [ ] Wire up the Kable implementation in `desktop` module DI
- [ ] Task: End-to-end verification
    - [ ] Verify Android app still compiles and connects using Nordic
    - [ ] Verify Desktop app compiles and connects using Kable
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Integration and Final Testing' (Protocol in workflow.md)