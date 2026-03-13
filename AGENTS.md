# Meshtastic Android - Agent Guide

This file serves as a comprehensive guide for AI agents and developers working on the `Meshtastic-Android` codebase. Use this as your primary reference for understanding the architecture, conventions, and strict rules of this project.

For execution-focused recipes, see `docs/agent-playbooks/README.md`.

## 1. Project Vision
We are incrementally migrating Meshtastic-Android to a **Kotlin Multiplatform (KMP)** architecture. The goal is to decouple business logic from the Android framework, enabling future expansion to iOS and other platforms while maintaining a high-performance native Android experience.

## 2. Codebase Map

| Directory | Description |
| :--- | :--- |
| `app/` | Main application module. Contains `MainActivity`, Koin DI modules, and app-level logic. Uses package `org.meshtastic.app`. |
| `build-logic/` | Convention plugins for shared build configuration (e.g., `meshtastic.kmp.library`, `meshtastic.koin`). |
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
| `core:ui` | Shared Compose UI components (`EmptyDetailPlaceholder`, `MainAppBar`, dialogs, preferences) and platform abstractions, including `jvmAndroidMain` bridges for shared JVM/Android actuals. |
| `core:service` | KMP service layer; Android bindings stay in `androidMain`. |
| `core:api` | Public AIDL/API integration module for external clients. |
| `core:prefs` | KMP preferences layer built on DataStore abstractions. |
| `core:barcode` | Barcode scanning (Android-only). Shared UI in `main/`; only the decoder (`createBarcodeAnalyzer`) differs per flavor (ML Kit / ZXing). Shared contract in `core:ui`. |
| `core:nfc` | NFC abstractions (KMP). Android NFC hardware implementation in `androidMain`; shared contract via `LocalNfcScannerProvider` in `core:ui`. |
| `core/ble/` | Bluetooth Low Energy stack using Nordic libraries. |
| `core/resources/` | Centralized string and image resources (Compose Multiplatform). |
| `core/testing/` | **Shared test doubles, fakes, and utilities for `commonTest` across all KMP modules.** Lightweight with minimal dependencies (only `core:model`, `core:repository`, + test libs). Keeps module dependency graph clean by centralizing test consolidation. See `core/testing/README.md`. |
| `feature/` | Feature modules (e.g., `settings`, `map`, `messaging`, `node`, `intro`, `connections`). All are KMP with `jvm()` target. |
| `feature/connections` | Connections UI — device discovery, BLE/TCP/USB scanning, shared composables in `commonMain`; Android BLE bonding/NSD/USB in `androidMain`. |
| `feature/firmware` | Firmware update flow (KMP module with Android DFU in `androidMain`). |
| `desktop/` | Compose Desktop application — first non-Android KMP target. Nav 3 shell, full Koin DI graph, TCP transport with `want_config` handshake, adaptive list-detail screens for nodes/messaging, ~35 real settings screens, connections UI. See `docs/kmp-status.md`. |
| `mesh_service_example/` | Sample app showing `core:api` service integration. |

## 3. Development Guidelines

### A. UI Development (Jetpack Compose)
-   **Material 3:** The app uses Material 3.
-   **Strings:**
    -   **Rule:** MUST use the **Compose Multiplatform Resource** library in `core:resources`.
    -   **Location:** `core/resources/src/commonMain/composeResources/values/strings.xml`.
-   **Dialogs:** Use centralized components in `core:ui`.
-   **Platform/Flavor UI:** Inject platform-specific behavior (e.g., map providers) via `CompositionLocal` from `app`. See `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/MapViewProvider.kt` for the contract pattern and `app/src/main/kotlin/org/meshtastic/app/MainActivity.kt` for provider wiring.

### B. Logic & Data Layer
-   **KMP Focus:** All business logic must reside in `commonMain` of the respective `core` module.
-   **Platform purity:** Never import `java.*` or `android.*` in `commonMain`. Use KMP alternatives:
    -   `java.util.Locale` → Kotlin `uppercase()` / `lowercase()` (locale-independent for ASCII) or `expect`/`actual`.
    -   `java.util.concurrent.ConcurrentHashMap` → `atomicfu` or `Mutex`-guarded `mutableMapOf()`.
    -   `java.util.concurrent.locks.*` → `kotlinx.coroutines.sync.Mutex`.
    -   `java.io.*` → Okio (`BufferedSource`/`BufferedSink`).
-   **I/O:** Use **Okio** (`BufferedSource`/`BufferedSink`) for stream operations. Never use `java.io` in `commonMain`.
-   **Concurrency:** Use Kotlin Coroutines and Flow.
-   **Thread-Safety:** Use `atomicfu` and `kotlinx.collections.immutable` for shared state in `commonMain`. Avoid `synchronized` or JVM-specific atomics.
-   **Dependency Injection:**
    -   Use **Koin Annotations** with the K2 compiler plugin (0.4.0+).
    -   Keep root graph assembly in `app` (module inclusion in `AppKoinModule` and startup wiring in `MeshUtilApplication`).
    -   Use `@Module`, `@ComponentScan`, and `@KoinViewModel` annotations directly in `commonMain` shared modules.
    -   **Note on Koin 0.4.0 compile safety:** Koin's A1 (per-module) validation is globally disabled in `build-logic`. Because Meshtastic employs Clean Architecture dependency inversion (interfaces in `core:repository`, implementations in `core:data`), enforcing A1 resolution per-module fails. Validation occurs at the full-graph (A3) level instead.
-   **ViewModels:** Follow the MVI/UDF pattern. Use the multiplatform `androidx.lifecycle.ViewModel` in `commonMain` to maintain a single source of truth for UI state, relying heavily on `StateFlow`.
-   **BLE:** All Bluetooth communication must route through `core:ble` using Nordic Semiconductor's Android Common Libraries and Kotlin Coroutines/Flows. Never use legacy Android Bluetooth callbacks directly.
-   **Dependencies:** Check `gradle/libs.versions.toml` before assuming a library is available. New dependencies MUST be added to the version catalog, not directly to a `build.gradle.kts` file.
-   **Shared JVM + Android code:** If a KMP module needs a `jvmAndroidMain` source set for code shared between desktop JVM and Android, apply the `meshtastic.kmp.jvm.android` convention plugin. Do **not** hand-wire `sourceSets.dependsOn(...)` edges in module `build.gradle.kts` files—the convention uses Kotlin's hierarchy template API and avoids default hierarchy warnings.
-   **Room KMP:** Always use `factory = { MeshtasticDatabaseConstructor.initialize() }` in `Room.databaseBuilder` and `inMemoryDatabaseBuilder`. DAOs and Entities reside in `commonMain`.
-   **Testing:** Write ViewModel and business logic tests in `commonTest` (not `test/` Robolectric) so every target runs them. Use `core:testing` shared fakes when available. **Test framework dependencies** (`kotlin("test")` for both `commonTest` and `androidHostTest` source sets) are automatically provided by the `meshtastic.kmp.library` convention plugin—no need to add them manually to individual module `build.gradle.kts` files. See `build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/KotlinAndroid.kt::configureKmpTestDependencies()` for details.

### C. Namespacing
-   **Standard:** Use the `org.meshtastic.*` namespace for all code.
-   **Legacy:** Maintain the `com.geeksville.mesh` Application ID and specific intent strings for backward compatibility.

## 4. Execution Protocol

### A. Build and Verify
**Prerequisite:** JDK 17 is required. Copy `secrets.defaults.properties` to `local.properties` before building.
1.  **Clean:** `./gradlew clean`
2.  **Format:** `./gradlew spotlessCheck` then `./gradlew spotlessApply`
3.  **Lint:** `./gradlew detekt`
4.  **Build + Unit Tests:** `./gradlew assembleDebug test` (CI also runs `testDebugUnitTest`)
5.  **Flavor/CI Parity (when relevant):** `./gradlew lintFdroidDebug lintGoogleDebug testFdroidDebug testGoogleDebug`
6.  **Desktop (when touched):** `./gradlew :desktop:test :desktop:run`

### B. Documentation Sync
-   If you change architecture, module boundaries, target declarations, CI tasks, validation commands, or agent workflow rules, update the corresponding docs in the same slice.
-   KMP status: `docs/kmp-status.md`. Roadmap: `docs/roadmap.md`. Decisions: `docs/decisions/`. Architecture review: `docs/decisions/architecture-review-2026-03.md`.
-   At minimum, review and update the relevant source of truth among `AGENTS.md`, `.github/copilot-instructions.md`, `GEMINI.md`, `docs/agent-playbooks/*`, and `docs/kmp-status.md` when those areas are affected.

### C. Expect/Actual Patterns
Use `expect`/`actual` sparingly for platform-specific types (e.g., `Location`, platform utilities) to keep core logic pure. For navigation, prefer shared Navigation 3 backstack state (`List<NavKey>`) over platform controller types.

## 5. Troubleshooting
-   **Build Failures:** Always check `gradle/libs.versions.toml` for dependency conflicts.
-   **Missing Secrets:** Copy `secrets.defaults.properties` → `local.properties` with valid (or dummy) values for `MAPS_API_KEY`, `datadogApplicationId`, and `datadogClientToken`.
-   **JDK Version:** JDK 17 is required. Mismatched JDK versions cause Gradle sync/build failures.
-   **Configuration Cache:** Add `--no-configuration-cache` flag if cache-related issues persist.
-   **Koin Injection Failures:** Verify the KMP component is included in `app` root module wiring (`AppKoinModule`) and that `startKoin` loads that module at app startup.
-   **Desktop `Dispatchers.Main` missing:** JVM/Desktop requires `kotlinx-coroutines-swing` for `Dispatchers.Main`. Without it, any code using `lifecycle.coroutineScope` or `Dispatchers.Main` will crash at runtime. The desktop module already includes this dependency.
