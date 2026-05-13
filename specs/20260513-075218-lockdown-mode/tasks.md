# Tasks: Lockdown Mode

**Input**: Design documents from `specs/20260513-075218-lockdown-mode/`  
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅  
**Base**: Building on Nick's PR #5439 (`features/lockdown-v2` branch, 785+ additions)

## Phase 0: Cherry-pick PR #5439

**Purpose**: Establish baseline from Nick's working proof-of-concept before refactoring

- [ ] T000a Fetch Nick's `features/lockdown-v2` branch and cherry-pick/rebase onto `feat/lockdown-mode` (resolve conflicts against current `origin/main`)
- [ ] T000b Verify cherry-picked code compiles: `./gradlew assembleDebug` (expect lint/detekt issues — fix in later phases)
- [ ] T000c Inventory PR files for subsequent refactoring: identify which files stay as-is, which move modules, which need interface extraction

---

## Phase 1: Setup

**Purpose**: Establish module structure and dependencies for lockdown feature

- [ ] T001 Create `core/model/src/commonMain/kotlin/org/meshtastic/core/model/lockdown/` package directory
- [ ] T002 [P] Verify `androidx.security:security-crypto` dependency exists in `core/datastore/build.gradle.kts` (already added by PR — confirm in correct module)
- [ ] T003 [P] Verify proto submodule contains `LockdownAuth` and `LockdownStatus` generated classes in `core/proto/build/generated/source/wire/org/meshtastic/proto/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extract and refactor PR #5439 code into proper KMP architecture

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

**Note**: Nick's PR contains working implementations for most of these. Tasks below specify what to **port/refactor** from the PR rather than creating from scratch.

- [ ] T004 Port `LockdownState` sealed class from PR's `core/model/.../LockdownState.kt` → refactor to add `Locked(lockReason: LockdownStatus.State)` (use proto enum directly, not Int), verify 8 variants match spec: NotApplicable, NeedsProvision, Locked, Unlocking, Unlocked(bootsRemaining, validUntilEpoch), UnlockFailed(backoffSeconds), LockNowPending, LockNowAcknowledged
- [ ] T005 [P] Extract `LockdownCoordinator` interface from PR's concrete class to `core/repository/src/commonMain/kotlin/org/meshtastic/core/repository/LockdownCoordinator.kt` — add lifecycle hooks from PR: `onConnect(nodeId: Int)`, `onConfigComplete()`, `onDisconnect()` alongside `state`, `isAuthorized`, `handleStatus()`, `submitPassphrase()`, `lockNow()`
- [ ] T006 [P] Extract `LockdownPassphraseStore` interface from PR's concrete class to `core/repository/src/commonMain/kotlin/org/meshtastic/core/repository/LockdownPassphraseStore.kt` with get(nodeId), put(nodeId, passphrase), clear(nodeId)
- [ ] T007 Move PR's `LockdownPassphraseStore` impl from `core/service/src/androidMain/` to `core/datastore/src/androidMain/kotlin/org/meshtastic/core/datastore/LockdownPassphraseStoreImpl.kt` — keep EncryptedSharedPreferences logic, implement extracted interface
- [ ] T008 [P] Create `LockdownPassphraseStoreImpl` no-op stub for JVM in `core/datastore/src/jvmMain/kotlin/org/meshtastic/core/datastore/LockdownPassphraseStoreImpl.kt`
- [ ] T009 [P] Create `LockdownPassphraseStoreImpl` no-op stub for iOS in `core/datastore/src/iosMain/kotlin/org/meshtastic/core/datastore/LockdownPassphraseStoreImpl.kt`
- [ ] T010 Extract state machine logic from PR's `LockdownHandlerImpl` (currently in `core/service/src/androidMain/`) to `core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/LockdownCoordinatorImpl.kt` — keep auto-replay, wasLockNow flag, pending passphrase tracking. Remove Android/AIDL dependencies so it compiles in commonMain.
- [ ] T010b Keep thin AIDL adapter in `core/service/src/androidMain/` that delegates to `LockdownCoordinatorImpl` for `MeshService` IPC calls (`sendLockdownPassphrase`, `sendLockNow`)
- [ ] T011 Verify PR's `FromRadioPacketHandlerImpl` `lockdown_status` dispatch is intact; add `coordinator.onConfigComplete()` call from config completion handler if not already present
- [ ] T012 Verify PR's `CommandSenderImpl` extensions (`sendLockdownPassphrase`/`sendLockNow`) are intact; adapt method signatures if coordinator interface changed
- [ ] T012b Wire `LockdownCoordinator.onConnect(nodeId)`/`onDisconnect()` into `MeshConnectionManagerImpl` connection lifecycle callbacks (port from PR's existing wiring)
- [ ] T012c Expose `lockdownState: StateFlow<LockdownState>` and `sessionAuthorized: StateFlow<Boolean>` via `ServiceRepository` (port from PR's existing exposure)
- [ ] T013 Register `LockdownCoordinator` and `LockdownPassphraseStore` bindings in Koin DI — use `@Single` annotation on impl classes (`LockdownCoordinatorImpl`, `LockdownPassphraseStoreImpl`) and `@Module` on containing Koin module per project convention

**Checkpoint**: Foundation ready — coordinator processes lockdown status, sends auth, manages state. AIDL layer delegates to coordinator. User story UI can begin.

---

## Phase 3: User Story 1 — Unlock a Locked Node (Priority: P1) 🎯 MVP

**Goal**: User connects to a locked node, enters passphrase, node unlocks, full config accessible.

**Independent Test**: Connect to a locked node → enter correct passphrase → verify UNLOCKED state and config access.

### Implementation for User Story 1

- [ ] T014 [US1] Move and refactor Nick's `LockdownUnlockDialog` from `app/src/main/.../ui/` to `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/lockdown/LockdownDialog.kt` — adapt to non-dismissable AlertDialog composable with passphrase field, submit button, error display, and disconnect option (`onDismissRequest = {}` + `BackHandler`)
- [ ] T015 [US1] Implement unlock flow in `LockdownDialog`: passphrase entry → call `coordinator.submitPassphrase()` → show loading state → handle UNLOCKED/UNLOCK_FAILED transitions
- [ ] T016 [US1] Implement backoff enforcement in `LockdownDialog`: when `UnlockFailed(backoffSeconds > 0)`, show countdown timer and disable Submit button until backoff expires
- [ ] T017 [US1] Integrate `LockdownDialog` in app shell composable — expose `lockdownState` from ViewModel (port PR's `UIViewModel.lockdownState` pattern), observe state, show dialog when state is Locked/NeedsProvision/Unlocking/UnlockFailed, dismiss when Unlocked/NotApplicable
- [ ] T018 [US1] Add string resources for lockdown UI: "Unlock Device", "Enter passphrase", "Incorrect passphrase", "Retry in %d seconds", "Disconnect" in `core/resources/src/commonMain/composeResources/values/strings.xml`
- [ ] T019 [US1] Run `python3 scripts/sort-strings.py` after adding string resources

**Checkpoint**: User Story 1 complete — locked nodes can be unlocked via non-dismissable dialog.

---

## Phase 4: User Story 2 — Provision a New Lockdown Passphrase (Priority: P1)

**Goal**: User connects to an unprovisioned node, creates a passphrase with optional TTL, node provisions DEK and unlocks.

**Independent Test**: Connect to unprovisioned node → set passphrase → verify UNLOCKED with session info.

### Implementation for User Story 2

- [ ] T020 [US2] Add provision mode to `LockdownDialog`: when state is `NeedsProvision`, show "Set Passphrase" title, passphrase + confirm fields, optional "Boots remaining" and "Hours until expiry" number inputs
- [ ] T021 [US2] Implement passphrase validation: non-empty, 1-32 bytes, confirm field matches, empty TTL fields send 0
- [ ] T022 [US2] Convert "hours until expiry" user input to `valid_until_epoch` (current Unix time + hours * 3600) before sending to coordinator
- [ ] T023 [US2] Add string resources for provision mode: "Set Passphrase", "Confirm passphrase", "Passphrases do not match", "Boots remaining (optional)", "Hours until expiry (optional)" in `core/resources/src/commonMain/composeResources/values/strings.xml`
- [ ] T024 [US2] Run `python3 scripts/sort-strings.py` after adding string resources

**Checkpoint**: User Story 2 complete — unprovisioned nodes can be set up with a passphrase.

---

## Phase 5: User Story 3 — Lock Now (Priority: P2)

**Goal**: User presses "Lock Now" in Security settings, device re-locks and reboots, app disconnects gracefully.

**Independent Test**: Unlock node → press Lock Now → verify device disconnects and next connection requires auth.

### Implementation for User Story 3

- [ ] T025 [US3] Create `LockNowButton` composable in `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/lockdown/LockNowButton.kt` — visible when `coordinator.state != NotApplicable` (firmware supports lockdown), enabled only when state is `Unlocked`, hidden/disabled with hint when locked or not applicable
- [ ] T026 [US3] Wire `LockNowButton` into existing `SecurityConfigItemList.kt` (port PR's integration point in `feature/settings/src/commonMain/kotlin/.../radio/component/SecurityConfigItemList.kt`)
- [ ] T027 [US3] Implement explicit disconnect in `LockdownCoordinatorImpl` after `LockNowAcknowledged` state: delay 500ms → call connection manager disconnect
- [ ] T028 [US3] Handle `LockNowPending` and `LockNowAcknowledged` states in `LockdownDialog` overlay: show "Locking device..." spinner and "Device locked" confirmation
- [ ] T029 [US3] Add string resources: "Lock Now", "Locking device...", "Device locked", "Device is locked" in `core/resources/src/commonMain/composeResources/values/strings.xml`
- [ ] T030 [US3] Run `python3 scripts/sort-strings.py` after adding string resources

**Checkpoint**: User Story 3 complete — users can actively re-lock devices.

---

## Phase 6: User Story 4 — Cached Passphrase Auto-Reconnect (Priority: P2)

**Goal**: Returning users reconnect without re-entering passphrase; auto-replay handles it transparently.

**Independent Test**: Authenticate → disconnect → reconnect → verify no passphrase prompt appears.

### Implementation for User Story 4

- [ ] T031 [US4] Implement auto-replay in `LockdownCoordinatorImpl`: on `Locked` state entry, check `passphraseStore.get(nodeId)`, if non-null call `submitPassphrase(cached, 0, 0)` automatically
- [ ] T032 [US4] Implement cache-on-success in `LockdownCoordinatorImpl`: on transition to `Unlocked` after user-entered passphrase (not auto-replay), call `passphraseStore.put(nodeId, passphrase)`
- [ ] T033 [US4] Implement cache-clear-on-failure: on `UnlockFailed` after auto-replay attempt, call `passphraseStore.clear(nodeId)` and transition to `Locked` (prompting user)
- [ ] T034 [US4] Add visual indicator in `LockdownDialog` for auto-replay in progress: show "Authenticating..." with spinner instead of passphrase fields while auto-replay is attempted

**Checkpoint**: User Story 4 complete — reconnections are seamless for cached passphrases.

---

## Phase 7: User Story 5 — View Session Token Status (Priority: P3)

**Goal**: Users see remaining session lifetime (boots, expiry) in Security settings.

**Independent Test**: Unlock node → view Security settings → verify boots remaining and expiry displayed.

### Implementation for User Story 5

- [ ] T035 [US5] Create `LockdownSessionStatus` composable in `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/lockdown/LockdownSessionStatus.kt` displaying boots remaining and formatted expiry time
- [ ] T036 [US5] Wire `LockdownSessionStatus` into `SecurityConfigScreen` above `LockNowButton` — visible only when coordinator state is `Unlocked`
- [ ] T037 [US5] Add string resources: "Session: %d reboots remaining", "expires %s", "no time limit", "no expiry configured" in `core/resources/src/commonMain/composeResources/values/strings.xml`
- [ ] T038 [US5] Run `python3 scripts/sort-strings.py` after adding string resources

**Checkpoint**: User Story 5 complete — session TTL info visible in settings.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Banner gating, privacy audit, lint, and final validation

- [ ] T039 [P] Gate all action-prompting banners (Region Unset, config warnings) on `lockdownCoordinator.isAuthorized` — suppress when not authorized
- [ ] T040 [P] Audit `LockdownCoordinatorImpl` and `LockdownPassphraseStoreImpl` logs: ensure no passphrase bytes are logged; redact device addresses to last 4 hex chars
- [ ] T041 [P] Review lockdown UI against Meshtastic design standards: M3 components, accessibility (TalkBack semantics, touch targets), typography hierarchy
- [ ] T042 [P] Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto`
- [ ] T043 [P] Verify `LockdownCoordinator.onDisconnect()` is called on connection disconnect (already wired in T012b) to ensure clean state for next connection
- [ ] T043b [P] Write unit tests for `LockdownCoordinatorImpl` state machine: cover all 8 state transitions, auto-replay success/failure, lock-now flow with wasLockNow flag, onDisconnect reset, and backoff enforcement
- [ ] T044 Run `./gradlew spotlessApply spotlessCheck detekt` for all touched modules
- [ ] T045 Run `./gradlew assembleDebug test allTests` to verify compilation and tests pass
- [ ] T046 Verify build with `./gradlew :core:model:allTests :core:repository:allTests :core:data:allTests :core:datastore:allTests :feature:settings:allTests`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 0 (Cherry-pick)**: No dependencies — must complete first to establish baseline code
- **Phase 1 (Setup)**: Depends on Phase 0 — verify module structure after cherry-pick
- **Phase 2 (Foundational)**: Depends on Phase 1 — refactors PR code into KMP architecture. BLOCKS all user stories
- **Phases 3-4 (US1, US2)**: Both depend on Phase 2; can run in parallel (US1 and US2 share the same `LockdownDialog` composable but address different states)
- **Phase 5 (US3)**: Depends on Phase 2; independent of US1/US2
- **Phase 6 (US4)**: Depends on Phase 2 + Phase 3 (auto-replay triggers from the same Locked state as US1)
- **Phase 7 (US5)**: Depends on Phase 5 (session status displayed near Lock Now button)
- **Phase 8 (Polish)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (Unlock)**: Phase 2 only — independently testable
- **US2 (Provision)**: Phase 2 only — independently testable (shares LockdownDialog with US1)
- **US3 (Lock Now)**: Phase 2 only — independently testable
- **US4 (Auto-Reconnect)**: Phase 2 + US1 (needs unlock flow to cache passphrase first)
- **US5 (Session Status)**: Phase 2 + US3 (displayed alongside Lock Now button)

### Parallel Opportunities

Within Phase 2:
- T005, T006 can run in parallel (independent interface extractions)
- T007, T008, T009 can run in parallel (platform impls of same interface)
- T010 and T010b can be done together (split coordinator from AIDL adapter)

Within user stories:
- US1 and US2 can be developed together (same screen, different states)
- US3 is fully independent
- All string resource tasks are parallelizable

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 0: Cherry-pick PR #5439 (baseline)
2. Complete Phase 1: Verify setup
3. Complete Phase 2: Refactor into KMP architecture (extract interfaces, move modules, split commonMain/androidMain)
4. Complete Phase 3 + 4: US1 + US2 together (they share `LockdownDialog`)
5. **STOP and VALIDATE**: Test unlock and provision flows
6. This delivers a functional lockdown client for day-one firmware support

### Incremental Delivery

1. Cherry-pick + Setup → Compilable baseline from PR
2. Foundational refactor → KMP-proper state machine
3. US1 + US2 → Unlock and provision functional (MVP!)
3. US3 → Lock Now button in Security settings
4. US4 → Auto-reconnect for returning users
5. US5 → Session info display
6. Polish → Banner gating, audit, lint
