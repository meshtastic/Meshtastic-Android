# Implementation Plan: Node List Context Menu Alignment

**Branch**: `jamesarich/issue-5544-alignment-align-node-list-long-press-co-1d63b1` | **Date**: 2025-05-20 | **Spec**: `specs/20260520-153449-node-list-context-menu/spec.md`
**Input**: Feature specification from `/specs/20260520-153449-node-list-context-menu/spec.md`

## Summary

Reorder the node list long-press context menu to canonical order (Favorite → Mute notifications → Message → Trace Route → Ignore → Remove), add two new menu items (Message, Trace Route), rename "Mute Always" to "Mute notifications", and suppress the context menu for the local node. All changes are scoped to `commonMain` source sets in the `feature:node` module with new string resources in `core:resources`.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Compose Multiplatform (M3 Expressive), Koin 4.2+, Navigation 3  
**Storage**: N/A (no persistence changes)  
**Testing**: `./gradlew :feature:node:allTests` (KMP common tests)  
**Target Platform**: Android + Compose Desktop (KMP)  
**Project Type**: Mobile app (KMP multiplatform)  
**Performance Goals**: Context menu appearance < 300ms (NFR-001)  
**Constraints**: No platform-specific code; all logic in `commonMain`  
**Scale/Scope**: 3 files modified, 2 new composable functions, 2 new string resources

## Constitution Check

*GATE: ✅ PASSED — all seven principles satisfied.*

- **I. Kotlin Multiplatform Core**: ✅ All changes are in `commonMain` source sets only:
  - `feature/node/src/commonMain/.../component/NodeContextMenu.kt` — reorder + add items
  - `feature/node/src/commonMain/.../list/NodeListScreen.kt` — wire new callbacks
  - `feature/node/src/commonMain/.../list/NodeListViewModel.kt` — add message/traceroute methods
  - `core/resources/src/commonMain/composeResources/values/strings.xml` — add `trace_route` string
  - No `androidMain`/`jvmMain` changes required.

- **II. Zero Lint Tolerance**: ✅ Verification commands:
  ```bash
  ./gradlew spotlessApply spotlessCheck detekt :feature:node:allTests :core:resources:allTests
  ```

- **III. Compose Multiplatform UI**: ✅ Uses Compose Multiplatform `DropdownMenu`/`DropdownMenuItem` with `leadingIcon` pattern (M3 Expressive). No navigation changes (menu actions invoke callbacks). No floats displayed.

- **IV. Privacy First**: ✅ No PII/location/crypto exposure. No new logging. `core/proto` not modified.

- **V. Design Standards Compliance**: ✅ Menu order matches the cross-platform Menu Alignment Audit canonical order. This feature directly implements the upstream behavior spec (Issue #5544 references the cross-platform design standard).

- **VI. Documentation Freshness**: ✅ No user-facing documentation pages affected. Context menu is a UI interaction, not a documented feature page.

- **VII. Verify Before Push**: ✅ Commands:
  ```bash
  ./gradlew spotlessApply spotlessCheck detekt assembleDebug :feature:node:allTests
  gh pr checks <PR>
  ```

## Project Structure

### Documentation (this feature)

```text
specs/20260520-153449-node-list-context-menu/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (UI contract)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/
├── component/
│   └── NodeContextMenu.kt          # Reorder items, add MessageMenuItem + TraceRouteMenuItem
├── list/
│   ├── NodeListScreen.kt           # Wire onMessage + onTraceRoute callbacks
│   └── NodeListViewModel.kt        # Add traceRoute() + getDirectMessageRoute() methods
└── detail/
    └── NodeRequestActions.kt        # Already has requestTraceroute (reuse)

core/resources/src/commonMain/composeResources/values/
└── strings.xml                      # Add "trace_route" string; use existing "message", "mute_notifications"

feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/
└── component/
    └── NodeContextMenuTest.kt       # New: verify menu order + disabled states
```

**Structure Decision**: KMP multiplatform feature module structure. All business logic and UI in `commonMain` source set per Constitution §I.

## Complexity Tracking

> No violations. All gates pass without exception.
