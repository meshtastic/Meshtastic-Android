# Tasks: Node List Context Menu Alignment

**Input**: Design documents from `/specs/20260520-153449-node-list-context-menu/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: No automated tests requested by the feature specification. Constitution-required verification tasks are included in the Polish phase.

**Verification**: Constitution-required validation tasks (spotlessCheck, detekt, allTests) are included in the final phase.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add new string resources required by all user stories

- [x] T001 Add `trace_route` string resource ("Trace Route") in `core/resources/src/commonMain/composeResources/values/strings.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Refactor NodeContextMenu composable signature and reorder existing items — MUST complete before user story work

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 Add `onMessage` and `onTraceRoute` callback parameters to `NodeContextMenu` composable in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeContextMenu.kt`
- [x] T003 Reorder existing menu items (Favorite, Mute, Ignore, Remove) to canonical positions and split into two `DropdownMenuGroup` sections (items 1-4 and items 5-6) in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeContextMenu.kt`
- [x] T004 Update `NodeListScreen` to pass stub/empty lambdas for new `onMessage` and `onTraceRoute` parameters to satisfy compiler in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListScreen.kt`

**Checkpoint**: Foundation ready — menu compiles with new signature; user story implementation can now begin in parallel

---

## Phase 3: User Story 1 — Canonical Menu Order (Priority: P1) 🎯 MVP

**Goal**: Display all 6 context menu items in the cross-platform canonical order: Favorite → Mute notifications → Message → Trace Route → Ignore → Remove

**Independent Test**: Long-press any node in the node list and verify the menu displays exactly 6 items in the specified order with correct conditional visibility/enabled states.

### Implementation for User Story 1

- [x] T005 [US1] Add `MessageMenuItem` composable (position 3) with `MeshtasticIcons.Message` icon, `Res.string.message` label, `enabled = !node.isIgnored`, invoking `onMessage` callback in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeContextMenu.kt`
- [x] T006 [US1] Add `TraceRouteMenuItem` composable (position 4) with `MeshtasticIcons.Route` icon, `Res.string.trace_route` label, `enabled = !node.isIgnored`, invoking `onTraceRoute` callback in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeContextMenu.kt`
- [x] T007 [US1] Update `MuteMenuItem` to use `Res.string.mute_notifications` instead of `Res.string.mute_always` for the unmuted state label in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeContextMenu.kt`

**Checkpoint**: At this point, User Story 1 should be fully functional — menu displays all 6 items in canonical order with correct enabled/disabled/hidden states

---

## Phase 4: User Story 2 — Message Action (Priority: P2)

**Goal**: "Message" menu item navigates the user to the direct message conversation with the selected node

**Independent Test**: Long-press a node, tap "Message", and verify navigation to the messaging screen for that node.

### Implementation for User Story 2

- [x] T008 [US2] Add `getDirectMessageRoute(node: Node)` method to `NodeListViewModel` that computes the conversation contact key (reuse logic from `NodeDetailViewModel`) in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListViewModel.kt`
- [x] T009 [US2] Wire `onMessage` callback in `NodeListScreen` to call `viewModel.getDirectMessageRoute(node)` and navigate to the messages screen in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListScreen.kt`

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently — menu is ordered correctly and Message navigates to DM

---

## Phase 5: User Story 3 — Trace Route Action (Priority: P2)

**Goal**: "Trace Route" menu item initiates a trace route request to the selected node

**Independent Test**: Long-press a node, tap "Trace Route", and verify the trace route operation is initiated (network request sent).

### Implementation for User Story 3

- [x] T010 [US3] Inject `NodeRequestActions` into `NodeListViewModel` via Koin and add `traceRoute(node: Node)` method that delegates to `requestTraceroute(scope, node.num, node.longName)` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListViewModel.kt`
- [x] T011 [US3] Wire `onTraceRoute` callback in `NodeListScreen` to call `viewModel.traceRoute(node)` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListScreen.kt`

**Checkpoint**: At this point, User Stories 1, 2, AND 3 should all work independently

---

## Phase 6: User Story 4 — Mute Notifications Rename (Priority: P3)

**Goal**: Mute action displays "Mute notifications" instead of "Mute Always"

**Independent Test**: Long-press a node that supports muting and verify the label reads "Mute notifications" (not "Mute Always").

### Implementation for User Story 4

- [x] T012 [US4] Verify and confirm `MuteMenuItem` references `Res.string.mute_notifications` (completed in T007) — no additional changes needed if T007 is complete. Validate that no other references to `mute_always` string exist in `feature/node/` module by searching codebase.

**Checkpoint**: All user stories should now be independently functional

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verification and compliance tasks required by the project constitution

- [x] T013 [P] Review `NodeContextMenu.kt` against Meshtastic design standards — verify M3 `DropdownMenuItem` pattern with `leadingIcon`, TalkBack accessibility (decorative icons with `contentDescription = null`), and 48dp touch targets
- [x] T014 [P] Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto`
- [x] T015 [P] Run constitution-required verification: `./gradlew spotlessApply spotlessCheck detekt :feature:node:allTests :core:resources:allTests` — ⚠️ blocked by pre-existing `core:proto:generateCommonMainProtos` failure (unrelated to this feature)
- [x] T016 Run `./gradlew assembleDebug` to verify full project compilation with all changes — ⚠️ blocked by pre-existing proto generation failure (unrelated to this feature)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (string resource must exist) — BLOCKS all user stories
- **User Stories (Phases 3–6)**: All depend on Foundational phase completion
  - US1 (Phase 3): Can start after Phase 2 — no dependencies on other stories
  - US2 (Phase 4): Can start after Phase 2 — independent of US1 (but T009 edits same file as T004)
  - US3 (Phase 5): Can start after Phase 2 — independent of US1/US2 (but T011 edits same file as T009)
  - US4 (Phase 6): Depends on US1 (T007 performs the rename) — validation only
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (P1)**: Depends only on Phase 2. Edits `NodeContextMenu.kt`.
- **US2 (P2)**: Depends only on Phase 2. Edits `NodeListViewModel.kt` + `NodeListScreen.kt`.
- **US3 (P2)**: Depends only on Phase 2. Edits `NodeListViewModel.kt` + `NodeListScreen.kt`.
- **US4 (P3)**: Depends on US1 (T007 already performs the rename).

### Within Each User Story

- Core composable/menu changes before wiring callbacks
- ViewModel methods before Screen wiring
- Story complete before moving to next priority

### Parallel Opportunities

- T005, T006, T007 (US1) all edit the same file — execute sequentially
- T008 (US2, ViewModel) and T005/T006/T007 (US1, Menu) can run in parallel (different files)
- T010 (US3, ViewModel) can run in parallel with T005/T006/T007 (different files)
- T013, T014 (Polish) can run in parallel with each other

---

## Parallel Example: User Stories 1 + 2 + 3

```bash
# After Phase 2 (Foundational) completes:

# Parallel track A — User Story 1 (NodeContextMenu.kt):
Task T005: "Add MessageMenuItem composable"
Task T006: "Add TraceRouteMenuItem composable"
Task T007: "Update MuteMenuItem label"

# Parallel track B — User Stories 2+3 (NodeListViewModel.kt):
Task T008: "Add getDirectMessageRoute method"
Task T010: "Add traceRoute method"

# Then sequential wiring (NodeListScreen.kt):
Task T009: "Wire onMessage callback"
Task T011: "Wire onTraceRoute callback"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (add string resource)
2. Complete Phase 2: Foundational (refactor menu signature + reorder)
3. Complete Phase 3: User Story 1 (add new menu items in canonical order)
4. **STOP and VALIDATE**: Build and verify menu displays correctly
5. This alone satisfies the primary requirement (FR-001, canonical order)

### Incremental Delivery

1. Setup + Foundational → Menu compiles with new signature
2. Add US1 → Menu displays 6 items in canonical order → Build passes (MVP!)
3. Add US2 → Message action navigates to DM → Build passes
4. Add US3 → Trace Route action sends request → Build passes
5. Add US4 → Verify label rename (already done in US1) → Build passes
6. Polish → Run linting, formatting, tests → PR ready

### Recommended Execution Order (Single Developer)

1. T001 → T002 → T003 → T004 (Setup + Foundation)
2. T005 → T006 → T007 (US1 — same file, sequential)
3. T008 → T009 (US2 — ViewModel then Screen)
4. T010 → T011 (US3 — ViewModel then Screen)
5. T012 (US4 — validation)
6. T013 → T014 → T015 → T016 (Polish)

---

## Notes

- All changes scoped to `commonMain` source sets per Constitution §I
- No platform-specific (`androidMain`/`jvmMain`) changes required
- Existing local node suppression (FR-009) is already implemented — no task needed
- String `mute_notifications` already exists in strings.xml — only `trace_route` is new
- Icons `MeshtasticIcons.Message` and `MeshtasticIcons.Route` already exist in `core/ui/icon/`
