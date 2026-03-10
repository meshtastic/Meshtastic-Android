# Meshtastic Android - Agent Guide

This file serves as a comprehensive guide for AI agents and developers working on the `Meshtastic-Android` codebase. Use this as your primary reference for understanding the architecture, conventions, and strict rules of this project.

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
| `core/ble/` | Bluetooth Low Energy stack using Nordic libraries. |
| `core/resources/` | Centralized string and image resources (Compose Multiplatform). |
| `feature/` | Feature modules (e.g., `settings`, `map`, `messaging`). |

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
    -   Use **Koin**.
    -   **Restriction:** Move Koin modules to the `app` module if the library module is KMP with multiple flavors, as KSP/Koin generation often fails in these complex scenarios.

### C. Namespacing
-   **Standard:** Use the `org.meshtastic.*` namespace for all code.
-   **Legacy:** Maintain the `com.geeksville.mesh` Application ID and specific intent strings for backward compatibility.

## 4. Execution Protocol

### A. Build and Verify
1.  **Format:** `./gradlew spotlessApply`
2.  **Lint:** `./gradlew detekt`
3.  **Test:** `./gradlew testAndroid` (or `testCommonMain` for pure logic)

### B. Expect/Actual Patterns
Use `expect`/`actual` sparingly for platform-specific types (e.g., `Location`, `NavHostController`) to keep the core logic pure and platform-agnostic.

## 5. Troubleshooting
-   **Build Failures:** Always check `gradle/libs.versions.toml` for dependency conflicts.
-   **Koin Generation:** If a component fails to inject in a KMP module, ensure the corresponding module is bound in the `app` layer's DI package.
