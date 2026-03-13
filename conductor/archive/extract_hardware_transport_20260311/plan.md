# Implementation Plan: Extract hardware/transport layers out of :app into dedicated :core modules

## Phase 1: Define Shared Interface and Extract Stream Framing [checkpoint: 80a39a5]
- [x] Task: Create `RadioTransport` interface in `core:repository/commonMain`. a47f399
    - [x] Write Tests
    - [x] Implement Feature
- [x] Task: Move `StreamFrameCodec` logic to `core:network/commonMain`. cc1ff26
    - [x] Write Tests
    - [x] Implement Feature
- [x] Task: Refactor existing `IRadioInterface` usages to point to the new `RadioTransport` interface (preparation step). 1b4cec6
    - [x] Write Tests
    - [x] Implement Feature
- [x] Task: Conductor - User Manual Verification 'Phase 1: Define Shared Interface and Extract Stream Framing' (Protocol in workflow.md) 80a39a5

## Phase 2: Extract Platform Transports
- [x] Task: Move TCP transport implementation to `core:network/jvmAndroidMain`. [8688070]
    - [x] Write Tests
    - [x] Implement Feature
- [x] Task: Move BLE transport implementation to `core:ble/androidMain`. [8688070]
    - [x] Write Tests
    - [x] Implement Feature
- [x] Task: Move Serial/USB transport implementation to `core:service/androidMain`. [8688070]
    - [x] Write Tests
    - [x] Implement Feature
- [x] Task: Conductor - User Manual Verification 'Phase 2: Extract Platform Transports' (Protocol in workflow.md) [checkpoint: 8688070]

## Phase 3: Desktop Unification and Cleanup
- [x] Task: Retire `DesktopRadioInterfaceService` in the `desktop` module.
    - [x] Write Tests
    - [x] Implement Feature
- [x] Task: Update the `desktop` DI graph to inject the shared `TcpTransport` implementation.
    - [x] Write Tests
    - [x] Implement Feature
- [x] Task: Delete the old `app/repository/radio/` directory.
    - [x] Write Tests
    - [x] Implement Feature
- [x] Task: Conductor - User Manual Verification 'Phase 3: Desktop Unification and Cleanup' (Protocol in workflow.md) [checkpoint: 8688070]