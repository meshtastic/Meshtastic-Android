# Agent Session Context - Meshtastic Android
# This is a dated, append-only handover log. Add new entries at the TOP.
# Do NOT edit or remove previous entries — stale state claims cause agent confusion.
# Format: ## YYYY-MM-DD — <summary>

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
