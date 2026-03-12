# Meshtastic-Android: AI Agent Instructions (GEMINI.md)

**CRITICAL AGENT DIRECTIVE:** This file contains validated, comprehensive instructions for interacting with the Meshtastic-Android repository. You MUST adhere strictly to these rules, build commands, and architectural constraints. Only deviate or explore alternatives if the documented commands fail with unexpected errors.

If this file conflicts with `AGENTS.md`, follow `AGENTS.md`.

## 1. Project Overview & Architecture
Meshtastic-Android is a Kotlin Multiplatform (KMP) application for off-grid, decentralized mesh networks.

- **Language:** Kotlin (primary), AIDL.
- **Build System:** Gradle (Kotlin DSL). JDK 17 is REQUIRED.
- **Target SDK:** API 36. Min SDK: API 26 (Android 8.0).
- **Flavors:**
  - `fdroid`: Open source only, no tracking/analytics.
  - `google`: Includes Google Play Services (Maps) and DataDog analytics.
- **Core Architecture:** Modern Android Development (MAD) with KMP core.
  - **KMP Modules:** `core:model`, `core:proto`, `core:common`, `core:resources`, `core:database`, `core:datastore`, `core:repository`, `core:domain`, `core:prefs`, `core:network`, `core:di`, `core:data`, `core:ble`, `core:nfc`, `core:service`, `core:ui`, `core:navigation`, `core:testing`. All declare `jvm()` target and compile clean on JVM.
  - **Android-only Modules:** `core:api` (AIDL), `core:barcode` (CameraX + flavor-specific decoder). Shared contracts abstracted into `core:ui/commonMain`.
  - **UI:** Jetpack Compose (Material 3).
  - **DI:** Koin Annotations with K2 compiler plugin. Root graph assembly is centralized in `app` (`AppKoinModule` + `startKoin`), while shared modules can expose annotated definitions that are included by the app root module.
  - **Navigation:** AndroidX Navigation 3 (JetBrains multiplatform fork: `org.jetbrains.androidx.navigation3`) with shared backstack state (`List<NavKey>`).
  - **Lifecycle (multiplatform):** JetBrains forks `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` and `lifecycle-runtime-compose`.
  - **Room KMP:** Always use `factory = { MeshtasticDatabaseConstructor.initialize() }` in `Room.databaseBuilder` and `inMemoryDatabaseBuilder`. DAOs and Entities reside in `commonMain`.

## 2. Environment Setup (Mandatory First Steps)
Before attempting any builds or tests, ensure the environment is configured:

1. **JDK 17 MUST be used** to prevent Gradle sync/build failures.
2. **Secrets:** You must copy `secrets.defaults.properties` to `local.properties` to satisfy build requirements, even for dummy builds:
   ```properties
   # local.properties example
   MAPS_API_KEY=dummy_key
   datadogApplicationId=dummy_id
   datadogClientToken=dummy_token
   ```

## 3. Strict Execution Commands
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

**Formatting & Linting (Run BEFORE committing):**
```bash
./gradlew spotlessCheck  # Check formatting first
./gradlew spotlessApply  # Auto-fix formatting
./gradlew detekt         # Run static analysis
```

**Building:**
```bash
./gradlew clean          # Always start here if facing issues
./gradlew assembleDebug  # Full build (fdroid and google)
```

**Testing:**
```bash
./gradlew test                # Run local unit tests
./gradlew testDebugUnitTest   # CI-aligned Android unit tests
./gradlew connectedAndroidTest # Run instrumented tests
./gradlew testFdroidDebug testGoogleDebug # Flavor-specific unit tests
./gradlew lintFdroidDebug lintGoogleDebug # Flavor-specific lint checks
```
*Note: If testing Compose UI on the JVM (Robolectric) with Java 17, pin your tests to `@Config(sdk = [34])` to avoid SDK 35 compatibility crashes.*

## 4. Coding Standards & Mandates

- **UI Components:** Always utilize `:core:ui` for shared Jetpack Compose components (e.g., `MeshtasticResourceDialog`, `TransportIcon`). Do not reinvent standard dialogs or preference screens.
- **Strings/Localization:** **NEVER** use hardcoded strings or the legacy `app/src/main/res/values/strings.xml`.
  - **Rule:** You MUST use the Compose Multiplatform Resource library.
  - **Location:** `core/resources/src/commonMain/composeResources/values/strings.xml`.
  - **Usage:** `stringResource(Res.string.your_key)`
- **Platform purity:** Never import `java.*` or `android.*` in `commonMain`. Use KMP alternatives:
  - `java.util.Locale` → Kotlin `uppercase()` / `lowercase()` (locale-independent for ASCII) or `expect`/`actual`.
  - `java.util.concurrent.ConcurrentHashMap` → `atomicfu` or `Mutex`-guarded `mutableMapOf()`.
  - `java.util.concurrent.locks.*` → `kotlinx.coroutines.sync.Mutex`.
  - `java.io.*` → Okio (`BufferedSource`/`BufferedSink`).
- **Bluetooth/BLE:** Do not use legacy Android Bluetooth callbacks. All BLE communication MUST route through `:core:ble`, utilizing Nordic Semiconductor's Android Common Libraries and Kotlin Coroutines/Flows.
- **Dependencies:** Never assume a library is available. Check `gradle/libs.versions.toml` first. If adding a new dependency, it MUST be added to the version catalog, not directly to a `build.gradle.kts` file.
- **Namespacing:** Prefer the `org.meshtastic` namespace for all new code. The legacy `com.geeksville.mesh` ApplicationId is maintained for compatibility.
- **Testing:** Write ViewModel and business logic tests in `commonTest` (not `test/` Robolectric) so every target runs them. Use `core:testing` shared fakes when available.
- **Documentation Sync:** Update documentation continuously as part of the same change. If you modify architecture, module targets, CI tasks, validation commands, or agent workflow rules, update the relevant docs (`AGENTS.md`, `.github/copilot-instructions.md`, `GEMINI.md`, `docs/agent-playbooks/*`, `docs/kmp-status.md`, and `docs/decisions/architecture-review-2026-03.md`) in the same slice.

## 5. Module Map
When locating code to modify, use this map:
- **`app/`**: Main application wiring and Koin DI modules/wrappers (`@KoinViewModel`, `@Module`, `@KoinWorker`). Package: `org.meshtastic.app`.
- **`:core:data`**: Core business logic and managers. Package: `org.meshtastic.core.data`.
- **`:core:repository`**: Domain interfaces and common models. Package: `org.meshtastic.core.repository`.
- **`:core:ble`**: Coroutine-based Bluetooth logic (Nordic Semiconductor). Package: `org.meshtastic.core.ble`.
- **`:core:nfc`**: NFC abstractions (KMP). Android NFC hardware in `androidMain`; shared contract via `LocalNfcScannerProvider` in `core:ui`.
- **`:core:barcode`**: Barcode scanning (Android-only). Shared UI in `main/`; only the decoder (`createBarcodeAnalyzer`) differs per flavor (ML Kit / ZXing). Shared contract in `core:ui`.
- **`:core:api`**: AIDL service interface (`IMeshService.aidl`) for third-party integrations (like ATAK).
- **`:core:ui`**: Shared Compose UI elements, platform abstractions, and theming.
- **`:core:navigation`**: Shared Navigation 3 routes/keys.
- **`:core:network`**: KMP networking (Ktor, `StreamFrameCodec`, `TcpTransport`).
- **`:core:testing`**: Shared test doubles, fakes, and utilities for `commonTest` across all KMP modules.
- **`:desktop`**: Compose Desktop application — first non-Android KMP target. Nav 3 shell, full Koin DI, TCP transport with `want_config` handshake, adaptive list-detail screens for nodes/messaging, ~35 real settings screens, connections UI. See `docs/kmp-status.md`.
- **`:feature:*`**: Isolated feature screens (e.g., `:feature:messaging` for chat, `:feature:map` for mapping, `:feature:connections` for device discovery, `:feature:firmware` for updates).
