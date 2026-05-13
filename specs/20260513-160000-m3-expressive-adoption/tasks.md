# Tasks: M3 Expressive Design System Adoption

**Input**: Design documents from `/specs/20260513-160000-m3-expressive-adoption/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

**Tests**: Screenshot test reference updates are included as they are required by NFR-005. No new unit/integration test tasks unless explicitly testing new behavior (SwipeToRevealBox).

**Verification**: Constitution-required validation tasks included (spotlessCheck, detekt, assembleDebug, allTests).

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **KMP Multi-module**: `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/`, `feature/*/src/commonMain/`
- **DataStore**: `core/datastore/src/commonMain/`
- **Screenshot tests**: `screenshot-tests/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish expressive theme foundations and shared configuration

- [ ] T001 Add `@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)` to `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/theme/Theme.kt`
- [ ] T002 [P] Add `@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)` to `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/theme/Type.kt`
- [ ] T003 [P] Verify `MaterialExpressiveTheme` and `MotionScheme.expressive()` are applied at root level in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/theme/Theme.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Typography and shared component foundations that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 Define complete expressive typescale in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/theme/Type.kt` with `bodyLarge.fontSize = 16.sp` and `bodyMedium.fontSize = 16.sp` per design standards §5 and contracts/expressive-typography-api.md
- [ ] T004a [P] Create `LocalReduceMotion` composition local in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/theme/Motion.kt` — reads system `Settings.Global.ANIMATOR_DURATION_SCALE` via expect/actual, provides boolean to all composables. Provide from `AppTheme` in `Theme.kt`
- [ ] T005 [P] Add `hasCompletedSwipeAction: Boolean` field to UserPreferences in `core/datastore/src/commonMain/kotlin/com/geeksville/mesh/datastore/` (DataStore proto/preferences)
- [ ] T006 [P] Create `SwipeToRevealBox.kt` composable in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/SwipeToRevealBox.kt` implementing the full contract from contracts/swipe-to-reveal-api.md (SwipeAnchor enum, SwipeDirection enum, AnchoredDraggableState, spring animations stiffness=300f/dampingRatio=0.7f for return, stiffness=400f/dampingRatio=0.8f for reveal)
- [ ] T007 [P] Create `rememberSwipeToRevealState` composable function in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/SwipeToRevealBox.kt`
- [ ] T008 [P] Create `Modifier.swipeHint` extension in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/SwipeToRevealBox.kt` implementing edge-peek animation (24.dp peek, 400ms spring offset, 1000ms hold, 400ms spring return)
- [ ] T009 Add reduced-motion support to `SwipeToRevealBox` — instant snap when `LocalReduceMotion.current` is true, and skip hint animation per behavioral contract

**Checkpoint**: Foundation ready — typography, DataStore preference, and SwipeToRevealBox component available for all user stories

---

## Phase 3: User Story 1 — Expressive Navigation Experience (Priority: P1) 🎯 MVP

**Goal**: Deliver modern navigation with spring-physics pill indicators, expressive top app bars, and smooth page transitions

**Independent Test**: Navigate between all primary destinations; verify smooth indicator animations, spring-based page transitions, and correct top app bar styling

### Implementation for User Story 1

- [ ] T010 [US1] Apply expressive pill-style indicators to `NavigationBar` in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/MeshtasticNavigationSuite.kt` using `NavigationBarItemDefaults` expressive indicator
- [ ] T011 [P] [US1] Apply expressive pill-style indicators to `NavigationRail` in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/MeshtasticNavigationSuite.kt` using `NavigationRailItemDefaults` expressive indicator
- [ ] T012 [US1] Apply expressive styling to `MainAppBar` in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/MainAppBar.kt` — circle-background nav button, expressive `TopAppBarDefaults` colors, and emphasized typography for title
- [ ] T013 [P] [US1] Verify spring-physics navigation indicator animations are automatically provided by `MotionScheme.expressive()` and complete within 300ms budget (SC-002) — confirm via Layout Inspector animation recording; no additional animation code should be needed per research.md RQ-5
- [ ] T014 [US1] Add reduced-motion accessibility support to navigation — verify animations are suppressed when system animator duration scale is 0

**Checkpoint**: Navigation experience is fully expressive with pill indicators, spring animations, and expressive app bars

---

## Phase 4: User Story 2 — Expressive Typography and Visual Hierarchy (Priority: P1)

**Goal**: Apply expressive typography consistently across all screens so users perceive clear information hierarchy through emphasized headings, distinct body text, and appropriate typescale usage

**Independent Test**: Navigate to any screen; verify typescale renders with correct emphasized variants, readable sizes (≥16sp body), and consistent hierarchy

### Implementation for User Story 2

- [ ] T015 [P] [US2] Update node detail screen typography in `feature/node/src/commonMain/` — primary metric values use `headlineSmall`, labels use `bodyMedium`, node names use `bodyLargeEmphasized`
- [ ] T016 [P] [US2] Update message thread typography in `feature/messaging/src/commonMain/` — message body uses `bodyLarge`, timestamps use `labelMedium`, sender names use `bodyLargeEmphasized`
- [ ] T017 [P] [US2] Update settings/configuration screens typography in `feature/settings/src/commonMain/` — section headers use `titleMediumEmphasized`, preference labels use `bodyLarge`
- [ ] T018 [P] [US2] Update connections screen typography in `feature/connections/src/commonMain/` — device names use `bodyLargeEmphasized`, status text uses `bodyMedium`
- [ ] T019 [P] [US2] Update map screen typography in `feature/map/src/commonMain/` — apply appropriate typescale to node labels and info overlays
- [ ] T020 [P] [US2] Update firmware screen typography in `feature/firmware/src/commonMain/` — version headers use `titleMediumEmphasized`, progress labels use `bodyMedium`
- [ ] T021 [P] [US2] Update channel configuration typography in `feature/channel/src/commonMain/` — channel names use `titleMediumEmphasized`, descriptions use `bodyMedium`
- [ ] T022 [P] [US2] Update radio configuration screen typography in `feature/radio/src/commonMain/` — section headers use `titleMediumEmphasized`, labels use `bodyLarge`
- [ ] T023 [US2] Replace any remaining hardcoded `TextStyle` with `MaterialTheme.typography.*` references across all feature modules (audit pass)
- [ ] T024 [US2] Verify system font-size scaling up to 200% produces no layout clipping across all screens per typography behavioral contract (Dynamic Type scaling)

**Checkpoint**: All screens display consistent expressive typography hierarchy

---

## Phase 5: User Story 3 — Expressive Component Interactions (Priority: P2)

**Goal**: Upgrade buttons, FABs, sliders, progress indicators, and dialogs to use expressive spring-morphing animations, dynamic shapes, and responsive feedback

**Independent Test**: Interact with FABs (message compose, connections), sliders (radio power config), progress indicators (firmware update), and buttons; verify spring animations, shape styling, and expressive feedback

### Implementation for User Story 3

- [ ] T025 [P] [US3] Enhance `MenuFAB` in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/MenuFAB.kt` — add `PlainTooltip` / `TooltipBox` on long-press for FAB label, apply expressive `FloatingActionButtonDefaults.shape`
- [ ] T026 [P] [US3] Upgrade `SliderPreference` in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/SliderPreference.kt` — apply spring-based `animateFloatAsState` for thumb positioning per research.md RQ-2
- [ ] T027 [P] [US3] Upgrade `AlertDialogs` in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/AlertDialogs.kt` — apply expressive shape and motion to dialog enter/exit transitions
- [ ] T028 [P] [US3] Ensure all progress indicators across feature modules use expressive wavy variants (`CircularWavyProgressIndicator`, `LinearWavyProgressIndicator`) for indeterminate states — audit `feature/firmware/src/commonMain/` and any other modules with progress indicators
- [ ] T029 [P] [US3] Apply expressive button styling (`ButtonDefaults` with expressive shapes) to primary action buttons across `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/` button composables
- [ ] T030 [P] [US3] Upgrade connection FABs in `feature/connections/src/commonMain/` — apply expressive sizing and tooltip labels
- [ ] T031 [US3] Verify all expressive component animations maintain 60fps on mid-range device per NFR-001 (manual check with Layout Inspector/animation profiler)

**Checkpoint**: All interactive components use expressive styling with spring physics and dynamic shapes

---

## Phase 6: User Story 4 — Expressive List Interactions (Priority: P2)

**Goal**: Enable swipe-to-action on node list and message list, reducing tap-count for common operations

**Independent Test**: Swipe node list items right (request position) and left (mute); swipe message items left (delete/archive); verify spring animations, hint behavior, and action execution

### Implementation for User Story 4

- [ ] T032 [US4] Integrate `SwipeToRevealBox` into node list items in `feature/node/src/commonMain/kotlin/com/geeksville/mesh/ui/node/list/NodeListScreen.kt` — right-swipe = request position action, left-swipe = mute node action
- [ ] T033 [P] [US4] Create action composables for node swipe (RequestPositionAction, MuteNodeAction) with appropriate icons from `MeshtasticIcons` and color styling
- [ ] T034 [US4] Integrate `SwipeToRevealBox` into message list items in `feature/messaging/src/commonMain/` — left-swipe only = delete/archive action (`enableStartSwipe = false`)
- [ ] T035 [P] [US4] Create action composable for message swipe (DeleteMessageAction) with appropriate icon and color styling
- [ ] T036 [US4] Connect `hasCompletedSwipeAction` DataStore preference to node list ViewModel — expose as `StateFlow<Boolean>` for hint visibility
- [ ] T036a [P] [US4] Add `hasShownHintThisSession` transient boolean to node list and message list ViewModels — controls per-session hint display (resets on app restart, set true after hint plays once per screen visit)
- [ ] T037 [US4] Connect `hasCompletedSwipeAction` DataStore preference to message list ViewModel — expose as `StateFlow<Boolean>` for hint visibility
- [ ] T038 [US4] Apply `Modifier.swipeHint` to first visible item in node list when `!hasCompletedSwipeAction` — animate edge-peek per contract (24dp, per-session until first successful swipe)
- [ ] T039 [US4] Apply `Modifier.swipeHint` to first visible item in message list when `!hasCompletedSwipeAction`
- [ ] T040 [US4] Set `hasCompletedSwipeAction = true` in DataStore after first successful swipe-to-action execution in either list
- [ ] T041 [US4] Add TalkBack accessibility `customActions` semantics to `SwipeToRevealBox` so swipe actions are announced for screen reader users
- [ ] T042 [US4] Verify swipe gestures are NOT enabled on channel list (which uses drag-and-drop reordering) per edge case in spec

**Checkpoint**: Node and message lists support swipe-to-action with discoverability hint and accessibility support

---

## Phase 7: User Story 5 — Expressive Focus and Accessibility (Priority: P3)

**Goal**: Provide clear animated focus indicators for keyboard/D-pad navigation and ensure all expressive animations respect reduced-motion preferences

**Independent Test**: Navigate entire app via keyboard/TalkBack; verify focus rings appear on all interactive elements; enable "reduce motion" and verify spring animations are suppressed

### Implementation for User Story 5

- [ ] T043 [P] [US5] Implement custom focus indication using `Modifier.focusable()` with visible focus ring styling in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/` — add a shared `expressiveFocusIndication` since CMP material3 doesn't ship animated focus rings yet (per research.md RQ-2)
- [ ] T044 [P] [US5] Apply focus indication to all interactive elements in `core/ui/src/commonMain/kotlin/com/geeksville/mesh/ui/component/` — buttons, FABs, list items, sliders, navigation items
- [ ] T045 [US5] Verify reduced-motion behavior across all expressive animations — confirm `MotionScheme` respects system animator duration scale = 0, and `SwipeToRevealBox` uses instant snap
- [ ] T046 [US5] Verify all expressive components maintain existing TalkBack content descriptions and action labels — no accessibility regressions per SC-008
- [ ] T047 [US5] Verify 44dp minimum touch targets on all interactive elements with expressive styling per design standards accessibility requirement

**Checkpoint**: App is fully accessible via keyboard/D-pad/TalkBack with expressive focus indicators and motion-respectful behavior

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Screenshot test updates, validation, and final quality assurance

- [ ] T048 [P] Update screenshot test reference images for navigation components via `./gradlew :screenshot-tests:updateScreenshotTests` in `screenshot-tests/`
- [ ] T049 [P] Update screenshot test reference images for typography changes across feature modules
- [ ] T050 [P] Update screenshot test reference images for expressive component styling (FABs, buttons, dialogs, progress indicators)
- [ ] T051 [P] Update screenshot test reference images for swipe-to-action components (revealed action states)
- [ ] T052 [P] Review all touched files against Meshtastic design standards — confirm no deviations in typography sizes, color roles, or accessibility
- [ ] T053 [P] Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto`
- [ ] T054 [P] Add `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` annotations at function level in all feature module composables that directly call expressive APIs per research.md RQ-6
- [ ] T055 Run constitution-required verification: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`
- [ ] T056 Verify cold-start time remains within 50ms of baseline per NFR-002

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **US1 Navigation (Phase 3)**: Depends on Phase 2 (typography/theme foundations)
- **US2 Typography (Phase 4)**: Depends on Phase 2 (typescale definition)
- **US3 Components (Phase 5)**: Depends on Phase 2 (theme foundations)
- **US4 List Interactions (Phase 6)**: Depends on Phase 2 (SwipeToRevealBox, DataStore pref)
- **US5 Accessibility (Phase 7)**: Depends on Phases 3-6 (validates all expressive work)
- **Polish (Phase 8)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — no dependencies on other stories
- **US2 (P1)**: Can start after Phase 2 — no dependencies on other stories; **can run in parallel with US1**
- **US3 (P2)**: Can start after Phase 2 — no dependencies on other stories; **can run in parallel with US1/US2**
- **US4 (P2)**: Can start after Phase 2 — no dependencies on other stories; **can run in parallel with US1/US2/US3**
- **US5 (P3)**: Should start after US1–US4 complete (validates their accessibility behavior)

### Within Each User Story

- Theme/typography foundations before component styling
- Core component before feature module integration
- Implementation before accessibility verification
- Commit after each task or logical group

### Parallel Opportunities

- All Phase 2 tasks T005–T009 can run in parallel (different files)
- US1 and US2 can execute in parallel (different files: navigation vs feature module typography)
- US3 tasks T025–T030 can all run in parallel (different component files)
- US4 tasks T033/T035 (action composables) can run in parallel with each other
- All Phase 8 screenshot update tasks T048–T054 can run in parallel

---

## Parallel Example: User Story 2 (Typography Rollout)

```bash
# Launch all feature module typography updates in parallel:
Task T015: "Update node detail screen typography"
Task T016: "Update message thread typography"
Task T017: "Update settings screens typography"
Task T018: "Update connections screen typography"
Task T019: "Update map screen typography"
Task T020: "Update firmware screen typography"
Task T021: "Update channel configuration typography"
Task T022: "Update radio configuration typography"
```

## Parallel Example: User Story 3 (Component Upgrades)

```bash
# Launch all component upgrades in parallel:
Task T025: "Enhance MenuFAB with tooltip and expressive shape"
Task T026: "Upgrade SliderPreference with spring animation"
Task T027: "Upgrade AlertDialogs with expressive shape/motion"
Task T028: "Audit progress indicators for wavy variants"
Task T029: "Apply expressive button styling"
Task T030: "Upgrade connection FABs"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Setup (T001–T003)
2. Complete Phase 2: Foundational (T004–T009)
3. Complete Phase 3: US1 Navigation (T010–T014)
4. Complete Phase 4: US2 Typography (T015–T024)
5. **STOP and VALIDATE**: App navigation and typography are fully expressive
6. Run `./gradlew spotlessApply detekt assembleDebug allTests`

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 Navigation → Test independently → commit (navigation expressive)
3. US2 Typography → Test independently → commit (typography rollout)
4. US3 Components → Test independently → commit (component upgrades)
5. US4 List Interactions → Test independently → commit (swipe-to-action)
6. US5 Accessibility → Test independently → commit (focus + reduced-motion)
7. Polish → Screenshot updates + validation → commit (cross-cutting)

### Parallel Team Strategy

With multiple developers after Foundational phase:
- Developer A: US1 (Navigation) + US2 (Typography) — P1 priority
- Developer B: US3 (Components) — P2 priority
- Developer C: US4 (List Interactions) — P2 priority
- All converge: US5 (Accessibility validation) → Polish

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable
- Per research.md: only CMP material3 1.11.0-alpha07 available APIs are used — no manual reimplementations
- Per clarification: no feature flags; each PR is atomic and revertible via git
- Per spec: swipe gestures excluded from channel list (drag-and-drop conflict)
- Commit after each task or logical group
