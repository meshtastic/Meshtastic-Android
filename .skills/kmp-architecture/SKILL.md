# Skill: KMP Architecture & Source-Set Bridging

## Description
Guidelines on managing Kotlin Multiplatform (KMP) source-sets, expected abstractions, networking, database, and platform integration rules.

## 1. Source-Set Boundaries
- **`commonMain`:** All business logic, DB entities, API network logic, ViewModels, and UI rendering. NO `java.*` or `android.*` imports.
- **`androidMain`:** Android framework integration (`Context`, system services, NFC hardware, BLE Android bindings).
- **`jvmMain` / `jvmAndroidMain`:** Shared JVM code between Android and Desktop. Uses the `meshtastic.kmp.jvm.android` convention plugin to bridge `jvm` and `android` source sets without manual `dependsOn` hacks.
- **`app` / `desktop`:** Host shells. Responsible for Koin DI root wiring, `MainKoinModule`, host-level UI themes, and running the `MeshtasticNavDisplay`.

## 2. Bridging Strategies
- **Interface + DI (Preferred):** Expose an interface in `core:repository` or `core:ui` (e.g. `LocationRepository`, `MapViewProvider`), implement it in `androidMain` or the host `app`, and bind it via Koin or `CompositionLocal`.
- **`expect`/`actual` (Restricted):** Use only when a platform API cannot be abstracted cleanly (e.g. low-level File I/O mappings, `uppercase()` Locale helpers). Avoid deep class hierarchies using `expect`/`actual`.
  - **Naming:** Keep `expect` in `FileIo.kt`, but put shared helpers in `FileIoUtils.kt` to prevent JVM duplicate class errors.
- **Shared Helpers:** Do not duplicate pure Kotlin logic between `androidMain` and `jvmMain`. Extract to a `commonMain` helper.

## 3. Core Libraries & Constraints
- **Concurrency:** `kotlinx.coroutines`. Use `org.meshtastic.core.common.util.ioDispatcher` over `Dispatchers.IO` directly. Inject `CoroutineDispatchers` from `core:di` into classes that need dispatchers — never reference `Dispatchers.IO`/`Main`/`Default` directly in business logic.
- **Error Handling:** Use `safeCatching {}` from `core:common` instead of `runCatching {}` in coroutine/suspend contexts. `runCatching` swallows `CancellationException`, breaking structured concurrency. Keep `runCatching` only in cleanup/teardown code (abort, close, eviction loops).
- **Standard Library Replacements:**
  - `ConcurrentHashMap` -> `atomicfu` or Mutex-guarded `mutableMapOf()`.
  - `java.util.concurrent.locks.*` -> `kotlinx.coroutines.sync.Mutex`.
  - `java.io.*` -> `Okio` (`BufferedSource`/`BufferedSink`).
- **Networking:** Pure **Ktor**. No OkHttp. Ktor `Logging` plugin for debugging.
- **HTTP Configuration:** Use `HttpClientDefaults` from `core:network` for shared base URL (`API_BASE_URL`), timeouts, and retry constants. Both Android (`NetworkModule`) and Desktop (`DesktopKoinModule`) HttpClient instances must use these. Feature API services use relative paths; `DefaultRequest` sets the base URL.
- **BLE:** Route through `core:ble` using **Kable**.
- **Room KMP:** Use `factory = { MeshtasticDatabaseConstructor.initialize() }` in `Room.databaseBuilder`.

## 4. Hierarchy & Source-Set Conventions
- **Hierarchy template first:** Prefer Kotlin's default hierarchy template and convention plugins over manual `dependsOn(...)` graphs. Manual source-set wiring should be reserved for cases the template cannot model.
- **`expect`/`actual` restraint:** Prefer interfaces + DI for platform capabilities; use `expect`/`actual` for small unavoidable platform primitives. Avoid broad expect/actual class hierarchies when an interface-based boundary is sufficient.
- **Shared helpers over duplicated lambdas:** When `androidMain` and `jvmMain` contain identical pure-Kotlin logic (formatting, action dispatch, validation), extract to `commonMain`. Examples: `formatLogsTo()`, `handleNodeAction()`, `findNodeByNameSuffix()`, `MeshtasticAppShell`, `BaseRadioTransportFactory`.

## 5. Dependency Catalog Aliases
- **JetBrains fork aliases:** Version catalog aliases for JetBrains-forked AndroidX artifacts use the `jetbrains-*` prefix (e.g., `jetbrains-lifecycle-runtime-compose`, `jetbrains-navigation3-ui`). Plain `androidx-*` aliases are true Google AndroidX artifacts. Never mix them up in `commonMain`.
- **Compose Multiplatform:** Version catalog aliases for Compose Multiplatform artifacts use the `compose-multiplatform-*` prefix (e.g., `compose-multiplatform-material3`, `compose-multiplatform-foundation`). Never use plain `androidx.compose` dependencies in `commonMain`.
- **Dependencies:** Always check `gradle/libs.versions.toml` before assuming a library is available.

## 6. I/O & Serialization
- **Okio standard:** This project standardizes on Okio (`BufferedSource`/`BufferedSink`). JetBrains recommends `kotlinx-io` (built on Okio), but this project has not migrated. Do not introduce `kotlinx-io` without an explicit decision.
- **Room KMP:** Always use `factory = { MeshtasticDatabaseConstructor.initialize() }` in `Room.databaseBuilder` and `inMemoryDatabaseBuilder`. DAOs and Entities reside in `commonMain`.
- **Room Patterns:**
  - Use `@Upsert` for insert-or-update operations instead of manual `INSERT OR IGNORE` + `UPDATE` logic.
  - Use `LIMIT 1` on `@Query` methods that expect a single row.
  - Prevent N+1 queries: batch operations with `@Upsert fun putAll(items: List<T>)` or chunked `WHERE IN` queries (chunk size ≤ 999 to respect SQLite bind parameter limit).

## 7. Build-Logic Conventions
- In `build-logic/convention`, prefer lazy Gradle configuration (`configureEach`, `withPlugin`, provider APIs). Avoid `afterEvaluate` in convention plugins unless there is no viable lazy alternative.

## 8. Onboarding a New Target (Desktop/iOS)
1. Ensure all new logic compiles against the KMP core (`jvm()`, `iosArm64()`, etc.).
2. Do not use platform-specific constructs in `commonMain` or you break the iOS/Desktop builds.
3. Test using `kmpSmokeCompile` to verify cross-platform compilation.
4. For desktop wiring, copy the pattern in `desktop/src/main/kotlin/org/meshtastic/desktop/di/DesktopKoinModule.kt` and use `NoopStubs.kt` to temporarily mock missing platform implementations.

## Reference Anchors
- **Shared Okio I/O:** `core/domain/src/commonMain/kotlin/org/meshtastic/core/domain/usecase/settings/ImportProfileUseCase.kt`
- **Desktop DI Stubs:** `desktop/src/main/kotlin/org/meshtastic/desktop/stub/NoopStubs.kt`
- **Version Catalog:** `gradle/libs.versions.toml`
- **Convention Plugins:** `build-logic/convention/`
