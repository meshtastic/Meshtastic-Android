# Implementation Plan: KMP Recommended Project Structure Alignment

**Branch**: `020-kmp-project-structure` | **Date**: 2025-07-15 | **Spec**: `specs/006-kmp-project-structure/spec.md`
**Input**: Feature specification from `specs/006-kmp-project-structure/spec.md`

## Summary

Migrate 27 KMP modules from legacy `android {}` blocks inside `kotlin {}` to the recommended `kotlin.androidLibrary {}` top-level DSL. The project already runs AGP 9.2.1 and applies `com.android.kotlin.multiplatform.library` via convention plugins — the primary work is DSL migration in individual module `build.gradle.kts` files and hardening the convention plugin helper `configureKotlinMultiplatform()` to absorb common settings (like `androidResources.enable = false` and `withHostTest {}`) so modules need fewer per-module overrides. Build verification at each step is critical.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21 (JDK 17 for published modules)
**Primary Dependencies**: AGP 9.2.1, Kotlin Multiplatform Gradle Plugin, `com.android.kotlin.multiplatform.library`
**Storage**: N/A (build infrastructure only)
**Testing**: `./gradlew assembleDebug :desktop:packageUberJarForCurrentOS allTests` for build verification
**Target Platform**: Android (API 26+) + Compose Desktop (JVM)
**Project Type**: Mobile app (KMP) — build infrastructure change
**Performance Goals**: Build time must not increase >5% (NFR-001)
**Constraints**: Configuration cache, isolated projects, parallel execution must remain functional (NFR-002)
**Scale/Scope**: 27 KMP modules across `core/` (19) and `feature/` (8), plus 4 convention plugin files in `build-logic/`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Kotlin Multiplatform Core**: ✅ PASS — No source files are modified. All changes are to `build.gradle.kts` configuration files and convention plugin Kotlin files in `build-logic/`. Business logic in `commonMain` is untouched. The migration moves Android target configuration from `kotlin { android {} }` to `kotlin { androidLibrary {} }` — a pure DSL change with identical semantics.

- **II. Zero Lint Tolerance**: ✅ PASS — Verification commands:
  - `./gradlew spotlessApply spotlessCheck` (formatting)
  - `./gradlew detekt` (static analysis)
  - Convention plugin files in `build-logic/` are covered by the `:build-logic:convention:spotlessCheck` and `:build-logic:convention:detekt` tasks.

- **III. Compose Multiplatform UI**: ✅ N/A — No UI changes. This is a build infrastructure change only.

- **IV. Privacy First**: ✅ PASS — No runtime behavior changes. No PII exposure. `core/proto` submodule not modified.

- **V. Design Standards Compliance**: ✅ N/A — No user-facing UI changes. Cross-Platform Spec: N/A — build infrastructure change.

- **VI. Verify Before Push**: ✅ — Local verification commands:
  ```bash
  ./gradlew spotlessApply spotlessCheck detekt assembleDebug :desktop:packageUberJarForCurrentOS allTests
  DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS
  ```
  Post-push: `gh pr checks <PR>` or `gh run list --branch 020-kmp-project-structure --limit 5`

## Project Structure

### Documentation (this feature)

```text
specs/006-kmp-project-structure/
├── plan.md              # This file
├── research.md          # Phase 0: DSL migration research
├── data-model.md        # Phase 1: Module classification and migration matrix
├── quickstart.md        # Phase 1: Step-by-step migration guide
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
build-logic/convention/src/main/kotlin/
├── KmpLibraryConventionPlugin.kt        # Convention plugin — no changes expected
├── KmpFeatureConventionPlugin.kt        # Convention plugin — no changes expected
├── KmpJvmAndroidConventionPlugin.kt     # Convention plugin — no changes expected
└── org/meshtastic/buildlogic/
    └── KotlinAndroid.kt                 # configureKotlinMultiplatform() — harden defaults

core/*/build.gradle.kts                  # 19 KMP modules — migrate android {} → androidLibrary {}
feature/*/build.gradle.kts               # 8 KMP modules — migrate android {} → androidLibrary {}
  (excluding feature/widget/ — Android-only, not affected)
```

**Structure Decision**: Existing module structure is preserved. This change only modifies `build.gradle.kts` files and convention plugin helpers. No directory restructuring.

## Complexity Tracking

> No constitution violations. No complexity exceptions needed.
