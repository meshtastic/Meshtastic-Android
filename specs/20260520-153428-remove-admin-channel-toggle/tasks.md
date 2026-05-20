# Tasks: Remove Admin Channel Enabled Toggle

**Input**: Design documents from `/specs/20260520-153428-remove-admin-channel-toggle/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

**Tests**: No new automated tests requested by the feature specification. Verification relies on existing module tests and lint checks.

**Verification**: Constitution-required validation tasks (spotlessCheck, detekt, compile/test) are included in Phase 3.

**Organization**: This is a minimal single-file UI deletion. Given the simplicity (~9 lines removed), the phase structure is condensed.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Exact file paths included in descriptions

---

## Phase 1: Setup

**Purpose**: No project initialization needed — existing KMP multi-module project. This phase is intentionally empty.

**Checkpoint**: N/A — project already configured.

---

## Phase 2: User Story 1 — Security Config Screen Without Legacy Toggle (Priority: P1) 🎯 MVP

**Goal**: Remove the `admin_channel_enabled` SwitchPreference toggle and its HorizontalDivider from the Security Config screen so users see a cleaner interface focused on PKC-based administration.

**Independent Test**: Navigate to Security Config screen → verify the toggle is absent and all other settings remain functional. Run `./gradlew :feature:settings:allTests :feature:settings:compileKotlinJvm`.

### Implementation

- [x] T001 [US1] Remove unused import `org.meshtastic.core.resources.legacy_admin_channel` (line 49) in feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component/SecurityConfigScreen.kt
- [x] T002 [US1] Remove HorizontalDivider + SwitchPreference block for `admin_channel_enabled` (lines 207–214) in feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component/SecurityConfigScreen.kt

**Checkpoint**: Security Config screen renders without the admin channel toggle. The "Administration" TitledCard shows only the "Managed Mode" switch.

---

## Phase 3: Verification & Polish (Priority: P1)

**Purpose**: Constitution-required validation — formatting, static analysis, and compilation across all KMP targets.

- [x] T003 [P] Run `./gradlew spotlessApply spotlessCheck detekt` on `:feature:settings` module to ensure zero lint violations
- [x] T004 [P] Run `./gradlew :feature:settings:allTests :feature:settings:compileKotlinJvm` to verify all tests pass and all KMP targets compile
- [x] T005 Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto`

**Checkpoint**: All verification passes. Feature complete.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Empty — no action required
- **Phase 2 (US1 Implementation)**: Can start immediately
- **Phase 3 (Verification)**: Depends on Phase 2 completion

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies on other stories — this IS the feature
- **User Story 2 (P1)**: Verified implicitly by T004 (existing tests cover PKC admin functionality). No code changes needed — US2 is a non-regression guarantee satisfied by the test suite passing.

### Within Phase 2

- T001 and T002 are in the same file and operate on non-overlapping line ranges — they CAN be applied in parallel (different line regions) but are sequenced for clarity.

### Parallel Opportunities

- T003 and T004 can run in parallel (different Gradle tasks, independent checks)
- T005 is a manual/automated review that can happen alongside T003/T004

---

## Parallel Example: Verification Phase

```bash
# Launch verification tasks in parallel:
Task T003: "./gradlew spotlessApply spotlessCheck detekt"
Task T004: "./gradlew :feature:settings:allTests :feature:settings:compileKotlinJvm"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete T001 + T002: Remove import and toggle block (same file, ~9 lines)
2. Complete T003 + T004: Verify lint + tests pass
3. Complete T005: Privacy/proto confirmation
4. **DONE**: Feature is complete in a single increment

### Incremental Delivery

This feature is atomic — there is no meaningful increment smaller than the full change. The removal of the import (T001) and the toggle block (T002) must ship together to avoid lint failures.

---

## Notes

- Single file modified: `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component/SecurityConfigScreen.kt`
- No data model changes (proto field preserved per spec non-goals)
- No string resource removal (Crowdin-managed, separate concern)
- User Story 2 (PKC admin unaffected) is validated by existing test suite — no new code needed
- Total lines removed: ~9 (1 import + 8 composable lines)
