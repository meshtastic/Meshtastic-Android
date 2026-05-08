---
description: "Task list for feature: App Documentation (Android/KMP)"
---

# Tasks: App Documentation (Android/KMP)

**Input**: Design documents from `specs/003-app-docs-markdown/`  
**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`  
**Status**: Not Started

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can be worked in parallel if dependencies are satisfied
- **[Story]**: `US1`..`US5` map to the user stories in `spec.md`
- Every task names the primary file paths to touch

---

## Phase 0: Design Standards Gate (Blocking)

**Purpose**: Review Meshtastic design standards before shipping any new UI for docs or the Chirpy assistant.

- [X] T000 **[UI-GATE]** Review `.skills/design-standards/SKILL.md` and upstream Meshtastic design standards; record constraints for `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/ui/DocsBrowserScreen.kt`, `ChirpyAssistantSheet.kt`, and screenshot styling.
- [X] T001 **[UI-GATE]** Confirm icon choices in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/icon/` for help/search/info/security states and choose MeshtasticIcons equivalents for docs UI and reference tables.

**Checkpoint**: Design constraints are documented and ready to guide implementation.

---

## Phase 1: Documentation Content

**Purpose**: Author the docs corpus that both the website and in-app browser will consume.

### User Guide pages
- [X] T010 [P] [US1] Create `docs/user/onboarding.md` covering first launch, intro flow, permissions, and initial setup using content from `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/WelcomeScreen.kt`, `LocationScreen.kt`, and `NotificationsScreen.kt`.
- [X] T011 [P] [US1] Create `docs/user/connections.md` covering Bluetooth, USB, and TCP connection flows using `feature/intro/.../BluetoothScreen.kt` and `feature/connections/**` as authoritative sources.
- [X] T012 [P] [US1] Create `docs/user/messages-and-channels.md` covering conversations, channel security, direct messages, and message state using `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/Message.kt` and `component/MessageScreenComponents.kt`.
- [X] T013 [P] [US1] Create `docs/user/nodes.md` covering node list status, roles, badges, and quick actions using `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListScreen.kt`.
- [X] T014 [P] [US1] Create `docs/user/node-metrics.md` covering node detail, device metrics, environment metrics, signal, power, traceroute, and logs using `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/detail/NodeDetailScreens.kt` and `metrics/*`.
- [X] T015 [P] [US1] Create `docs/user/map-and-waypoints.md` covering maps, waypoints, and map-specific actions using `feature/map/src/androidMain/kotlin/org/meshtastic/feature/map/MapScreen.kt`.
- [X] T016 [P] [US1] Create `docs/user/settings-radio-user.md` covering radio, LoRa, display, and user settings using `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/DeviceConfigurationScreen.kt`.
- [X] T017 [P] [US1] Create `docs/user/settings-module-admin.md` covering module, administration, and advanced settings using `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/ModuleConfigurationScreen.kt` and `AdministrationScreen.kt`.
- [X] T018 [P] [US1] Create `docs/user/telemetry-and-sensors.md` covering telemetry surfaces and sensor interpretation using `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/EnvironmentMetrics.kt`, `PowerMetrics.kt`, and related metric screens.
- [X] T019 [P] [US1] Create `docs/user/tak.md` covering TAK integration and setup using `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component/TAKConfigItemList.kt` and related settings screens.
- [X] T020 [P] [US1] Create `docs/user/mqtt.md` covering MQTT setup and usage using `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component/MQTTConfigItemList.kt` and messaging references.
- [X] T021 [P] [US1] Create `docs/user/discovery.md` covering local mesh discovery and node exploration based on current discovery-related UI/state and app navigation flows. **Note**: Feature 001 (Local Mesh Discovery) is Not Started — author this page as a concept/goals overview initially and revise with screenshots and detailed UI guidance once 001 reaches Phase 5+ UI milestones.
- [X] T022 [P] [US1] Create `docs/user/firmware.md` covering update flows, warnings, and recovery using `feature/firmware/src/commonMain/kotlin/org/meshtastic/feature/firmware/FirmwareUpdateScreen.kt`.
- [X] T023 [P] [US1] Create `docs/user/desktop.md` covering Desktop host usage, transport differences, and parity notes using `desktop/src/main/kotlin/org/meshtastic/desktop/` and shared navigation patterns.

### Developer Guide pages
- [X] T024 [P] [US4] Create `docs/developer/architecture.md` describing layer boundaries (`app`, `desktop`, `feature/*`, `core/*`) and shared KMP responsibilities.
- [X] T025 [P] [US4] Create `docs/developer/codebase.md` documenting repository layout, namespacing, and build-logic conventions.
- [X] T026 [P] [US4] Create `docs/developer/adding-a-feature-module.md` documenting `meshtastic.kmp.feature`, source sets, DI, resources, and testing expectations.
- [X] T027 [P] [US4] Create `docs/developer/navigation-and-deep-links.md` documenting `Routes.kt`, `DeepLinkRouter.kt`, and Navigation 3 graph registration patterns.
- [X] T028 [P] [US4] Create `docs/developer/transport.md` documenting BLE, TCP, Serial/USB, and host-specific abstractions.
- [X] T029 [P] [US4] Create `docs/developer/persistence.md` documenting Room KMP, DataStore/core:prefs, and where docs intentionally do **not** use persistence.
- [X] T030 [P] [US4] Create `docs/developer/testing.md` documenting KMP test strategy, host tests, and planned screenshot automation.
- [X] T031 [P] [US4] Create `docs/developer/contributing.md` documenting branch naming, verification, and PR hygiene.

### Content-supporting assets
- [X] T032 [P] [US1] Create or inventory `docs/assets/screenshots/` references and map each page to required PNG or SVG assets.
- [X] T033 [P] [US1] Extract onboarding tips, warnings, and disclaimers from `feature/intro/**`, `feature/firmware/**`, and relevant feature UIs into highlighted callout sections inside the authored markdown.
- [X] T034 [US1] Review all markdown for reference-table compliance where 2+ icon/state captures appear together.

**Checkpoint**: Complete markdown corpus exists with planned screenshots and callouts.

---

## Phase 2: Jekyll Site Setup

**Purpose**: Make the authored markdown browsable on the web with versioning.

- [X] T040 [P] [US1] Create `docs/_config.yml` with `just-the-docs`, sidebar search, and the required collection/navigation settings.
- [X] T041 [P] [US1] Create `docs/index.md` redirect behavior for `/latest/` and beta handling.
- [X] T042 [P] [US1] Create `docs/_data/versions.yml` with an initial `beta` entry and stable release entry schema.
- [X] T043 [P] [US1] Create any shared include/layout files needed for version selector, beta banner, and consistent screenshot styling.
- [X] T044 [US1] Validate local Jekyll build output from the authored markdown and confirm the navigation hierarchy matches the spec.

**Checkpoint**: Local website build is navigable and version-ready.

---

## Phase 3: Build Pipeline (Markdown → HTML, Index, Bundle)

**Purpose**: Implement Gradle-native docs generation suitable for KMP.

- [X] T050 [P] [US1] Create `feature/docs/build.gradle.kts` using `meshtastic.kmp.feature` and dependencies for `core:common`, `core:navigation`, `core:resources`, `core:ui`, `core:di`, and existing markdown renderer libraries.
- [X] T051 [P] [US1] Add `:feature:docs` to `settings.gradle.kts`.
- [X] T052 [P] [US1] Add docs-generation support in `build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/DocsTasks.kt` (or equivalent) with lazy task registration.
- [X] T053 [P] [US1] Implement frontmatter parsing, nav-order extraction, and markdown normalization in build logic or `feature/docs` build task code.
- [X] T054 [P] [US1] Implement HTML rendering via `flexmark-java` (or `commonmark-java` fallback) in the docs generation task.
- [X] T055 [P] [US1] Implement callout and banner post-processing, shared CSS injection, and `data-page` emission for generated HTML.
- [X] T056 [P] [US1] Generate `index.json` matching `specs/003-app-docs-markdown/contracts/keyword-index-schema.json`.
- [X] T057 [P] [US1] Wire generated output into `feature/docs/build/generated/docs/common/` as a Gradle resource source directory.
- [X] T058 [P] [US1] Add Android asset mirroring if required for WebView file loading under `feature/docs/build/generated/docs/androidAssets/`.
- [X] T059 [P] [US1] Enforce bundle-size warnings/failures and missing-asset validation in `validateDocsBundle`.
- [X] T060 [US1] Add aggregate root tasks (`generateDocsBundle`, `validateDocsBundle`, `publishDocsSite`) and document their usage.

**Checkpoint**: Gradle can generate the docs bundle and website artifact from markdown.

---

## Phase 4: In-App Doc Browser

**Purpose**: Ship the offline docs browser inside Settings.

- [X] T070 [P] [US2] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/model/DocModels.kt` implementing the entities from `data-model.md`.
- [X] T071 [P] [US2] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/data/DocBundleLoader.kt` to load packaged docs metadata and page content.
- [X] T072 [P] [US2] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/ui/DocsBrowserScreen.kt` with grouped TOC, search entry point, and loading/empty states.
- [X] T073 [P] [US2] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/ui/DocsPageRouteScreen.kt` to route page IDs to renderer surfaces.
- [X] T074 [P] [US2] Create Android renderer `feature/docs/src/androidMain/kotlin/org/meshtastic/feature/docs/ui/DocHtmlView.android.kt` using `AndroidView` + `WebView`.
- [X] T075 [P] [US2] Create Desktop/iOS page renderers in `src/jvmMain` and `src/iosMain` using Compose markdown or embedded browser abstraction.
- [X] T076 [P] [US2] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/navigation/DocsNavigation.kt` with typed navigation entries.
- [X] T077 [P] [US2] Add `SettingsRoute.HelpDocs` and `SettingsRoute.HelpDocPage` to `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`.
- [X] T078 [P] [US2] Update `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/DeepLinkRouter.kt` for `help-docs` (canonical) / `helpDocs` (compat alias) routing.
- [X] T079 [P] [US2] Update `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt` to add the Help & Documentation row and register docs destinations.
- [X] T080 [P] [US2] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/di/FeatureDocsModule.kt`.
- [X] T081 [P] [US2] Include `FeatureDocsModule` in `app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt` and `desktop/src/main/kotlin/org/meshtastic/desktop/di/DesktopKoinModule.kt`.
- [X] T082 [US2] Add shared/unit tests for bundle loading, page ordering, and route serialization under `feature/docs/src/commonTest/kotlin/org/meshtastic/feature/docs/`.

**Checkpoint**: Help & Documentation opens inside Settings and reads bundled content offline.

---

## Phase 5: Search / Index / Discoverability

**Purpose**: Make the docs corpus searchable on all targets.

- [X] T090 [P] [US2] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/data/KeywordSearchEngine.kt` using `KeywordIndexEntry`.
- [X] T091 [P] [US2] Add alias normalization and title-first ranking logic.
- [X] T092 [P] [US2] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/ui/DocsSearchBar.kt` and wire it into `DocsBrowserScreen.kt`.
- [X] T093 [P] [US2] Add section-aware search results and page suggestions for missing page/deep-link cases.
- [X] T094 [P] [US2] Add tests for ranking, aliases, and tie-breaking in `KeywordSearchEngineTest.kt`.
- [X] T095 [US2] Ensure keyword search is the user-visible fallback on unsupported AI targets.

**Checkpoint**: Search works without AI on every target.

---

## Phase 6: AI Assistant (Gemini Nano)

**Purpose**: Add an Android-only on-device assistant without breaking KMP or `fdroid`.

- [X] T100 [P] [US3] Create shared AI contracts in `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/ai/AIDocAssistant.kt` and result/state models.
- [X] T101 [P] [US3] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/ui/ChirpyAssistantSheet.kt` with chat UI, pinned input, session history, and source-page chips.
- [X] T102 [P] [US3] Add keyword-retrieval + token-budget helper logic in shared code.
- [X] T103 [P] [US3] Implement Google-flavor Android binding under `app/src/google/kotlin/org/meshtastic/app/docs/GoogleDocsAiModule.kt` (or equivalent) to call Gemini Nano via Google AI Edge SDK.
- [X] T104 [P] [US3] Bind a no-op or keyword-only fallback implementation in `app/src/fdroid/kotlin/org/meshtastic/app/di/FlavorModule.kt`.
- [X] T105 [P] [US3] Bind a Desktop fallback implementation from `desktop/src/main/kotlin/org/meshtastic/desktop/di/DesktopKoinModule.kt`.
- [X] T105b [P] [US3] Bind an iOS fallback implementation (keyword-search-only, sharing the Desktop fallback pattern) in the iOS Koin module or via a shared non-Android default binding.
- [X] T106 [P] [US3] Add runtime capability checks for Android API level, flavor, model availability, and busy/quota states.
- [X] T107 [P] [US3] Surface assistant fallback states cleanly in the shared UI and hide the input entirely when unsupported.
- [X] T108 [P] [US3] Add tests covering token budget trimming, unsupported platform behavior, and fallback search suggestions.
- [X] T109 [US3] Verify the Chirpy vector asset is bundled and rendered correctly across targets.

**Checkpoint**: Supported Android Google builds get Gemini Nano; all other targets fall back gracefully.

---

## Phase 7: CI Automation and GitHub Pages

**Purpose**: Keep docs current and deployable.

- [X] T120 [P] [US5] Create `.github/workflows/docs-deploy.yml` using `ubuntu-24.04`, JDK 21, Gradle setup, docs-generation tasks, and Pages deploy steps.
- [X] T121 [P] [US5] Create `.github/workflows/docs-release.yml` for `v*.*.*` tags, version manifest updates, and `/latest/` redirect refresh.
- [X] T122 [P] [US5] Create or wire `recordDocsScreenshots` to the chosen screenshot framework (`Roborazzi` preferred, `Paparazzi` acceptable).
- [X] T123 [P] [US5] Add screenshot asset diff detection and automated PR creation logic for changed PNGs.
- [X] T124 [P] [US5] Add schema validation against `specs/003-app-docs-markdown/contracts/keyword-index-schema.json` during CI.
- [X] T125 [P] [US5] Add bundle-size validation and missing-asset validation to CI as blocking steps.
- [X] T126 [P] [US5] Update workflow permissions and Pages artifact publishing configuration.
- [X] T127 [US5] Dry-run the workflows locally as far as practical and verify contract alignment.

**Checkpoint**: Docs build, validate, and deploy automatically in CI.

---

## Phase 8: Polish, Accessibility, and Edge Cases

**Purpose**: Final quality pass before implementation is considered complete.

- [X] T130 [P] [US2] Add accessibility labels, headings, and focus order checks to docs browser and Chirpy UI.
- [X] T131 [P] [US2] Validate dark-mode rendering for generated HTML, screenshots, and icon reference tables.
- [X] T132 [P] [US2] Handle missing-page and stale-deep-link fallbacks in the docs browser UI.
- [X] T133 [P] [US3] Add explicit user messaging for Gemini busy/quota/model-not-installed states.
- [X] T134 [P] [US1] Review all pages for plain-language voice, no internal jargon leaks, and consistency with current UI strings.
- [X] T135 [P] [US4] Review developer docs for correctness against actual modules, routes, and DI setup.
- [X] T136 [P] [US5] Validate Lighthouse accessibility on the generated site and record results.
- [X] T137 [P] [US5] Add README updates for Help & Documentation and the deep-link contract.
- [X] T138 [US1] Run final verification: `./gradlew spotlessCheck detekt kmpSmokeCompile test allTests generateDocsBundle validateDocsBundle publishDocsSite`.

**Checkpoint**: Feature is accessible, correct, and release-ready.

---

## Dependency Notes

- Phase 0 blocks all UI work.
- Phase 1 (content) and Phase 2 (site scaffolding) can overlap.
- Phase 3 must finish before Phase 4 can load generated bundles reliably.
- Phase 5 depends on Phase 3 metadata/index generation and Phase 4 browser UI.
- Phase 6 depends on Phase 5 because AI retrieval uses the keyword index and search engine.
- Phase 7 depends on Phases 2 and 3.
- Phase 8 depends on all preceding phases.

## Recommended Delivery Order

1. Ship **US1** first (web docs + pipeline).
2. Add **US2** (in-app browser + deep links).
3. Add **US3** (Gemini Nano + fallbacks).
4. Finish **US4** polishing and architecture docs.
5. Finish **US5** automation and screenshot bot flow.
