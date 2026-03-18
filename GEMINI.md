# Meshtastic Android - Agent Guide

This file serves as a comprehensive guide for AI agents and developers working on the `Meshtastic-Android` codebase. Use this as your primary reference for understanding the architecture, conventions, and strict rules of this project.

For execution-focused recipes, see `docs/agent-playbooks/README.md`.

## 1. Project Vision & Architecture
Meshtastic-Android is a Kotlin Multiplatform (KMP) application for off-grid, decentralized mesh networks. The goal is to decouple business logic from the Android framework, enabling future expansion to iOS and other platforms while maintaining a high-performance native Android experience.

- **Language:** Kotlin (primary), AIDL.
- **Build System:** Gradle (Kotlin DSL). JDK 17 is REQUIRED.
- **Target SDK:** API 36. Min SDK: API 26 (Android 8.0).
- **Flavors:**
  - `fdroid`: Open source only, no tracking/analytics.
  - `google`: Includes Google Play Services (Maps) and DataDog analytics.
- **Core Architecture:** Modern Android Development (MAD) with KMP core.
  - **KMP Modules:** Most `core:*` modules. All declare `jvm()` target and compile clean on JVM.
  - **Android-only Modules:** `core:api` (AIDL), `core:barcode` (CameraX + flavor-specific decoder). Shared contracts abstracted into `core:ui/commonMain`.
  - **UI:** Jetpack Compose Multiplatform (Material 3).
  - **DI:** Koin Annotations with K2 compiler plugin. Root graph assembly is centralized in `app` and `desktop`.
  - **Navigation:** JetBrains Navigation 3 (Multiplatform fork) with shared backstack state.
  - **Lifecycle:** JetBrains multiplatform `lifecycle-viewmodel-compose` and `lifecycle-runtime-compose`.
  - **Database:** Room KMP.

## 2. Codebase Map

| Directory | Description |
| :--- | :--- |
| `app/` | Main application module. Contains `MainActivity`, Koin DI modules, and app-level logic. Uses package `org.meshtastic.app`. |
| `build-logic/` | Convention plugins for shared build configuration (e.g., `meshtastic.kmp.feature`, `meshtastic.kmp.library`, `meshtastic.koin`). |
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
| `core:network` | KMP networking layer using Ktor, MQTT abstractions, and shared transport (`StreamFrameCodec` in commonMain, `TcpTransport` in jvmAndroidMain). |
| `core:di` | Common DI qualifiers and dispatchers. |
| `core:navigation` | Shared navigation keys/routes for Navigation 3. |
| `core:ui` | Shared Compose UI components (`EmptyDetailPlaceholder`, `MainAppBar`, dialogs, preferences) and platform abstractions. |
| `core:service` | KMP service layer; Android bindings stay in `androidMain`. |
| `core:api` | Public AIDL/API integration module for external clients. |
| `core:prefs` | KMP preferences layer built on DataStore abstractions. |
| `core:barcode` | Barcode scanning (Android-only). |
| `core:nfc` | NFC abstractions (KMP). Android NFC hardware implementation in `androidMain`. |
| `core/ble/` | Bluetooth Low Energy stack using Kable. |
| `core/resources/` | Centralized string and image resources (Compose Multiplatform). |
| `core/testing/` | **Shared test doubles, fakes, and utilities for `commonTest` across all KMP modules.** |
| `feature/` | Feature modules (e.g., `settings`, `map`, `messaging`, `node`, `intro`, `connections`). All are KMP with `jvm()` target. Use `meshtastic.kmp.feature` convention plugin. |
| `desktop/` | Compose Desktop application — first non-Android KMP target. Nav 3 shell, full Koin DI graph, TCP transport with `want_config` handshake. |
| `mesh_service_example/` | Sample app showing `core:api` service integration. |

## 3. Development Guidelines & Coding Standards

### A. UI Development (Jetpack Compose)
-   **Material 3:** The app uses Material 3.
-   **Strings:** MUST use the **Compose Multiplatform Resource** library in `core:resources` (`stringResource(Res.string.your_key)`). NEVER use hardcoded strings.
-   **Dialogs:** Use centralized components in `core:ui` (e.g., `MeshtasticResourceDialog`).
-   **Platform/Flavor UI:** Inject platform-specific behavior (e.g., map providers) via `CompositionLocal` from `app`.

### B. Logic & Data Layer
-   **KMP Focus:** All business logic must reside in `commonMain` of the respective `core` module.
-   **Platform purity:** Never import `java.*` or `android.*` in `commonMain`. Use KMP alternatives:
    -   `java.util.Locale` → Kotlin `uppercase()` / `lowercase()` or `expect`/`actual`.
    -   `java.util.concurrent.ConcurrentHashMap` → `atomicfu` or `Mutex`-guarded `mutableMapOf()`.
    -   `java.util.concurrent.locks.*` → `kotlinx.coroutines.sync.Mutex`.
    -   `java.io.*` → Okio (`BufferedSource`/`BufferedSink`).
-   **Concurrency:** Use Kotlin Coroutines and Flow.
-   **Dependency Injection:** Use **Koin Annotations** with the K2 compiler plugin (0.4.0+). Keep root graph assembly in `app`.
-   **ViewModels:** Follow the MVI/UDF pattern. Use the multiplatform `androidx.lifecycle.ViewModel` in `commonMain`.
-   **BLE:** All Bluetooth communication must route through `core:ble` using Kable.
-   **Dependencies:** Check `gradle/libs.versions.toml` before assuming a library is available.
-   **JetBrains fork aliases:** Version catalog aliases for JetBrains-forked AndroidX artifacts use the `jetbrains-*` prefix (e.g., `jetbrains-lifecycle-runtime-compose`, `jetbrains-navigation3-ui`). Plain `androidx-*` aliases are true Google AndroidX artifacts. Never mix them up in `commonMain`.
-   **Compose Multiplatform:** Version catalog aliases for Compose Multiplatform artifacts use the `compose-multiplatform-*` prefix (e.g., `compose-multiplatform-material3`, `compose-multiplatform-foundation`). Never use plain `androidx.compose` dependencies in common Main.
-   **Room KMP:** Always use `factory = { MeshtasticDatabaseConstructor.initialize() }` in `Room.databaseBuilder` and `inMemoryDatabaseBuilder`. DAOs and Entities reside in `commonMain`.
-   **Testing:** Write ViewModel and business logic tests in `commonTest`. Use `core:testing` shared fakes.
-   **Build-logic conventions:** In `build-logic/convention`, prefer lazy Gradle configuration (`configureEach`, `withPlugin`, provider APIs). Avoid `afterEvaluate` in convention plugins unless there is no viable lazy alternative.

### C. Namespacing
-   **Standard:** Use the `org.meshtastic.*` namespace for all code.
-   **Legacy:** Maintain the `com.geeksville.mesh` Application ID.

## 4. Execution Protocol

### A. Environment Setup
1. **JDK 17 MUST be used** to prevent Gradle sync/build failures.
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
./gradlew test
```

**Testing:**
```bash
./gradlew test                # Run local unit tests
./gradlew testFdroidDebugUnitTest testGoogleDebugUnitTest # CI-aligned Android unit tests (flavor-explicit)
./gradlew connectedAndroidTest # Run instrumented tests
./gradlew testFdroidDebug testGoogleDebug # Flavor-specific unit tests
./gradlew lintFdroidDebug lintGoogleDebug # Flavor-specific lint checks
```
*Note: If testing Compose UI on the JVM (Robolectric) with Java 17, pin your tests to `@Config(sdk = [34])` to avoid SDK 35 compatibility crashes.*

**CI workflow conventions (GitHub Actions):**
- Reusable CI is split into a host job and an Android matrix job in `.github/workflows/reusable-check.yml`.
- Host job runs style/static checks, explicit Android lint tasks, unit tests, and Kover XML coverage uploads once.
- Android matrix job runs explicit assemble tasks for `app` and `mesh_service_example`; instrumentation is enabled by input and matrix API.
- Prefer explicit Gradle task paths in CI (for example `app:lintFdroidDebug`, `app:connectedGoogleDebugAndroidTest`) instead of shorthand tasks like `lintDebug`.
- Pull request CI is main-only (`.github/workflows/pull-request.yml` targets `main` branch).
- Gradle cache writes are trusted on `main` and merge queue runs (`merge_group` / `gh-readonly-queue/*`); other refs use read-only cache mode in reusable CI.
- PR `check-changes` path filtering lives in `.github/workflows/pull-request.yml` and must include module dirs plus build/workflow entrypoints (`build-logic/**`, `gradle/**`, `.github/workflows/**`, `gradlew`, `settings.gradle.kts`, etc.) so CI is not skipped for infra-only changes.

### C. Documentation Sync
Update documentation continuously as part of the same change. If you modify architecture, module targets, CI tasks, validation commands, or agent workflow rules, update the relevant docs (`AGENTS.md`, `.github/copilot-instructions.md`, `GEMINI.md`, `docs/agent-playbooks/*`, `docs/kmp-status.md`, and `docs/decisions/architecture-review-2026-03.md`).

## 5. Troubleshooting
-   **Build Failures:** Check `gradle/libs.versions.toml` for dependency conflicts.
-   **Missing Secrets:** Check `local.properties`.
-   **JDK Version:** JDK 17 is required.
-   **Configuration Cache:** Add `--no-configuration-cache` flag if cache-related issues persist.
-   **Koin Injection Failures:** Verify the KMP component is included in `app` root module wiring (`AppKoinModule`).