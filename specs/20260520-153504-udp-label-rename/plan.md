# Implementation Plan: UDP Label Rename

**Branch**: `jamesarich/issue-5546-alignment-add-missing-network-config-fi-fbde6f` | **Date**: 2025-05-20 | **Spec**: `specs/20260520-153504-udp-label-rename/spec.md`
**Input**: Feature specification from `specs/20260520-153504-udp-label-rename/spec.md`

## Summary

Rename the `udp_enabled` string resource value from "Enabled" to "UDP broadcasting" in the shared string resources file (`core/resources/src/commonMain/composeResources/values/strings.xml`, line 1304). This is a single-line value change with no code logic modifications. After the edit, run `python3 scripts/sort-strings.py` to maintain alphabetical ordering and regenerate the strings index, then verify with a full build.

## Technical Context

**Language/Version**: Kotlin 2.3+ / JDK 21  
**Primary Dependencies**: Compose Multiplatform (string resources via `Res.string.*`)  
**Storage**: N/A  
**Testing**: `./gradlew assembleDebug test allTests` (full build verification)  
**Target Platform**: Android + Compose Desktop (KMP)  
**Project Type**: Mobile app (Kotlin Multiplatform)  
**Performance Goals**: N/A (label-only change)  
**Constraints**: N/A  
**Scale/Scope**: 1 line changed in 1 file

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Kotlin Multiplatform Core**: ✅ PASS — Only `commonMain` is touched (`core/resources/src/commonMain/composeResources/values/strings.xml`). No platform-specific source sets modified.
- **II. Zero Lint Tolerance**: ✅ PASS — Will run `./gradlew spotlessApply spotlessCheck detekt` after the change. String XML changes are covered by Spotless XML formatting rules.
- **III. Compose Multiplatform UI**: ✅ PASS — No UI code changes. The existing composable (`NetworkConfigItemList.kt`) already references `Res.string.udp_enabled`; only the resolved value changes. No float formatting involved.
- **IV. Privacy First**: ✅ PASS — No PII, location data, or cryptographic material involved. `core/proto` submodule is not touched.
- **V. Design Standards Compliance**: ✅ PASS — The label "UDP broadcasting" aligns with the naming pattern of sibling toggles ("WiFi enabled", "Ethernet enabled"). This is a platform-specific UI label correction; cross-platform spec marked N/A in the feature spec with justification.
- **VI. Documentation Freshness**: ✅ PASS — No documentation pages affected by a string resource value change. No `skip-docs-check` label needed.
- **VII. Verify Before Push**: Will run:
  ```bash
  python3 scripts/sort-strings.py
  ./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests
  ```
  Post-push: `gh pr checks <PR>` or `gh run list --branch jamesarich/issue-5546-alignment-add-missing-network-config-fi-fbde6f --limit 5`

All gates pass. No exceptions required.

## Project Structure

### Documentation (this feature)

```text
specs/20260520-153504-udp-label-rename/
├── plan.md              # This file
├── research.md          # Phase 0 output (trivial — no unknowns)
├── data-model.md        # Phase 1 output (minimal — string resource only)
├── quickstart.md        # Phase 1 output (implementation steps)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
core/resources/src/commonMain/composeResources/values/
└── strings.xml          # Line 1304: udp_enabled value change

scripts/
└── sort-strings.py      # Post-edit sort script (existing)
```

**Structure Decision**: This change touches a single string value in the shared KMP resources module. No new files, modules, or directories are created.

## Complexity Tracking

> No violations. All constitution principles satisfied without exception.
