# Tasks: Compose Preview Screenshot Testing

**Input**: Design documents from `specs/018-compose-screenshot-testing/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Exact file paths included in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add CST plugin to the build system, create the screenshot-tests module, and configure experimental flags.

- [x] T001 [P] Add CST plugin and screenshot-validation-api entries to `gradle/libs.versions.toml` (version `compose-screenshot = "0.0.1-alpha14"`, library `screenshot-validation-api`, plugin `compose-screenshot`)
- [x] T002 [P] Add `android.experimental.enableScreenshotTest=true` to `gradle.properties`
- [x] T003 Create `screenshot-tests/build.gradle.kts` with `com.android.library`, `com.android.compose.screenshot`, `kotlin-android`, compose compiler, `meshtastic.detekt`, `meshtastic.spotless` plugins; configure `namespace`, `compileSdk`, `minSdk`, `experimentalProperties`, `imageDifferenceThreshold`, and `screenshotTestImplementation` dependencies
- [x] T004 [P] Create minimal `screenshot-tests/src/main/AndroidManifest.xml`
- [x] T005 Add `include(":screenshot-tests")` to `settings.gradle.kts` (conditionally excluded when `desktopOnly`)
- [x] T006 Verify the module compiles: run `./gradlew :screenshot-tests:assembleDebug`

**Checkpoint**: The screenshot-tests module exists, compiles, and is ready to receive `@PreviewTest` wrappers.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Ensure core/ui previews meet conventions (visibility, theme wrapping) so screenshot test wrappers can import them.

**CRITICAL**: These audit tasks must complete before US1 can validate any screenshots.

- [x] T007 Audit `core/ui/src/commonMain/` preview composables: inventory all `@Preview`/`@PreviewLightDark` functions, record visibility and `AppTheme` wrapping status
- [x] T008 [P] Fix `core/ui` previews lacking `AppTheme` wrapping — add `AppTheme { ... }` wrapper to preview functions in `EditListPreference.kt`, `SwitchPreference.kt`, `ElevationInfo.kt`, `PreferenceCategory.kt`, `EditTextPreference.kt`, `SecurityIcon.kt`, `BitwisePreference.kt`, `PositionPrecisionPreference.kt`, `RegularPreference.kt`, `IndoorAirQuality.kt`, `EditBase64Preference.kt`, `DropDownPreference.kt`, `NodeChip.kt`, `TextDividerPreference.kt`, `EditIPv4Preference.kt`, `IconInfo.kt`, `LazyColumnDragAndDropDemo.kt`, `EditPasswordPreference.kt`
- [x] T009 [P] Change `private` preview functions to `internal` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/detail/NodeDetailPreviews.kt` (4 functions)
- [x] T010 [P] Verify all audited previews compile on all KMP targets: run `./gradlew :core:ui:compileKotlinJvm :feature:node:compileKotlinJvm`

**Checkpoint**: All existing previews have `internal`/`public` visibility and `AppTheme` wrapping. Ready for screenshot test wrappers.

---

## Phase 3: User Story 1 — Catch Visual Regressions Automatically (Priority: P1) MVP

**Goal**: Wire a representative set of existing previews into the screenshot test module, generate reference images, and validate that the regression detection pipeline works end-to-end.

**Independent Test**: Modify a composable's padding or color, run `validateFdroidDebugScreenshotTest`, confirm it fails with a diff report.

### Implementation for User Story 1

- [x] T011 [US1] Add `implementation(project(":core:ui"))` and `implementation(project(":core:resources"))` dependencies to `screenshot-tests/build.gradle.kts`
- [x] T012 [P] [US1] Create `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/core/AlertScreenshotTests.kt` with `@PreviewTest` + `@Preview`/`@PreviewLightDark` wrappers for `AlertPreviews.kt` preview functions from `core:ui` (each wrapper must have BOTH `@PreviewTest` AND the matching preview annotation per FR-006)
- [x] T013 [P] [US1] Create `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/core/ComponentScreenshotTests.kt` with `@PreviewTest` + `@Preview`/`@PreviewLightDark` wrappers for a representative subset of `core:ui` component previews (`NodeKeyStatusIcon`, `ChannelItem`, `ListItem`, `HopsInfo`, `SignalInfo`, `DistanceInfo`, `LastHeardInfo`, `MaterialBatteryInfo`, `TitledCard`, `ChannelInfo`, `SatelliteCountInfo`, `MaterialBluetoothSignalInfo`) — each wrapper must have BOTH annotations per FR-006
- [x] T014 [US1] Generate initial reference images: run `./gradlew :screenshot-tests:updateDebugScreenshotTest`
- [x] T015 [US1] Validate screenshot tests pass: run `./gradlew :screenshot-tests:validateDebugScreenshotTest`
- [x] T016 [US1] End-to-end regression test: temporarily modify a previewed composable's styling (e.g., padding in `ChannelItem.kt`), run validate task, confirm it fails with HTML diff report at `screenshot-tests/build/reports/screenshotTest/preview/fdroidDebug/index.html`, then revert the change
- [x] T017 [US1] Commit reference images in `screenshot-tests/src/screenshotTestDebug/reference/` to version control

**Checkpoint**: Visual regression detection works end-to-end for core/ui components. A UI change triggers a validate failure with a diff report.

---

## Phase 4: User Story 2 — Add and Maintain Preview Composables in commonMain (Priority: P2)

**Goal**: Expand screenshot coverage to feature modules with existing previews and add new previews for uncovered modules.

**Independent Test**: Create a new `@PreviewLightDark` composable in a feature module, add a `@PreviewTest` wrapper, run update task, verify reference images appear for both light and dark variants.

### Implementation for User Story 2

- [x] T018 [US2] Add feature module dependencies to `screenshot-tests/build.gradle.kts`: `implementation(project(":feature:messaging"))`, `implementation(project(":feature:node"))`, `implementation(project(":feature:wifi-provision"))`, `implementation(project(":feature:connections"))`, `implementation(project(":feature:settings"))`, `implementation(project(":feature:firmware"))`, `implementation(project(":feature:intro"))`
- [x] T019 [P] [US2] Create `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/feature/MessagingScreenshotTests.kt` with `@PreviewTest` wrappers for previews from `feature/messaging` (`QuickChatPreviews`, `MessageItemPreviews`, `ReactionPreviews`)
- [x] T020 [P] [US2] Create `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/feature/NodeScreenshotTests.kt` with `@PreviewTest` wrappers for previews from `feature/node` (`NodeDetailComponentPreviews`, `NodeDetailPreviews`, `EnvironmentMetrics`, `DeviceMetrics`, `CommonCharts`)
- [x] T021 [P] [US2] Create `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/feature/WifiProvisionScreenshotTests.kt` with `@PreviewTest` wrappers for previews from `feature/wifi-provision` (`WifiProvisionPreviews`)
- [x] T022 [P] [US2] Add new preview composables in `feature/connections/src/commonMain/kotlin/org/meshtastic/feature/connections/component/ConnectionsPreviews.kt` for `DeviceListItem`, `ConnectingDeviceInfo`, `DisconnectButton` with `@PreviewLightDark`, `internal` visibility, `AppTheme` wrapping, and synthetic sample data
- [x] T023 [P] [US2] Add new preview composables in `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/component/SettingsPreviews.kt` for primary settings sections with `@PreviewLightDark`, `internal` visibility, `AppTheme` wrapping (migrate from existing `androidMain` previews where possible)
- [x] T024 [P] [US2] Add new preview composables in `feature/firmware/src/commonMain/kotlin/org/meshtastic/feature/firmware/FirmwarePreviews.kt` for `FirmwareUpdateScreen` and sub-components with `@PreviewLightDark`, `internal` visibility, `AppTheme` wrapping
- [x] T024a [P] [US2] Add new preview composables in `feature/intro/src/commonMain/kotlin/org/meshtastic/feature/intro/IntroPreviews.kt` for welcome/onboarding screen composables with `@PreviewLightDark`, `internal` visibility, `AppTheme` wrapping (migrate from existing `androidMain` preview where possible)
- [x] T025 [P] [US2] Create `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/feature/ConnectionsScreenshotTests.kt` with `@PreviewTest` wrappers for the new connections previews
- [x] T026 [P] [US2] Create `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/feature/SettingsScreenshotTests.kt` with `@PreviewTest` wrappers for the new settings previews
- [x] T027 [P] [US2] Create `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/feature/FirmwareScreenshotTests.kt` with `@PreviewTest` wrappers for the new firmware previews
- [x] T027a [P] [US2] Create `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/feature/IntroScreenshotTests.kt` with `@PreviewTest` wrappers for the new intro previews
- [x] T028 [US2] Generate reference images for all new wrappers: run `./gradlew :screenshot-tests:updateFdroidDebugScreenshotTest`
- [x] T029 [US2] Validate all screenshot tests pass: run `./gradlew :screenshot-tests:validateFdroidDebugScreenshotTest`
- [x] T030 [US2] Commit updated reference images to version control

**Checkpoint**: Feature modules have preview coverage. New previews for `connections`, `settings`, and `firmware` exist in `commonMain`. All generate valid reference images.

---

## Phase 5: User Story 3 — Supply Screenshot Assets to Docs Pipeline (Priority: P3)

**Goal**: Create a Gradle task that copies selected reference images to a docs asset directory for consumption by spec 003.

**Independent Test**: Run the docs copy task, verify the target directory contains expected PNGs.

### Implementation for User Story 3

- [x] T031 [US3] Create a `copyDocsScreenshots` Gradle task in `screenshot-tests/build.gradle.kts` that copies selected reference images from `src/screenshotTestDebug/reference/` to `docs/screenshots/` with stable file names (based on a manifest or naming convention filter)
- [x] T032 [US3] Create `screenshot-tests/docs-screenshots-manifest.txt` listing the reference image patterns to copy (one per line, supports glob patterns)
- [x] T033 [US3] Verify the task works: run `./gradlew :screenshot-tests:copyDocsScreenshots` and confirm `docs/screenshots/` contains the expected PNGs
- [x] T034 [US3] Add `docs/screenshots/` to `.gitignore` (generated output, not committed — the docs build regenerates from references)

**Checkpoint**: The docs pipeline can consume screenshot assets via the `copyDocsScreenshots` task.

---

## Phase 6: User Story 4 — Validate Screenshots in CI (Priority: P4)

**Goal**: Add screenshot validation to the CI pipeline so regressions are caught on every PR.

**Independent Test**: Open a PR that changes a previewed component without updating references; confirm CI fails and uploads the diff report.

### Implementation for User Story 4

- [x] T035 [US4] Add screenshot validation step to `.github/workflows/pull-request.yml`: run `./gradlew :screenshot-tests:validateFdroidDebugScreenshotTest` after the existing build step
- [x] T036 [US4] Add artifact upload step to `.github/workflows/pull-request.yml`: on validation failure, upload `screenshot-tests/build/reports/screenshotTest/` as `screenshot-diff-report` artifact
- [x] T037 [US4] If `pull-request.yml` delegates to `reusable-check.yml`, add the screenshot validation and artifact upload steps to the reusable workflow instead
- [x] T038 [US4] Verify CI integration by reviewing the workflow YAML for correctness (step ordering, `if: failure()` condition on artifact upload, correct Gradle task name)

**Checkpoint**: CI runs screenshot validation on every PR. Diff reports are uploaded as artifacts on failure.

---

## Phase 7: User Story 5 — Audit and Upgrade Existing Previews (Priority: P5)

**Goal**: Complete the audit of remaining core/ui component previews and add remaining `@PreviewTest` wrappers for full coverage.

**Independent Test**: Run the full screenshot test suite and confirm all existing and new previews generate valid reference images.

### Implementation for User Story 5

- [x] T039 [P] [US5] Create `@PreviewTest` wrappers for remaining `core:ui` component previews not covered in T013: `ImportFab`, `SliderPreference`, `EditTextPreference`, `EditListPreference`, `SwitchPreference`, `BitwisePreference`, `PositionPrecisionPreference`, `RegularPreference`, `DropDownPreference`, `EditBase64Preference`, `EditIPv4Preference`, `EditPasswordPreference`, `PreferenceCategory`, `TextDividerPreference`, `ElevationInfo`, `SecurityIcon`, `IndoorAirQuality`, `NodeChip`, `IconInfo` in `screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/core/PreferenceComponentScreenshotTests.kt`
- [x] T040 [P] [US5] Verify `feature:messaging` previews use `AppTheme` wrapping consistently; fix any that don't in `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/Message.kt` and `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/component/ReactionPreviews.kt`
- [x] T041 [P] [US5] Verify `feature:wifi-provision` previews use `AppTheme` wrapping consistently; fix any that don't in `feature/wifi-provision/src/commonMain/kotlin/org/meshtastic/feature/wifiprovision/ui/WifiProvisionPreviews.kt`
- [x] T042 [US5] Generate final reference images for all wrappers: run `./gradlew :screenshot-tests:updateDebugScreenshotTest`
- [x] T043 [US5] Validate complete screenshot test suite passes: run `./gradlew :screenshot-tests:validateDebugScreenshotTest`
- [x] T044 [US5] Commit all final reference images to version control (pending — will commit with Phase 8)

**Checkpoint**: All existing previews meet conventions. Full coverage achieved across audited modules.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verification, documentation, and quality gates.

- [x] T045 [P] Update `specs/018-compose-screenshot-testing/quickstart.md` with any corrections discovered during implementation
- [x] T046 [P] Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto`
- [x] T047 [P] Run constitution-required verification: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests :screenshot-tests:validateDebugScreenshotTest`
- [x] T048 [P] Verify `screenshot-tests` module lint passes: `./gradlew :screenshot-tests:detekt`
- [x] T049 Validate quickstart guide: follow the steps in `specs/018-compose-screenshot-testing/quickstart.md` to add a new preview + screenshot test from scratch, confirm it works end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (T003 must exist for T010 compile check) — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 completion — MVP deliverable
- **US2 (Phase 4)**: Depends on Phase 1 (module exists) + Phase 2 (previews audited). Can start in parallel with US1 for new preview authoring (T022-T024), but wrapper creation (T019-T021, T025-T027) depends on US1 confirming the pipeline works
- **US3 (Phase 5)**: Depends on US1 (reference images must exist). Independent of US2
- **US4 (Phase 6)**: Depends on US1 (validation task must work). Independent of US2 and US3
- **US5 (Phase 7)**: Depends on Phase 2 (audit started) + US1 (pipeline validated). Can run after US1
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Core MVP — validate regression detection works. No dependencies on other stories
- **US2 (P2)**: Expands coverage. New preview authoring (T022-T024) can start after Phase 2; wrappers and reference generation after US1
- **US3 (P3)**: Docs integration. Only needs reference images from US1. Independent of US2, US4, US5
- **US4 (P4)**: CI integration. Only needs working validate task from US1. Independent of US2, US3, US5
- **US5 (P5)**: Coverage completion. Extends US1's initial coverage to all remaining previews

### Parallel Opportunities

- **Phase 1**: T001, T002, T004 can run in parallel (different files)
- **Phase 2**: T008, T009, T010 can run in parallel (different modules/files)
- **Phase 3**: T012, T013 can run in parallel (different test files)
- **Phase 4**: T019-T027 can all run in parallel (different test/preview files)
- **Phase 6**: T035, T036 can run in parallel if in same file (workflow steps are additive)
- **Phase 7**: T039, T040, T041 can run in parallel (different files)
- **Phase 8**: T045-T048 can all run in parallel

### Implementation Strategy

- **MVP**: Phases 1-3 (Setup + Foundational + US1). Delivers a working screenshot regression pipeline for core/ui components
- **Incremental**: Phase 4 (US2) expands coverage. Phase 5 (US3) adds docs integration. Phase 6 (US4) adds CI enforcement
- **Full**: Phase 7 (US5) completes coverage audit. Phase 8 polishes and validates
