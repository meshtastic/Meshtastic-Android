# Skill: Testing and CI Verification

## Description
Guidelines and commands for verifying code changes locally and understanding the Meshtastic-Android CI pipeline. Use this to determine which testing matrix is needed based on the change type.

## 1) Baseline local verification order

Run in a single invocation for routine changes to ensure code formatting, analysis, and basic compilation:

```bash
./gradlew spotlessCheck spotlessApply detekt assembleDebug test allTests
```

> **Why no `clean`?** Incremental builds are safe and significantly faster. Only use `clean` when debugging stale cache issues.

> **Why `test allTests` and not just `test`:**
> In KMP modules, the `test` task name is **ambiguous**. Gradle matches both `testAndroid` and
> `testAndroidHostTest` and refuses to run either, silently skipping KMP modules.
> `allTests` is the `KotlinTestReport` lifecycle task registered by the KMP plugin.
> Conversely, `allTests` does **not** cover pure-Android modules (`:androidApp`, `:core:barcode`, etc.), which is why both `test` and `allTests` are needed.

*Note: If testing Compose UI on the JVM (Robolectric) with Java 21, pin tests to `@Config(sdk = [34])` to avoid SDK 35 compatibility crashes.*

### SharedFlow + backgroundScope in `runTest`

When testing long-lived coroutines (e.g., `Flow.collect` loops launched in `backgroundScope`), **use `runTest(UnconfinedTestDispatcher())`** instead of plain `runTest`:

```kotlin
// ❌ BAD — SharedFlow emissions silently never reach collectors
@Test fun `inbound packet is forwarded`() = runTest {
    backgroundScope.launch { sut.start(backgroundScope) }
    sharedFlow.emit(packet)
    // assertion fails — collector never receives the emission
}

// ✅ GOOD — UnconfinedTestDispatcher eagerly dispatches subscriber resumptions
@Test fun `inbound packet is forwarded`() = runTest(UnconfinedTestDispatcher()) {
    backgroundScope.launch { sut.start(backgroundScope) }
    sharedFlow.emit(packet)
    // assertion passes — collector receives emission immediately
}
```

**Why:** `backgroundScope` uses `StandardTestDispatcher` by default, which does **not** eagerly dispatch `SharedFlow` subscriber resumptions. Even `advanceUntilIdle()` won't trigger delivery. `UnconfinedTestDispatcher()` fixes this by dispatching eagerly. This affects any test where a coroutine in `backgroundScope` collects from a `SharedFlow` or `MutableSharedFlow`.

## 2) Change-type verification matrix

- `docs-only` changes: Usually no Gradle run required, but run `spotlessCheck` if practical.
- `UI text/resource` changes: `spotlessCheck`, `detekt`, `assembleDebug`.
- `feature/commonMain logic` changes: `spotlessCheck`, `detekt`, `test allTests`, `assembleDebug`.
- `navigation/DI wiring` changes: `spotlessCheck`, `detekt`, `assembleDebug`, `test allTests`, plus flavor unit tests if available.
  - If touching any KMP module, also run `kmpSmokeCompile`.
- `worker/service/background` changes: Broad tests, targeted WorkManager checks.
- `BLE/networking/core repository`: `spotlessCheck`, `detekt`, `assembleDebug`, `test allTests`.

## 3) Flavor checks

Run these when relevant to map, provider, or flavor-specific behavior:

```bash
./gradlew lintFdroidDebug lintGoogleDebug
./gradlew testFdroidDebug testGoogleDebug
```

## 3b) Screenshot testing (two modules)

Compose Preview Screenshot Testing (AGP/layoutlib) is split into two modules — keep the distinction:

- **`:screenshot-tests`** — visual-regression **gate**. CI runs `:screenshot-tests:validateDebugScreenshotTest`. Holds atomic, dual-purpose components. Touching one of these previews is expected to move a gated baseline.
- **`:docs-screenshots`** — **generate-only**, NOT validated in CI. Holds doc-framed compositions (crops/full screens tuned for the docs site). Reframe these freely; it never churns the regression gate.

```bash
./gradlew :screenshot-tests:updateDebugScreenshotTest   # regression goldens
./gradlew :docs-screenshots:updateDebugScreenshotTest   # doc-framed composition images
./gradlew :screenshot-tests:copyDocsScreenshots         # copy doc images from BOTH modules → docs/assets
```

Rendering is **host-deterministic** (layoutlib): a local `update` produces references byte-identical to CI, so locally-recorded goldens pass `validate`. `copyDocsScreenshots` overwrites a stale committed `nodes_detail_local.png` each run — `git checkout` it. Public previews consumed cross-module by a wrapper need a `detekt-baseline.xml` entry (PreviewPublic). New screenshot? Pick the module by purpose; see `docs/assets/screenshots/README.md`.

## 3c) Fresh-install manual/agent testing: skip onboarding

Debug builds accept an intent extra to skip the intro flow (`MainActivity.kt`, `BuildConfig.DEBUG`-gated — never reaches release/Play builds). Pair with `pm grant` (native Android, no app code) to pre-accept runtime permissions:

```bash
adb shell pm grant <pkg> android.permission.BLUETOOTH_SCAN
adb shell pm grant <pkg> android.permission.BLUETOOTH_CONNECT
adb shell pm grant <pkg> android.permission.ACCESS_FINE_LOCATION
adb shell pm grant <pkg> android.permission.POST_NOTIFICATIONS   # API 33+
adb shell am start -n <pkg>/org.meshtastic.app.MainActivity --ez skip_onboarding true
```

Use this whenever driving the app from a fresh install/uninstall (screenshot tests, UI automation, agent-driven exploration) instead of clicking through the intro screens.

## 4) CI Pipeline Architecture

CI is defined in `.github/workflows/reusable-check.yml` and structured as parallel job groups:

1. **`lint-check`** — Runs spotless, detekt, Android lint, and KMP smoke compile in a single Gradle invocation (avoids 3x cold-start overhead). Uses `fetch-depth: 0` (full clone) for spotless ratcheting and version code calculation. Produces `cache_read_only` output and computed `version_code` for downstream jobs.
2. **`test-shards`** — A 3-shard matrix that runs unit tests in parallel (depends on `lint-check`):
   - `shard-core`: `allTests` for all `core:*` KMP modules.
   - `shard-feature`: `allTests` for all `feature:*` KMP modules.
   - `shard-app`: Explicit test tasks for pure-Android/JVM modules (`app`, `desktop`, `core:barcode`).
   Each shard generates Kover XML coverage and uploads test results + coverage to Codecov with per-shard flags.
   Downstream jobs use `fetch-depth: 1` and receive `VERSION_CODE` from lint-check via env var, enabling shallow clones.
3. **`android-check`** — Builds APKs for all flavors (depends on `lint-check`).
4. **`build-desktop`** — Multi-OS matrix (`macos-latest`, `windows-latest`, `ubuntu-24.04`, `ubuntu-24.04-arm`) that builds desktop distributions via `createDistributable` (depends on `lint-check`).
5. **`screenshot-check`** — Runs `:screenshot-tests:validateDebugScreenshotTest` (the visual-regression gate) and uploads a diff report. Note: `:docs-screenshots` is intentionally NOT validated here (generate-only).

### Runner Strategy (Three Tiers)
- **`ubuntu-24.04-arm`** — Lightweight/utility jobs (status checks, labelers, triage, changelog, release metadata, stale, moderation). Benefits from ARM runners' shorter queue times.
- **`ubuntu-24.04`** — Main Gradle-heavy jobs (CI `lint-check`/`test-shards`/`android-check`, release builds, Dokka, publish, dependency-submission). Pin for reproducibility.
- **Desktop runners:** Multi-OS matrix (`macos-latest`, `windows-latest`, `ubuntu-24.04`, `ubuntu-24.04-arm`) for the `build-desktop` job and release packaging.

### CI Gradle Properties
`gradle.properties` is tuned for local dev (8g heap, 4g Kotlin daemon). CI uses `.github/ci-gradle.properties`, which the `gradle-setup` composite action copies to `~/.gradle/gradle.properties`. Key CI overrides:
- `org.gradle.daemon=false` (single-use runners)
- `kotlin.incremental=false` (fresh checkouts)
- `-Xmx4g` Gradle heap, `-Xmx2g` Kotlin daemon
- VFS watching disabled, workers capped at 4
- `org.gradle.isolated-projects=true` for better parallelism
- Disables unused Android build features (`resvalues`, `shaders`)

### CI Conventions
- **KMP Smoke Compile:** `./gradlew kmpSmokeCompile` is a lifecycle task (registered in `RootConventionPlugin`) that auto-discovers all KMP modules and depends on their `compileKotlinJvm` + `compileKotlinIosSimulatorArm64` tasks.
- **`maxParallelForks` CI logic:** `ProjectExtensions.kt` checks `project.findProperty("ci") == "true"` and uses full available processors in CI (4 forks on std runners) vs. half locally. All CI invocations pass `-Pci=true`.
- **Detekt report formats:** Detekt.kt checks `project.findProperty("ci") == "true"` and disables html, txt, md reports in CI; only xml + sarif are retained for GitHub annotations.
- **Robolectric SDK caching:** The `gradle-setup` composite action caches `~/.m2/repository/org/robolectric` to prevent flaky `SocketException` on SDK downloads. Cache key is `robolectric-{version}-sdk{level}` — update when bumping version or SDK level.
- **`mavenLocal()` gated:** Disabled by default to prevent CI cache poisoning. Pass `-PuseMavenLocal` for local JitPack testing.
- **JUnit parallel execution:** Enabled project-wide with classes running sequentially (`junit.jupiter.execution.parallel.mode.classes.default=same_thread`) to avoid `Dispatchers.setMain()` races. Cross-module parallelism comes from Gradle forks (`maxParallelForks`).
- **`test-retry` plugin:** Applied to all module types (maxRetries=2, maxFailures=10).
- **`fail-fast: false`:** Test sharding does not cancel other shards on failure.
- **Explicit Gradle task paths:** Prefer `app:lintFdroidDebug` over shorthand `lintDebug` in CI.
- **Pull request CI:** Main-only (`.github/workflows/pull-request.yml` targets `main`).
- **Merge queue hygiene:** `merge-queue.yml` cancels superseded runs for the same PR (GitHub does not auto-cancel destroyed merge-group runs) and skips the heavy pipeline for docs-only entries (`docs/**`, `*.md`). `rb-check` runs ONLY in the merge queue. `main-check.yml` passes `run_lint: false` — every main commit is a merge-queue-verified merge commit, so main pushes only rebuild the debug APKs for the snapshot release.
- **Cache writes:** Trusted on `main` and merge queue runs; other refs use read-only cache.
- **Path filtering:** `check-changes` in `pull-request.yml` must include module dirs plus build/workflow entrypoints (`build-logic/**`, `gradle/**`, `.github/workflows/**`, `gradlew`, `settings.gradle.kts`, etc.).
- **AboutLibraries:** Runs in `offlineMode` by default (no GitHub/SPDX API calls). Release builds pass `-PaboutLibraries.release=true` via Fastlane/Gradle CLI to enable remote license fetching. Do NOT re-gate on `CI` or `GITHUB_TOKEN` alone.

