# Tasks: KMP Recommended Project Structure Alignment

**Input**: Design documents from `/specs/006-kmp-project-structure/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: No new automated tests are required by this specification. Validation is via existing build and test commands.

**Verification**: Every phase includes constitution-required validation tasks for formatting, static analysis, and the relevant compile/test commands.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing. US4 (Legacy DSL Block Migration) is the primary implementation work, subdivided by migration tier.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Phase 1: Setup (Baseline & Prerequisites)

**Purpose**: Establish a clean build baseline and verify the starting state before any changes

- [ ] T001 Verify clean build baseline by running `./gradlew assembleDebug :desktop:packageUberJarForCurrentOS allTests` and record clean `assembleDebug` wall-clock time (3 consecutive runs after `./gradlew clean`, median value) to `specs/006-kmp-project-structure/baseline-timing.txt` for NFR-001 comparison
- [ ] T002 Verify `DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS` passes before migration
- [x] T003 Run `grep -rn "android {" core/*/build.gradle.kts feature/*/build.gradle.kts` to document all 27 legacy `android {}` blocks as the pre-migration inventory

---

## Phase 2: Convention Plugin Hardening (US2 — Blocking Prerequisite)

**Purpose**: Harden convention plugin defaults so modules can rely on convention for `androidResources.enable = false`. This MUST complete before any module migration.

**Goal**: Convention plugins reflect recommended patterns — `configureKotlinMultiplatform()` absorbs the `androidResources.enable = false` default so 23 of 27 modules no longer need to set it explicitly.

**⚠️ CRITICAL**: No module migration (Phase 3–5) can begin until this phase is complete.

**Independent Test**: `./gradlew assembleDebug` passes — adding the default is backward-compatible since all modules currently override it.

- [x] T004 [US2] Add `androidResources.enable = false` default to `configureKotlinMultiplatform()` inside the `pluginManager.withPlugin` block in `build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/KotlinAndroid.kt` (add after `minSdk` assignment, before namespace auto-derivation)
- [x] T005 [US2] Verify convention plugin change builds successfully: `./gradlew assembleDebug :desktop:packageUberJarForCurrentOS allTests`

**Checkpoint**: Convention plugin hardened — module migration can now begin

---

## Phase 3: Tier 1 Module Migration — Simple Modules (US4, Priority: P1) 🎯 MVP

**Goal**: Migrate 6 modules where convention handles everything — remove `android {}` block entirely, remove redundant `jvm()` declarations, remove explicit `namespace` (auto-derived), remove `androidResources.enable = false` (convention default).

**Independent Test**: `./gradlew assembleDebug allTests` passes after all 6 modules migrated. Verify with `grep -rn "android {" core/di/build.gradle.kts core/nfc/build.gradle.kts core/ui/build.gradle.kts core/navigation/build.gradle.kts feature/settings/build.gradle.kts feature/messaging/build.gradle.kts` returning zero matches.

### Implementation for Tier 1

- [x] T006 [P] [US4] Migrate `core/di/build.gradle.kts` — remove `android {}` block (namespace auto-derived, resources disabled by convention), remove redundant `jvm()` if present
- [x] T007 [P] [US4] Migrate `core/nfc/build.gradle.kts` — remove `android {}` block (namespace auto-derived, resources disabled by convention)
- [x] T008 [P] [US4] Migrate `core/ui/build.gradle.kts` — remove `android {}` block (namespace auto-derived, resources disabled by convention), remove redundant `jvm()` if present
- [x] T009 [P] [US4] Migrate `core/navigation/build.gradle.kts` — remove `android {}` block (namespace auto-derived)
- [x] T010 [P] [US4] Migrate `feature/settings/build.gradle.kts` — remove `android {}` block (namespace auto-derived, resources disabled by convention)
- [x] T011 [P] [US4] Migrate `feature/messaging/build.gradle.kts` — remove `android {}` block (namespace auto-derived, resources disabled by convention)
- [x] T012 [US4] Verify Tier 1 batch: `./gradlew assembleDebug allTests` — commit must be independently buildable (NFR-003)

**Checkpoint**: Tier 1 complete — 6 of 27 modules migrated, build passes

---

## Phase 4: Tier 2 Module Migration — Standard Modules with Host Tests (US4, Priority: P1)

**Goal**: Migrate 18 modules that opt into `withHostTest {}`. Replace `android {}` with `androidLibrary { withHostTest {} }`, remove redundant `jvm()`, remove explicit `namespace` (auto-derived except `feature:wifi-provision`), remove `androidResources.enable = false` (convention default).

**Independent Test**: `./gradlew assembleDebug :desktop:packageUberJarForCurrentOS allTests` passes after all 18 modules migrated.

### Tier 2a: Modules with `withHostTest { isIncludeAndroidResources = true }` (13 modules)

- [x] T013 [P] [US4] Migrate `core/ble/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove redundant `jvm()`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T014 [P] [US4] Migrate `core/common/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove redundant `jvm()`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T015 [P] [US4] Migrate `core/data/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove redundant `jvm()`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T016 [P] [US4] Migrate `core/domain/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove redundant `jvm()`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T017 [P] [US4] Migrate `core/model/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove redundant `jvm()`, remove `androidResources.enable = false`
- [x] T018 [P] [US4] Migrate `core/network/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove redundant `jvm()`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T019 [P] [US4] Migrate `core/service/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T020 [P] [US4] Migrate `core/takserver/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove redundant `jvm()`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T021 [P] [US4] Migrate `feature/connections/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T022 [P] [US4] Migrate `feature/firmware/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T023 [P] [US4] Migrate `feature/intro/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T024 [P] [US4] Migrate `feature/map/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove redundant `jvm()`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T025 [P] [US4] Migrate `feature/node/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true } }`, remove explicit namespace, remove `androidResources.enable = false`

### Tier 2b: Modules with empty `withHostTest {}` (4 modules)

- [x] T026 [P] [US4] Migrate `core/datastore/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest {} }`, remove redundant `jvm()`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T027 [P] [US4] Migrate `core/prefs/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest {} }`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T028 [P] [US4] Migrate `core/repository/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest {} }`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T029 [P] [US4] Migrate `core/testing/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest {} }`, remove explicit namespace, remove `androidResources.enable = false`

### Tier 2c: Namespace override required (1 module)

- [x] T030 [P] [US4] Migrate `feature/wifi-provision/build.gradle.kts` — replace `android {}` with `androidLibrary { namespace = "org.meshtastic.feature.wifiprovision"; withHostTest {} }`, remove redundant `jvm()`, remove `androidResources.enable = false` (namespace MUST be explicit — auto-derived `feature.wifi.provision` differs from required `feature.wifiprovision`)

### Tier 2 Verification

- [x] T031 [US4] Verify Tier 2 batch: `./gradlew assembleDebug :desktop:packageUberJarForCurrentOS allTests` and `DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS` — commit must be independently buildable (NFR-003)

**Checkpoint**: Tiers 1 + 2 complete — 24 of 27 modules migrated, build passes

---

## Phase 5: Tier 3 Module Migration — Special Modules (US4, Priority: P1)

**Goal**: Migrate 3 modules with custom configuration (minSdk override, device tests, resource prefix).

**Independent Test**: `./gradlew assembleDebug :desktop:packageUberJarForCurrentOS allTests` passes. All special configurations preserved.

### Implementation for Tier 3

- [x] T032 [P] [US4] Migrate `core/proto/build.gradle.kts` — replace `android {}` with `androidLibrary { minSdk = 21 }` (ATAK compatibility override), remove `androidResources.enable = false`
- [x] T033 [P] [US4] Migrate `core/database/build.gradle.kts` — replace `android {}` with `androidLibrary { withHostTest { isIncludeAndroidResources = true }; withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" } }`, remove explicit namespace, remove `androidResources.enable = false`
- [x] T034 [P] [US4] Migrate `core/resources/build.gradle.kts` — replace `android {}` with `androidLibrary { androidResources { enable = true; resourcePrefix = "meshtastic_" }; withHostTest { isIncludeAndroidResources = true } }`, remove redundant `jvm()` (resources MUST override convention default to `enable = true`)
- [x] T035 [US4] Verify Tier 3 batch: `./gradlew assembleDebug :desktop:packageUberJarForCurrentOS allTests` — commit must be independently buildable (NFR-003)

**Checkpoint**: All 27 KMP modules migrated — zero legacy `android {}` blocks remain

---

## Phase 6: Full Verification & Build Validation (US1, Priority: P1)

**Goal**: Developer builds successfully after restructuring — full verification across all build modes and configurations.

**Independent Test**: All 4 acceptance scenarios from US1 pass.

- [ ] T036 [US1] Run full Android build: `./gradlew assembleDebug` — verify zero errors
- [ ] T037 [US1] Run full Desktop build: `./gradlew :desktop:packageUberJarForCurrentOS` — verify zero errors
- [ ] T038 [US1] Run all tests: `./gradlew allTests` — verify zero regressions
- [ ] T039 [US1] Run DESKTOP_ONLY mode: `DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS` — verify succeeds without Android SDK
- [ ] T040 [US1] Compare clean `assembleDebug` time against baseline from T001 (3 consecutive runs after `./gradlew clean`, median value, same machine) — verify <5% increase (NFR-001)

**Checkpoint**: US1 complete — all build modes verified

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, cleanup, and constitution compliance

- [x] T041 [P] Verify no legacy `android {}` blocks remain: `grep -rn "android {" core/*/build.gradle.kts feature/*/build.gradle.kts | grep -v "widget\|api\|barcode\|androidLibrary\|androidResources\|androidMain\|androidHostTest\|androidDeviceTest\|androidRuntimeClasspath"` — must return zero matches (SC-001)
- [x] T042 [P] Verify no redundant `jvm()` calls remain in migrated modules: `grep -n "jvm()" core/*/build.gradle.kts feature/*/build.gradle.kts | grep -v "widget\|api\|barcode"` — must return zero matches
- [x] T043 [P] Verify `configureKotlinMultiplatform()` in `build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/KotlinAndroid.kt` uses only `KotlinMultiplatformAndroidLibraryTarget` API — no legacy `android {}` extension configuration (SC-007)
- [x] T044 [P] [US3] Verify module dependency direction is preserved: no `core/` module depends on `feature/` modules (FR-006)
- [x] T045 [P] Run constitution-required verification: `./gradlew spotlessApply spotlessCheck detekt`
- [x] T046 [P] Verify Gradle configuration cache, isolated projects, and parallel execution remain functional (NFR-002): `./gradlew assembleDebug --configuration-cache`
- [ ] T047 Validate quickstart.md in `specs/006-kmp-project-structure/quickstart.md` matches final implementation — update if migration steps differ from what was executed. Ensure guide is generic enough for other Meshtastic platform repos to reference (NFR-004)
- [x] T048 [US2] Add FR-008 enforcement: add a CI-time grep check in Phase 7 verification or a Gradle `afterEvaluate` assertion in `configureKotlinMultiplatform()` that fails the build if any KMP module contains a legacy `android {}` block inside `kotlin {}` — prevents future regressions (FR-008)
- [ ] T049 [P] Verify SC-006: create a temporary scratch module applying `meshtastic.kmp.library`, confirm it configures correctly for Android + Desktop with no manual `android {}` block, then remove it

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Convention Plugin Hardening (Phase 2)**: Depends on Phase 1 — BLOCKS all module migrations
- **Tier 1 Migration (Phase 3)**: Depends on Phase 2 completion
- **Tier 2 Migration (Phase 4)**: Depends on Phase 2 completion (can run in parallel with Phase 3)
- **Tier 3 Migration (Phase 5)**: Depends on Phase 2 completion (can run in parallel with Phases 3–4)
- **Full Verification (Phase 6)**: Depends on Phases 3, 4, and 5 completion
- **Polish (Phase 7)**: Depends on Phase 6 completion

### User Story Dependencies

- **US2 (Convention Plugins)**: Phase 2 — Must complete first; blocking prerequisite
- **US4 (Legacy DSL Migration)**: Phases 3, 4, 5 — Primary implementation; depends on US2
- **US1 (Build Verification)**: Phase 6 — Validation of US4 work; depends on all tiers complete
- **US3 (Module Boundary Clarity)**: Verified in Phase 7 (T044) — no implementation needed, existing structure already satisfies

### Within Each Tier

- All module migrations within a tier marked [P] can run in parallel (different files, no dependencies)
- Tier verification task must run after all module tasks in that tier complete
- Tiers 1, 2, and 3 can themselves run in parallel after Phase 2 completes

### Parallel Opportunities

- **Phase 2**: Sequential (single file change + verification)
- **Phase 3**: T006–T011 all parallel (6 different `build.gradle.kts` files)
- **Phase 4**: T013–T030 all parallel (18 different `build.gradle.kts` files)
- **Phase 5**: T032–T034 all parallel (3 different `build.gradle.kts` files)
- **Phase 7**: T041–T046 all parallel (independent verification commands)
- **Cross-tier**: Phases 3, 4, 5 can all run in parallel after Phase 2

---

## Parallel Example: Tier 1 Migration

```text
# All 6 Tier 1 modules can be migrated simultaneously:
T006: core/di/build.gradle.kts
T007: core/nfc/build.gradle.kts
T008: core/ui/build.gradle.kts
T009: core/navigation/build.gradle.kts
T010: feature/settings/build.gradle.kts
T011: feature/messaging/build.gradle.kts

# Then verify the batch:
T012: ./gradlew assembleDebug allTests
```

## Parallel Example: Full Tier Parallelism

```text
# After Phase 2 (convention plugin hardening), all three tiers can start simultaneously:
Tier 1 (T006–T012): 6 simple modules — remove android {} entirely
Tier 2 (T013–T031): 18 standard modules — replace with androidLibrary { withHostTest {} }
Tier 3 (T032–T035): 3 special modules — custom config (minSdk, device tests, resources)

# Maximum parallelism: 27 module files can be edited simultaneously
```

---

## Implementation Strategy

### MVP First (Tier 1 Only)

1. Complete Phase 1: Setup (baseline)
2. Complete Phase 2: Convention plugin hardening (T004–T005)
3. Complete Phase 3: Tier 1 — 6 simple modules (T006–T012)
4. **STOP and VALIDATE**: Build passes, 6 modules proven
5. This alone demonstrates the migration pattern works

### Incremental Delivery

1. Setup + Convention Plugin Hardening → Foundation ready
2. Tier 1 (6 modules) → Build verified → Pattern proven (MVP!)
3. Tier 2 (18 modules) → Build verified → Bulk migration complete
4. Tier 3 (3 modules) → Build verified → All 27 modules done
5. Full verification + polish → Ready for PR

### Key Risk Mitigation

- Each tier is independently verifiable — if Tier 2 breaks, Tier 1 is still valid
- Convention plugin change (T004) is backward-compatible — existing explicit overrides in modules are no-ops
- Individual module migrations can be reverted independently (`git checkout -- <module>/build.gradle.kts`)
- `feature:wifi-provision` is the only namespace edge case — flagged explicitly in T030

---

## Notes

- [P] tasks = different files, no dependencies on each other
- [US4] is the primary user story — all module migration tasks belong to it
- No source code files are modified — only `build.gradle.kts` files and one convention plugin `.kt` file
- The spec lists 28 modules but `core:model` was counted in both research.md Tier 1 and data-model.md Tier 2 — data-model.md (Tier 2 with `withHostTest`) is authoritative, yielding 27 total KMP modules
- `feature:widget`, `core:api`, `core:barcode` are Android-only and NOT affected by this migration
- Commit after each tier verification for clean rollback points
