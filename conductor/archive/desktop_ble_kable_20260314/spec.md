# Specification: Desktop BLE Enablement via Kable

## Overview
This track introduces a Kable BLE backend specifically for the `jvmMain` (Desktop) target within `core:ble`. To facilitate this without breaking the existing Android implementation, we will introduce a `MeshtasticRadioProfile` abstraction in `core:ble/commonMain`. This abstraction will ensure that the app-level BLE transport path no longer depends on Android-specific or Nordic-specific classes. Initially, Android will continue to use the Nordic BLE implementation, while Desktop will use Kable. Once this seam is proven, a future decision will determine whether Android should fully migrate to Kable. This approach lays the groundwork for seamless integration of future targets (e.g., iOS) under the same KMP abstraction.

## Functional Requirements
- **MeshtasticRadioProfile Abstraction:** Introduce a multiplatform interface (`MeshtasticRadioProfile`) in `core:ble/commonMain` to abstract all BLE operations.
- **Remove Nordic Dependencies:** Ensure that the app-level BLE transport path is entirely decoupled from Nordic types, relying solely on the new abstraction.
- **Kable Backend (jvmMain):** Implement the Kable backend for the Desktop target. This backend must support all core BLE operations:
  - Scanning for nearby Meshtastic devices.
  - Establishing and managing BLE connections.
  - Reading from and writing to characteristics (sending/receiving protobuf payloads).
- **Nordic Backend Preservation (androidMain):** Update the existing Android Nordic implementation to implement the new `MeshtasticRadioProfile` interface without changing its core behavior.
- **Future-Proofing:** Design the abstraction in a way that is generic enough to support adding an iOS or other future target's BLE implementation with minimal refactoring.

## Non-Functional Requirements
- **Testing:** New `commonMain` unit tests must be written utilizing fakes for the Kable implementation. This is crucial as we cannot rely on Nordic's ready-made mocks in a multiplatform context or if a full migration to Kable occurs.
- **Architecture:** The abstraction must adhere to the project's KMP goals, keeping `core:ble/commonMain` completely free of platform-specific imports (e.g., `java.*`, `android.*`).
- **Compatibility:** The Android build and BLE functionality must remain fully functional using the existing Nordic library.

## Acceptance Criteria
- [ ] `MeshtasticRadioProfile` is defined in `core:ble/commonMain`.
- [ ] No Nordic-specific or Android-specific types are present in the app-level BLE transport path.
- [ ] Desktop application can successfully scan, connect, and perform read/write operations with a Meshtastic device using Kable.
- [ ] Android application continues to function normally using the Nordic library.
- [ ] New unit tests using Kable fakes are added to `commonMain` and pass successfully.
- [ ] The abstraction architecture provides a clear path for future platform support (like iOS).

## Out of Scope
- Migrating the Android application to use the Kable backend (this will be evaluated after this track is complete).
- Modifying non-BLE network transports (e.g., USB, TCP).