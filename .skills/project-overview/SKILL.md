# Skill: Project Overview & Codebase Map

## Description
High-level project context, module directory, namespacing conventions, environment setup, and troubleshooting for Meshtastic-Android.

## 1. Project Vision & Architecture
Meshtastic-Android is a Kotlin Multiplatform (KMP) application for off-grid, decentralized mesh networks. The goal is to decouple business logic from the Android framework, enabling expansion to iOS and Desktop while maintaining a high-performance native Android experience.

- **Language:** Kotlin (primary), AIDL.
- **Build System:** Gradle (Kotlin DSL). JDK 21 is REQUIRED.
- **Target SDK:** API 36. Min SDK: API 26 (Android 8.0).
- **Flavors:**
  - `fdroid`: Open source only, no tracking/analytics.
  - `google`: Includes Google Play Services (Maps) and DataDog analytics (RUM, Session Replay, Compose action tracking, custom `connect` RUM action). 100% sampling, Apple-parity environments ("Local"/"Production").
- **KMP Modules:** Most `core:*` modules declare `jvm()`, `iosArm64()`, and `iosSimulatorArm64()` targets and compile clean across all.
- **Android-only Modules:** `core:api` (AIDL), `core:barcode` (CameraX + flavor-specific decoder). Shared contracts abstracted into `core:ui/commonMain`.

## 2. Codebase Map

| Directory | Description |
| :--- | :--- |
| `app/` | Main application module. Contains `MainActivity`, Koin DI modules, and app-level logic. Uses package `org.meshtastic.app`. |
| `build-logic/` | Convention plugins for shared build configuration (e.g., `meshtastic.kmp.feature`, `meshtastic.kmp.library`, `meshtastic.kmp.jvm.android`, `meshtastic.koin`). |
| `config/` | Detekt static analysis rules (`config/detekt/detekt.yml`) and Spotless formatting config (`config/spotless/.editorconfig`). |
| `docs/` | Architecture docs and agent playbooks. See `docs/kmp-status.md` and `docs/roadmap.md` for current status. |
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
| `core:navigation` | Shared navigation keys/routes for Navigation 3 using `@Serializable sealed interface` hierarchies. `DeepLinkRouter` for typed backstack synthesis, and `MeshtasticNavSavedStateConfig` with `subclassesOfSealed()` for automatic polymorphic backstack persistence. |
| `core:ui` | Shared Compose UI components (`MeshtasticAppShell`, `MeshtasticNavDisplay`, `MeshtasticNavigationSuite`, `AlertHost`, `SharedDialogs`, `PlaceholderScreen`, `MainAppBar`, dialogs, preferences) and platform abstractions. |
| `core:service` | KMP service layer; Android bindings stay in `androidMain`. |
| `core:api` | Public AIDL/API integration module for external clients. |
| `core:prefs` | KMP preferences layer built on DataStore abstractions. |
| `core:barcode` | Barcode scanning (Android-only). |
| `core:nfc` | NFC abstractions (KMP). Android NFC hardware implementation in `androidMain`. |
| `core/ble/` | Bluetooth Low Energy stack using Kable. |
| `core/resources/` | Centralized string and image resources (Compose Multiplatform). |
| `core/testing/` | Shared test doubles, fakes, and utilities for `commonTest` across all KMP modules. |
| `feature/` | Feature modules (e.g., `settings`, `map`, `messaging`, `node`, `intro`, `connections`, `firmware`, `wifi-provision`, `widget`). All are KMP except `widget`. Use `meshtastic.kmp.feature` convention plugin. |
| `feature/wifi-provision` | KMP WiFi provisioning via BLE (Nymea protocol). Uses `core:ble` Kable abstractions. |
| `feature/firmware` | Fully KMP firmware update system: Unified OTA (BLE + WiFi), native Nordic Secure DFU protocol (pure KMP), USB/UF2 updates, and `FirmwareRetriever` with manifest-based resolution. Desktop is a first-class target. |
| `desktop/` | Compose Desktop application. Thin host shell relying on feature modules for shared UI. Full Koin DI graph, TCP, Serial/USB, and BLE transports. Versioning via `config.properties` + `GitVersionValueSource`. |
| `mesh_service_example/` | **DEPRECATED — scheduled for removal.** Legacy sample app. See `core/api/README.md` for the current integration guide. |

## 3. Namespacing
- **Standard:** Use the `org.meshtastic.*` namespace for all code.
- **Legacy:** Maintain the `com.geeksville.mesh` Application ID.

## 4. Environment Setup
1. **JDK 21 MUST be used** to prevent Gradle sync/build failures.
2. **Secrets:** Copy `secrets.defaults.properties` to `local.properties`:
   ```properties
   MAPS_API_KEY=dummy_key
   datadogApplicationId=dummy_id
   datadogClientToken=dummy_token
   ```

## 5. Troubleshooting
- **Build Failures:** Check `gradle/libs.versions.toml` for dependency conflicts.
- **Missing Secrets:** Check `local.properties` (see Environment Setup above).
- **JDK Version:** JDK 21 is required.
- **Configuration Cache:** Add `--no-configuration-cache` flag if cache-related issues persist.
- **Koin Injection Failures:** Verify the KMP component is included in `app` root module wiring (`AppKoinModule`).

## Reference Anchors
- **KMP Migration Status:** `docs/kmp-status.md`
- **Roadmap:** `docs/roadmap.md`
- **Architecture Decision Records:** `docs/decisions/`
- **Version Catalog:** `gradle/libs.versions.toml`
