# Implementation Plan - Extract RadioInterfaceService to KMP

## Phase 1: Research & Abstraction
- [ ] Review `AndroidRadioInterfaceService` and `DesktopRadioInterfaceService` to identify identical connection loop logic.
- [ ] Identify platform-specific dependencies in both implementations (e.g., Android `BluetoothDevice`, notifications).
- [ ] Define shared abstractions (e.g., `TransportFactory`, `NotificationDelegate`) if needed to decouple platform-specific side effects.

## Phase 2: Logic Extraction
- [ ] Create `SharedRadioInterfaceService` in `core:service/commonMain`.
- [ ] Move the core connection loop, state management, and retry logic into the shared service.
- [ ] Adapt Android and Desktop to use the new shared service.

## Phase 3: Cleanup & Wiring
- [ ] Remove `DesktopRadioInterfaceService`.
- [ ] Refactor or remove `AndroidRadioInterfaceService` if entirely superseded.
- [ ] Update Koin DI graph in `core:service/commonMain` to provide the unified service.

## Phase 4: Verification
- [ ] Verify that `core:service` and `:app` compile cleanly for Android and Desktop.
- [ ] Write or update unit tests in `commonTest` to cover the shared connection lifecycle logic.
