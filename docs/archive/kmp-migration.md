# Kotlin Multiplatform (KMP) Migration Guide

> [!IMPORTANT]
> This document is now primarily a **historical migration guide**.
> For the current evidence-backed status snapshot, see [`docs/kmp-progress-review-2026.md`](./kmp-progress-review-2026.md).

## Overview
Meshtastic-Android is actively migrating its core logic layers to Kotlin Multiplatform (KMP). This migration decouples the business logic, domain models, local storage, network protocols, and dependency injection from the Android JVM framework. The ultimate goal is a modular, highly testable `core` that can be shared across multiple platforms (e.g., Android, Desktop, and potentially iOS).

## Historical Status Snapshot

By early 2026, the migration had successfully decoupled the foundational data and domain layers, and the primary namespace had been unified to `org.meshtastic`.

For the current state of completion, blockers, and remaining effort, use [`docs/kmp-progress-review-2026.md`](./kmp-progress-review-2026.md).

### Accomplished Milestones

*   **Early Foundations (2022-2025):**
    *   ✅ **Storage and repository groundwork:** DataStore adoption, repository-pattern refactors, and service/data decoupling began well before the explicit KMP conversion wave.
    *   ✅ **`core:model` & `core:proto`:** Migrated early as pure data layers.
    *   ✅ **`core:strings` / `core:resources`:** Migrated to Compose Multiplatform for unified string resources (#3617, #3669).
    *   ✅ **Logging:** Replaced Android-bound `Timber` with KMP-ready `Kermit` (#4083).
    *   ✅ **`core:common`:** Decoupled basic utilities and cleanly extracted away from Android constraints (#4026).
*   **Namespace Modernization:** 
    *   The `app` module source code was completely relocated from `com.geeksville.mesh` to `org.meshtastic.app`.
    *   **Legacy Compatibility:** External integrations (like ATAK) rely on legacy Android Intents. `AndroidManifest.xml` preserves the `<action android:name="com.geeksville.mesh.*" />` signatures to ensure unbroken backwards compatibility.
*   **Module Conversions (`meshtastic.android.library` -> `meshtastic.kmp.library`):**
    *   ✅ **`core:repository`:** Interfaces extracted to `commonMain`.
    *   ✅ **`core:domain`:** Use cases migrated. Android `Handler` and `java.io.File` logic replaced with Coroutines and Okio (#4731, #4685).
    *   ✅ **`core:prefs`:** Android SharedPreferences replaced with Multiplatform DataStore (#4731).
    *   ✅ **`core:network`:** Extracted KMP interfaces for MQTT and local network abstractions.
    *   ✅ **`core:di`:** Coroutine dispatchers mapped to standard Kotlin abstractions instead of Android thread pools.
    *   ✅ **`core:database`:** Migrated to Room Kotlin Multiplatform (#4702).
    *   ✅ **`core:data`:** Concrete repository implementations moved to `commonMain`. Android-specific logic (e.g., parsing `device_hardware.json` from `assets`) was abstracted behind KMP interfaces with implementations provided in `androidMain`.
*   **Architecture Refinements:**
    *   `core:analytics` was completely dissolved. Abstract tracking interfaces were moved to `core:repository`, and concrete SDK implementations (Firebase, DataDog) were moved to the `app` module.
    *   Test stability greatly improved by eliminating Robolectric for core logic tests in favor of pure MockK stubs.

*   ✅ **`core:ble` / `core:bluetooth`:** Implemented a "Nordic Hybrid" Interface-Driven abstraction. Defined pure KMP interfaces (`BleConnectionManager`, `BleDevice`, etc.) in `commonMain` so that Desktop and Web targets can compile, while using Nordic's `KMM-BLE-Library` specifically inside the `androidMain` source set.
    *   ✅ **`core:service`:** Converted to a KMP module, isolating Android service bindings and lifecycle concerns to `androidMain`.
    *   ℹ️ **`core:api`:** Remains an Android-specific integration module because AIDL is Android-only. Treat it as a platform adapter rather than a shared KMP target.

### Remaining Work for Broader KMP Maturity
The main bottleneck is no longer simply “moving code into KMP modules.” The remaining work is now about validating and hardening that architecture for non-Android targets.

1.  **Android-edge modules still remain platform-specific:**
    *   **`core:barcode` / `core:nfc`:** Android-specific hardware integrations. *Partially addressed:* `core:ui` no longer depends on them directly and abstracts scanning via `CompositionLocalProvider`.
    *   **`core:api`:** Intentionally Android-specific because AIDL is Android-only. Any transport-neutral contracts should continue to be separated from the Android adapter layer.
2.  **Feature modules are structurally migrated, but cleanup continues:**
    *   *Current State:* all `feature/*` modules now build as KMP libraries, and `androidx.lifecycle.ViewModel` is KMP-compatible.
    *   **`feature:messaging`, `feature:intro`, `feature:map`, `feature:settings`, `feature:node`, `feature:firmware`:** all have major logic/UI in shared modules, with Android-specific adapters isolated where still required.
    *   Remaining work is mostly about boundary cleanup, platform adapter consistency, and ensuring future non-Android targets can compile cleanly.
3.  **Cross-target validation is still incomplete:**
    *   Most KMP modules currently declare only Android targets in practice.
    *   CI still validates Android builds and tests, but not a broad JVM/iOS/Desktop target matrix.
4.  **`core:ui` & Navigation are largely complete, but now need target hardening rather than migration work:**
    *   ✅ **Navigation:** Migrated fully to **AndroidX Navigation 3**. The backstack is now a simple state list (`List<NavKey>`), enabling trivial sharing across multiplatform targets without relying on Android's legacy `NavController` or `navigation-compose`.
    *   ✅ **`core:ui`:** Converted to a pure KMP library (`meshtastic.kmp.library.compose`).
        *   Abstracted Clipboard, Intents, and Bitmaps via `PlatformUtils` and `expect`/`actual`.
        *   Replaced Android's `Linkify` with a pure Kotlin Regex and `AnnotatedString` solution.
        *   Ensured all shared UI components rely solely on Compose Multiplatform.
    *   The remaining work here is mostly validation on additional targets and continued isolation of Android-only framework hooks.

### Dependency Injection
The project currently uses **Koin Annotations**.
*   **Current State:** `core:di` is a KMP module that exposes `javax.inject` annotations (`@Inject`), and the app root still assembles the graph in `AppKoinModule`.
*   **Important Update:** The original plan was to keep all DI-dependent components centralized in the `app` module, but the current implementation now includes some Koin `@Module`, `@ComponentScan`, and `@KoinViewModel` usage directly in `commonMain` shared modules. See [`docs/kmp-progress-review-2026.md`](./kmp-progress-review-2026.md) for the current architecture assessment.
*   **Accomplished:** We have successfully migrated from Hilt (Dagger) to **Koin 4.x** using the compiler plugin, completely removing Hilt from the project to enable deeper Multiplatform adoption.

## Best Practices & Guidelines (2026)
When contributing to `core` modules, adhere to the following KMP standards:

*   **No Android Context in `commonMain`:** Never pass `Context`, `Application`, or `Activity` into `commonMain`. Use Dependency Injection to provide platform-specific implementations from `androidMain` or `app`.
*   **ViewModels:** Use `androidx.lifecycle.ViewModel` and `viewModelScope` within `commonMain` for platform-agnostic state management. The original target pattern was to keep shared ViewModels DI-agnostic and provide app-level Koin wrappers, but the current codebase now contains some Koin annotations directly in shared modules. Prefer the more framework-light pattern for new code unless there is a clear reason to couple a shared ViewModel to Koin.
*   **Testing:** Use pure `kotlin.test` and `MockK` for unit tests in `commonTest`. Avoid `Robolectric` unless explicitly testing an `androidMain` component. Platform-specific unit tests (e.g. for Workers) should be relocated to the `app` module's `test` source set if they depend on Koin components.
*   **Resources:** Use Compose Multiplatform Resources (`core:resources`) for all strings and drawables. Never use Android `strings.xml` in `commonMain`.
*   **Coroutines & Flows:** Use `StateFlow` and `SharedFlow` for all asynchronous state management across the domain layer.
*   **Persistence:** Use `androidx.datastore` for preferences and Room KMP for complex relational data.
*   **Dependency Injection:** We use **Koin Annotations + KSP**. Per 2026 KMP industry standards, it is recommended to push Koin `@Module`, `@ComponentScan`, and `@KoinViewModel` annotations into `commonMain`. This encapsulates dependency graphs per feature, providing a Hilt-like experience (compile-time validation) while remaining fully multiplatform-compatible.

---
*Document refreshed on 2026-03-10 as a historical companion to `docs/kmp-progress-review-2026.md`.*
