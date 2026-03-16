# Specification: Extract Remaining App-Only ViewModels

## Overview
This track aims to migrate the final 5 ViewModels currently trapped in the `app` module to their respective KMP `feature:*` or `core:*` modules. These ViewModels contain business logic that should be shared across platforms, but are currently coupled to Android-specific APIs.

## Functional Requirements
- **Isolate Dependencies:** Identify and abstract Android-specific APIs using a hybrid approach (expect/actual for low-level types and injected interfaces for services).
- **Relocate ViewModels:** Move the core logic of these ViewModels to `commonMain` in the target modules:
    - `SettingsViewModel` & `RadioConfigViewModel` -> `feature:settings`
    - `DebugViewModel` -> `feature:settings`
    - `MetricsViewModel` -> `feature:node`
    - `UIViewModel` logic -> `core:ui`
- **Dependency Injection:** Update Koin modules to provide platform-specific implementations of the abstracted interfaces.
- **Maintain Parity:** Ensure existing functionality is preserved on Android while enabling these features on Desktop.

## Acceptance Criteria
- All 5 ViewModels are extracted from the `app` module and logic resides in `commonMain`.
- `commonTest` coverage is established for the shared logic in each respective module.
- The `app` module file count is further reduced.
- Desktop target can instantiate and use the shared ViewModels.