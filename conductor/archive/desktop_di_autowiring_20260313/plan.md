# Implementation Plan: Desktop DI Auto-Wiring and Validation

## Phase 1: Setup KSP for Desktop and Test Scaffolding
- [x] Task: Update the `meshtastic.koin` convention plugin (or equivalent `build-logic` files) to apply KSP to the `jvmMain` (Desktop) target for `@KoinViewModel` auto-wiring.
- [x] Task: Write Failing Test: Create `DesktopKoinTest.kt` in `desktop/src/test/kotlin/org/meshtastic/desktop/di/` using `kotlin.test`.
    - [x] Initialize Koin application.
    - [x] Include `desktopModule()`, `desktopPlatformModule()`, and `desktopPlatformStubsModule()`.
    - [x] Call `checkModules()` inside the test and ensure it fails if there are missing interfaces.
- [x] Task: Implement to Pass Tests: Add any missing stubs or correct module includes in `desktopPlatformStubsModule()` to ensure the basic Koin graph resolves.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Setup KSP for Desktop and Test Scaffolding' (Protocol in workflow.md)

## Phase 2: Auto-wire ViewModels and Clean Up
- [x] Task: Refactor: Remove manual `viewModel { ... }` blocks from `DesktopKoinModule.kt` (if any are present).
- [x] Task: Implement: Ensure the desktop build configuration (`desktop/build.gradle.kts`) correctly includes the KSP-generated Koin modules and that KSP targets the JVM platform.
- [x] Task: Implement to Pass Tests: Verify that running `./gradlew :desktop:test` succeeds and that `DesktopKoinTest.kt` validates the new KSP-wired graph.
- [x] Task: Conductor - User Manual Verification 'Phase 2: Auto-wire ViewModels and Clean Up' (Protocol in workflow.md)