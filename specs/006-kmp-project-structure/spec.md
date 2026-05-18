# Feature Specification: KMP Recommended Project Structure Alignment

**Feature Branch**: `020-kmp-project-structure`  
**Created**: 2025-07-15  
**Status**: Draft  
**Input**: User description: "Restructure Meshtastic Android project to align with the updated Kotlin Multiplatform recommended project structure"  
**Cross-Platform Spec**: N/A — this is a build/infrastructure change with no user-facing behavior changes across platforms

## Summary

The Meshtastic Android project should be aligned with the official Kotlin Multiplatform recommended project structure as documented in the [KMP project structure guide](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html) and the [JetBrains blog post on the new KMP default structure](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/). The project already runs on AGP 9.2.1 and already applies the `com.android.kotlin.multiplatform.library` plugin via convention plugins, but an audit reveals that **all 27 KMP modules still contain legacy `android {}` blocks inside `kotlin {}`** that should be migrated to the recommended `kotlin.androidLibrary {}` top-level DSL. This effort focuses on completing that migration, validating module boundaries, and ensuring convention plugins enforce the canonical patterns — so the project fully conforms to the new structure with clear module responsibilities.

## Goals

1. **Migrate all legacy `android {}` blocks** inside `kotlin {}` to the recommended `kotlin.androidLibrary {}` top-level DSL across all 27 KMP modules in `core/` and `feature/`.
2. **Validate and harden convention plugins** so that `KmpLibraryConventionPlugin` and `KmpFeatureConventionPlugin` configure Android targets exclusively through the new plugin's DSL, preventing legacy patterns from being reintroduced.
3. **Confirm entry-point module separation** — `app/` (Android) and `desktop/` (JVM) are already separate entry-point modules with no shared business logic, satisfying the AGP 9 mandatory requirement.
4. **Document the module boundary model** — map the current `core/` (shared logic) and `feature/` (shared UI + logic) organization to the recommended `sharedLogic` / `sharedUI` categories, confirming they already satisfy the recommended split.
5. **Ensure forward compatibility** — verify the project structure is compatible with future KMP toolchain updates and potential new targets (iOS, web) without requiring another structural overhaul.

## Non-Goals

- **Introducing iOS, web, or server modules** — while the recommended structure supports these, this spec scopes only the existing Android + Desktop targets.
- **Renaming modules to match the default template names** — the recommended structure uses `shared`, `androidApp`, `desktopApp` as defaults, but the project's existing `core/`, `feature/`, `app/`, `desktop/` naming is equally valid and will not be renamed for cosmetic alignment.
- **Changing application behavior or UI** — this is purely a build infrastructure and module organization change. No user-facing functionality changes.
- **Migrating away from existing technology choices** — Koin, Ktor, Room KMP, Compose Multiplatform, and Navigation 3 remain as-is per the constitution.
- **Migrating `feature:widget` away from `com.android.library`** — this module is genuinely Android-only (Glance app widgets) and correctly uses the Android library plugin, not the KMP library plugin.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Developer Builds Successfully After Restructuring (Priority: P1)

As a contributor, I want the project to build successfully on both Android and Desktop targets after any structural changes, so that my development workflow is uninterrupted.

**Why this priority**: A broken build blocks all development. This is the absolute minimum bar for any structural change.

**Independent Test**: Can be fully tested by running `./gradlew assembleDebug`, `./gradlew :desktop:packageUberJarForCurrentOS`, and `./gradlew allTests` and verifying all pass.

**Acceptance Scenarios**:

1. **Given** the restructured project, **When** a developer runs the full Android build (`assembleDebug`), **Then** the build completes with zero errors.
2. **Given** the restructured project, **When** a developer runs the Desktop build (`:desktop:packageUberJarForCurrentOS`), **Then** the build completes with zero errors.
3. **Given** the restructured project, **When** a developer runs all tests (`allTests`), **Then** all existing tests pass with no regressions.
4. **Given** the restructured project with `DESKTOP_ONLY=true`, **When** a developer builds without Android SDK, **Then** the desktop-only build succeeds as before.

---

### User Story 2 - Convention Plugins Reflect Recommended Patterns (Priority: P2)

As a build maintainer, I want the convention plugins in `build-logic/` to apply the correct KMP plugin configuration patterns recommended by JetBrains, so that adding new modules follows a clear, documented convention.

**Why this priority**: Convention plugins are the enforcement layer for project structure. If they are correct, individual module configurations stay consistent.

**Independent Test**: Can be tested by verifying that `KmpLibraryConventionPlugin` applies the `com.android.kotlin.multiplatform.library` plugin and that no KMP library module uses the legacy `com.android.library` plugin.

**Acceptance Scenarios**:

1. **Given** the `meshtastic.kmp.library` convention plugin, **When** applied to a KMP module, **Then** it configures the module using `com.android.kotlin.multiplatform.library` (not `com.android.library`).
2. **Given** the `meshtastic.kmp.feature` convention plugin, **When** applied to a feature module, **Then** it inherits the correct KMP library plugin chain without legacy Android library configuration.
3. **Given** a new core module is created, **When** a developer applies `meshtastic.kmp.library`, **Then** the module follows the recommended structure with `kotlin.androidLibrary {}` configuration.

---

### User Story 3 - Module Boundary Clarity for New Contributors (Priority: P3)

As a new contributor, I want a clear separation between entry-point modules, shared business logic modules, and shared UI modules, so that I know where to add new code based on its purpose.

**Why this priority**: Clear boundaries reduce onboarding friction and prevent architectural drift over time.

**Independent Test**: Can be tested by reviewing the module dependency graph and verifying that entry-point modules (`app/`, `desktop/`) do not contain shared logic, and that `core/` modules do not depend on `feature/` modules.

**Acceptance Scenarios**:

1. **Given** the module dependency graph, **When** analyzing `app/` dependencies, **Then** `app/` depends on `core/` and `feature/` modules but contains no shared business logic.
2. **Given** the module dependency graph, **When** analyzing `desktop/` dependencies, **Then** `desktop/` depends on `core/` and `feature/` modules but contains no shared business logic.
3. **Given** any `core/` module, **When** examining its dependencies, **Then** it does not depend on any `feature/` module (unidirectional flow preserved).

---

### User Story 4 - Legacy DSL Block Migration (Priority: P1)

As a build maintainer, I want all 27 KMP modules' legacy `android {}` blocks inside `kotlin {}` migrated to the recommended `kotlin.androidLibrary {}` top-level DSL, so that the build configuration fully uses the new plugin's canonical API.

**Why this priority**: The project already runs AGP 9.2.1 and applies the `com.android.kotlin.multiplatform.library` plugin, but every KMP module still configures its Android target using the legacy `android {}` block inside `kotlin {}` (for namespace, resource settings, etc.). This is the primary gap between the current state and full alignment with the recommended structure. It ties with P1 because the build already works — this is about eliminating technical debt before it becomes a blocker.

**Independent Test**: Can be tested by searching all `core/` and `feature/` KMP module `build.gradle.kts` files for `android {` blocks inside `kotlin {}` — zero should remain after migration. All configuration should appear in `kotlin.androidLibrary {}` top-level blocks or be handled by convention plugins.

**Acceptance Scenarios**:

1. **Given** any KMP library module in `core/` or `feature/`, **When** examining its `build.gradle.kts`, **Then** it contains no `android {}` block inside `kotlin {}` — Android configuration uses the `kotlin.androidLibrary {}` top-level DSL or is delegated to convention plugins.
2. **Given** the `configureKotlinMultiplatform()` helper in build-logic, **When** it configures Android targets, **Then** it uses `KotlinMultiplatformAndroidLibraryTarget` APIs from the new plugin, not legacy `android {}` extension configuration.
3. **Given** the full project with all 27 modules migrated, **When** building Android and Desktop targets, **Then** all builds succeed with zero errors.

---

### Edge Cases

- What happens when the `DESKTOP_ONLY` mode is active and no Android SDK is available? The restructured plugins must continue to conditionally skip Android plugin application (as the current `isDesktopOnly` guard already does).
- How does the system handle modules that are genuinely Android-only (e.g., `core:api`, `core:barcode`, `screenshot-tests`)? These modules should continue using `com.android.library` or `com.android.application` as appropriate — the KMP library plugin applies only to multiplatform modules.
- What happens if a module currently uses `com.android.library` but should be KMP? The migration path must be documented and executed per-module, with build verification at each step.

## Architecture

### Gap Analysis: Current State vs Recommended Structure

The audit reveals the project is **substantially aligned** with the recommended KMP structure, with one significant gap remaining:

```
AREA                                 STATUS   FINDING
====                                 ======   =======

Entry-point separation               ✅ DONE  app/ (Android) and desktop/ (JVM) are
                                              already separate entry-point modules.
                                              AGP 9 mandatory requirement satisfied.

KMP library plugin adoption           ✅ DONE  KmpLibraryConventionPlugin already applies
                                              com.android.kotlin.multiplatform.library
                                              via the android-kotlin-multiplatform-library
                                              catalog alias. No androidTarget {} calls.

AGP version                           ✅ DONE  Project already runs AGP 9.2.1.

Legacy android {} blocks in kotlin {} ⚠️ GAP   All 27 KMP modules still configure Android
                                              via android {} blocks INSIDE kotlin {} for
                                              namespace, androidResources, withHostTest.
                                              These should migrate to kotlin.androidLibrary {}
                                              top-level DSL blocks.

Module boundary model                 ✅ DONE  core/ = shared business logic (≈ sharedLogic)
                                              feature/ = shared UI + logic (≈ sharedUI)
                                              Unidirectional: app/desktop → feature → core

Dependency direction                  ✅ DONE  No reverse dependencies. core/ does not
                                              depend on feature/. Unidirectional flow.

DESKTOP_ONLY mode                     ✅ DONE  Conditional Android plugin skipping via
                                              isDesktopOnly guard in convention plugins.

Android-only modules                  ✅ DONE  feature:widget uses com.android.library
                                              correctly. core:api, core:barcode also
                                              Android-only with appropriate plugins.

Convention plugin architecture        ⚠️ GAP   configureKotlinMultiplatform() helper
                                              may contain legacy android {} configuration
                                              patterns alongside the new plugin's API.
                                              Needs audit and cleanup.
```

### Affected Modules (Legacy `android {}` blocks to migrate)

**Core modules (19)**:
`core:ble`, `core:common`, `core:data`, `core:database`, `core:datastore`, `core:di`, `core:domain`, `core:model`, `core:navigation`, `core:network`, `core:nfc`, `core:prefs`, `core:proto`, `core:repository`, `core:resources`, `core:service`, `core:takserver`, `core:testing`, `core:ui`

**Feature modules (8 — excluding widget)**:
`feature:connections`, `feature:firmware`, `feature:intro`, `feature:map`, `feature:messaging`, `feature:node`, `feature:settings`, `feature:wifi-provision`

**Not affected** (correctly using `com.android.library` or `com.android.application`):
`app/`, `desktop/`, `feature:widget`, `core:api`, `core:barcode`, `screenshot-tests`

### Mapping to Recommended Structure

```
RECOMMENDED STRUCTURE          MESHTASTIC EQUIVALENT        NOTES
=====================          =======================      =====

androidApp/                    app/                         ✅ Same role, different name
  - kotlin.android                                          (naming is cosmetic)
  + com.android.application

desktopApp/                    desktop/                     ✅ Same role, different name
  - kotlin.jvm + compose

shared/ (single shared mod)    core/ + feature/             ✅ Already modularized further
  OR                             (27+ KMP modules)           than the default; this is the
sharedLogic/ + sharedUI/         core/ ≈ sharedLogic         recommended "advanced" pattern
                                 feature/ ≈ sharedUI

build-logic/                   build-logic/                 ✅ Project-specific convention
                                                             plugins (not prescribed by
                                                             JetBrains but aligned)
```

### Reference Projects Comparison

The JetBrains blog cites `kotlinconf-app`, `KMP-App-Template`, and `RSS Reader` as reference implementations. The Meshtastic project is more mature and more modularized than any of these, having already decomposed the monolithic `shared` module into granular `core/` and `feature/` modules. This is the recommended evolution path for larger projects.

### Key Components

| Component                        | Module / File                                              | Purpose                                                       |
| -------------------------------- | ---------------------------------------------------------- | ------------------------------------------------------------- |
| KmpLibraryConventionPlugin       | `build-logic/convention/.../KmpLibraryConventionPlugin.kt` | Applies KMP + Android library plugins to shared modules       |
| KmpFeatureConventionPlugin       | `build-logic/convention/.../KmpFeatureConventionPlugin.kt` | Composite plugin for feature modules (KMP + Compose + Koin)   |
| KmpJvmAndroidConventionPlugin    | `build-logic/convention/.../KmpJvmAndroidConventionPlugin.kt` | Configures jvmAndroidMain shared source set                 |
| AndroidLibraryConventionPlugin   | `build-logic/convention/.../AndroidLibraryConventionPlugin.kt` | Legacy Android library plugin (for Android-only modules)    |
| configureKotlinMultiplatform()   | `build-logic/convention/.../KotlinAndroid.kt`              | Shared Kotlin/Android configuration helper                    |
| settings.gradle.kts              | Root `settings.gradle.kts`                                 | Module registration and plugin management                     |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: All 27 KMP library modules (`core/*` except `core:api` and `core:barcode`, plus `feature/*` except `feature:widget`) MUST have their `android {}` blocks inside `kotlin {}` migrated to `kotlin.androidLibrary {}` top-level DSL blocks.
- **FR-002**: Entry-point modules (`app/`, `desktop/`) MUST remain as standalone application modules that depend on shared modules but contain no reusable business logic. This is already satisfied and MUST NOT regress.
- **FR-003**: The `KmpLibraryConventionPlugin` MUST configure Android target properties (namespace, compileSdk, minSdk, resource settings) exclusively through the `com.android.kotlin.multiplatform.library` plugin's API (`KotlinMultiplatformAndroidLibraryTarget`), not through legacy `android {}` extension blocks.
- **FR-004**: Android-only modules (`core:api`, `core:barcode`, `feature:widget`, `screenshot-tests`) MUST continue using `com.android.library` or `com.android.application` as they are not multiplatform modules.
- **FR-005**: The `DESKTOP_ONLY` build mode MUST continue functioning — when active, Android plugin application MUST be skipped for KMP modules (existing `isDesktopOnly` guard preserved).
- **FR-006**: Module dependency direction MUST remain unidirectional: `app/desktop → feature → core → build-logic`. No reverse dependencies.
- **FR-007**: The `configureKotlinMultiplatform()` helper function MUST be audited and updated to remove any residual legacy configuration that duplicates or conflicts with the new plugin's target configuration.
- **FR-008**: Convention plugins MUST prevent future modules from using the legacy `android {}` pattern — applying `meshtastic.kmp.library` MUST automatically configure the Android target through the new plugin's DSL with no manual `android {}` block needed in the module's `build.gradle.kts`.

### Non-Functional Requirements

- **NFR-001**: The restructuring MUST NOT increase full-project build time by more than 5%.
- **NFR-002**: Gradle configuration cache, isolated projects, and parallel execution (`gradle.properties` settings) MUST remain functional after changes.
- **NFR-003**: All changes MUST be backward-compatible within a single migration PR — no intermediate broken states on the main branch.
- **NFR-004**: The migration MUST be documentable as a step-by-step checklist that other Meshtastic platform repositories can reference.

## Source-Set Impact

| Source Set     | Impact                                              | Justification                                                                            |
| -------------- | --------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `commonMain`   | No changes to source files                          | Business logic and UI remain in commonMain; only build configuration changes              |
| `androidMain`  | No changes to source files                          | Platform-specific code unchanged; plugin configuration changes only                      |
| `jvmMain`      | No changes to source files                          | Desktop-specific code unchanged; plugin configuration changes only                       |
| Build files     | Modified `build.gradle.kts` across 28 files (27 modules + 1 convention plugin) | Plugin IDs and configuration blocks updated to match recommended patterns                |
| Convention plugins | Modified convention plugin Kotlin files           | Updated to enforce recommended plugin application and configuration patterns             |

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed
- [x] No new network calls that transmit user data
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

This feature is purely a build infrastructure change with no runtime behavior changes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 27 KMP library modules have zero `android {}` blocks inside `kotlin {}` — all Android configuration uses `kotlin.androidLibrary {}` top-level DSL or is handled by convention plugins.
- **SC-002**: Full project builds succeed on both Android (`assembleDebug`) and Desktop (`:desktop:packageUberJarForCurrentOS`) with zero new errors after migration.
- **SC-003**: All existing tests pass (`allTests`) with zero regressions after migration.
- **SC-004**: The `DESKTOP_ONLY` build mode continues to function correctly, building without Android SDK.
- **SC-005**: Build time for a clean `assembleDebug` does not increase by more than 5% compared to the pre-migration baseline.
- **SC-006**: A new contributor can add a new KMP module by applying one convention plugin (`meshtastic.kmp.library`) and the module is correctly configured for both Android and Desktop targets with no manual `android {}` block needed.
- **SC-007**: The `configureKotlinMultiplatform()` helper contains zero references to legacy `android {}` extension configuration — all Android target configuration goes through the new plugin's API.

## Assumptions

- All business logic and UI composables reside in `commonMain` source set (per Constitution §I, §III).
- The project already runs AGP 9.2.1 and applies `com.android.kotlin.multiplatform.library` in `KmpLibraryConventionPlugin` — the plugin adoption is complete; only the DSL migration from `android {}` to `kotlin.androidLibrary {}` remains.
- The `configureKotlinMultiplatform()` helper already uses `KotlinMultiplatformAndroidLibraryTarget` for compileSdk/minSdk configuration — any remaining legacy `android {}` blocks are in individual module `build.gradle.kts` files, not solely in convention plugins.
- No module uses the deprecated `androidTarget {}` call — this was verified by audit (zero matches found).
- The typical `android {}` block content in KMP modules is limited to `namespace` and `androidResources.enable = false` and occasionally `withHostTest {}` — migration should be mechanical.
- Android-only modules (`core:api`, `core:barcode`, `feature:widget`) are not candidates for the KMP library plugin migration — they correctly remain on `com.android.library`.
- The Gradle version catalog (`libs.versions.toml`) already declares the `android-kotlin-multiplatform-library` plugin alias pointing to AGP 9.2.1.
- The `jvmAndroidMain` shared source set pattern (used by some modules via `meshtastic.kmp.jvm.android`) is compatible with the new plugin and does not conflict with the `kotlin.androidLibrary {}` DSL.
- The recommended structure's module naming (`shared`, `androidApp`, `desktopApp`) is a default convention, not a requirement — the project's existing `core/`, `feature/`, `app/`, `desktop/` naming is equally valid per the JetBrains documentation.
