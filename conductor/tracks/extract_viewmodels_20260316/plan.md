# Implementation Plan: Extract Remaining App-Only ViewModels

## Phase 1: Infrastructure & Abstractions [checkpoint: 89c6fd5]
- [x] Task: Implement `MeshtasticUri` (expect/actual wrapper for `android.net.Uri`) in `core:common`. 81e5a4a
- [x] Task: Define `FileService` and `LocationService` interfaces in `core:repository/commonMain`. 1ffa7d2
- [x] Task: Create Android implementations for these services in `core:service/androidMain`. 1ffa7d2
- [x] Task: Conductor - User Manual Verification 'Phase 1: Infrastructure & Abstractions' (Protocol in workflow.md) 89c6fd5

## Phase 2: Feature Module Extractions (Settings & Node) [checkpoint: 3ea2b2a]
- [x] Task: Extract `AndroidSettingsViewModel` & `AndroidRadioConfigViewModel` to `feature:settings/commonMain`. 091452a
- [x] Task: Extract `AndroidMetricsViewModel` to `feature:node/commonMain`. 52c2f6e
- [x] Task: Extract `AndroidDebugViewModel` to `feature:settings/commonMain`. e1a0387
- [x] Task: Update Koin modules in `feature:settings` and `feature:node` to wire the new shared ViewModels. (Handled automatically by Koin Annotations K2 plugin) e1a0387
- [x] Task: Conductor - User Manual Verification 'Phase 2: Feature Module Extractions' (Protocol in workflow.md) 3ea2b2a

## Phase 3: Core UI & Cleanup [checkpoint: c59243d]
- [x] Task: Extract `UIViewModel` logic to `core:ui/commonMain`. 3ea2b2a
- [x] Task: Verify the `app` module thinning progress and finalize any remaining DI cleanup in `AppKoinModule`. 3ea2b2a
- [x] Task: Ensure all new shared ViewModels have baseline `commonTest` coverage using `core:testing` fakes. fdf34f5
- [x] Task: Conductor - User Manual Verification 'Phase 3: Core UI & Cleanup' (Protocol in workflow.md) c59243d