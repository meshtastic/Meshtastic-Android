---

description: "Task list template for feature implementation"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **KMP commonMain**: `feature/[name]/src/commonMain/kotlin/org/meshtastic/feature/[name]/`
- **Core modules**: `core/[module]/src/commonMain/kotlin/org/meshtastic/core/[module]/`
- **Core UI**: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/`
- **Settings**: `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/`
- **Resources**: `core/resources/src/commonMain/composeResources/values/strings.xml`
- **Tests (KMP)**: `feature/[name]/src/commonTest/kotlin/`
- **Tests (Android-only)**: `app/src/test/`

<!-- 
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.
  
  The /speckit.tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/
  
  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment
  
  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Design gate, enums, DataStore/Room keys, and string resources required by all user stories.

- [ ] T001 `[UI-GATE]` Review `.skills/design-standards/SKILL.md` and upstream Meshtastic design standards; record constraints for new composables. This task blocks all UI work.
- [ ] T002 [P] Create model/enum files in `feature/[name]/src/commonMain/.../model/`
- [ ] T003 [P] Add DataStore/Room preference keys in `core/prefs/` or `core/datastore/`
- [ ] T004 Add string resources to `core/resources/src/commonMain/composeResources/values/strings.xml`. Run `python3 scripts/sort-strings.py` after.

**Dependencies**: T001 blocks all UI phases. T002 and T003 are independent.
**Checkpoint**: Preference infrastructure ready — all user stories can now begin.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure, accessibility fixes, and ViewModel wiring that MUST complete before ANY user story can ship.

- [ ] T005 [Required existing file changes — e.g., accessibility fixes, ViewModel wiring]
- [ ] T006 [Modify ViewModel to expose new StateFlows from DataStore]

**Dependencies**: Phase 1 must complete first.
**Checkpoint**: Foundation ready — user story implementation can begin.

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Implementation for User Story 1

- [ ] T010 [P] [US1] Create composable in `feature/[name]/src/commonMain/.../component/[Name].kt`
- [ ] T011 [US1] Implement core UI logic (depends on T010)
- [ ] T012 [US1] Wire into screen in `feature/[name]/src/commonMain/.../list/[Screen].kt`

**Checkpoint**: User Story 1 complete — [core feature] works end-to-end.

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Implementation for User Story 2

- [ ] T020 [US2] Implement [feature component]
- [ ] T021 [US2] Add settings UI in `feature/settings/src/commonMain/.../[Name].kt`

**Checkpoint**: User Story 2 complete.

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Performance validation, edge case hardening, tests, and verification.

- [ ] TXXX [P] Verify performance targets (e.g., 60fps scrolling with N+ items)
- [ ] TXXX [P] Ensure all float values use `NumberFormatter.format()` before display
- [ ] TXXX Validate edge cases documented in spec
- [ ] TXXX [P] Write unit tests in `feature/[name]/src/commonTest/`
- [ ] TXXX [P] Write Compose UI tests in `feature/[name]/src/commonTest/`
- [ ] TXXX Run `./gradlew :feature:[name]:allTests :core:[module]:allTests`
- [ ] TXXX Run `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — T001 UI-GATE blocks all UI phases
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **User Stories (Phase 3+)**: Depend on Phase 2 completion
  - User stories proceed sequentially by priority (P1 → P2 → P3) unless independent
- **Polish (Final Phase)**: Depends on all user story phases

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational — No dependencies on other stories
- **User Story 2 (P2)**: [May depend on US1 scaffold / Can start after Foundational]
- **User Story 3 (P3)**: [May depend on US1/US2 / Independent after Foundational]

### Critical Path

```
Phase 1 → Phase 2 → Phase 3 (US1) → ... → Phase N (Polish)
```

### Parallel Opportunities

```
[List tasks that can run in parallel — different files, no dependencies]
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (design gate + infrastructure)
2. Complete Phase 2: Foundational (ViewModel + accessibility)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test end-to-end, verify persistence, check TalkBack
5. Ship as MVP

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready
2. Phase 3: US1 → **MVP shippable**
3. Phase 4: US2 → Enhanced experience
4. Phase N: Polish → Tests + verification → Merge-ready

### Parallel Team Strategy

With multiple developers:

1. All complete Phase 1 + Phase 2 together
2. Once Foundational is done:
   - Developer A: US1 → US2 → ... *(critical path)*
   - Developer B: Independent stories *(parallel)*
3. Converge at Phase N (Polish)
