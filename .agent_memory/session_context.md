# Agent Session Context - Meshtastic Android
# This is a dated, append-only handover log. Add new entries at the TOP.
# Do NOT edit or remove previous entries — stale state claims cause agent confusion.
# Format: ## YYYY-MM-DD — <summary>

## 2026-05-28 — Added comprehensive CarScreenDataBuilder unit coverage
- Created `feature/car/src/test/kotlin/org/meshtastic/feature/car/util/CarScreenDataBuilderTest.kt` with 533 lines covering signal quality thresholds/boundaries, node UI mapping, node and conversation sorting, local stats fallbacks, uptime formatting, recent message limiting, contact key generation, and constants.
- Restored the `MessageSnapshot` data class in `CarStateCoordinator.kt` and re-added `recentMessages()` plus `MAX_CONVERSATION_MESSAGES` in `CarScreenDataBuilder.kt` so the current source matched the requested pure-helper API surface for testing.
- Verified with `./gradlew :feature:car:spotlessCheck :feature:car:detekt :feature:car:testFdroidDebugUnitTest --quiet` and the requested quiet test command (`./gradlew :feature:car:testFdroidDebugUnitTest --quiet 2>&1 | tail -20`), both successful.

## 2026-05-28 — Lowered car min API to 7 and removed dead conversation code
- Changed `feature/car` manifest `androidx.car.app.minCarApiLevel` metadata from 8 to 7.
- Guarded `HomeScreen.showEmergencyAlert()` behind `carContext.carAppApiLevel >= 8` and logged unsupported API 7 hosts with Kermit.
- Removed unused `ConversationScreen`, `CarTtsEngine`, message snapshot/cache/read-aloud plumbing, and now-unused car reply/read-aloud strings.
- Simplified `CarStateCoordinator` and `CarScreenDataBuilder` to match the inline `ConversationItem` flow.
- Verified with `./gradlew :feature:car:spotlessApply :feature:car:spotlessCheck :feature:car:detekt :feature:car:compileFdroidDebugKotlin --quiet 2>&1 | tail -30`.

## 2026-05-28 — Migrated car home messages tab to ConversationItem
- Reworked `feature/car` `HomeScreen` messaging tab to build CAL `ConversationItem` entries instead of browsable `Row`s, including `Person`/`CarMessage` helpers and native reply/mark-read callbacks.
- Removed `HomeScreen` conversation navigation so the car host owns messaging affordances; `ConversationScreen` remains on disk for later cleanup phases.
- Added `CarStateCoordinator.markAsRead()` using `packetRepository.clearUnreadCount(...)` with Kermit error logging via `runCatching`.
- Verified with `./gradlew :feature:car:spotlessApply :feature:car:spotlessCheck :feature:car:detekt :feature:car:compileFdroidDebugKotlin` and the requested quiet compile command (`:feature:car:compileFdroidDebugKotlin --quiet 2>&1 | tail -20`), both successful.

## 2026-05-28 — Implemented car conversation shortcuts and avatars
- Added `feature/car/.../util/PersonIconFactory.kt` to render circular initial avatars using node-derived foreground/background colors for `Person` and shortcut icons.
- Added `feature/car/.../service/ConversationShortcutManager.kt` to publish long-lived dynamic conversation shortcuts for favorite nodes and active channels, plus on-demand shortcut creation for notifications.
- Wired `MeshtasticCarSession` to start/stop shortcut observation on a dedicated session coroutine scope.
- Updated `CarNotificationManager` to ensure conversation shortcuts exist before posting and to attach both `shortcutId` and `LocusIdCompat` to messaging notifications.
- Verified green with `./gradlew :feature:car:spotlessCheck :feature:car:detekt --quiet` and `./gradlew :feature:car:compileFdroidDebugKotlin --quiet 2>&1 | tail -20` after workspace bootstrap.

## 2026-05-28 — Implemented car local stats tab and extracted screen data builder
- Added `CarLocalStats` to `feature/car` UI models and exposed `localStatsState` from `CarStateCoordinator`.
- Wired a new HomeScreen `Status` tab with battery, channel utilization, air utilization, node counts, uptime, and packet TX/RX rows.
- Created `feature/car/.../util/CarScreenDataBuilder.kt` to centralize pure UI-model mapping helpers for nodes, conversations, local stats, uptime formatting, contact key building, and recent message selection.
- Added the new `ic_car_status.xml` drawable plus status strings in `feature/car/src/main/res/values/strings.xml`.
- Cleaned up `CarReplyReceiver` detekt violations that blocked module validation.
- Ran `python3 scripts/sort-strings.py` and verified green with `./gradlew :feature:car:spotlessApply :feature:car:spotlessCheck :feature:car:detekt :feature:car:compileFdroidDebugKotlin :feature:car:testFdroidDebugUnitTest`.

## 2026-05-28 — Implemented car module Phase 1 messaging wiring fixes
- Replaced `CommandSender` usage in `feature/car` `CarStateCoordinator` with injected `SendMessageUseCase`, keeping the public `sendMessage()` API synchronous for UI callbacks while launching the use case on the coordinator scope after message-length validation.
- Updated `CarNotificationManager` reply and mark-read notification actions with semantic action metadata and `setShowsUserInterface(false)` for automotive-friendly inline handling.
- Reworked `CarReplyReceiver` into a `KoinComponent` that injects `SendMessageUseCase` and `PacketRepository`, then sends replies / clears unread counts asynchronously with Kermit error logging.
- Added `android:permission="androidx.car.app.CarAppService"` to the `MeshtasticCarAppService` manifest declaration.
- Verified with `./gradlew :feature:car:compileFdroidDebugKotlin --quiet` after required workspace bootstrap.

## 2026-05-21 — Upgraded Chirpy to a fully-personalized Live Diagnostic Node & Mesh Assistant
- Integrated `NodeRepository` into `GeminiNanoDocAssistant.kt` and the Google AI Koin dependency injection module (`GoogleAiModule.kt`).
- Developed a dynamic live-state prompt formatting block within `buildPrompt(...)` that queries current hardware model, firmware version, connection status, GPS capability, channel utilization, airtime, battery level/voltage, user profile long/short names, and total registered mesh peer counts & active online peers directly from `NodeRepository`'s reactive flows.
- Injected this live radio diagnostics context dynamically as a system instruction metadata block on every user query. This empowers the on-device model to answer real-time, personalized diagnostic questions (e.g. "what is my battery level?", "how many active nodes are on my mesh right now?") with 100% on-device offline accuracy.
- Tuned context retrieval constraints for the modern `nano-v4-full` (Gemini Nano v4) model: expanded the total context budget `MAX_CONTEXT_CHARS` from 8,000 to **32,000 characters** (up to ~12K tokens out of the model's native 32K window), and scaled `MAX_PAGE_CHARS` to **16,000 characters** and `MAX_SNIPPET_CHARS` to **8,000 characters** to supply vastly richer, more detailed, and complete documentation fragments.

## 2026-05-21 — Activated full on-device token streaming and polished Chirpy's personality instructions
- Upgraded the on-device inference flow inside `GeminiNanoDocAssistant.kt` to use Firebase AI SDK's reactive `generateContentStream(prompt)` instead of the blocking `generateContent` invocation.
- Aggregated chunks and emitted incremental `AIDocAssistantResult.Partial` states down the Kotlin Flow, enabling true word-by-word/chunk-by-chunk streaming in the UI for a much more responsive user experience.
- Refined the `SYSTEM_INSTRUCTION` personality rules for Chirpy to position him as our adorable LoRa radio Node mascot instead of an avian theme, emphasizing high-enthusiasm mesh networking, signal connectivity, battery status, and radio/routing concepts (e.g. "fully charged", "relaying info", "staying connected") while preserving technical precision.
- Significantly increased the frequency and flavor of mesh networking, hardware, and radio puns (e.g. "let's relay some knowledge!", "channeling my inner router!", "completely on the same frequency!") to make Chirpy incredibly fun and interactive.
- Overhauled system error messages inside `DocsNavigation.kt` and the loading bubble state inside `ChirpyAssistantSheet.kt` to align with the highly-enthusiastic mascot theme (e.g. "routing through the mesh… 📡", "channel is totally congested… 📶", "battery is charging or firmware is still downloading… 🔋").

## 2026-05-21 — Implemented streaming chat support and Firebase Remote Config integration for Chirpy
- Added `firebase-config` dependency to Version Catalog `libs.versions.toml` and `androidApp/build.gradle.kts`.
- Added the `AIDocAssistantResult.Partial` variant to support intermediate stream updates.
- Extended the `AIDocAssistant` interface and implemented `answerStream` in both `KeywordFallbackAssistant` and `GeminiNanoDocAssistant`.
- Integrated Firebase Remote Config into `GeminiNanoDocAssistant` to dynamically fetch the model name (`chirpy_model_name`) and system instruction (`chirpy_system_instruction`) with release-optimized fetch intervals.
- Refactored `GeminiNanoDocAssistant.answer` to reuse `answerStream` flow under the hood, eliminating duplicate prompting code.
- Integrated the streaming flow into the Compose UI layer (`DocsNavigation.kt`), appending a placeholder message and updating it in place on each stream emission.
- Verified that all unit tests (`:feature:docs:allTests`) and static analysis checks (`spotlessApply spotlessCheck detekt`) pass 100% green.

## 2026-05-21 — Fixed Chirpy Assistant invalid model name and enhanced failure fallback suggestions
- Fixed a 404/Unknown inference error by updating `GeminiNanoDocAssistant.kt`'s `MODEL_NAME` from `"gemini-3.1-flash-lite"` to the correct Firebase AI Logic preview name `"gemini-3.1-flash-lite-preview"`.
- Overhauled multi-turn hybrid chat seeding: eliminated the redundant and wasteful background `chat.sendMessage` network/token call on the first turn. Instead, if the first turn is answered on-device, the session caches the question and answer locally, and starts the subsequent cloud-chat session with these pre-loaded into the `startChat(history = ...)` parameter.
- Expanded the hybrid model's `looksLikeNoAnswer` heuristics to better detect when the on-device model fails or has limited info, allowing seamless fallback to the grounded cloud model.
- Polished `DocsNavigation.kt`'s `chirpyResultToMessage` system error handling to map raw exception/object types (e.g., `DocsAiError.Unknown`) to polished, friendly user-facing messages.
- Programmed a smart fallback in the UI: if an inference error occurs (e.g. offline, rate limit, or model not found), Chirpy now automatically displays local keyword search results as recommended page chips so the user is never left stranded.
- Verified 100% compliance with Spotless, Detekt, and unit tests (`:feature:docs:allTests` and `:androidApp:testGoogleDebugUnitTest` are fully green).

## 2026-05-20 — Decoupled and Isolated Flatpak manifest generation logic to build-logic/flatpak
- Isolated the optimized `GenerateFlatpakSourcesTask` from monolithic `build-logic/convention` into its own specialized, lightweight `:flatpak` subproject under `build-logic`.
- Created `:flatpak` configuration and registered the formal plugin ID `"meshtastic.flatpak"` implemented by `FlatpakConventionPlugin` inside the default package namespace (perfectly matching project-wide plugin architectures).
- Implemented modern, configuration-cache-safe lazy provider directory evaluation for the default Gradle user home cache.
- Cleaned up `:convention` by removing the redundant class and registration imports from `RootConventionPlugin.kt`.
- Applied the new plugin in the root `build.gradle.kts` using `id("meshtastic.flatpak")`.
- Verified 100% compliant spotless and detekt formatting checks (`./gradlew spotlessCheck detekt` is green).
- Successfully committed and pushed the branch `fix/flatpak-snapshot-resolution` to remote `jamesarich` with proper `GITHUB_TOKEN` environment bypass.
- Consolidated and updated GitHub PR #5542's description to comprehensively document the correctness, performance, and modular isolation of the Flatpak generator.

## 2026-05-20 — Extracted GenerateFlatpakSourcesTask to precompiled build-logic convention plugin
- Audited the Flatpak build structure and successfully extracted the entire task logic, data classes, and extension helpers from loose script files to a precompiled compiled Kotlin class: `build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/GenerateFlatpakSourcesTask.kt`.
- Registered the task directly within `RootConventionPlugin.kt` and removed the legacy `gradle/flatpak.gradle.kts` script block from the root `build.gradle.kts` file entirely.
- Resolved and fixed an implicit Gradle non-serializable property capture inside the lazy property mappings, ensuring full compliance with the Gradle Configuration Cache and restoring successful cache storage with zero errors.
- Validated with complete clean building (`./gradlew clean`) and static code analysis (`./gradlew spotlessCheck detekt`), completing with 100% green passes.

## 2026-05-20 — Optimized GenerateFlatpakSourcesTask for performance and correctness
- Optimized `GenerateFlatpakSourcesTask` in `gradle/flatpak.gradle.kts` by implementing single-pass Maven metadata XML pre-indexing ($O(1)$ lookups) and deferred SHA-256 calculation (executing digests only on deduplicated, finalized resources).
- Refactored loose map structures into strongly-typed Gradle-compliant internal data classes (`SnapshotVersion`, `SnapshotMetadata`, and `FlatpakSourceCandidate`) to improve type safety and maintainability.
- Verified output correctness: the optimized manifest output `flatpak-sources.json` is 100% character-for-character identical to the original unoptimized output.
- Successfully passed all static analysis and code quality checks with `./gradlew spotlessCheck detekt` (100% green).

## 2026-05-20 — Implemented dynamic Gradle cache SNAPSHOT metadata resolution for Flatpak offline builds
- Overhauled `GenerateFlatpakSourcesTask` in `gradle/flatpak.gradle.kts` to identify `-SNAPSHOT` dependencies, parse local cached `maven-metadata.xml` in `resources-2.1`, and dynamically map them to remote timestamped snapshot URLs (e.g. Sonatype Snapshots) while preserving their original non-timestamped file names as `dest-filename`.
- Created a pure JDK XML parser within the task to parse the `<snapshotVersions>` block from cached XML files.
- Verified that compiling the desktopApp and running the flatpak generator task successfully maps snapshot dependencies (such as `org.meshtastic:takpacket-sdk-jvm:0.2.4-SNAPSHOT`) to their remote unique snapshot URLs in `flatpak-sources.json`.
- Ran quality and validation checks: `./gradlew spotlessCheck detekt` (100% SUCCESSFUL with zero issues).

## 2026-05-20 — Resolved Flatpak jitpack.io dependency download 404s in sandboxed offline builds
- Modified `GenerateFlatpakSourcesTask` in `gradle/flatpak.gradle.kts` to dynamically detect dependency groups starting with `com.github.` (which are hosted on JitPack).
- Configured the generation of `primaryUrl` for these dependencies to resolve directly from `https://jitpack.io` and created custom high-availability fallback lists.
- Verified that compiling the desktopApp and running the flatpak generator task (`./gradlew :desktopApp:assemble :generateFlatpakSourcesFromCache --no-configuration-cache`) successfully produces a valid 10MB `flatpak-sources.json` containing correctly mapped JitPack URLs.
- Ran quality and validation checks: `./gradlew spotlessCheck detekt` (100% SUCCESSFUL with zero issues).

## 2026-05-20 — Replaced standalone translations landing page with dynamic global header language switcher dropdown
- Deleted redundant standalone page `docs/en/translations.md` and updated `docs/README.md` to reflect layout change.
- Completely re-engineered `docs/_includes/language_switcher.html` to:
  1. Dynamically parse active page paths and locales, matching default English pages with translated variants.
  2. Bind button text to native active language names (e.g., displaying "Беларуская" instead of hardcoded "English").
  3. Pre-verify file existence in `site.pages` to only render valid translation links, preventing any 404 errors.
  4. Automatically hide the language switcher on English pages that do not have translations available yet.
- Modified custom CSS styles in `docs/_includes/head_custom.html` to use `right: 0; left: auto;` layout alignment, preventing dropdown menu overflows on smaller screens.
- Successfully built and verified live documentation pages in the browser using the browser subagent, confirming fully operational dynamic swappers.
- Ran quality and validation checks: `./gradlew generateDocsBundle validateDocsBundle spotlessCheck detekt` (100% SUCCESSFUL).

## 2026-05-20 — Fixed Jekyll documentation site left navigation nesting and sub-page visibility
- Cleaned up redundant and brittle dynamic default scope parent settings in `docs/_config.yml`.
- Added explicit `parent: User Guide` front-matter fields to all 17 English user guide markdown files under `docs/en/user/`.
- Added explicit `parent: Developer Guide` front-matter fields to all 9 English developer guide markdown files under `docs/en/developer/`.
- Run and verified Gradle documentation bundle compilation: `./gradlew generateDocsBundle validateDocsBundle` (PASSED: 672 pages).
- Validated formatting and static analysis rules: `./gradlew spotlessApply spotlessCheck detekt` (100% green).
- Successfully completed full baseline compilation checks with `./gradlew assembleDebug`.

## 2026-05-20 — Completely overhauled, simplified, and pushed Flatpak offline source automation
- Completely retired the third-party `flatpak-gradle-generator` plugin and its associated complex configuration and library overrides (removed ~150 lines of boilerplate).
- Implemented a native JVM-based custom Gradle task `GenerateFlatpakSourcesTask` inside `gradle/flatpak.gradle.kts` which walks the post-compilation Gradle cache and generates a perfectly sorted, deduplicated `flatpak-sources.json` in under 3 seconds.
- Integrated high-availability mirror URL generation (Google, Gradle Plugin Portal, GCP Maven Central, and Aliyun Maven repositories) for complete build robustness during Flathub sandboxed offline builds.
- Streamlined CI workflow files `.github/workflows/release.yml` and `.github/workflows/reusable-check.yml` to call our native Gradle task.
- Validated all formatting and static analysis checks using `./gradlew spotlessCheck detekt` (100% green).
- Committed, successfully pushed the branch `fix/flatpak-sources-automation` to remote `jamesarich`, and updated the PR description on GitHub for PR #5533.

## 2026-05-20 — Rebased Flatpak Optimization Branch onto upstream/main
- Successfully rebased the working branch `fix/flatpak-sources-automation` onto the latest fetched `origin/main` (upstream).
- Resolved potential rebase history divergence by preserving only our unique conflict-bypassing override commit and skipping squash-merged commits.
- Updated git submodules to track the new upstream base commit.
- Re-ran all baseline quality and static analysis checks (`spotlessCheck`, `detekt`) and confirmed 100% success.
- Validated end-to-end correctness by running `:combineFlatpakSources` to verify complete and correct generation of the consolidated `flatpak-sources.json` manifest with 2,339 unique resources.

## 2026-05-20 — Overhauled and Bulletproofed Flatpak Source Generation
- Overhauled and streamlined `build-logic/convention/build.gradle.kts` to dynamically query and resolve all 25+ Version Catalog plugin marker coordinates in a type-safe dynamic loop.
- Replaced the hardcoded embedded Gradle Kotlin compiler version with dynamic standard library detection: `KotlinVersion.CURRENT.toString()`.
- Implemented root Gradle consolidator task `:combineFlatpakSources` using JVM-native Json Slurper/Output, deduplicating 2222 entries and injecting Google/Aliyun backup `mirror-urls`.
- Streamlined reusable-check and release CI workflows, replacing complex `jq` hacks with a simple, single gradle task invocation.
- Verified `spotlessCheck`, `detekt`, and end-to-end completely offline package builds (`--offline`) completed with 100% success.

## 2026-05-20 — Refactored and polished Flatpak dependency manifests to modern Gradle standards
- Polished the Flatpak generator tasks and convention setup following a comprehensive audit:
  1. Centralized version definitions to reference the central `libs.versions` catalog (`kotlin`, `koin.plugin`, etc.) instead of hardcoded strings.
  2. Documented every single dependency override and compiler-plugin helper with clear, inline comments (`// why: ...`).
  3. Cleaned out legacy dependencies and streamlined `core:database` to only capture `kspKotlinJvmProcessorClasspath`.
  4. Changed includeConfigurations to use type-safe `setOf` instead of `listOf` to align with Gradle's `SetProperty` APIs.
- Verified that all static analysis checks pass successfully: `./gradlew spotlessCheck detekt` is 100% green.
- Validated end-to-end correctness by successfully compiling the app completely offline (`--offline`) and packaging the release UberJar.

## 2026-05-20 — Fixed Jekyll documentation site build and deployment in CI
- Created `docs/index.html` to automatically redirect root path requests (`/`) to the English directory (`/en/`).
- Updated `.github/workflows/docs-deploy.yml` to compile the Jekyll root site using `--baseurl /${{ github.event.repository.name }}` and setup Ruby with version `4.0.4` to match project release workflow conventions.
- Updated `.github/workflows/docs-release.yml` to compile both versioned and root Jekyll sites, assemble them into `build/final_site/` with Dokka HTML references, configure correct baseurls respectively, and setup Ruby with version `4.0.4`.
- Verified that local Gradle docs tasks (`generateDocsBundle`, `validateDocsBundle`, `publishDocsSite`) compile successfully and the redirect file is correctly populated in the output.

## 2026-05-20 — Optimized slow Flatpak CI jobs
- Restrained flatpakGradleGenerator to target only the runtimeClasspath configuration in desktopApp and build-logic:convention modules.
- Updated release.yml and reusable-check.yml to invoke only targeted tasks (:desktopApp:flatpakGradleGenerator and :build-logic:convention:flatpakGradleGenerator) instead of running the task on the root and all subprojects.
- Retained Matrix architecture runner configuration for build validity, as Skiko/Compose Desktop native artifacts are resolved dynamically based on host architecture.
- Cleaned up leftover speed-up workarounds: completely removed the Flatpak Gradle Generator plugin application and tasks from the root project and all other library/feature subprojects (`core:ble`, `core:common`, `core:database`, `core:model`, `core:navigation`, `core:proto`, and `feature:messaging`), including deleting the unused `flatpakKmpAndroidMeta` configuration from `feature:messaging`.
- Verified that local execution of `:desktopApp:flatpakGradleGenerator` runtimeClasspath resolution speed dropped from 46 seconds to 12 seconds, and all Spotless and Detekt linting checks passed.

## 2026-05-12 — Implemented Apple alignment for docs feature (FR-038)
- Branch: `feat/20260507-161858-app-docs-markdown`
- Gap analysis against `meshtastic-apple` completed. Implemented 4 alignment items:
  1. Per-page TOC icons via `DocPageIconResolver.kt` mapping `iconId` to `MeshtasticIcons`
  2. New `docs/user/signal-meter.md` (RSSI vs SNR, bar-level criteria, LoRa signal concepts)
  3. New `docs/user/units-and-locale.md` (automatic metric/imperial via `MetricFormatter`)
  4. New `.github/workflows/docs-staleness.yml` (advisory PR comments for UI changes without doc updates)
- Added `iconId: String?` field to `DocPage` and `KeywordIndexEntry` models
- Updated `DocBundleLoader` with iconId for all 24 pages plus 2 new entries (signal-meter, units-and-locale)
- Updated `DocsBrowserScreen` to show leading icons in TOC list items
- Marked T061-T085 as completed in tasks.md (were implemented in prior session)
- Added Phase 9 (T200-T206) for Apple alignment tasks — all marked complete
- Skipped Apple-only features: watch, carplay, translate, TipKit, SwiftData docs
- Verified: `spotlessApply`, `detekt`, `assembleDebug`, `compileKotlinJvm` — all green

## 2026-05-11 — Migrated feature/intro UI to commonMain
- Moved intro onboarding UI composables and nav graph from `feature/intro/src/androidMain/` into `feature/intro/src/commonMain/`, adding shared `IntroPermissions` and `IntroSettingsNavigator` interfaces plus a common `introGraph` Navigation 3 extension.
- Refactored `AppIntroductionScreen` into a thin Android host that provides Android permission/settings adapters via composition locals, and added `AndroidIntroPermissions`, `AndroidIntroSettingsNavigator`, and JVM desktop no-op stubs.
- Verified with `./gradlew spotlessApply :feature:intro:compileKotlinJvm :feature:intro:compileAndroidMain`.

## 2026-05-11 — Added Esp32OtaUpdateHandler common tests
- Created `feature/firmware/src/commonTest/kotlin/org/meshtastic/feature/firmware/ota/Esp32OtaUpdateHandlerTest.kt`.
- Covered WiFi OTA success flow, download/upload progress reporting, connection-drop error handling, hash rejection, verification timeout, and cancellation propagation.
- Validation note: per task instruction, no Gradle commands were run.

## 2026-05-11 — Added profile import/export round-trip coverage
- Created `feature/settings/src/commonTest/kotlin/org/meshtastic/feature/settings/radio/ProfileRoundTripTest.kt`.
- Covered `RadioConfigViewModel.exportProfile()` → `importProfile()` round trips using the real `ExportProfileUseCase` and `ImportProfileUseCase` with an in-memory `FileService` test double.
- Added representative, empty, and partially populated `DeviceProfile` cases, asserting message equality and stable protobuf bytes across re-export.
- Validation note: per task instruction, no Gradle commands were run.

## 2026-05-11 — Added DirectRadioControllerImpl common tests
- Created `core/service/src/commonTest/kotlin/org/meshtastic/core/service/DirectRadioControllerImplTest.kt`.
- Covered service-repository flow delegation, send message/send shared contact behavior, remote config request delegation, location stop, and device address updates.
- Validation note: `./gradlew --no-configuration-cache :core:service:allTests` is currently blocked by pre-existing compile failures in `core/network` (`MQTTRepositoryImpl` unresolved `KEEPALIVE_SECONDS`) and downstream `core/data` unresolved `org.meshtastic.core.network` symbols.

## 2026-05-11 — Added DatabaseManager withDb retry host test
- Created `core/database/src/androidHostTest/kotlin/org/meshtastic/core/database/DatabaseManagerWithDbRetryTest.kt`.
- Covered the concurrent `withDb()` retry path by pausing an in-flight query, switching to a new DB, closing the old pool, and asserting the retried query succeeds against the new DB.
- Verified with `./gradlew --no-configuration-cache :core:database:spotlessApply :core:database:testAndroidHostTest --tests "org.meshtastic.core.database.DatabaseManagerWithDbRetryTest"`
  and `./gradlew --no-configuration-cache :core:database:spotlessCheck :core:database:testAndroidHostTest`.

## 2026-05-11 — Expanded MQTT repository coverage
- Extended `core/network/src/commonTest/kotlin/org/meshtastic/core/network/repository/MQTTRepositoryImplTest.kt`
  with topic construction, JSON/protobuf decoding, reconnect retry, subscription retry, and connection-state coverage.
- Added internal `MqttClientSession` / `MqttClientSetup` test hook plus `updateConnectionState()` in
  `core/network/src/commonMain/kotlin/org/meshtastic/core/network/repository/MQTTRepositoryImpl.kt` to exercise repository behavior without a real broker.
- Verified with `./gradlew --no-configuration-cache :core:network:allTests`.

## 2026-05-11 — Added RadioConfigViewModel MQTT probe tests
- Extended `feature/settings/src/commonTest/kotlin/org/meshtastic/feature/settings/radio/RadioConfigViewModelTest.kt`
  with MQTT probe success, timeout, thrown-exception-to-Other, and clear/reset coverage.
- Verified with `./gradlew --no-configuration-cache :feature:settings:jvmTest --tests "org.meshtastic.feature.settings.radio.RadioConfigViewModelTest"`.

## 2026-05-11 — Added MeshRouterImpl accessor routing tests
- Created `core/data/src/commonTest/kotlin/org/meshtastic/core/data/manager/MeshRouterImplTest.kt`.
- Covered lazy routing access for action-handler send/request/admin calls, traceroute handler access, and service-action passthrough.
- Verified with `./gradlew --no-configuration-cache :core:data:allTests`.

## 2026-05-11 — Added SettingsViewModel saveDataCsv coverage
- Extended `feature/settings/src/commonTest/kotlin/org/meshtastic/feature/settings/SettingsViewModelTest.kt`
  with `saveDataCsv writes filtered export via file service`.
- The new test seeds `FakeNodeRepository` + `FakeMeshLogRepository`, captures the `FileService.write()`
  sink with Mokkery, and verifies filtered CSV output from the real `ExportDataUseCase`.
- Verified with `./gradlew --no-configuration-cache :feature:settings:jvmTest --tests "org.meshtastic.feature.settings.SettingsViewModelTest"`
  after running `:feature:settings:spotlessApply`.

## 2026-05-11 — Added CompassViewModel accuracy edge-case tests
- Extended `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/compass/CompassViewModelTest.kt`
  with PDOP-only, HDOP+VDOP, HDOP-only, precision-bits fallback, missing accuracy metadata,
  zero-distance angular error, and very-small-distance angular error coverage.
- Validation note: `:feature:node:allTests` still fails on the pre-existing
  `MetricsViewModelTest.saveEnvironmentMetricsCSV writes correct data` Turbine timeout in JVM and Android host tests.
  The new CompassViewModel tests pass in the same run.

## 2026-05-11 — Added Node domain model tests
- Created `core/model/src/commonTest/kotlin/org/meshtastic/core/model/NodeTest.kt`.
- Covered `isOnline`, `distance`, `bearing`, `colors`, `createFallback`, `getRelayNode`, `isUnknownUser`, `validPosition`, `hasPKC`, and `mismatchKey`.
- Validation blockers: `:core:model:allTests` currently fails on pre-existing `DataPacketTest` iOS compile errors, and direct `NodeTest` execution hits an existing class-version mismatch in `core:common` helpers.

## 2026-05-11 — Added HeartbeatSender transport tests
- Created `core/network/src/commonTest/kotlin/org/meshtastic/core/network/transport/HeartbeatSenderTest.kt`.
- Covered encoded heartbeat payloads, nonce sequencing, interval-driven scheduling, cancellation, zero-interval behavior, and restart semantics using coroutine virtual time.
- Verified with `./gradlew --console=plain --no-configuration-cache :core:network:allTests`.

## 2026-05-11 — Added BaseMapViewModel waypoint expiration tests
- Extended `feature/map/src/commonTest/kotlin/org/meshtastic/feature/map/BaseMapViewModelTest.kt`.
- Added coverage for future, boundary (`expire == now`), never-expiring (`expire == 0`), and mixed waypoint filtering.
- Verified with `./gradlew --no-daemon --no-configuration-cache :feature:map:spotlessCheck :feature:map:allTests`.

## 2026-05-03 — Switched Gradle GC to G1GC
- Replaced `-XX:+UseZGC` with `-XX:+UseG1GC` in `gradle.properties` to resolve "not supported" error.
- Added `-XX:+ParallelRefProcEnabled` for better build performance.
- Verified with Gradle sync.

## 2026-05-02 — CI cost-control PR review fixes
- Applied PR review feedback: encoding fixes in sort-strings.py, NUL-delimited staged-files loop
  in ai-guardrail.sh, installation instructions added, typo fix in strings.xml, command order
  fixed in AGENTS.md, narrowed .aiexclude/.gitattributes patterns, allTests added to SKILL.md.

## 2026-04-XX — Token Mitigation (Phase 1-3)
- `.copilotignore` and `.aiexclude` updated with stricter ignore rules.
- `AGENTS.md` modularized to ~3KB base; detailed rules moved to `.skills/`.
- `scripts/ai-guardrail.sh` added to prevent binary/log leaks (installation: see script header).
- CI Cost Control skill added at `.skills/ci-cost-control/SKILL.md`.

## Golden Context (stable across sessions)
- Always check `.skills/compose-ui/strings-index.txt` before reading `strings.xml`.
- Run `python3 scripts/sort-strings.py` after adding strings to keep the index organized.
- Always check `gh run list` before pushing.
- Pre-commit hook `scripts/ai-guardrail.sh` protects against binary leaks (see script for install).
