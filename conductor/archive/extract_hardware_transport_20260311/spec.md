# Track Specification: Extract hardware/transport layers out of :app into dedicated :core modules

## Overview
This track addresses a critical modularity gap identified in the KMP architecture review: the Radio interface layer is currently locked within the `app` module and is non-KMP. The goal is to define a shared `RadioTransport` interface in `core:repository` and fully extract all transport implementations (BLE, TCP, USB) from `app/repository/radio/` into their appropriate `core` modules.

## Functional Requirements
- **Define `RadioTransport` Interface:** Create a new `RadioTransport` interface in `core:repository/commonMain` to replace the existing `IRadioInterface`.
- **Extract Stream Framing:** Move `StreamFrameCodec`-based framing logic to `core:network/commonMain`.
- **Extract BLE Transport:** Move the BLE transport implementation (`NordicBleInterface`, etc.) to `core:ble/androidMain`.
- **Extract TCP Transport:** Move the TCP transport implementation to `core:network/jvmAndroidMain`.
- **Extract Serial/USB Transport:** Move the Serial/USB transport implementation to `core:service/androidMain`.
- **Unify Desktop Transport:** Retire Desktop's parallel `DesktopRadioInterfaceService` and migrate it to use the shared `RadioTransport` and `TcpTransport`.

## Acceptance Criteria
- [ ] A `RadioTransport` interface exists in `core:repository/commonMain`.
- [ ] No transport logic (BLE, TCP, USB) remains in `app/repository/radio/`.
- [ ] The `app` and `desktop` modules successfully compile and run using the extracted transport layers.
- [ ] The `desktop` module uses the shared `TcpTransport` implementation instead of its own duplicate logic.

## Out of Scope
- Rewriting the underlying logic of the transports (e.g., changing how Nordic BLE works). This is purely a structural extraction and KMP alignment.
- Extracting non-transport components (like the Connections UI) from the `app` module.