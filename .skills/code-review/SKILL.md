# Skill: Code Review

## Description
Perform comprehensive code reviews for `Meshtastic-Android`, ensuring changes adhere to KMP architecture, Kotlin Multiplatform conventions, MAD standards, and CMP best practices.

## Recurring Defect Classes (check these first)

These four classes account for most of the Major findings raised on recent PRs, and they recur because the compiler, `detekt`, and `spotless` cannot see any of them. Check them while *writing* the code, not only while reviewing it.

### A. Presence vs. sentinel zero
**A numeric field whose absence matters must be nullable. Never let `0` stand in for "not reported".**

`0` is a legitimate reading for RSSI (0 dBm), SNR, temperature, and air-quality concentration, so a `0` default silently merges "no data" with a real measurement. Both directions are bugs: an absent value gets persisted and displayed as a real one, and a genuine `0` gets discarded by a `takeIf { it != 0 }` guard.

- [ ] **Accumulators and defaults:** a field collected over time defaults to `null`, not `0`. Absence checks read `== null` and presence checks `!= null` — never `== 0` for either.
- [ ] **Aggregates over empty sets:** median/mean/min helpers return `T?` and propagate `null` for an empty input. A `0` fallback biases the result in whichever direction the comparator sorts — under the higher-is-better RSSI ordering used for ranking, an empty set's `0` outranks a real `-80 dBm`; under a plain `min` it would instead win as the smallest. Either way the missing value competes as if measured.
- [ ] **Comparators:** sort missing values explicitly last; do not let them fall through to a numeric default.
- [ ] **No zero-guards on real scales:** `takeIf { it != 0 }` is only valid where `0` is genuinely impossible. On any signed or zero-inclusive scale it destroys data.
- [ ] **Proto presence:** when a proto field gains explicit presence, adopt the nullable accessor everywhere rather than keeping a `0` comparison. Fields with *no* presence cannot be fixed app-side — say so rather than faking it.
- [ ] **Tests:** a nullable numeric needs **both** a null case and a zero-value case. One without the other does not pin the distinction.

### B. Read–decide–write across a suspend boundary
**Read the current value, decide, and write inside a single `dataStore.edit { }` block.**

Reading a `StateFlow` (or a prior `suspend` getter), branching on it, and then issuing separate writes leaves a window where a concurrent change interleaves — so a guard can fire against state that no longer exists and clobber a user preference. `NotificationPrefsImpl.setGeofenceAlertOptIn` (`core/prefs/…/notification/NotificationPrefsImpl.kt`) is the reference example: it parses, mutates, caps, and writes in one `edit`.

- [ ] Any "if the flag is X, set Y and Z" transition happens inside one `edit`/`updateData` lambda.
- [ ] The decision reads the block's own `prefs`/`current` snapshot, **not** a cached `StateFlow` value from outside.
- [ ] Multi-key transitions are one `edit` call, not several `scope.launch` writes.
- [ ] Radio-side config mutations go through a single `editSettings { }` transaction (see `AdminController.editSettings`).

### C. Tests that pass for the wrong reason
**Assert that the intended code path produced the result — not merely that the result exists.**

Seeding a fake's backing store and then asserting the value comes back passes even if the production code under test is deleted. The test must fail when the path breaks.

- [ ] **Prove the path ran:** assert the side effect that only the intended path produces (a call counter incremented, a request issued, a cache written) alongside the observed value.
- [ ] **Don't pre-seed the answer:** drive the value in through the path being tested (a gated fake response) instead of injecting it directly into the cache.
- [ ] **Isolate the variable:** a test for one field must not let a second differing field explain the assertion.
- [ ] **Assert survivors, not just counts:** for dedup/merge logic, assert the identities that remain, not the size.
- [ ] **No ordering assertions under `Dispatchers.Unconfined`:** emission order is not a stable contract there — assert final state.

### D. Room schema bump without a migration test
**Every schema-version increment ships a migration test that proves existing rows survive.**

- [ ] A new `core/database/schemas/<n>.json` is accompanied by an `(n-1)→n` test in `core/database/src/androidHostTest/.../*MigrationTest.kt`.
- [ ] The test inserts rows at the old version, migrates, and asserts **row count and column values** are preserved — not merely that the migration runs.
- [ ] Columns going nullable assert that pre-existing values are retained and that the new `NULL` state is reachable (this is class A at the storage layer).

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
- [ ] **Placeholders:** Require `PlaceholderScreen(name)` from `core:ui/commonMain` for unimplemented desktopApp/JVM features. No inline placeholders in feature modules.
- [ ] **Adaptive Layouts:** Verify use of `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)` to support desktopApp/tablet breakpoints (≥ 1200dp).

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
- [ ] **Robolectric Configuration:** Check that Compose UI tests running via Robolectric on JVM are pinned to `@Config(sdk = [34])` to prevent SDK 35 compatibility issues.

### 8. Logging & Crash Reporting
Kermit is the only logging API, and on the **google** flavor its writers fan every call out to **both** Firebase Crashlytics and Datadog RUM (`androidApp/src/google/.../GooglePlatformAnalytics.kt`). Log level is therefore a *reporting* decision, not just a verbosity one.

**The rule: `Logger.e` means "a defect someone can fix". Everything else is `Logger.w` or below.**

- [ ] **Severity gates reporting:** `Severity.Error`/`Assert` become a Crashlytics non-fatal (`shouldReportAsException`, which exempts `CancellationException` and any `ExpectedCondition` in the cause chain) **and** a Datadog RUM error (`shouldDowngradeForDatadog`, which exempts only `ExpectedCondition`). `Warn` and below never report in either sink, with no exceptions. Attaching a throwable at warn level is free and keeps the stack trace in the logs, so demoting costs nothing.
- [ ] **Don't "unify" the two cancellation rules.** Crashlytics drops `CancellationException` because it is a crash-triage tool; Datadog keeps it because a cancellation logged at *error* means a call site swallowed it instead of rethrowing — broken structured concurrency, and a real bug. That asymmetry is the detector that found #6468. Likewise, neither rule unwraps the cause chain for cancellation: coroutine machinery attaches cancellations as the cause of unrelated genuine failures, and unwrapping would silently drop those reports.
- [ ] **`Logger.e` with no throwable still reports.** Crashlytics synthesises an `Exception(message)`; Datadog raises a RUM error from the level alone. `Logger.e { "…" }` is *not* a cheap log line.
- [ ] **Expected conditions must not be reported.** Bluetooth off, a permission not granted, location services off, a deliberate disconnect, a peer/broker protocol violation, a handled retry, a guard that is doing its job — these are environment states, not bugs. Reporting them buries real regressions during release triage.
- [ ] **Use the `ExpectedCondition` seam** (`core/common/src/commonMain/.../log/ExpectedCondition.kt`):
  - Exception type that *only ever* means "the environment said no" → implement `ExpectedCondition` and give it a stable, low-cardinality `expectedConditionLabel` (e.g. `ble-scan-bluetooth-disabled`). `BleScanStartException` is the reference example.
  - Exception type shared between expected and genuine failures → leave the type alone and log that call site at `Logger.w`.
  - Both sinks consult `shouldReportAsException(severity, throwable)`, so an `ExpectedCondition` is suppressed even if some call site logs it at error. Treat that as a backstop, not a licence to log expected states at error.
- [ ] **Prefer a rate over an exception.** For conditions worth *watching* but not *fixing* (watchdog fired, reconnect attempt failed), emit a warn log with a stable label and track its rate in the log backend. Do not manufacture a throwable just to get a stack trace.
- [ ] **Third-party log bridges:** adapters that forward another library's logs into Kermit must downgrade that library's "error" level — its errors are usually operational. See `core/ble/.../KermitLogEngine.kt` (Kable).
- [ ] **New `Logger.e` in a PR:** ask what the on-call engineer would *do* about it. If the answer is "nothing, that's just the user's phone", it is a `Logger.w`.

### 9. ProGuard / R8 Rules
- [ ] **New Dependencies:** If a new reflection-heavy dependency is added (DI, serialization, JNI, ServiceLoader), verify keep rules exist in **both** `androidApp/proguard-rules.pro` (R8) and `desktopApp/proguard-rules.pro` (ProGuard). The two files must stay aligned.
- [ ] **Release Smoke-Test:** For dependency or ProGuard rule changes, verify `assembleRelease` and `./gradlew :desktopApp:runRelease` succeed.

## Review Output Guidelines

**Problems only.** Every comment identifies a concrete defect with evidence in the diff. No praise, no style preferences the linters already own, no speculative design feedback, no refactoring suggestions for code the PR did not touch. A review that finds nothing says so in one line.

1. **Be Specific:** Cite the exact file, line, symbol, or condition. Provide a fix direction — a snippet illustrating the canonical project pattern when the fix is not obvious.
2. **One problem per comment.** Do not bundle several findings into one thread.
3. **Reference the Docs:** Cite `AGENTS.md` and the architecture playbooks to justify a change request (e.g., "Per AGENTS.md, `java.io.*` cannot be used in `commonMain`; please migrate to Okio").
4. **Don't repeat what's already flagged.** Check existing review threads before adding a finding.
5. **Enforce Build Health — only where a gap exists:** If a change lands in a KMP module and the PR's only test evidence is a bare `./gradlew test`, say so: that task is ambiguous in KMP modules and silently skips them, so the code was never exercised and `allTests` is required. Do not append a generic build reminder to a review that has no such gap.

### Analyse impact before judging test coverage

"There are tests" is not coverage. For each non-trivial production change, map: **changed behaviour** (the concrete code path) → **observable surfaces** (public API, protocol handling, persisted rows, DataStore, Compose state, notifications, service lifecycle, transport, MQTT, widgets, Auto, desktop, R8-shaped release behaviour) → **regression risks** (ordering, reconnect/retry, process death, schema compatibility with rows an older build wrote, cross-module call sites, flavor and platform differences) → **the test that should exist and does not**.

A bug fix needs a test that fails *without* the fix. An updated screenshot golden, Room schema JSON, or regenerated baseline profile proves serialisation, not behaviour. Don't demand a test category for a surface the change cannot reach.

### Review moved code as if it were new

When a type moves files or is extracted, diff the old implementation against the new one: a removed `override`, a changed exception contract, a dropped `require`/`check`, a changed default parameter value, a nullability flip on a numeric field (class A), a lost `@Serializable`/`@Parcelize`/Koin annotation, a scope change altering instance lifetime, a changed dispatcher or `SharingStarted`. Then verify every call site of the removed declaration still holds. Pre-existing defects that came along with the move are in scope — label them *"pre-existing — good opportunity to fix during this refactor"* so the author can decide on scope.

## Git & PR Hygiene Rules
- **Commit Hygiene:** Squash fixup/polish/review-feedback commits before opening a PR. Each commit should represent a logical, self-contained unit of work — not a back-and-forth conversation.
- **PR Descriptions:** Keep PR descriptions concise and scannable. State *what changed* and *why*, not a per-commit play-by-play. Use a short summary paragraph followed by a bullet list of changes. Avoid tables, headers-per-commit, or verbose breakdowns. Reference the `meshtastic/firmware` repo PRs for tone and style.
- **PR Titles:** Use conventional commit format: `feat(scope):`, `fix(scope):`, `refactor(scope):`, `chore(scope):`. Keep titles under ~72 characters.
