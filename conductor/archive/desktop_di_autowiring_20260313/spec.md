# Specification: Desktop DI Auto-Wiring and Validation

## Overview
This track addresses immediate architecture health priorities for the Desktop KMP target:
1. **Desktop Koin `checkModules()` test:** Add a compile-time/test-time validation test to ensure Desktop DI bindings resolve correctly and catch missing interfaces early.
2. **Auto-wire Desktop ViewModels:** Configure KSP to generate Koin modules for ViewModels annotated with `@KoinViewModel` in the JVM target, eliminating the need for manual ViewModel wiring in `DesktopKoinModule`.

## Functional Requirements
- **KSP Configuration:** Update the `meshtastic.koin` (or equivalent) convention plugin to apply KSP and Koin annotations processing to the `jvmMain` (Desktop) target.
- **ViewModel Auto-Wiring:** Remove all manual `viewModel { ... }` definitions in `DesktopKoinModule` and ensure they are successfully replaced by the KSP-generated Koin modules.
- **DI Validation Test:** Implement a new test file (e.g., `DesktopKoinTest.kt`) in `desktop/src/test/kotlin/org/meshtastic/desktop/di/` using `kotlin.test`.
- **Test Scope:** The `checkModules()` test must include and validate all active Desktop Koin modules, including `desktopModule()`, `desktopPlatformModule()`, `desktopPlatformStubsModule()`, and any KSP-generated modules.

## Non-Functional Requirements
- **Build Performance:** The addition of KSP to the JVM target should not unnecessarily degrade build times. Cacheability must be maintained.
- **Style:** Adhere strictly to the project's existing Kotlin code style and Koin best practices.

## Acceptance Criteria
- [ ] Running `./gradlew :desktop:test` executes the new `checkModules()` test successfully.
- [ ] No manual ViewModel definitions remain in `DesktopKoinModule` for shared ViewModels (they are auto-wired).
- [ ] If a dependency is missing from the Desktop DI graph, the `checkModules()` test fails explicitly.

## Out of Scope
- Migrating other platforms (Android, iOS) DI implementations.
- Refactoring the internal logic of the ViewModels themselves.