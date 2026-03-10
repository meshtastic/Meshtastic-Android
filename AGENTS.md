# Meshtastic Android - Agent Guide

This file serves as a comprehensive guide for AI agents and developers working on the `Meshtastic-Android` codebase. Use this as your primary reference for understanding the architecture, conventions, and strict rules of this project.

For execution-focused recipes, see `docs/agent-playbooks/README.md`.

## 1. Project Vision
We are incrementally migrating Meshtastic-Android to a **Kotlin Multiplatform (KMP)** architecture. The goal is to decouple business logic from the Android framework, enabling future expansion to iOS and other platforms while maintaining a high-performance native Android experience.

## 2. Codebase Map

| Directory | Description |
| :--- | :--- |
| `app/` | Main application module. Contains `MainActivity`, Koin DI modules, and app-level logic. Uses package `org.meshtastic.app`. |
| `core/model` | Domain models and common data structures. |
| `core:proto` | Protobuf definitions (Git submodule). |
| `core:common` | Low-level utilities, I/O abstractions (Okio), and common types. |
| `core:database` | Room KMP database implementation. |
| `core:datastore` | Multiplatform DataStore for preferences. |
| `core:repository` | High-level domain interfaces (e.g., `NodeRepository`, `LocationRepository`). |
| `core:domain` | Pure KMP business logic and UseCases. |
| `core:data` | Core manager implementations and data orchestration. |
| `core:network` | KMP networking layer using Ktor and MQTT abstractions. |
| `core:di` | Common DI qualifiers and dispatchers. |
| `core:navigation` | Shared navigation keys/routes for Navigation 3. |
| `core:ui` | Shared Compose UI components and platform abstractions. |
| `core:service` | KMP service layer; Android bindings stay in `androidMain`. |
| `core:api` | Public AIDL/API integration module for external clients. |
| `core:prefs` | KMP preferences layer built on DataStore abstractions. |
| `core:barcode` | Barcode abstractions with Android hardware implementation. |
| `core:nfc` | NFC abstractions with Android hardware implementation. |
| `core/ble/` | Bluetooth Low Energy stack using Nordic libraries. |
| `core/resources/` | Centralized string and image resources (Compose Multiplatform). |
| `feature/` | Feature modules (e.g., `settings`, `map`, `messaging`). |
| `feature/firmware` | Firmware update flow (KMP module with Android DFU in `androidMain`). |
| `mesh_service_example/` | Sample app showing `core:api` service integration. |

## 3. Development Guidelines

### A. UI Development (Jetpack Compose)
-   **Material 3:** The app uses Material 3.
-   **Strings:**
    -   **Rule:** MUST use the **Compose Multiplatform Resource** library in `core:resources`.
    -   **Location:** `core/resources/src/commonMain/composeResources/values/strings.xml`.
-   **Dialogs:** Use centralized components in `core:ui`.

### B. Logic & Data Layer
-   **KMP Focus:** All business logic must reside in `commonMain` of the respective `core` module.
-   **I/O:** Use **Okio** (`BufferedSource`/`BufferedSink`) for stream operations. Never use `java.io` in `commonMain`.
-   **Concurrency:** Use Kotlin Coroutines and Flow.
-   **Thread-Safety:** Use `atomicfu` and `kotlinx.collections.immutable` for shared state in `commonMain`. Avoid `synchronized` or JVM-specific atomics.
-   **Dependency Injection:**
    -   Use **Koin Annotations** with the K2 compiler plugin.
    -   Keep root graph assembly in `app` (module inclusion in `AppKoinModule` and startup wiring in `MeshUtilApplication`).
    -   Keep `commonMain` business logic framework-agnostic. Shared modules may contain Koin-annotated definitions where that pattern already exists, but they must be included by the app root module.

### C. Namespacing
-   **Standard:** Use the `org.meshtastic.*` namespace for all code.
-   **Legacy:** Maintain the `com.geeksville.mesh` Application ID and specific intent strings for backward compatibility.

## 4. Execution Protocol

### A. Build and Verify
1.  **Clean:** `./gradlew clean`
2.  **Format:** `./gradlew spotlessCheck` then `./gradlew spotlessApply`
3.  **Lint:** `./gradlew detekt`
4.  **Build + Unit Tests:** `./gradlew assembleDebug test` (CI also runs `testDebugUnitTest`)
5.  **Flavor/CI Parity (when relevant):** `./gradlew lintFdroidDebug lintGoogleDebug testFdroidDebug testGoogleDebug`

### B. Expect/Actual Patterns
Use `expect`/`actual` sparingly for platform-specific types (e.g., `Location`, platform utilities) to keep core logic pure. For navigation, prefer shared Navigation 3 backstack state (`List<NavKey>`) over platform controller types.

## 5. Troubleshooting
-   **Build Failures:** Always check `gradle/libs.versions.toml` for dependency conflicts.
-   **Koin Injection Failures:** Verify the KMP component is included in `app` root module wiring (`AppKoinModule`) and that `startKoin` loads that module at app startup.
