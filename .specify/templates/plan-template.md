# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the feature. Most fields below are pre-filled with project defaults —
  adjust only what's feature-specific.
-->

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Compose Multiplatform, Material 3 Adaptive, Koin 4.2+ (K2 Compiler Plugin), Room KMP, DataStore KMP  
**Storage**: [DataStore KMP for preferences / Room KMP for entities / N/A]  
**Testing**: KMP `allTests` for `feature:*` and `core:*` modules; `testFdroidDebugUnitTest` for `app`  
**Target Platform**: Android, Desktop (JVM), iOS — all via `commonMain`  
**Project Type**: Mobile/desktop app (Kotlin Multiplatform)  
**Performance Goals**: [e.g., 60fps scrolling, <1s response or NEEDS CLARIFICATION]  
**Constraints**: All UI in `commonMain`; no `java.*`/`android.*` in common; CMP float pre-formatting via `NumberFormatter.format()`  
**Scale/Scope**: [e.g., N new files, M modified files across feature/X, core/Y]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ⬜ | All code in `commonMain`. No `java.*`/`android.*` imports. |
| II. Zero Lint Tolerance | ⬜ | `spotlessApply` + `detekt` required before merge. |
| III. Compose Multiplatform UI | ⬜ | CMP composables, `NumberFormatter.format()` for floats, Navigation 3 patterns. |
| IV. Privacy First | ⬜ | No PII/location/key logging. Proto submodule read-only. |
| V. Design Standards Compliance | ⬜ | UI-GATE review required before UI work. |
| VI. Verify Before Push | ⬜ | Full verification: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`. |
| VII. Coroutine Safety | ⬜ | `safeCatching {}` not `runCatching {}`. Project `ioDispatcher` not `Dispatchers.IO`. |
| VIII. Resource Discipline | ⬜ | `stringResource(Res.string.key)`, `MeshtasticIcons`, `sort-strings.py` after adding strings. |
| IX. Branch & Scope Hygiene | ⬜ | Branch prefix, upstream base, ~5-commit scope limit. |

**Gate Result**: [⬜ Pending / ✅ All principles satisfied / ❌ Violations requiring justification]

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

<!--
  ACTION REQUIRED: Fill in with the actual files affected by this feature.
  Use the module layout below as a guide. Delete unused modules.
-->

```text
feature/[name]/                          ← Primary changes
├── src/commonMain/kotlin/org/meshtastic/feature/[name]/
│   ├── component/
│   │   ├── [ExistingComposable].kt      ← Modify
│   │   └── [NewComposable].kt           ← NEW
│   ├── list/
│   │   ├── [Screen].kt                  ← Modify — [description]
│   │   └── [ViewModel].kt              ← Modify — [description]
│   └── model/
│       └── [NewModel].kt               ← NEW — [description]

core/[module]/                           ← Core layer changes
├── src/commonMain/kotlin/org/meshtastic/core/[module]/
│   └── [File].kt                        ← Modify — [description]

feature/settings/                        ← Settings integration (if applicable)
├── src/commonMain/kotlin/org/meshtastic/feature/settings/
│   └── [SettingsSection].kt             ← NEW — [description]

core/resources/
└── src/commonMain/composeResources/values/strings.xml  ← Add string resources
```

**Structure Decision**: [Document the selected structure and why existing modules
are modified rather than creating new ones, per KMP module architecture.]

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/[name]` | New + Modify | [count] | [Low/Medium/High] |
| `core/[module]` | Modify | [count] | [Low/Medium/High] |
| `core/resources` | Modify | 1 file (strings.xml) | Low |

## Integration Points

<!--
  Document how this feature integrates with existing systems:
  navigation routes, DataStore keys, DI modules, etc.
-->

## Design Constraints

<!--
  List technical constraints specific to this feature.
  Include M3/Expressive, accessibility, and CMP constraints.
-->

- All UI lives in `commonMain` — not platform-specific
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- Error handling uses `safeCatching {}` not `runCatching {}`
- Dispatchers via `org.meshtastic.core.common.util.ioDispatcher`
- Float values must be pre-formatted with `NumberFormatter.format()` (CMP constraint)

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| [Risk description] | [Low/Med/High] | [Low/Med/High] | [Mitigation with task reference] |

## Phase Alignment with Tasks

<!--
  ACTION REQUIRED: Fill in after tasks.md is generated by /speckit.tasks.
  Reference actual task IDs from tasks.md — do NOT use plan-internal numbering.
-->

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Setup | [Purpose] | [Task IDs] | None |
| N. Polish | [Purpose] | [Task IDs] | All prior phases |

### Critical Path

```
Phase 1 → Phase 2 → ... → Phase N
```

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |
