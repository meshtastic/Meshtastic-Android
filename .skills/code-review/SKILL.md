# Skill: Code Review

## Description
Perform comprehensive code reviews for `Meshtastic-Android`, ensuring changes adhere to KMP architecture, Kotlin Multiplatform conventions, MAD standards, and CMP best practices.

## Code Review Checklist

When reviewing code, meticulously verify the following categories. Flag any deviations and propose the canonical project pattern as a fix.

### 1. KMP Architecture & Source Set Boundaries
- [ ] **No Platform Bleed:** Ensure absolutely no `java.*` or `android.*` imports exist in `commonMain` source sets.
- [ ] **KMP Native Alternatives:** Verify the use of KMP alternatives for standard JVM libraries:
  - `java.util.concurrent.locks.*` -> `kotlinx.coroutines.sync.Mutex`
  - `java.util.concurrent.ConcurrentHashMap` -> `atomicfu` or Mutex-guarded `mutableMapOf()`
  - `java.io.*` -> `Okio` (`BufferedSource`/`BufferedSink`)
  - `java.util.Locale` -> Kotlin `uppercase()`/`lowercase()` (purged from `commonMain`)
- [ ] **Coroutine Safety:** Use `safeCatching {}` from `core:common` instead of `runCatching {}` in coroutine/suspend contexts. `runCatching` silently swallows `CancellationException`, breaking structured concurrency. Keep `runCatching` only in cleanup/teardown code (abort, close, eviction). Use `kotlinx.coroutines.CancellationException` (not `kotlin.coroutines.cancellation.CancellationException`).
- [ ] **Shared Helpers:** If `androidMain` and `jvmMain` contain identical pure-Kotlin logic, mandate extracting it to a shared function in `commonMain`.
- [ ] **File Naming Conflicts:** For `expect`/`actual` declarations, ensure files sharing the same package namespace have distinct names (e.g., keep `expect` in `LogExporter.kt` and shared helpers in `LogFormatter.kt`) to avoid duplicate class errors on the JVM target.
- [ ] **Interface & DI Over `expect`/`actual`:** Check that `expect`/`actual` is reserved for small platform primitives. Interfaces + DI should be preferred for larger capabilities.

### 2. UI & Compose Multiplatform (CMP)
- [ ] **Compose Multiplatform Resources:** Ensure NO hardcoded strings. Must use `core:resources` (e.g., `stringResource(Res.string.key)` or asynchronous `getStringSuspend(Res.string.key)` for ViewModels/Coroutines). NEVER use blocking `getString()` in a coroutine.
- [ ] **String Formatting:** CMP only supports `%N$s` and `%N$d`. Flag any float formats (`%N$.1f`) in Compose string resources; they must be pre-formatted using `NumberFormatter.format()` from `core:common`. Use `MetricFormatter` for metric-specific displays (temperature, voltage, current, percent, humidity, pressure, SNR, RSSI).
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
- [ ] **HTTP Configuration:** Verify timeouts and base URLs use `HttpClientDefaults` from `core:network`. Never hardcode timeouts in feature modules. `DefaultRequest` sets the base URL; feature API services use relative paths.
- [ ] **Image Loading (Coil):** Coil must use `coil-network-ktor3` in host modules. Feature modules should ONLY depend on `libs.coil` (coil-compose) and never configure fetchers.
- [ ] **Room KMP:** Ensure `factory = { MeshtasticDatabaseConstructor.initialize() }` is used in `Room.databaseBuilder`. DAOs and Entities must reside in `commonMain`.
- [ ] **Room Patterns:** Verify use of `@Upsert` for insert-or-update logic. Check for `LIMIT 1` on single-row queries. Flag N+1 query patterns (loops calling single-row queries) — batch with chunked `WHERE IN` instead.
- [ ] **Bluetooth (BLE):** All Bluetooth communication must be routed through `core:ble` using Kable abstractions.

### 6. Dependency Catalog Aliases
- [ ] **JetBrains vs. AndroidX:**
  - In `commonMain`: Must use `jetbrains-*` aliases (e.g., `jetbrains-lifecycle-*`, `jetbrains-navigation3-ui`).
  - In `androidMain`: Can use `androidx-*` or `jetbrains-*` as appropriate, but do not mix them up in `commonMain`.
- [ ] **Compose Multiplatform:** Ensure `compose-multiplatform-*` aliases are used instead of plain `androidx.compose` in all KMP modules.

### 7. Testing
- [ ] **Test Placement:** New Compose UI tests must go in `commonTest` using `runComposeUiTest {}` from `androidx.compose.ui.test.v2` (not the deprecated v1 `androidx.compose.ui.test` package) + `kotlin.test.Test`. Do not add `androidTest` (instrumented) tests.
- [ ] **Shared Test Utilities:** Test fakes, doubles, and utilities should be placed in `core:testing`.
- [ ] **Libraries:** Verify usage of `Turbine` for Flow testing, `Kotest` for property-based testing, and `Mokkery` for mocking.
- [ ] **Robolectric Configuration:** Check that Compose UI tests running via Robolectric on JVM are pinned to `@Config(sdk = [34])` to prevent Java 21 / SDK 35 compatibility issues.

### 8. ProGuard / R8 Rules
- [ ] **New Dependencies:** If a new reflection-heavy dependency is added (DI, serialization, JNI, ServiceLoader), verify keep rules exist in **both** `app/proguard-rules.pro` (R8) and `desktop/proguard-rules.pro` (ProGuard). The two files must stay aligned.
- [ ] **Release Smoke-Test:** For dependency or ProGuard rule changes, verify `assembleRelease` and `./gradlew :desktop:runRelease` succeed.

## Review Output Guidelines
1. **Be Specific & Constructive:** Provide exact file references and code snippets illustrating the required project pattern.
2. **Reference the Docs:** Cite `AGENTS.md` and project architecture playbooks to justify change requests (e.g., "Per AGENTS.md, `java.io.*` cannot be used in `commonMain`; please migrate to Okio").
3. **Enforce Build Health:** Remind authors to run `./gradlew test allTests` locally to verify changes, especially since KMP `test` tasks are ambiguous.
4. **Praise Good Patterns:** Acknowledge correct usage of complex architecture requirements, like proper Navigation 3 scene transitions or elegant `commonMain` helper extractions.

## Git & PR Hygiene Rules
- **Commit Hygiene:** Squash fixup/polish/review-feedback commits before opening a PR. Each commit should represent a logical, self-contained unit of work — not a back-and-forth conversation.
- **PR Descriptions:** Keep PR descriptions concise and scannable. State *what changed* and *why*, not a per-commit play-by-play. Use a short summary paragraph followed by a bullet list of changes. Avoid tables, headers-per-commit, or verbose breakdowns. Reference the `meshtastic/firmware` repo PRs for tone and style.
- **PR Titles:** Use conventional commit format: `feat(scope):`, `fix(scope):`, `refactor(scope):`, `chore(scope):`. Keep titles under ~72 characters.
