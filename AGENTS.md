# Meshtastic Android - Agent Guide

This file serves as a comprehensive guide for AI agents and developers working on the `Meshtastic-Android` codebase. Use this as your primary reference for understanding the architecture, conventions, and strict rules of this project.

For execution-focused recipes, see `docs/agent-playbooks/README.md`.

## 1. Project Vision & Architecture
Meshtastic-Android is a Kotlin Multiplatform (KMP) application for off-grid, decentralized mesh networks. The goal is to decouple business logic from the Android framework, enabling future expansion to iOS and other platforms while maintaining a high-performance native Android experience.

- **Language:** Kotlin (primary), AIDL.
- **Build System:** Gradle (Kotlin DSL). JDK 21 is REQUIRED.
- **Target SDK:** API 36. Min SDK: API 26 (Android 8.0).
- **Flavors:**
  - `fdroid`: Open source only, no tracking/analytics.
  - `google`: Includes Google Play Services (Maps) and DataDog analytics (RUM, Session Replay, Compose action tracking, custom `connect` RUM action). 100% sampling, Apple-parity environments ("Local"/"Production").
- **Core Architecture:** Modern Android Development (MAD) with KMP core.
  - **KMP Modules:** Most `core:*` modules. All declare `jvm()`, `iosArm64()`, and `iosSimulatorArm64()` targets and compile clean across all.
  - **Android-only Modules:** `core:api` (AIDL), `core:barcode` (CameraX + flavor-specific decoder). Shared contracts abstracted into `core:ui/commonMain`.
  - **UI:** Jetpack Compose Multiplatform (Material 3).
  - **DI:** Koin Annotations with K2 compiler plugin. Root graph assembly is centralized in `app` and `desktop`.
  - **Navigation:** JetBrains Navigation 3 (Scene-based architecture) with shared backstack state. Deep linking uses RESTful paths (e.g. `/nodes/1234`) parsed by `DeepLinkRouter` in `core:navigation`.
  - **Lifecycle:** JetBrains multiplatform `lifecycle-viewmodel-compose` and `lifecycle-runtime-compose`.
  - **Adaptive UI:** Material 3 Adaptive (v1.3+) with support for Large (1200dp) and Extra-large (1600dp) breakpoints.
  - **Database:** Room KMP.

## 2. Codebase Map

| Directory | Description |
| :--- | :--- |
| `app/` | Main application module. Contains `MainActivity`, Koin DI modules, and app-level logic. Uses package `org.meshtastic.app`. |
| `build-logic/` | Convention plugins for shared build configuration (e.g., `meshtastic.kmp.feature`, `meshtastic.kmp.library`, `meshtastic.kmp.jvm.android`, `meshtastic.koin`). |
| `config/` | Detekt static analysis rules (`config/detekt/detekt.yml`) and Spotless formatting config (`config/spotless/.editorconfig`). |
| `docs/` | Architecture docs and agent playbooks. See `docs/agent-playbooks/README.md` for version baseline and task recipes. |
| `core/model` | Domain models and common data structures. |
| `core:proto` | Protobuf definitions (Git submodule). |
| `core:common` | Low-level utilities, I/O abstractions (Okio), and common types. |
| `core:database` | Room KMP database implementation. |
| `core:datastore` | Multiplatform DataStore for preferences. |
| `core:repository` | High-level domain interfaces (e.g., `NodeRepository`, `LocationRepository`). |
| `core:domain` | Pure KMP business logic and UseCases. |
| `core:data` | Core manager implementations and data orchestration. |
| `core:network` | KMP networking layer using Ktor, MQTT abstractions, and shared transport (`StreamFrameCodec`, `TcpTransport`, `SerialTransport`, `BleRadioInterface`). |
| `core:di` | Common DI qualifiers and dispatchers. |
| `core:navigation` | Shared navigation keys/routes for Navigation 3 using `@Serializable sealed interface` hierarchies per feature domain (e.g., `SettingsRoute`, `NodesRoute`). `DeepLinkRouter` for typed backstack synthesis, and `MeshtasticNavSavedStateConfig` with `subclassesOfSealed()` for automatic polymorphic backstack persistence — new routes are registered at compile time. |
| `core:ui` | Shared Compose UI components (`MeshtasticAppShell`, `MeshtasticNavDisplay`, `MeshtasticNavigationSuite`, `AlertHost`, `SharedDialogs`, `PlaceholderScreen`, `MainAppBar`, dialogs, preferences) and platform abstractions. |
| `core:service` | KMP service layer; Android bindings stay in `androidMain`. |
| `core:api` | Public AIDL/API integration module for external clients. |
| `core:prefs` | KMP preferences layer built on DataStore abstractions. |
| `core:barcode` | Barcode scanning (Android-only). |
| `core:nfc` | NFC abstractions (KMP). Android NFC hardware implementation in `androidMain`. |
| `core/ble/` | Bluetooth Low Energy stack using Kable. |
| `core/resources/` | Centralized string and image resources (Compose Multiplatform). |
| `core/testing/` | **Shared test doubles, fakes, and utilities for `commonTest` across all KMP modules.** |
| `feature/` | Feature modules (e.g., `settings`, `map`, `messaging`, `node`, `intro`, `connections`, `firmware`, `wifi-provision`, `widget`). All are KMP with `jvm()` and `ios()` targets except `widget`. Use `meshtastic.kmp.feature` convention plugin. |
| `feature/wifi-provision` | KMP WiFi provisioning via BLE (Nymea protocol). Scans for provisioning devices, lists available networks, applies credentials. Uses `core:ble` Kable abstractions. |
| `feature/firmware` | Fully KMP firmware update system: Unified OTA (BLE + WiFi via Kable/Ktor), native Nordic Secure DFU protocol (pure KMP, no Nordic library), USB/UF2 updates, and `FirmwareRetriever` with manifest-based resolution. Desktop is a first-class target. |
| `desktop/` | Compose Desktop application — first non-Android KMP target. Thin host shell relying entirely on feature modules for shared UI. Full Koin DI graph, TCP, Serial/USB, and BLE transports with `want_config` handshake. Versioning mirrors Android via `config.properties` + `GitVersionValueSource`; a `generateDesktopBuildConfig` task produces `DesktopBuildConfig.kt` at build time. |
| `mesh_service_example/` | **DEPRECATED — scheduled for removal.** Legacy sample app showing `core:api` service integration. Do not add code here. See `core/api/README.md` for the current integration guide. |

## 3. Development Guidelines & Coding Standards

### A. UI Development (Jetpack Compose)
-   **Material 3:** The app uses Material 3.
-   **Strings:** MUST use the **Compose Multiplatform Resource** library in `core:resources` (`stringResource(Res.string.your_key)`). For ViewModels or non-composable Coroutines, use the asynchronous `getStringSuspend(Res.string.your_key)`. NEVER use hardcoded strings, and NEVER use the blocking `getString()` in a coroutine.
-   **String formatting:** CMP's `stringResource(res, args)` / `getString(res, args)` only support `%N$s` (string) and `%N$d` (integer) positional specifiers. Float formats like `%N$.1f` are NOT supported — they pass through unsubstituted. For float values, pre-format in Kotlin using `NumberFormatter.format(value, decimalPlaces)` from `core:common` and pass the result as a `%N$s` string arg. Use bare `%` (not `%%`) for literal percent signs in CMP-consumed strings, since CMP does not convert `%%` to `%`. For JVM-only code using `formatString()` (which wraps `String.format()`), full printf specifiers including `%N$.Nf` and `%%` are supported.
-   **Dialogs:** Use centralized components in `core:ui` (e.g., `MeshtasticResourceDialog`).
-   **Alerts:** Use `AlertHost(alertManager)` from `core:ui/commonMain` in each platform host shell (`Main.kt`, `DesktopMainScreen.kt`). For global responses like traceroute and firmware validation, use the specialized common handlers: `TracerouteAlertHandler(uiViewModel)` and `FirmwareVersionCheck(uiViewModel)`. Do NOT duplicate inline alert-rendering logic or trigger alerts directly during composition. For shared QR/contact dialogs, use the `SharedDialogs(uiViewModel)` composable.
-   **Placeholders:** For desktop/JVM features not yet implemented, use `PlaceholderScreen(name)` from `core:ui/commonMain`. Do NOT define inline placeholder composables in feature modules.
-   **Theme Picker:** Use `ThemePickerDialog` and `ThemeOption` from `feature:settings/commonMain`. Do NOT duplicate the theme dialog or enum in platform-specific source sets.
-   **Adaptive Layouts:** Use `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)` to support the 2026 Desktop Experience breakpoints. Prioritize **higher information density** and mouse-precision interactions for Desktop and External Display (Android 16 QPR3) targets. **Investigate 3-pane "Power User" scenes** (e.g., Node List + Detail + Map/Charts) using Navigation 3 Scenes, `extraPane()`, and draggable dividers (`VerticalDragHandle` + `paneExpansionState`) for widths ≥ 1200dp.
-   **Platform/Flavor UI:** Inject platform-specific behavior (e.g., map providers) via `CompositionLocal` from `app`.

### B. Logic & Data Layer
-   **KMP Focus:** All business logic must reside in `commonMain` of the respective `core` module.
-   **Platform purity:** Never import `java.*` or `android.*` in `commonMain`. Use KMP alternatives:
    -   `java.util.Locale` → Kotlin `uppercase()` / `lowercase()` or `expect`/`actual`.
    -   `java.util.concurrent.ConcurrentHashMap` → `atomicfu` or `Mutex`-guarded `mutableMapOf()`.
    -   `java.util.concurrent.locks.*` → `kotlinx.coroutines.sync.Mutex`.
    -   `java.io.*` → Okio (`BufferedSource`/`BufferedSink`). Note: JetBrains now recommends `kotlinx-io` as the official Kotlin I/O library (built on Okio). This project standardizes on Okio directly; do not migrate without explicit decision.
    -   `kotlinx.coroutines.Dispatchers.IO` → `org.meshtastic.core.common.util.ioDispatcher` (expect/actual). Note: `Dispatchers.IO` is available in `commonMain` since kotlinx.coroutines 1.8.0, but this project uses the `ioDispatcher` wrapper for consistency.
-   **Shared helpers over duplicated lambdas:** When `androidMain` and `jvmMain` contain identical pure-Kotlin logic (formatting, action dispatch, validation), extract it to a function in `commonMain`. Examples: `formatLogsTo()` in `feature:settings`, `handleNodeAction()` in `feature:node`, `findNodeByNameSuffix()` in `feature:connections`, `MeshtasticAppShell` in `core:ui/commonMain`, and `BaseRadioTransportFactory` in `core:network/commonMain`.
-   **KMP file naming:** In KMP modules, `commonMain` and platform source sets (`androidMain`, `jvmMain`) share the same package namespace. If both contain a file with the same name (e.g., `LogExporter.kt`), the Kotlin/JVM compiler will produce a duplicate class error. Use distinct filenames: keep the `expect` declaration in `LogExporter.kt` and put shared helpers in a separate file like `LogFormatter.kt`.
-   **`jvmAndroidMain` source set:** Modules that share JVM-specific code between Android and Desktop apply the `meshtastic.kmp.jvm.android` convention plugin. This creates a `jvmAndroidMain` source set via Kotlin's hierarchy template API. Used in `core:common`, `core:model`, `core:data`, `core:network`, and `core:ui`.
-   **Hierarchy template first:** Prefer Kotlin's default hierarchy template and convention plugins over manual `dependsOn(...)` graphs. Manual source-set wiring should be reserved for cases the template cannot model.
-   **`expect`/`actual` restraint:** Prefer interfaces + DI for platform capabilities; use `expect`/`actual` for small unavoidable platform primitives. Avoid broad expect/actual class hierarchies when an interface-based boundary is sufficient.
-   **Feature navigation graphs:** Feature modules export Navigation 3 graph functions as extension functions on `EntryProviderScope<NavKey>` in `commonMain` (e.g., `fun EntryProviderScope<NavKey>.settingsGraph(backStack: NavBackStack<NavKey>)`). Host shells (`app`, `desktop`) assemble these into a single `entryProvider<NavKey>` block. Do NOT define navigation graphs in platform-specific source sets.
-   **Concurrency:** Use Kotlin Coroutines and Flow.
-   **Dependency Injection:** Use **Koin Annotations** with the K2 compiler plugin (`koin-plugin` in version catalog). The `koin-annotations` library version is unified with `koin-core` (both use `version.ref = "koin"`). The `KoinConventionPlugin` uses the typed `KoinGradleExtension` to configure the K2 plugin (e.g., `compileSafety.set(false)`). Keep root graph assembly in `app`.
-   **ViewModels:** Follow the MVI/UDF pattern. Use the multiplatform `androidx.lifecycle.ViewModel` in `commonMain`. Both `app` and `desktop` use `MeshtasticNavDisplay` from `core:ui/commonMain`, which configures `ViewModelStoreNavEntryDecorator` + `SaveableStateHolderNavEntryDecorator`. ViewModels obtained via `koinViewModel()` inside `entry<T>` blocks are scoped to the entry's backstack lifetime and cleared on pop.
-   **Navigation host:** Use `MeshtasticNavDisplay` from `core:ui/commonMain` instead of calling `NavDisplay` directly. It provides entry-scoped ViewModel decoration, `DialogSceneStrategy` for dialog entries, and a shared 350 ms crossfade transition. Host modules (`app`, `desktop`) should NOT configure `entryDecorators`, `sceneStrategies`, or `transitionSpec` themselves.
-   **BLE:** All Bluetooth communication must route through `core:ble` using Kable.
-   **Networking:** Pure **Ktor** — no OkHttp anywhere. Engines: `ktor-client-android` for Android, `ktor-client-java` for desktop/JVM. Use Ktor `Logging` plugin for HTTP debug logging (not OkHttp interceptors). `HttpClient` is provided via Koin in `app/di/NetworkModule` and `core:network/di/CoreNetworkAndroidModule`.
-   **Image Loading (Coil):** Use `coil-network-ktor3` with `KtorNetworkFetcherFactory` on **all** platforms. `ImageLoader` is configured in host modules only (`app` via Koin `@Single`, `desktop` via `setSingletonImageLoaderFactory`). Feature modules depend only on `libs.coil` (coil-compose) for `AsyncImage` — never add `coil-network-*` or `coil-svg` to feature modules.
-   **Dependencies:** Check `gradle/libs.versions.toml` before assuming a library is available.
-   **AboutLibraries:** Runs in `offlineMode` by default (no GitHub/SPDX API calls). Release builds pass `-PaboutLibraries.release=true` via Fastlane properties (Android) or Gradle CLI (desktop) to enable remote license/funding fetching. Do NOT re-gate on `CI` or `GITHUB_TOKEN` alone — that burns API calls on every PR check.
-   **JetBrains fork aliases:** Version catalog aliases for JetBrains-forked AndroidX artifacts use the `jetbrains-*` prefix (e.g., `jetbrains-lifecycle-runtime-compose`, `jetbrains-navigation3-ui`). Plain `androidx-*` aliases are true Google AndroidX artifacts. Never mix them up in `commonMain`.
-   **Compose Multiplatform:** Version catalog aliases for Compose Multiplatform artifacts use the `compose-multiplatform-*` prefix (e.g., `compose-multiplatform-material3`, `compose-multiplatform-foundation`). Never use plain `androidx.compose` dependencies in common Main.
-   **Room KMP:** Always use `factory = { MeshtasticDatabaseConstructor.initialize() }` in `Room.databaseBuilder` and `inMemoryDatabaseBuilder`. DAOs and Entities reside in `commonMain`.
-   **QR Codes:** Use `rememberQrCodePainter` from `core:ui/commonMain` (powered by `qrcode-kotlin`) for generating QR codes. Do not use Android Bitmap or ZXing APIs in common code.
-   **Testing:** Write ViewModel and business logic tests in `commonTest`. Use `Turbine` for Flow testing, `Kotest` for property-based testing, and `Mokkery` for mocking. Use `core:testing` shared fakes.
-   **Build-logic conventions:** In `build-logic/convention`, prefer lazy Gradle configuration (`configureEach`, `withPlugin`, provider APIs). Avoid `afterEvaluate` in convention plugins unless there is no viable lazy alternative.

### C. Namespacing
-   **Standard:** Use the `org.meshtastic.*` namespace for all code.
-   **Legacy:** Maintain the `com.geeksville.mesh` Application ID.

## 4. Execution Protocol

### A. Environment Setup
1. **JDK 21 MUST be used** to prevent Gradle sync/build failures.
2. **Secrets:** You must copy `secrets.defaults.properties` to `local.properties`:
   ```properties
   MAPS_API_KEY=dummy_key
   datadogApplicationId=dummy_id
   datadogClientToken=dummy_token
   ```

### B. Strict Execution Commands
Always run commands in the following order to ensure reliability. Do not attempt to bypass `clean` if you are facing build issues.

**Baseline (recommended order):**
```bash
./gradlew clean
./gradlew spotlessCheck
./gradlew spotlessApply
./gradlew detekt
./gradlew assembleDebug
./gradlew test allTests
```

**Testing:**
```bash
# Full host-side unit test run (required — see note below):
./gradlew test allTests

# Pure-Android / pure-JVM modules only (app, desktop, core:api, core:barcode, feature:widget, mesh_service_example):
./gradlew test

# KMP modules only (all core:* KMP + all feature:* KMP modules — jvmTest + testAndroidHostTest + iosSimulatorArm64Test):
./gradlew allTests

# CI-aligned flavor-explicit Android unit tests:
./gradlew testFdroidDebugUnitTest testGoogleDebugUnitTest

./gradlew connectedAndroidTest # Run instrumented tests
./gradlew testFdroidDebug testGoogleDebug # Flavor-specific unit tests
./gradlew lintFdroidDebug lintGoogleDebug # Flavor-specific lint checks
```

> **Why `test allTests` and not just `test`:**
> In KMP modules, the `test` task name is **ambiguous** — Gradle matches both `testAndroid` and
> `testAndroidHostTest` and refuses to run either, silently skipping all 25 KMP modules.
> `allTests` is the `KotlinTestReport` lifecycle task registered by the KMP Gradle plugin for each
> KMP module. It runs `jvmTest`, `testAndroidHostTest` (where declared with `withHostTest {}`), and
> `iosSimulatorArm64Test` (disabled at execution — iOS targets are compile-only). Conversely,
> `allTests` does **not** cover the pure-Android modules (`:app`, `:core:api`, `:core:barcode`,
> `:feature:widget`, `:mesh_service_example`, `:desktop`), which is why both are needed.

*Note: If testing Compose UI on the JVM (Robolectric) with Java 21, pin your tests to `@Config(sdk = [34])` to avoid SDK 35 compatibility crashes.*

**CI workflow conventions (GitHub Actions):**
- Reusable CI in `.github/workflows/reusable-check.yml` is structured as four parallel job groups:
  1. **`lint-check`** — Runs spotless, detekt, Android lint, and KMP smoke compile in a single Gradle invocation. Uses `fetch-depth: 0` (full clone) for spotless ratcheting and version code calculation. Produces `cache_read_only` output and computed `version_code` for downstream jobs.
  2. **`test-shards`** — A 3-shard matrix that runs unit tests in parallel (depends on `lint-check`):
     - `shard-core`: `allTests` for all `core:*` KMP modules.
     - `shard-feature`: `allTests` for all `feature:*` KMP modules.
      - `shard-app`: Explicit test tasks for pure-Android/JVM modules (`app`, `desktop`, `core:barcode`, `mesh_service_example`).
      Each shard generates its own Kover XML coverage and uploads test results + coverage to Codecov with per-shard flags.
      Downstream jobs (test-shards, android-check, build-desktop) use `fetch-depth: 1` and receive `VERSION_CODE` from lint-check via env var, enabling shallow clones.
   3. **`android-check`** — Builds APKs and runs instrumented tests (depends on `lint-check`).
  4. **`build-desktop`** — Multi-OS matrix (`macos-latest`, `windows-latest`, `ubuntu-24.04`) that builds runnable desktop distributions via `createDistributable` (depends on `lint-check`).
- Test sharding uses `fail-fast: false` so a failure in one shard does not cancel the others.
- JUnit Platform parallel execution is enabled project-wide with classes running sequentially (`junit.jupiter.execution.parallel.mode.classes.default=same_thread`) to avoid `Dispatchers.setMain()` races (JVM-global singleton used by 19+ ViewModel test classes). Cross-module parallelism comes from Gradle forks (`maxParallelForks`).
- `test-retry` plugin (maxRetries=2, maxFailures=10) is applied to all module types: `AndroidApplicationConventionPlugin`, `AndroidLibraryConventionPlugin`, and `KmpLibraryConventionPlugin`.
- Android matrix job runs explicit assemble tasks for `app` and `mesh_service_example`; instrumentation is enabled by input and matrix API.
- Prefer explicit Gradle task paths in CI (for example `app:lintFdroidDebug`, `app:connectedGoogleDebugAndroidTest`) instead of shorthand tasks like `lintDebug`.
- Pull request CI is main-only (`.github/workflows/pull-request.yml` targets `main` branch).
- Gradle cache writes are trusted on `main` and merge queue runs (`merge_group` / `gh-readonly-queue/*`); other refs use read-only cache mode in reusable CI.
- PR `check-changes` path filtering lives in `.github/workflows/pull-request.yml` and must include module dirs plus build/workflow entrypoints (`build-logic/**`, `gradle/**`, `.github/workflows/**`, `gradlew`, `settings.gradle.kts`, etc.) so CI is not skipped for infra-only changes.
- **Runner strategy (three tiers):**
  - **`ubuntu-24.04-arm`** — Lightweight/utility jobs (status checks, labelers, triage, changelog, release metadata, stale, moderation). These run only shell scripts or GitHub API calls and benefit from ARM runners' shorter queue times.
  - **`ubuntu-24.04`** — Main Gradle-heavy jobs (CI `lint-check`/`test-shards`/`android-check`, release builds, Dokka, CodeQL, publish, dependency-submission). Pin where possible for reproducibility.
  - **Desktop runners:** Reusable CI uses a multi-OS matrix (`macos-latest`, `windows-latest`, `ubuntu-24.04`) for the `build-desktop` job in `.github/workflows/reusable-check.yml`; release packaging matrix remains `[macos-latest, windows-latest, ubuntu-24.04, ubuntu-24.04-arm]`.
- **CI Gradle properties:** `gradle.properties` is tuned for local dev (8g heap, 4g Kotlin daemon). CI uses `.github/ci-gradle.properties`, which the `gradle-setup` composite action copies to `~/.gradle/gradle.properties` before any Gradle invocation. Key CI overrides: `org.gradle.daemon=false` (single-use runners), `kotlin.incremental=false` (fresh checkouts), `-Xmx4g` Gradle heap, `-Xmx2g` Kotlin daemon, VFS watching disabled, workers capped at 4, `org.gradle.isolated-projects=true` for better parallelism. Disables unused Android build features (`resvalues`, `shaders`). This follows the nowinandroid `ci-gradle.properties` pattern.
- **CI optimization strategies (2026):** Applied comprehensive CI optimizations (P0-P3):
  - **P0 (merged Gradle invocations):** `lint-check` merges spotlessCheck, detekt, android lint, and kmpSmokeCompile into a single Gradle invocation to avoid 3x cold-start overhead. Uses `filter: 'blob:none'` for blobless git clone. Switches submodules from `'recursive'` to boolean (saves overhead on nested submodule discovery).
  - **P1 (reduced PR overhead):** Added `run_coverage` workflow input (default: true); PRs skip Kover reports via conditional tasks in test-shards matrix. Increased `maxParallelForks` in CI to use all available processors (4 on standard runners) when `ci=true` property is set, vs. half locally for system responsiveness.
  - **P2 (build feature optimization):** Detekt disables non-essential report formats in CI (html, txt, md); retains only xml + sarif for GitHub annotations. Disables unused Android build features (resvalues, shaders) in `ci-gradle.properties`.
  - **P3 (structural improvement):** Removed `verify-check-changes-filter` from `validate-and-build` dependencies; it now runs in parallel as a standalone required check instead of gating the main build.
- **`maxParallelForks` CI logic:** ProjectExtensions.kt line ~79 checks `project.findProperty("ci") == "true"` and uses full available processors in CI (4 forks on std runners) vs. half locally. All CI invocations pass `-Pci=true` to enable this.
- **Detekt report formats:** Detekt.kt line ~44 checks `project.findProperty("ci") == "true"` and disables html, txt, md reports in CI; only xml + sarif are required for GitHub reporting.
- **KMP Smoke Compile:** Use `./gradlew kmpSmokeCompile` instead of listing individual module compile tasks. The `kmpSmokeCompile` lifecycle task (registered in `RootConventionPlugin`) auto-discovers all KMP modules and depends on their `compileKotlinJvm` + `compileKotlinIosSimulatorArm64` tasks.
- **Robolectric SDK caching:** The `gradle-setup` composite action caches `~/.m2/repository/org/robolectric` to prevent flaky `SocketException` failures when Robolectric downloads instrumented SDK jars. The cache key is `robolectric-{version}-sdk{level}` — update it when bumping the Robolectric version in `libs.versions.toml` or the SDK level in `robolectric.properties` / `@Config(sdk = ...)`.
- **`mavenLocal()` gated:** The `mavenLocal()` repository is disabled by default to prevent CI cache poisoning. For local JitPack testing, pass `-PuseMavenLocal` to Gradle.
- **Terminal Pagers:** When running shell commands like `git diff` or `git log`, ALWAYS use `--no-pager` (e.g., `git --no-pager diff`) to prevent the agent from getting stuck in an interactive prompt.
- **Text Search:** Prefer using `rg` (ripgrep) over `grep` or `find` for fast text searching across the codebase.

### C. Documentation Sync
`AGENTS.md` is the single source of truth for agent instructions. `.github/copilot-instructions.md` and `GEMINI.md` are thin stubs that redirect here — do NOT duplicate content into them.

When you modify architecture, module targets, CI tasks, validation commands, or agent workflow rules, update `AGENTS.md`, `docs/agent-playbooks/*`, `docs/kmp-status.md`, and `docs/decisions/architecture-review-2026-03.md` as needed.

## 5. Troubleshooting
-   **Build Failures:** Check `gradle/libs.versions.toml` for dependency conflicts.
-   **Missing Secrets:** Check `local.properties`.
-   **JDK Version:** JDK 21 is required.
-   **Configuration Cache:** Add `--no-configuration-cache` flag if cache-related issues persist.
-   **Koin Injection Failures:** Verify the KMP component is included in `app` root module wiring (`AppKoinModule`).