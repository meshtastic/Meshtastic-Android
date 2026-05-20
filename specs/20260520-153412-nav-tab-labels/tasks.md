# Tasks: Reorder Bottom Navigation Tab Labels

**Input**: Design documents from `specs/20260520-153412-nav-tab-labels/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

**Tests**: No new automated tests requested in the feature specification. Existing tests will be updated to reflect the rename but no new test coverage is added.

**Verification**: Constitution-required validation tasks are included for formatting, static analysis, and compile/test commands.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (String Resources)

**Purpose**: Add new string resource keys required by all subsequent tasks

- [X] T001 Add `messages` and `connect` string keys to `core/resources/src/commonMain/composeResources/values/strings.xml`
- [X] T002 Run `python3 scripts/sort-strings.py` to maintain alphabetical ordering and regenerate strings-index.txt

---

## Phase 2: Foundational (Enum Rename)

**Purpose**: Rename the `TopLevelDestination` enum entries — this BLOCKS all reference updates

**⚠️ CRITICAL**: No reference update tasks can begin until this phase is complete

- [X] T003 Rename `Conversations` → `Messages` and `Connections` → `Connect` in `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/TopLevelDestination.kt` — update label resource references from `Res.string.conversations` → `Res.string.messages` and `Res.string.connections` → `Res.string.connect`

**Checkpoint**: Enum entries renamed — reference updates can now proceed in parallel

---

## Phase 3: User Story 1 — See Updated Tab Labels (Priority: P1) 🎯 MVP

**Goal**: Bottom navigation bar displays "Messages" (position 1) and "Connect" (position 5) as tab labels

**Independent Test**: Launch the app → visually confirm bottom navigation shows "Messages" and "Connect" in correct positions

### Implementation for User Story 1

- [X] T004 [P] [US1] Update icon `when` branches for `Messages` and `Connect` in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/navigation/TopLevelDestinationExt.kt`
- [X] T005 [P] [US1] Update any explicit references from `Conversations`/`Connections` to `Messages`/`Connect` in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/MeshtasticNavigationSuite.kt`
- [X] T006 [P] [US1] Update default tab reference from `Connections` to `Connect` in `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/MultiBackstack.kt`
- [X] T007 [P] [US1] Update references in `androidApp/src/main/kotlin/org/meshtastic/app/ui/Main.kt`
- [X] T008 [P] [US1] Update references in `desktopApp/src/main/kotlin/org/meshtastic/desktop/Main.kt`
- [X] T009 [P] [US1] Update references in `desktopApp/src/main/kotlin/org/meshtastic/desktop/navigation/DesktopNavigation.kt`

**Checkpoint**: Tab labels display correctly — User Story 1 is visually complete

---

## Phase 4: User Story 2 — Navigation Still Functions After Rename (Priority: P1)

**Goal**: Tapping "Messages" and "Connect" tabs navigates to the correct screens without behavior change

**Independent Test**: Tap "Messages" tab → messaging screen loads; tap "Connect" tab → connection/pairing screen loads; rapid tab switching works without errors

### Implementation for User Story 2

- [X] T010 [US2] Update test references from `TopLevelDestination.Connections` to `TopLevelDestination.Connect` (8 locations) in `core/navigation/src/commonTest/kotlin/org/meshtastic/core/navigation/MultiBackstackTest.kt`

**Checkpoint**: Navigation functions correctly with renamed enum entries — no behavioral regression

---

## Phase 5: User Story 3 — Deep Links and State Restoration Work (Priority: P2)

**Goal**: Deep links and saved navigation state continue to route correctly after the rename

**Independent Test**: Trigger a message notification → tap it → app opens to messaging screen. Kill and restore app → previously selected tab is restored.

### Implementation for User Story 3

No additional implementation tasks required. Deep links and state restoration use typed route objects (`ContactsRoute.Contacts`, `ConnectionsRoute.Connections`) which are NOT affected by the enum entry rename. This was confirmed in research.md (Research Task 1).

**Checkpoint**: Verified by existing tests — no code changes needed for this story

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verification and constitution-required validation

- [X] T011 [P] Run `./gradlew spotlessApply` to auto-format all touched files
- [X] T012 [P] Run `./gradlew spotlessCheck detekt` to confirm zero lint violations
- [X] T013 Run `./gradlew assembleDebug` to verify project compiles successfully
- [X] T014 Run `./gradlew test allTests` to verify all existing tests pass with renamed references
- [X] T015 Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (string resources must exist before enum references them)
- **User Story 1 (Phase 3)**: Depends on Phase 2 (enum must be renamed before updating references)
- **User Story 2 (Phase 4)**: Depends on Phase 2 (enum must be renamed before updating test references)
- **User Story 3 (Phase 5)**: No implementation needed — verified by Phase 4 tests passing
- **Polish (Phase 6)**: Depends on Phases 3 and 4 completion

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) — Independent of US1 (different files)
- **User Story 3 (P2)**: Zero implementation — verified by existing route-based navigation tests

### Parallel Opportunities

- **Phase 3**: ALL tasks T004–T009 can run in parallel (each touches a different file)
- **Phase 4**: T010 can run in parallel with Phase 3 tasks (different file: test vs production)
- **Phase 6**: T011 and T012 can run in parallel with T015

---

## Parallel Example: User Stories 1 & 2

```bash
# After Phase 2 completes, launch ALL of these in parallel:
Task T004: "Update TopLevelDestinationExt.kt icon branches"
Task T005: "Update MeshtasticNavigationSuite.kt references"
Task T006: "Update MultiBackstack.kt default tab reference"
Task T007: "Update androidApp Main.kt references"
Task T008: "Update desktopApp Main.kt references"
Task T009: "Update DesktopNavigation.kt references"
Task T010: "Update MultiBackstackTest.kt test references"
```

---

## Implementation Strategy

### MVP First (User Stories 1 & 2)

1. Complete Phase 1: Add string resources (T001–T002)
2. Complete Phase 2: Rename enum entries (T003)
3. Complete Phase 3: Update all production references (T004–T009) — **in parallel**
4. Complete Phase 4: Update test references (T010) — **parallel with Phase 3**
5. **STOP and VALIDATE**: Run `./gradlew assembleDebug test allTests`
6. Complete Phase 6: Polish and verification (T011–T015)

### Total Effort Estimate

- **Sequential execution**: 15 tasks, ~30 minutes (mechanical renames)
- **Parallel execution**: Critical path is 6 steps (T001 → T002 → T003 → T004‖T010 → T013 → T014)
- **Risk**: Low — all changes are mechanical identifier renames with no logic changes

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- User Story 3 requires zero implementation — validated by passing tests from US2
- All Phase 3 tasks are mechanical `Conversations` → `Messages` and `Connections` → `Connect` identifier replacements
- Commit after Phase 2 and after Phase 3+4 combined for clean git history
- Old string keys (`conversations`, `connections`) are deliberately RETAINED — they serve as screen titles in other modules
