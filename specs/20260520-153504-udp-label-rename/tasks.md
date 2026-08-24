# Tasks: UDP Label Rename

**Input**: Design documents from `/specs/20260520-153504-udp-label-rename/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

**Tests**: No new automated tests requested. Verification via full build.

**Verification**: Constitution-required validation included (spotlessApply, detekt, assembleDebug, test, allTests).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Exact file paths included in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: No setup required — existing project, no new files or dependencies.

*(No tasks — project is already initialized and configured.)*

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: No foundational work needed — this is a single string value change in an existing file.

*(No tasks — no blocking prerequisites.)*

---

## Phase 3: User Story 1 - Identifiable UDP Toggle (Priority: P1) 🎯 MVP

**Goal**: Rename the `udp_enabled` string resource value from "Enabled" to "UDP broadcasting" so the toggle is self-describing on the Network Config screen.

**Independent Test**: Open the Network Config screen and verify the toggle label reads "UDP broadcasting."

### Implementation for User Story 1

- [X] T001 [US1] Change `udp_enabled` string value from "Enabled" to "UDP broadcasting" in `core/resources/src/commonMain/composeResources/values/strings.xml` (line 1304)
- [X] T002 [US1] Run `python3 scripts/sort-strings.py` to maintain alphabetical ordering and regenerate the strings index

**Checkpoint**: At this point, the label change is complete and the string resource file is properly sorted.

---

## Phase 4: User Story 2 - Cross-Platform Audit Alignment (Priority: P2)

**Goal**: Ensure the settings-validation-matrix audit correctly identifies the Android UDP broadcasting field as "Present."

**Independent Test**: The audit tool finds the toggle by its label "UDP broadcasting" and marks it as present.

*(No additional implementation tasks — US2 is satisfied by the same string change in US1. The descriptive label is what the audit tool searches for.)*

**Checkpoint**: Both user stories are satisfied by the single string value change.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Constitution-required verification and formatting

- [X] T003 Run `./gradlew spotlessApply` to apply formatting rules in `core/resources/src/commonMain/composeResources/values/strings.xml`
- [X] T004 Run `./gradlew detekt` to verify no static analysis violations
- [X] T005 Run `./gradlew assembleDebug test allTests` to verify full build and test suite passes
- [X] T006 Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Skipped — no setup needed
- **Foundational (Phase 2)**: Skipped — no blocking prerequisites
- **User Story 1 (Phase 3)**: Can start immediately
  - T001 → T002 (sort must run after the string change)
- **Polish (Phase 5)**: Depends on Phase 3 completion
  - T003 → T004 → T005 (sequential: format → lint → build)
  - T006 can run in parallel with T003–T005

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies — can start immediately
- **User Story 2 (P2)**: Fully satisfied by US1 implementation — no additional tasks

### Parallel Opportunities

- T003–T005 are sequential (each depends on prior passing)
- T006 is independent and can be verified at any time
- Overall this is a strictly sequential 6-task workflow due to the minimal scope

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Execute T001: Change the string value
2. Execute T002: Run sort script
3. **STOP and VALIDATE**: Verify string file is correct
4. Execute T003–T005: Full verification pipeline
5. Execute T006: Privacy confirmation
6. Ready for PR

### Execution Summary

| Phase | Tasks | Estimated Effort |
|-------|-------|-----------------|
| Phase 3 (US1) | T001, T002 | < 1 minute |
| Phase 5 (Polish) | T003–T006 | ~5 minutes (build time) |
| **Total** | **6 tasks** | **~6 minutes** |

---

## Notes

- This is a single-line value change — no code logic, no new files, no architectural changes
- The string key `udp_enabled` is retained (only the value changes) to avoid breaking Crowdin translations
- All verification is via existing Gradle tasks — no custom test code needed
- Commit after T002 (the functional change) and after T005 (verification passes)
