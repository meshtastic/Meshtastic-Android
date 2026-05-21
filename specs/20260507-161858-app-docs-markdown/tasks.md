---
description: "Task list for feature: App Documentation (Android/KMP)"
---

# Tasks: App Documentation (Android/KMP)

**Input**: Design documents from `specs/003-app-docs-markdown/`  
**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`  
**Status**: Complete (Phases 0–14)

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
  - [X] T061 [P] [US1] [FR-038] Update `syncDocsToComposeResources` in `feature/docs/build.gradle.kts` to include `assets/screenshots/**/*.png` alongside markdown files, and add a task dependency on `:screenshot-tests:copyDocsScreenshots` to ensure generated screenshots are populated before sync.
- [X] T062 [P] [US1] [FR-038] Rewrite or restructure markdown image paths during sync so `assets/screenshots/` references resolve to the compose resource file structure expected by the custom `ImageTransformer` at runtime.

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
- [X] T083 [P] [US2] [FR-038] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/ui/ComposeResourceImageTransformer.kt` implementing `ImageTransformer` from mikepenz markdown renderer. Must use `Res.getUri("files/docs/$link")` (synchronous) to resolve local resource URIs, then `rememberAsyncImagePainter()` from Coil 3 to load the image composably. Must return `null` for external `http://`/`https://` URLs. Add `libs.coil` dependency to `feature/docs/build.gradle.kts` commonMain.
- [X] T084 [P] [US2] [FR-038] Update `DocsPageRouteScreen.kt` to pass `ComposeResourceImageTransformer()` as the `imageTransformer` parameter to the `Markdown()` composable instead of using the default `NoOpImageTransformerImpl`.
- [X] T085 [US2] [FR-038] Verify inline screenshot rendering end-to-end: run `copyDocsScreenshots`, `syncDocsToComposeResources`, then launch the docs browser on Desktop and confirm images render inline on a page with `![alt](...)` references.

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
- T083/T084 (ImageTransformer) depend on T061/T062 (screenshots must be bundled before the transformer can resolve them).
- Phase 5 depends on Phase 3 metadata/index generation and Phase 4 browser UI.
- Phase 6 depends on Phase 5 because AI retrieval uses the keyword index and search engine.
- Phase 7 depends on Phases 2 and 3.
- Phase 8 depends on all preceding phases.
- Phase 10 depends on Phases 1–9 (all content and CI must be in place before Docusaurus sync).
- Phase 11 depends on Phases 9–10 (governance workflows and sync script must exist before consolidation).
- Phase 12 depends on Phase 6 (Chirpy assistant must exist before UX polish).
- Phase 13 depends on Phase 12 (Chirpy bubble redesign must exist before further polish).

## Recommended Delivery Order

1. Ship **US1** first (web docs + pipeline).
2. Add **US2** (in-app browser + deep links).
3. Add **US3** (Gemini Nano + fallbacks).
4. Finish **US4** polishing and architecture docs.
5. Finish **US5** automation and screenshot bot flow.

---

## Phase 9: Apple Alignment (Cross-Platform Feature Parity)

**Purpose**: Close feature gaps identified by comparing with `meshtastic-apple` docs implementation.

- [X] T200 [P] [US1] Create `docs/user/signal-meter.md` explaining LoRa signal quality, RSSI vs SNR, bar-level criteria, and common misconceptions — adapted from Apple equivalent for Android-specific signal surfaces.
- [X] T201 [P] [US1] Create `docs/user/units-and-locale.md` explaining automatic metric/imperial formatting via `MetricFormatter`, covering temperature, distance, speed, wind, rainfall, and locale settings — adapted from Apple equivalent for Android/KMP.
- [X] T202 [P] [US2] Add `iconId: String?` field to `DocPage` and `KeywordIndexEntry` models in `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/model/DocModels.kt`.
- [X] T203 [P] [US2] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/ui/DocPageIconResolver.kt` mapping `iconId` values to `MeshtasticIcons` vectors (equivalent to Apple's SF Symbols per-page mapping).
- [X] T204 [P] [US2] Update `DocsBrowserScreen.kt` TOC list items to show leading icon using `resolveIcon()`.
- [X] T205 [P] [US2] Update `DocBundleLoader.kt` static index with `iconId` for all 24 pages and add two new `KeywordIndexEntry` entries for `signal-meter` and `units-and-locale`.
- [X] T206 [P] [US5] Create `.github/workflows/docs-staleness.yml` — advisory CI workflow that posts a PR comment when user-facing UI files change without corresponding `docs/` updates, with `skip-docs-check` label bypass (adapted from Apple's `docs-staleness.yml` for Android KMP paths).

**Checkpoint**: Feature parity with Apple docs: per-page icons in TOC, two new user guide pages, and docs staleness CI check.

---

## Phase 10: Docusaurus Sync & Content Gaps (meshtastic.org Parity)

**Purpose**: Close gaps identified by comparing with Apple's `sync-apple-docs.js` workflow (PR [meshtastic/meshtastic#2393](https://github.com/meshtastic/meshtastic/pull/2393)) and Apple in-app doc content. Ensures Android docs are published on meshtastic.org alongside Apple docs and addresses missing content pages.

**Depends on**: Phases 1–9 (all content and CI must be in place before sync).

### Content

- [X] T210 [P] [US1] [FR-041] Create `docs/user/translate.md` — "Translate the App" contributor guide explaining how to submit translations via Crowdin. Cover: link to Crowdin project, which files are translatable (composeResources `strings.xml`, `docs/user/*.md`), step-by-step workflow, and how to add a new locale. Add frontmatter with `nav_order: 17`. Add Crowdin string resources for title and keywords.
- [X] T211 [P] [US4] [FR-042] Create `docs/developer/measurement.md` — developer guide for the `MetricFormatter` API and locale-aware unit conversion. Cover: supported measurement types (temperature, distance, speed, wind, rainfall), how locale detection works, how to add a new measurement type, and testing patterns. Reference `core/common/src/commonMain/kotlin/org/meshtastic/core/common/util/` formatters.
- [X] T212 [P] [US2] Update `DocBundleLoader.kt` static index with new pages (`translate`, `measurement`), `iconId` mappings, and `KeywordIndexEntry` entries. Update nav ordering for existing pages to accommodate the two new entries.

### Docusaurus Sync Script

- [X] T220 [P] [US5] [FR-039] Create `scripts/sync-android-docs.js` — Node.js script that reads `docs/user/*.md` and `docs/developer/*.md`, transforms them for Docusaurus compatibility (rewrite frontmatter to Docusaurus format, fix sibling `.md` links, rewrite image paths to `static/img/android/`), and writes output to a staging directory. Model after Apple's `scripts/sync-apple-docs.js` structure.
- [X] T221 [P] [US5] [FR-040] Add `--convert-webp` flag to `sync-android-docs.js` that converts PNG/JPG screenshots to WebP via `cwebp` and rewrites image references in markdown. Original PNGs remain canonical in-repo.
- [X] T222 [P] [US5] [FR-039] Create `.github/workflows/sync-android-docs.yml` — workflow triggered on push to `main` when `docs/**` files change. Steps: checkout, install Node.js and `webp`, run `sync-android-docs.js --convert-webp`, copy images to `static/img/android/`, and open a PR in `meshtastic/meshtastic` targeting `docs/software/android/`. Use `ubuntu-24.04` runner and `peter-evans/create-pull-request` or equivalent action.
- [X] T223 [US5] Dry-run the sync script locally: run `node scripts/sync-android-docs.js --convert-webp --dry-run` and verify output structure matches Docusaurus expectations (`docs/software/android/user/*.md`, `docs/software/android/developer/*.md`, `static/img/android/*.webp`).

### Integration

- [X] T230 [P] [US2] Add Crowdin string resources for `translate.md` title (`doc_title_translate`) and keywords (`doc_keywords_translate`) in `core/resources/src/commonMain/composeResources/values/strings.xml`. Run `python3 scripts/sort-strings.py`.
- [X] T231 [P] [US2] Add Crowdin string resources for `measurement.md` title (`doc_title_measurement`) and keywords (`doc_keywords_measurement`). Run `python3 scripts/sort-strings.py`.
- [X] T232 [US1] Update `docs/user.md` and `docs/developer.md` What's New sections to include `translate.md` and `measurement.md`. Jekyll scope-based defaults handle nav/sidebar automatically.
- [X] T233 [US5] Verified `crowdin.yml` glob `/docs/user/*.md` already covers `translate.md` — no update needed.
- [X] T234 [US1] Run final verification: `./gradlew spotlessApply detekt :feature:docs:allTests`.

**Checkpoint**: Android docs published on meshtastic.org, translate contributor page live, developer measurement docs complete.

---

## Phase 11: Governance Consolidation & Script Optimization

**Purpose**: Eliminate duplication across docs governance scripts and CI workflows. Reduce the number of places that must be manually updated when adding a doc page from 3 to 2 (markdown file + DocBundleLoader only).

**Depends on**: Phases 9–10 (governance workflows and sync script must exist).

### Shared Library

- [X] T240 [P] [US5] [FR-044] Create `scripts/lib/frontmatter.js` with `parseFrontmatter()`, `discoverSlugs()`, and `forEachDocPage()` utilities. Consolidates 4 independent frontmatter parsers and directory traversal patterns.
- [X] T241 [P] [US5] [FR-044] Refactor `scripts/validate-doc-links.js` to use shared `discoverSlugs()` and `forEachDocPage()`.
- [X] T242 [P] [US5] [FR-044] Refactor `scripts/check-doc-freshness.js` to use shared `parseFrontmatter()` and `forEachDocPage()`.
- [X] T243 [P] [US5] [FR-044] Refactor `scripts/check-doc-coverage.js` to use shared `forEachDocPage()`.
- [X] T244 [P] [US5] [FR-044] Refactor `scripts/sync-android-docs.js` to use shared `discoverSlugs()` — replace hardcoded `KNOWN_USER_SLUGS` and `KNOWN_DEV_SLUGS` sets with filesystem-derived discovery.

### Workflow Consolidation

- [X] T250 [P] [US5] [FR-045] Merge `docs-staleness.yml` into `docs-governance.yml` as a parallel `staleness` job. The staleness job uses `fetch-depth: 0` for git diff; the `validate` job uses `fetch-depth: 1`.
- [X] T251 [P] [US5] [FR-045] Remove standalone `.github/workflows/docs-staleness.yml`.
- [X] T252 [US5] Remove slug registry validation step from `docs-governance.yml` (no longer needed since slugs are filesystem-derived).
- [X] T253 [US5] Remove duplicate link validation step and Node.js setup from `docs-deploy.yml`. Remove unused `pull-requests: write` permission.

### 3-Consumer Propagation

- [X] T260 [P] [US5] [FR-043] Update Constitution principle VI to explicitly name in-app, Jekyll, and Docusaurus consumers with propagation rules.
- [X] T261 [US5] Update staleness check PR comment to include new-page checklist for all 3 consumer registries.
- [X] T262 [US5] Add `DocBundleLoader` registry validation step to `docs-governance.yml` (ensures every doc page is registered in the in-app index).

### Cleanup

- [X] T270 [US5] Remove duplicate `sync-android-docs.js` from meshtastic/meshtastic PR #2405 (workflow runs from Android clone).
- [X] T271 [US5] Update `docs/developer.md` references from `docs-staleness` to consolidated `Docs Governance` workflow.
- [X] T272 [US5] Verify all 4 scripts pass locally: `validate-doc-links`, `check-doc-freshness`, `check-doc-coverage`, `sync-android-docs --dry-run`.

**Checkpoint**: Single docs governance workflow, shared frontmatter library, filesystem-derived slugs, 3-consumer propagation model enforced.

### Preview & Screenshot Governance

- [X] T280 [P] [US5] [FR-046] Add `preview-staleness` job to `docs-governance.yml` — detects UI composable changes without `*Previews.kt` updates. Posts advisory PR comment with checklist. Bypassable via `skip-preview-check` label.
- [X] T281 [P] [US5] [FR-047] Add screenshot reference staleness detection to same job — detects `*Previews.kt` changes without reference image updates in `screenshot-tests/src/screenshotTestDebug/reference/`. Posts advisory with `updateDebugScreenshotTest` command.
- [X] T282 [US5] Rename workflow `Docs Governance` → `UI & Docs Governance` to reflect expanded scope.
- [X] T283 [US5] Update `docs/developer.md` contributing checklist with preview/screenshot maintenance guidance.
- [X] T284 [US5] Add dismiss-on-resolve logic: clear preview/screenshot advisory comments when both conditions resolve.

**Checkpoint**: Unified UI & Docs Governance workflow with advisory checks for docs, previews, and screenshot references.

---

## Phase 12: Chirpy UX & M3 Adaptive Nav Polish

**Purpose**: Bring Chirpy assistant and docs navigation up to M3 adaptive navigation best practices and improve conversational UX.

### M3 Adaptive Navigation

- [X] T300 [P] [US2] Integrate `ListDetailSceneStrategy` metadata into `DocsNavigation.kt` — `listPane()` for `HelpDocs`, `detailPane()` for `HelpDocPage`. Enables proper dual-pane layout on tablets/desktop.
- [X] T301 [P] [US2] Add `feature/docs/build.gradle.kts` dependency on `libs.jetbrains.compose.material3.adaptive.navigation3`.

### Global Chirpy State

- [X] T310 [P] [US3] Create `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/ai/ChirpySessionHolder.kt` — Koin `@Single` with Compose snapshot state (`showSheet`, `sessionState`) for shared Chirpy conversation across panes.
- [X] T311 [P] [US3] Refactor `DocsNavigation.kt` `rememberChirpyState()` to inject `ChirpySessionHolder` and derive `showFab` from backstack — FAB shows on list pane only when no detail is selected, always on detail pane.
- [X] T312 [P] [US3] Add auto-intro prompt: Chirpy generates a natural introduction when the sheet first opens with no messages.

### Chirpy Bubble Redesign (MessageItem Parity)

- [X] T320 [P] [US3] Rewrite `ChirpyAssistantSheet.kt` bubbles to use `Surface` + `BorderStroke(0.5.dp)` + `RoundedCornerShape` matching `MessageItem.kt` sender/receiver pattern — user bubbles right-aligned with `primaryContainer`, Chirpy bubbles left-aligned with `surfaceVariant`.
- [X] T321 [P] [US3] Add 24dp Chirpy avatar (`img_chirpy`) to the left of every assistant reply bubble.
- [X] T322 [P] [US3] Update `DocsPreviews.kt` with matching bubble styles and avatar.

### Thinking State & Source Navigation

- [X] T330 [P] [US3] Replace plain "Chirpy is thinking..." text with proper `ThinkingBubble` composable — assistant-styled bubble with Chirpy avatar and pulsing alpha animation.
- [X] T331 [P] [US3] Add `SourceRef(id, title)` data class to `DocModels.kt`; update `ChirpyMessage.sources` to carry page titles alongside IDs.
- [X] T332 [P] [US3] Replace plain-text source list with tappable `SuggestionChip`s in `AssistantBubble` using `FlowRow` layout and `secondaryContainer` colors.
- [X] T333 [P] [US3] Add `onNavigateToPage` to `ChirpyUiState` — dismisses sheet and navigates to referenced doc page. Wire through `DocsBrowserScreen` and `DocsPageRouteScreen`.
- [X] T334 [US3] Update `DocsPreviews.kt` with `SourceRef` sample data, `PreviewThinkingBubble`, and chip-enabled `ChirpyBubble`.

### Verification

- [X] T340 [US3] Verify M3 FAB behavior: confirmed no existing FABs implement hide-on-scroll (consistent with M3 guidelines which do not prescribe it). Chirpy FAB is always-visible, matching all other FABs in the app.
- [X] T341 [US3] Build, detekt, spotless, and all `feature:docs` tests pass. Deployed and verified on Pixel 9 Pro.

**Checkpoint**: Chirpy assistant follows M3 adaptive nav best practices with global state, MessageItem-style bubbles, thinking animation, and tappable source chips.

---

## Phase 13: Chirpy Messaging UI Polish & Firebase AI Hybrid

> Align Chirpy chat with messaging module conventions; add markdown rendering; update Firebase AI binding.

- Phase 13 depends on Phase 12 (Chirpy bubble redesign must exist before further polish).

### Firebase AI Logic Hybrid API

- [X] T350 [P] [US3] Update `GeminiNanoDocAssistant.kt` to use `gemini-2.5-flash-lite` model with `InferenceMode.PREFER_ON_DEVICE` — hybrid on-device/cloud inference via Firebase AI Logic.
- [X] T351 [P] [US3] Implement paragraph extraction with markdown stripping and 8K character context budget with 3K retry fallback on token limit errors.
- [X] T352 [P] [US3] Migrate imports from deprecated `com.google.firebase.ai.ondevice` to `com.google.firebase.ai`.

### Markdown Rendering in Assistant Messages

- [X] T360 [US3] Replace `Text()` with mikepenz `Markdown()` composable in `AssistantBubble` — Chirpy responses now render rich markdown (headers, lists, bold, code blocks, links).

### ChirpyChip Sender Label

- [X] T370 [P] [US3] Create `ChirpyChip` composable in `ChirpyAssistantSheet.kt` — simplified `NodeChip` pattern using `Card` with `tertiaryContainer` colors, 28dp height, 18dp Chirpy avatar + "Chirpy" text label.
- [X] T371 [P] [US3] Replace inline avatar-beside-bubble layout in `AssistantBubble` and `ThinkingBubble` with `ChirpyChip` positioned above the bubble — matching how `NodeChip` appears above received messages in `MessageItem.kt`.

### MessageInput-Style Text Field

- [X] T380 [P] [US3] Replace `OutlinedTextField` + `TextButton("Send")` with messaging-style input: `RoundedCornerShape(50f)` pill shape, `IconButton` with `MeshtasticIcons.Send`.
- [X] T381 [P] [US3] Add `KeyboardOptions(capitalization = Sentences, imeAction = Send)` + `KeyboardActions(onSend)` for keyboard submit support.
- [X] T382 [P] [US3] Add `LocalSoftwareKeyboardController.current?.hide()` on send to dismiss keyboard after submitting a message.

### Verification

- [X] T390 [US3] Build, detekt, spotless, and all tests pass. Deployed and verified on Pixel 9 Pro.

**Checkpoint**: Chirpy chat fully aligned with messaging module conventions — NodeChip-style sender label, MessageInput-style text field, markdown rendering, and Firebase AI hybrid inference.

---

## Phase 14: Translation Cascade (Crowdin → ML Kit → English)

**Purpose**: Enable runtime translation of bundled docs for users whose locale lacks Crowdin coverage.

### Translation Service Interface & Implementations

- [X] T400 [P] [US1] Create `DocTranslationService` interface in `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/translation/` with `translatePage()`, `isLanguageAvailable()`, `downloadLanguageModel()` and sealed result types.
- [X] T401 [P] [US1] Create `NoOpDocTranslator` for F-Droid/Desktop/iOS that returns `Unavailable`.
- [X] T402 [P] [US1] Create `MlKitDocTranslator` in `androidApp/src/google/kotlin/org/meshtastic/app/translation/` with auto model download, segment-and-translate pattern, and proper `suspendCancellableCoroutine` bridging.

### Markdown-Aware Translation

- [X] T410 [P] [US1] Create `MarkdownTranslationSegmenter` that extracts translatable text from markdown while preserving code blocks, links, images, frontmatter, and HTML blocks.
- [X] T411 [P] [US1] Create `DocTranslationCache` with Okio file-based caching, MD5 content keying, Mutex-guarded concurrency, atomic writes, and access-time eviction at 50MB.

### Cascade Integration

- [X] T420 [US1] Add `hasTranslatedResource()` to `DocBundleLoader` to detect Crowdin-provided locale-qualified bundles.
- [X] T421 [US1] Wire cascade into `DocsPageScreen`: show English content immediately, attempt ML Kit translation in background only when Crowdin bundle is absent, auto-download model on first use.
- [X] T422 [US1] Add `TranslationSource` model enum and UI indicator (subtitle in TopAppBar: "Community translated" or "Auto-translated").
- [X] T423 [US1] Add `ioDispatcher` hop and locale-keyed `LaunchedEffect` for correct threading and reactivity.

### DI & Platform Wiring

- [X] T430 [P] [US1] Bind `DocTranslationService` → `MlKitDocTranslator` in `GoogleAiModule`.
- [X] T431 [P] [US1] Bind `DocTranslationService` → `NoOpDocTranslator` in `DesktopKoinModule`.

### Testing

- [X] T440 [P] [US1] Create `MarkdownTranslationSegmenterTest` (15 tests covering paragraphs, headings, code, links, images, frontmatter, lists, tables, HTML blocks).
- [X] T441 [P] [US1] Create `DocTranslationCacheTest` (8 tests covering cache miss/hit, stale hash, locale isolation, clear, size, eviction, hash consistency).
- [X] T442 [P] [US1] Create `TranslationCascadeTest` (8 tests covering NoOp behavior, fake translator variations, sealed hierarchy).

### CI

- [X] T450 [US1] Add `docs/**/*.md` to `scheduled-updates.yml` `add-paths`.

**Checkpoint**: Translation cascade complete — Crowdin bundled translations served automatically by CMP, ML Kit auto-translates on Google flavor when Crowdin unavailable, graceful English fallback on all other platforms.

---

## Phase 15: Web i18n — Crowdin Translations on GitHub Pages

**Purpose**: Ensure in-repo Crowdin translations flow to web consumers (GH Pages docs site), not just the in-app bundle.

### Jekyll Configuration

- [X] T500 [P] Add `_data/locales.yml` with all supported locale metadata (name, text direction).
- [X] T501 [P] Add scope defaults in `_config.yml` for each locale path (`es`, `fr`, `de`, etc.) with `layout: locale_page` and `nav_exclude: true`.
- [X] T502 [P] Create `_layouts/locale_page.html` — wraps content with locale banner, language tag, RTL support, and link back to English.

### Language Switcher UI

- [X] T510 [P] Create `_includes/language_switcher.html` — detects available translations for current page from `site.pages`, renders dropdown with locale links.
- [X] T511 [P] Add language switcher CSS to `_includes/head_custom.html` (dropdown, hover states, dark-mode compatible).
- [X] T512 [P] Wire language switcher into `_includes/header_custom.html` alongside theme toggle.

### DocsTasks Locale Generation

- [X] T520 [P] Extend `GenerateDocsBundleTask` to discover `docs/{locale}/user/` directories and generate locale-qualified HTML + index entries.
- [X] T521 [P] Add `locales.json` manifest output listing all detected translation locales.
- [X] T522 [P] Add `locale` field to index.json entries for locale-aware consumers.
- [X] T523 [P] Set `lang` and `dir` attributes on generated HTML for locale pages.

### Content & Navigation

- [X] T530 [P] Create `docs/translations.md` — lists all available languages with links, Crowdin CTA, contribution instructions.
- [X] T531 [P] Crowdin config (`crowdin.yml`) already maps `docs/index.md` → `docs/{locale}/index.md` — locale landing pages auto-generated.

**Checkpoint**: Crowdin-contributed translations serve to web consumers via Jekyll GH Pages with locale routing, language switcher, and proper locale/RTL HTML attributes. Same markdown source serves both in-app (CMP bundle) and web (Jekyll) consumers.
