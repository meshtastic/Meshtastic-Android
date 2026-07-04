# Implementation Plan: Reorder Bottom Navigation Tab Labels

**Branch**: `jamesarich/issue-5543-alignment-reorder-bottom-navigation-tab-91d55d` | **Date**: 2026-05-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/20260520-153412-nav-tab-labels/spec.md`

## Summary

Rename two bottom navigation tab labels from "Conversations" → "Messages" and "Connections" → "Connect" to match the cross-platform canonical naming convention from the Menu Alignment Audit. Implementation involves: renaming the `TopLevelDestination` enum entries, adding new string resource keys for tab labels, and updating all references across the KMP codebase. Existing string keys are retained for screen titles.

## Technical Context

**Language/Version**: Kotlin 2.3+ / JDK 21  
**Primary Dependencies**: Compose Multiplatform, Navigation 3, Koin 4.2+  
**Storage**: N/A (string resource change only)  
**Testing**: `./gradlew allTests` (KMP commonTest), `./gradlew test` (Android-only)  
**Target Platform**: Android (mobile), Desktop (JVM) — KMP shared code  
**Project Type**: Mobile app (KMP multiplatform)  
**Performance Goals**: N/A (label change only, no runtime impact)  
**Constraints**: Must not break navigation routing, deep links, or state restoration  
**Scale/Scope**: 7 files modified across 3 modules (`core:navigation`, `core:ui`, `core:resources`) + test updates

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Kotlin Multiplatform Core**: ✅ All changes are in `commonMain` source sets (`core/navigation`, `core/ui`, `core/resources`). No platform-specific (`androidMain`/`desktopMain`) code is modified. Enum rename and string resources are shared across all targets.
- **II. Zero Lint Tolerance**: ✅ Will run: `./gradlew spotlessApply spotlessCheck detekt` for all touched modules. After adding string resources: `python3 scripts/sort-strings.py`.
- **III. Compose Multiplatform UI**: ✅ No new UI composables introduced. The `MeshtasticNavigationSuite` already uses `TopLevelDestination.label` via `stringResource()`; the label change is purely data-driven. No float formatting involved.
- **IV. Privacy First**: ✅ No PII, location data, or cryptographic keys involved. `core/proto` submodule is not touched.
- **V. Design Standards Compliance**: ✅ This change directly implements the [Menu Alignment Audit](https://github.com/meshtastic/design/blob/master/standards/audits/menu-alignment-audit.md) from `meshtastic/design`. Cross-platform behavior spec is the audit itself.
- **VI. Documentation Freshness**: ✅ No doc pages require updates — this is a label rename with no feature behavior change. Existing docs reference screen functionality, not tab label text.
- **VII. Verify Before Push**: Will run:
  ```bash
  ./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests
  python3 scripts/sort-strings.py
  ```
  Post-push: `gh pr checks <PR>` or `gh run list --branch jamesarich/issue-5543-alignment-reorder-bottom-navigation-tab-91d55d --limit 5`

## Project Structure

### Documentation (this feature)

```text
specs/20260520-153412-nav-tab-labels/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (via /speckit.tasks)
```

### Source Code (repository root)

```text
core/
├── navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/
│   ├── TopLevelDestination.kt          # Enum entries renamed
│   └── MultiBackstack.kt              # References updated
├── navigation/src/commonTest/kotlin/org/meshtastic/core/navigation/
│   └── MultiBackstackTest.kt          # Test references updated
├── ui/src/commonMain/kotlin/org/meshtastic/core/ui/
│   ├── navigation/TopLevelDestinationExt.kt  # Icon mapping updated
│   └── component/MeshtasticNavigationSuite.kt # References updated
├── resources/src/commonMain/composeResources/values/
│   └── strings.xml                     # New keys: messages, connect
androidApp/src/main/kotlin/org/meshtastic/app/ui/
│   └── Main.kt                         # References updated
desktopApp/src/main/kotlin/org/meshtastic/desktop/
│   ├── Main.kt                         # References updated
│   └── navigation/DesktopNavigation.kt # References updated
desktopApp/src/test/kotlin/org/meshtastic/desktop/ui/
│   └── DesktopTopLevelDestinationParityTest.kt # May need update
```

**Structure Decision**: Kotlin Multiplatform with shared `commonMain` source sets. All changes are in existing files; no new modules or directories created.

## Complexity Tracking

> No constitution violations. All gates pass.
