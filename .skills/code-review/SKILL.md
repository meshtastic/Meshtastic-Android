# Skill: Code Review

## Description
Perform comprehensive and precise code reviews for the `Meshtastic-Android` project. This skill ensures that incoming changes adhere strictly to the project's architecture guidelines, Kotlin Multiplatform (KMP) conventions, Modern Android Development (MAD) standards, and Jetpack Compose Multiplatform (CMP) best practices.

## Context & Prerequisites
The `Meshtastic-Android` codebase is a highly modernized Kotlin Multiplatform (KMP) application designed for off-grid, decentralized mesh networks.
- **Language:** Kotlin (primary), JDK 21 required.
- **Architecture:** KMP core with Android and Desktop host shells.
- **UI:** Jetpack Compose Multiplatform (CMP) and Material 3 Adaptive.
- **Navigation:** JetBrains Navigation 3 (Scene-based).
- **DI:** Koin Annotations (with K2 compiler plugin).
- **Async & I/O:** Kotlin Coroutines, Flow, Okio, Ktor.

## Code Review Checklist

When reviewing code, meticulously verify the following categories. Flag any deviations and propose the canonical project pattern as a fix.

### 1. KMP Architecture & Source Set Boundaries
- [ ] **No Platform Bleed:** Ensure absolutely no `java.*` or `android.*` imports exist in `commonMain` source sets.
- [ ] **KMP Native Alternatives:** Verify the use of KMP alternatives for standard JVM libraries:
  - `java.util.concurrent.locks.*` -> `kotlinx.coroutines.sync.Mutex`
  - `java.util.concurrent.ConcurrentHashMap` -> `atomicfu` or Mutex-guarded `mutableMapOf()`
  - `java.io.*` -> `Okio` (`BufferedSource`/`BufferedSink`)
  - `java.util.Locale` -> Kotlin `uppercase()`/`lowercase()` or `expect`/`actual`
- [ ] **Shared Helpers:** If `androidMain` and `jvmMain` contain identical pure-Kotlin logic, mandate extracting it to a shared function in `commonMain`.
- [ ] **File Naming Conflicts:** For `expect`/`actual` declarations, ensure files sharing the same package namespace have distinct names (e.g., keep `expect` in `LogExporter.kt` and shared helpers in `LogFormatter.kt`) to avoid duplicate class errors on the JVM target.
- [ ] **Interface & DI Over `expect`/`actual`:** Check that `expect`/`actual` is reserved for small platform primitives. Interfaces + DI should be preferred for larger capabilities.

### 2. UI & Compose Multiplatform (CMP)
- [ ] **Compose Multiplatform Resources:** Ensure NO hardcoded strings. Must use `core:resources` (e.g., `stringResource(Res.string.key)` or asynchronous `getStringSuspend(Res.string.key)` for ViewModels/Coroutines). NEVER use blocking `getString()` in a coroutine.
- [ ] **String Formatting:** CMP only supports `%N$s` and `%N$d`. Flag any float formats (`%N$.1f`) in Compose string resources; they must be pre-formatted using `NumberFormatter.format()` from `core:common`.
- [ ] **Centralized Dialogs & Alerts:** Flag inline alert-rendering logic. Mandate the use of `AlertHost(alertManager)` or `SharedDialogs` from `core:ui/commonMain`.
- [ ] **Placeholders:** Require `PlaceholderScreen(name)` from `core:ui/commonMain` for unimplemented desktop/JVM features. No inline placeholders in feature modules.
- [ ] **Adaptive Layouts:** Verify use of `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)` to support desktop/tablet breakpoints (≥ 1200dp).

### 3. Navigation & State
- [ ] **Shared Navigation Graphs:** Feature navigation graphs must be defined as extension functions on `EntryProviderScope<NavKey>` in `commonMain` (e.g., `fun EntryProviderScope<NavKey>.settingsGraph(...)`). Flag any graphs defined in platform-specific source sets.
- [ ] **Navigation Host:** Ensure `MeshtasticNavDisplay` (from `core:ui/commonMain`) is used as the host instead of invoking `NavDisplay` directly. Host modules should not configure `entryDecorators` themselves.
- [ ] **ViewModel Scoping:** ViewModels obtained via `koinViewModel()` must be inside `entry<T>` blocks to correctly tie to the backstack lifetime.

### 4. Dependency Injection (Koin Annotations)
- [ ] **Annotation Usage:** Ensure Koin is configured via annotations (`@Single`, `@Factory`, `@KoinViewModel`).
- [ ] **Root Assembly:** Confirm that the root Koin DI graph is only assembled in host shells (`app` and `desktop`).

### 5. Networking, DB & I/O
- [ ] **Ktor Strictly:** Check that Ktor is used for all HTTP networking. Flag and reject any usage of OkHttp.
- [ ] **Image Loading (Coil):** Coil must use `coil-network-ktor3` in host modules. Feature modules should ONLY depend on `libs.coil` (coil-compose) and never configure fetchers.
- [ ] **Room KMP:** Ensure `factory = { MeshtasticDatabaseConstructor.initialize() }` is used in `Room.databaseBuilder`. DAOs and Entities must reside in `commonMain`.
- [ ] **Bluetooth (BLE):** All Bluetooth communication must be routed through `core:ble` using Kable abstractions.

### 6. Dependency Catalog Aliases
- [ ] **JetBrains vs. AndroidX:**
  - In `commonMain`: Must use `jetbrains-*` aliases (e.g., `jetbrains-lifecycle-*`, `jetbrains-navigation3-ui`).
  - In `androidMain`: Can use `androidx-*` or `jetbrains-*` as appropriate, but do not mix them up in `commonMain`.
- [ ] **Compose Multiplatform:** Ensure `compose-multiplatform-*` aliases are used instead of plain `androidx.compose` in all KMP modules.

### 7. Testing
- [ ] **Test Placement:** New Compose UI tests must go in `commonTest` using `runComposeUiTest {}` + `kotlin.test.Test`. Do not add `androidTest` (instrumented) tests.
- [ ] **Shared Test Utilities:** Test fakes, doubles, and utilities should be placed in `core:testing`.
- [ ] **Libraries:** Verify usage of `Turbine` for Flow testing, `Kotest` for property-based testing, and `Mokkery` for mocking.
- [ ] **Robolectric Configuration:** Check that Compose UI tests running via Robolectric on JVM are pinned to `@Config(sdk = [34])` to prevent Java 21 / SDK 35 compatibility issues.

## Review Output Guidelines
1. **Be Specific & Constructive:** Provide exact file references and code snippets illustrating the required project pattern.
2. **Reference the Docs:** Cite `AGENTS.md` and project architecture playbooks to justify change requests (e.g., "Per AGENTS.md, `java.io.*` cannot be used in `commonMain`; please migrate to Okio").
3. **Enforce Build Health:** Remind authors to run `./gradlew test allTests` locally to verify changes, especially since KMP `test` tasks are ambiguous.
4. **Praise Good Patterns:** Acknowledge correct usage of complex architecture requirements, like proper Navigation 3 scene transitions or elegant `commonMain` helper extractions.
