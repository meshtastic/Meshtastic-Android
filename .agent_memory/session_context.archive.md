# Agent Session Context — ARCHIVE
# Older handover entries rotated out of session_context.md. Not read by default.
# Consult only if you need historical detail on a specific past change.

## 2026-05-21 — Fixed Chirpy Assistant invalid model name and enhanced failure fallback suggestions
- Fixed a 404/Unknown inference error by updating `GeminiNanoDocAssistant.kt`'s `MODEL_NAME` from `"gemini-3.1-flash-lite"` to the correct Firebase AI Logic preview name `"gemini-3.1-flash-lite-preview"`.
- Overhauled multi-turn hybrid chat seeding: eliminated the redundant background `chat.sendMessage` call on the first turn; if the first turn is answered on-device, the session caches the Q&A locally and seeds the subsequent cloud-chat session via `startChat(history = ...)`.
- Expanded the hybrid model's `looksLikeNoAnswer` heuristics to better detect on-device failure and fall back to the grounded cloud model.
- Programmed a smart UI fallback: on inference error (offline, rate limit, model not found), Chirpy displays local keyword search results as recommended page chips.
- Verified 100% compliance with Spotless, Detekt, and unit tests (`:feature:docs:allTests` and `:androidApp:testGoogleDebugUnitTest`).

## 2026-05-20 — Decoupled and Isolated Flatpak manifest generation logic to build-logic/flatpak
- Isolated the optimized `GenerateFlatpakSourcesTask` from monolithic `build-logic/convention` into its own specialized, lightweight `:flatpak` subproject under `build-logic`.
- Created `:flatpak` configuration and registered the formal plugin ID `"meshtastic.flatpak"` implemented by `FlatpakConventionPlugin` inside the default package namespace.
- Implemented modern, configuration-cache-safe lazy provider directory evaluation for the default Gradle user home cache.
- Cleaned up `:convention` by removing the redundant class and registration imports from `RootConventionPlugin.kt`.
- Applied the new plugin in the root `build.gradle.kts` using `id("meshtastic.flatpak")`.
- Verified 100% compliant spotless and detekt formatting checks.
- Pushed branch `fix/flatpak-snapshot-resolution`; updated PR #5542's description.

## 2026-05-20 — Extracted GenerateFlatpakSourcesTask to precompiled build-logic convention plugin
- Extracted the entire task logic, data classes, and extension helpers from loose script files to a precompiled Kotlin class: `build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/GenerateFlatpakSourcesTask.kt`.
- Registered the task directly within `RootConventionPlugin.kt` and removed the legacy `gradle/flatpak.gradle.kts` script block from the root `build.gradle.kts`.
- Resolved an implicit Gradle non-serializable property capture inside the lazy property mappings for full Configuration Cache compliance.
- Validated with `./gradlew clean` and `./gradlew spotlessCheck detekt` (100% green).

## 2026-05-20 — Optimized GenerateFlatpakSourcesTask for performance and correctness
- Implemented single-pass Maven metadata XML pre-indexing (O(1) lookups) and deferred SHA-256 calculation (digests only on deduplicated, finalized resources).
- Refactored loose maps into strongly-typed data classes (`SnapshotVersion`, `SnapshotMetadata`, `FlatpakSourceCandidate`).
- Verified output is character-for-character identical to the original unoptimized `flatpak-sources.json`.
- Passed `./gradlew spotlessCheck detekt`.

## 2026-05-20 — Implemented dynamic Gradle cache SNAPSHOT metadata resolution for Flatpak offline builds
- Overhauled `GenerateFlatpakSourcesTask` to identify `-SNAPSHOT` dependencies, parse local cached `maven-metadata.xml` in `resources-2.1`, and map them to remote timestamped snapshot URLs while preserving non-timestamped filenames as `dest-filename`.
- Created a pure JDK XML parser for the `<snapshotVersions>` block.
- Verified snapshot deps (e.g. `org.meshtastic:takpacket-sdk-jvm:0.2.4-SNAPSHOT`) map correctly. Passed spotless/detekt.

## 2026-05-20 — Resolved Flatpak jitpack.io dependency download 404s in sandboxed offline builds
- Modified the task to detect `com.github.` groups (JitPack-hosted) and resolve `primaryUrl` from `https://jitpack.io` with high-availability fallback lists.
- Verified a valid 10MB `flatpak-sources.json` with correct JitPack URLs. Passed spotless/detekt.

## 2026-05-20 — Replaced standalone translations landing page with dynamic global header language switcher dropdown
- Deleted `docs/en/translations.md`; updated `docs/README.md`.
- Re-engineered `docs/_includes/language_switcher.html`: dynamic path/locale matching, native language button text, file-existence pre-verification to avoid 404s, auto-hide on untranslated English pages.
- Adjusted CSS in `docs/_includes/head_custom.html` (`right: 0; left: auto;`) to prevent dropdown overflow.
- Verified live in browser. Passed docs bundle + spotless/detekt.

## 2026-05-20 — Fixed Jekyll documentation site left navigation nesting and sub-page visibility
- Cleaned up dynamic default scope parent settings in `docs/_config.yml`.
- Added explicit `parent:` front-matter to 17 user-guide and 9 developer-guide English markdown files.
- Verified docs bundle compilation (672 pages), spotless/detekt, and `assembleDebug`.

## 2026-05-20 — Completely overhauled, simplified, and pushed Flatpak offline source automation
- Retired the third-party `flatpak-gradle-generator` plugin (~150 lines of boilerplate).
- Implemented native JVM Gradle task `GenerateFlatpakSourcesTask` in `gradle/flatpak.gradle.kts` walking the post-compilation Gradle cache to emit a sorted, deduplicated `flatpak-sources.json` in <3s.
- Integrated high-availability mirror URLs (Google, Gradle Plugin Portal, GCP Maven Central, Aliyun).
- Updated CI `release.yml` and `reusable-check.yml`. Passed spotless/detekt. Pushed `fix/flatpak-sources-automation`; updated PR #5533.

## 2026-05-20 — Rebased Flatpak Optimization Branch onto upstream/main
- Rebased `fix/flatpak-sources-automation` onto latest `origin/main`, preserving the unique conflict-bypass override and skipping squash-merged commits.
- Updated submodules. Re-ran spotless/detekt. Verified `:combineFlatpakSources` produces 2,339 unique resources.

## 2026-05-20 — Overhauled and Bulletproofed Flatpak Source Generation
- Streamlined `build-logic/convention/build.gradle.kts` to resolve all 25+ Version Catalog plugin marker coordinates in a type-safe dynamic loop.
- Replaced hardcoded Kotlin compiler version with `KotlinVersion.CURRENT.toString()`.
- Implemented root `:combineFlatpakSources` (JVM-native JSON), deduplicating 2222 entries and injecting Google/Aliyun `mirror-urls`.
- Verified spotless/detekt and fully offline builds (`--offline`).

## 2026-05-20 — Refactored and polished Flatpak dependency manifests to modern Gradle standards
- Centralized version definitions to the `libs.versions` catalog; documented every override with `// why:` comments; streamlined `core:database` to capture only `kspKotlinJvmProcessorClasspath`; used type-safe `setOf` for `includeConfigurations`.
- Verified spotless/detekt and offline UberJar packaging.

## 2026-05-20 — Fixed Jekyll documentation site build and deployment in CI
- Added `docs/index.html` redirect (`/` → `/en/`).
- Updated `docs-deploy.yml` and `docs-release.yml` to compile Jekyll with `--baseurl` and Ruby 4.0.4, assembling versioned + root sites with Dokka HTML.
- Verified docs bundle tasks and redirect output.

## 2026-05-20 — Optimized slow Flatpak CI jobs
- Restrained `flatpakGradleGenerator` to the `runtimeClasspath` configuration in desktopApp and build-logic:convention only.
- Updated `release.yml`/`reusable-check.yml` to invoke targeted tasks instead of root+all-subprojects.
- Removed the plugin from root and other subprojects (`core:ble/common/database/model/navigation/proto`, `feature:messaging`), deleting unused `flatpakKmpAndroidMeta`.
- Runtime dropped 46s → 12s; spotless/detekt green.

## 2026-05-12 — Implemented Apple alignment for docs feature (FR-038)
- Branch: `feat/20260507-161858-app-docs-markdown`. Implemented 4 alignment items vs `meshtastic-apple`:
  1. Per-page TOC icons via `DocPageIconResolver.kt` mapping `iconId` to `MeshtasticIcons`.
  2. New `docs/user/signal-meter.md` (RSSI vs SNR).
  3. New `docs/user/units-and-locale.md` (metric/imperial via `MetricFormatter`).
  4. New `.github/workflows/docs-staleness.yml` (advisory PR comments).
- Added `iconId: String?` to `DocPage`/`KeywordIndexEntry`; updated `DocBundleLoader` (24 pages + 2 new) and `DocsBrowserScreen`.
- Marked T061-T085, T200-T206 complete. Skipped Apple-only features. Verified spotless/detekt/assembleDebug/compileKotlinJvm.

## 2026-05-11 — Migrated feature/intro UI to commonMain
- Moved intro onboarding composables + nav graph from `androidMain` to `commonMain`, adding shared `IntroPermissions`/`IntroSettingsNavigator` interfaces and a common `introGraph`.
- Refactored `AppIntroductionScreen` into a thin Android host providing permission/settings adapters via composition locals; added Android adapters and JVM no-op stubs.
- Verified `:feature:intro:compileKotlinJvm` + `:feature:intro:compileAndroidMain`.

## 2026-05-11 — Added Esp32OtaUpdateHandler common tests
- Created `Esp32OtaUpdateHandlerTest.kt`; covered WiFi OTA success, progress reporting, connection-drop, hash rejection, verification timeout, cancellation. (No Gradle run per instruction.)

## 2026-05-11 — Added profile import/export round-trip coverage
- Created `ProfileRoundTripTest.kt`; covered `exportProfile()`→`importProfile()` round trips via real use cases with an in-memory `FileService`. Representative/empty/partial `DeviceProfile` cases. (No Gradle run per instruction.)

## 2026-05-11 — Added DirectRadioControllerImpl common tests
- Created `DirectRadioControllerImplTest.kt`; covered flow delegation, send message/shared contact, remote config request, location stop, device address updates.
- Note: `:core:service:allTests` blocked by pre-existing compile failures in `core/network` (`MQTTRepositoryImpl` unresolved `KEEPALIVE_SECONDS`) and downstream `core/data`.

## 2026-05-11 — Added DatabaseManager withDb retry host test
- Created `DatabaseManagerWithDbRetryTest.kt`; covered the concurrent `withDb()` retry path (pause in-flight query, switch DB, close old pool, assert retry succeeds).
- Verified with `:core:database:testAndroidHostTest`.

## 2026-05-11 — Expanded MQTT repository coverage
- Extended `MQTTRepositoryImplTest.kt` with topic construction, JSON/protobuf decoding, reconnect/subscription retry, connection-state coverage.
- Added internal `MqttClientSession`/`MqttClientSetup` hook + `updateConnectionState()`. Verified `:core:network:allTests`.

## 2026-05-11 — Added RadioConfigViewModel MQTT probe tests
- Extended `RadioConfigViewModelTest.kt` with MQTT probe success, timeout, thrown-exception-to-Other, clear/reset. Verified `:feature:settings:jvmTest`.

## 2026-05-11 — Added MeshRouterImpl accessor routing tests
- Created `MeshRouterImplTest.kt`; covered lazy routing access for send/request/admin, traceroute handler, service-action passthrough. Verified `:core:data:allTests`.

## 2026-05-11 — Added SettingsViewModel saveDataCsv coverage
- Extended `SettingsViewModelTest.kt` with filtered-CSV export test (Fake repos + Mokkery `FileService.write()` capture, real `ExportDataUseCase`). Verified `:feature:settings:jvmTest`.

## 2026-05-11 — Added CompassViewModel accuracy edge-case tests
- Extended `CompassViewModelTest.kt` with PDOP-only, HDOP+VDOP, HDOP-only, precision-bits fallback, missing-accuracy, zero/small-distance angular error.
- Note: `:feature:node:allTests` still fails on pre-existing `MetricsViewModelTest` Turbine timeout; new tests pass.

## 2026-05-11 — Added Node domain model tests
- Created `NodeTest.kt`; covered `isOnline`, `distance`, `bearing`, `colors`, `createFallback`, `getRelayNode`, `isUnknownUser`, `validPosition`, `hasPKC`, `mismatchKey`.
- Blockers: `:core:model:allTests` fails on pre-existing `DataPacketTest` iOS compile errors; direct run hits a class-version mismatch in `core:common`.

## 2026-05-11 — Added HeartbeatSender transport tests
- Created `HeartbeatSenderTest.kt`; covered encoded payloads, nonce sequencing, interval scheduling, cancellation, zero-interval, restart (coroutine virtual time). Verified `:core:network:allTests`.

## 2026-05-11 — Added BaseMapViewModel waypoint expiration tests
- Extended `BaseMapViewModelTest.kt` with future, boundary (`expire == now`), never-expiring (`expire == 0`), mixed filtering. Verified `:feature:map:allTests`.

## 2026-05-03 — Switched Gradle GC to G1GC
- Replaced `-XX:+UseZGC` with `-XX:+UseG1GC` in `gradle.properties`; added `-XX:+ParallelRefProcEnabled`. Verified with Gradle sync.

## 2026-05-02 — CI cost-control PR review fixes
- Applied PR feedback: encoding fixes in sort-strings.py, NUL-delimited staged-files loop in ai-guardrail.sh, install instructions, strings.xml typo, AGENTS.md command order, narrowed .aiexclude/.gitattributes patterns, allTests added to SKILL.md.

## 2026-04-XX — Token Mitigation (Phase 1-3)
- `.copilotignore` and `.aiexclude` updated with stricter ignore rules.
- `AGENTS.md` modularized to ~3KB base; detailed rules moved to `.skills/`.
- `scripts/ai-guardrail.sh` added to prevent binary/log leaks.
- CI Cost Control skill added at `.skills/ci-cost-control/SKILL.md`.
