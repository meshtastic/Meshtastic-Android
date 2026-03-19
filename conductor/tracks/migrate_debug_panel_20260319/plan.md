# Implementation Plan: Debug Panel KMP Migration

## Phase 1: Analysis and Relocation [checkpoint: a2e83eb]
- [x] Task: Locate all source files for the Android Debug Panel (UI, ViewModels, States).
- [x] Task: Move these files from the Android-specific source sets (e.g., `feature/settings/src/androidMain`) into `feature/settings/src/commonMain`.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Analysis and Relocation' (Protocol in workflow.md)

## Phase 2: Adaptation to KMP [checkpoint: 834f42c]
- [x] Task: Resolve compilation errors by removing Android-specific imports (`android.*`, `java.*`).
- [x] Task: Migrate Android Jetpack Compose imports (`androidx.compose`) to Compose Multiplatform equivalents (`org.jetbrains.compose.*` or ensuring the standard Multiplatform aliases are used).
- [x] Task: Ensure the Debug Panel ViewModel uses the multiplatform `androidx.lifecycle.ViewModel`.
- [x] Task: Abstract any necessary platform-specific logging or hardware interactions using `expect`/`actual` or KMP interfaces.
- [x] Task: Write or migrate corresponding unit tests to `commonTest`.
- [x] Task: Conductor - User Manual Verification 'Phase 2: Adaptation to KMP' (Protocol in workflow.md)

## Phase 3: Desktop Integration [checkpoint: de2ae06]
- [x] Task: Wire the Debug Panel into the Desktop target's settings menu (`DesktopSettingsNavigation.kt`).
- [x] Task: Add DI bindings for the Desktop module if the Debug Panel requires specific dependencies.
- [x] Task: Verify the Debug Panel screen can be opened and navigated to from the Desktop app.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Desktop Integration' (Protocol in workflow.md)