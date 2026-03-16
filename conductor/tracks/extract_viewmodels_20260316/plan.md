# Implementation Plan: Extract Remaining App-Only ViewModels

## Phase 1: Infrastructure & Abstractions
- [x] Task: Implement `MeshtasticUri` (expect/actual wrapper for `android.net.Uri`) in `core:common`. 81e5a4a
- [x] Task: Define `FileService` and `LocationService` interfaces in `core:repository/commonMain`. 1ffa7d2
- [x] Task: Create Android implementations for these services in `core:service/androidMain`. 1ffa7d2
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Infrastructure & Abstractions' (Protocol in workflow.md)

## Phase 2: Feature Module Extractions (Settings & Node)
- [ ] Task: Extract `AndroidSettingsViewModel` & `AndroidRadioConfigViewModel` to `feature:settings/commonMain`.
- [ ] Task: Extract `AndroidMetricsViewModel` to `feature:node/commonMain`.
- [ ] Task: Extract `AndroidDebugViewModel` to `feature:settings/commonMain`.
- [ ] Task: Update Koin modules in `feature:settings` and `feature:node` to wire the new shared ViewModels.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Feature Module Extractions' (Protocol in workflow.md)

## Phase 3: Core UI & Cleanup
- [ ] Task: Extract `UIViewModel` logic to `core:ui/commonMain`.
- [ ] Task: Verify the `app` module thinning progress and finalize any remaining DI cleanup in `AppKoinModule`.
- [ ] Task: Ensure all new shared ViewModels have baseline `commonTest` coverage using `core:testing` fakes.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Core UI & Cleanup' (Protocol in workflow.md)