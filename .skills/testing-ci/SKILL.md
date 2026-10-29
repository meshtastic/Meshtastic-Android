---
name: testing-ci
description: Decide what to run locally before pushing Meshtastic-Android, and read the CI pipeline. Use this whenever you finish a change and need the right verification for its change type, when a CI job fails or is skipped, or when you need to know why the merge queue behaved the way it did.
---

# Skill: Testing and CI Verification

## Description
Guidelines and commands for verifying code changes locally and understanding the Meshtastic-Android CI pipeline. Use this to determine which testing matrix is needed based on the change type.

## 1) Baseline local verification order

Run in a single invocation for routine changes to ensure code formatting, analysis, and basic compilation:

```bash
./gradlew spotlessApply spotlessCheck detekt detektTypeResolved assembleDebug test allTests
```

`detekt` alone has no classpath, so it skips every rule that needs type resolution (`UnsafeCallOnNullableType`, `SuspendFunSwallowedCancellation`, `UnsafeCast` and the rest). `detektTypeResolved` runs those against each module's production JVM and Android debug compilations; it shares the module's `detekt-baseline.xml`.

Regenerating that baseline needs care, because detekt's baseline tasks rewrite `CurrentIssues` from their own run and keep only `ManuallySuppressedIssues`. A plain `detektBaseline` therefore drops every type-resolved ID, and `detektBaselineMain<Target>` or `detektBaseline<Variant>` writes `detekt-baseline-<compilation>.xml`, which no check reads. After a plain `detektBaseline`, run the type-resolved baseline tasks for that module (`detektBaselineMainJvm`, `detektBaselineMainAndroid`, `detektBaselineFdroidDebug` and so on), copy the IDs you mean to keep from the generated files into `detekt-baseline.xml`, delete the generated files, and confirm with `detekt detektTypeResolved`. `detektBaselineMainJvm` and `detektBaselineMainAndroid` both write `detekt-baseline-main.xml`, so run and copy them one at a time.

> **Why no `clean`?** Incremental builds are safe and significantly faster. Only use `clean` when debugging stale cache issues.

> **Why `test allTests` and not just `test`:**
> A KMP module has no `test` task: the root `test` silently skips it, and naming `:module:test`
> fails as **ambiguous** between `testAndroid` and `testAndroidHostTest`.
> `allTests` is the `KotlinTestReport` lifecycle task registered by the KMP plugin.
> Conversely, `allTests` does **not** cover pure-Android modules (`:androidApp`, `:core:barcode`, etc.), which is why both `test` and `allTests` are needed.

*Note: If testing Compose UI on the JVM (Robolectric), pin tests to `@Config(sdk = [34])` to avoid SDK 35 compatibility crashes.*

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
- `feature/commonMain logic` changes: `spotlessCheck`, `detekt`, `detektTypeResolved`, `test allTests`, `assembleDebug`.
- `navigation/DI wiring` changes: `spotlessCheck`, `detekt`, `detektTypeResolved`, `assembleDebug`, `test allTests`, plus flavor unit tests if available.
  - If touching any KMP module, also run `kmpSmokeCompile`.
- `worker/service/background` changes: Broad tests, targeted WorkManager checks.
- `BLE/networking/core repository`: `spotlessCheck`, `detekt`, `detektTypeResolved`, `assembleDebug`, `test allTests`.
- Any Kotlin change: CI compiles with `-PwarningsAsErrors=true` (see CI Conventions), so add it to the compile or test tasks you run.
- `build-logic/` changes: also `:build-logic:convention:test :build-logic:convention:validatePlugins`; the root `spotlessCheck detekt` already reach build-logic.

## 3) Flavor checks

Run these when relevant to map, provider, or flavor-specific behavior:

```bash
./gradlew lintFdroidDebug lintGoogleDebug
./gradlew testFdroidDebugUnitTest testGoogleDebugUnitTest
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

Rendering is **host-deterministic** (layoutlib): a local `update` produces references byte-identical to CI, so locally-recorded goldens pass `validate`. The convention plugin pins the render JVM to `user.timezone=UTC` (CST itself pins only the locale), so a preview that formats an absolute date records the same day on every host. **Exception — color emoji: do NOT gate CI on them.** Layoutlib bundles the text fonts but resolves color emoji through the host's emoji font, so glyph edges rasterize differently on macOS than on the Linux runner. Layout, text and vectors still match exactly; only the emoji anti-aliasing moves, which is enough to blow the 0.0005 `imageDifferenceThreshold` on an emoji-dense composition and cannot be fixed by re-running `update` locally. Assert the layout rule in a unit test instead — see `core/ui/src/commonTest/.../emoji/EmojiCellSizeTest.kt` — or put the composition in generate-only `:docs-screenshots`. `copyDocsScreenshots` overwrites a stale committed `nodes_detail_local.png` each run; restore it with `git restore --source=HEAD --staged --worktree -- docs/assets/screenshots/nodes_detail_local.png`. Public previews consumed cross-module by a wrapper need a `detekt-baseline.xml` entry (PreviewPublic). New screenshot? Pick the module by purpose; see `docs/assets/screenshots/README.md`.

## 3c) Fresh-install manual/agent testing: skip onboarding

Debug builds accept an intent extra to skip the intro flow, honored only on a launch through the `AutomationLauncher` alias (`androidApp/src/debug/AndroidManifest.xml`), which requires `DUMP` so only the shell can start it. Release builds have no alias. Pair with `pm grant` (native Android, no app code) to pre-accept runtime permissions:

```bash
adb shell pm grant <pkg> android.permission.BLUETOOTH_SCAN
adb shell pm grant <pkg> android.permission.BLUETOOTH_CONNECT
adb shell pm grant <pkg> android.permission.ACCESS_FINE_LOCATION
adb shell pm grant <pkg> android.permission.POST_NOTIFICATIONS   # API 33+
adb shell am start -n <pkg>/org.meshtastic.app.AutomationLauncher --ez skip_onboarding true
```

Add `--ez skip_connect_confirm true` and a `/connections?address=` deep link to connect without the new-device trust dialog.

Use this whenever driving the app from a fresh install/uninstall (screenshot tests, UI automation, agent-driven exploration) instead of clicking through the intro screens.

## 4) CI Pipeline Architecture

CI is defined in `.github/workflows/reusable-check.yml` as parallel job groups. No job `needs:` another, so every one queues for a runner as soon as the run starts:

1. **`lint-check`** runs `spotlessCheck`, `detekt`, `detektTypeResolved` and Android lint for both flavors of `:androidApp` and `:core:barcode` in a single Gradle invocation (avoids 3x cold-start overhead), plus `:build-logic:convention:validatePlugins` and `:build-logic:convention:test`, which no root task reaches; `meshtastic.root` routes the root `spotlessCheck` and `detekt` into build-logic. It checks out full history (`fetch-depth: 0`) because spotless ratchets against `origin/main`, and it has no outputs.
2. **`test-shards`** is a 3-shard matrix that runs unit tests in parallel. Shard membership is a load-balancing detail, not a taxonomy: heavy modules are moved between shards to even out wall time, so read the matrix rather than inferring it:
   - `shard-core`: `allTests` for the remaining `core:*` KMP modules, plus `kmpSmokeCompile`, the only iOS compile in CI (the shards pass `-x iosSimulatorArm64Test`).
   - `shard-feature`: `allTests` for the remaining `feature:*` KMP modules **plus `:core:service`**.
   - `shard-app`: Explicit test tasks for pure-Android/JVM modules (`androidApp`, `desktopApp`, `core:barcode`, `feature:widget`, `schema-strings`) **plus `:core:database`, `:core:network`, `:feature:node` and `:feature:wifi-provision`**.
   On `main`, `shard-feature` is the only test shard that writes the Gradle cache and the only job that writes the Kotlin/Native cache; `android-check` and `build-desktop` also write the Gradle cache. Every shard uploads its test results to Codecov. Kover XML coverage is generated and uploaded only when `run_coverage` is true, which only `main-check.yml` passes. Codecov flags follow the module group (`core`, `feature`, `app`, `desktop`), not the shard.
   The validation-only jobs (`lint-check`, `screenshot-check`, `test-shards`) pin `VERSION_CODE` to one constant so the versionCode-dependent tasks keep the same cache keys on every commit; `screenshot-check` and `test-shards` also clone shallow (`fetch-depth: 1`). `android-check` and `build-desktop` check out full blob-less history so the build derives the real versionCode.
3. **`android-check`** builds the fdroid and google debug APKs and checks their native-library ABI parity (`scripts/verify-abi-parity.sh`). The merge queue skips it. On `main` it also generates and submits the dependency graph; no other ref submits one.
4. **`build-desktop`** runs on `main` and on a PR whose `desktop` path filter matches, never in the merge queue. It is a multi-OS matrix (macOS, Windows, and Linux x64 and arm64; the job's `matrix.os` carries the labels) running `:desktopApp:packageDistributionForCurrentOS :desktopApp:proguardReleaseJars`. It packages the debug build type, real installers the snapshot release can ship, and pulls in `proguardReleaseJars` only so a jmods-less packaging JDK fails here rather than at release time. On Linux it then wraps jpackage's `app-image` directory into a real AppImage via `scripts/build-appimage.sh`.
5. **`screenshot-check`** — Runs `:screenshot-tests:validateDebugScreenshotTest` (the visual-regression gate) and uploads a diff report on failure. A PR runs it only when its `screenshots` path filter matches (`core/`, `feature/`, build machinery); the merge queue always runs it. `:docs-screenshots` is intentionally NOT validated here (generate-only).
6. **`rb-check`** — Reproducible-build verification (`scripts/verify-rb.sh`). Runs **only** in the merge queue.
7. **`verify-flatpak`** lives in its own workflow (`.github/workflows/verify-flatpak.yml`), **not** in `reusable-check.yml`, and is not called by it. Generates the Flatpak offline-build sources (`captureFlatpakSources`) and then builds the flatpak fully offline, on an x86_64 + aarch64 matrix of hosted Ubuntu runners. Each arch generates its sources inside its own job; none are committed. It is **not a required check** and never runs in the merge queue, so its triggers are scoped accordingly: a PR runs it only when it touches the flatpak tooling itself (`scripts/verify-flatpak/**`, the workflow), and a push to `main` runs it for those plus `gradle/wrapper/**`, because the offline manifest pins the Gradle distribution apart from the wrapper. The wider dependency surface it captures (`desktopApp/**`, `gradle/libs.versions.toml`, the root build scripts) is verified by the nightly cron.

8. **`protobufs-bump`** runs in its own workflow (`.github/workflows/protobufs-bump.yml`) on any PR that changes the `meshtastic-protobufs` line of `gradle/libs.versions.toml`. Comment only: it previews `:schema-strings:sync` at the new pin and leaves one sticky comment from `scripts/protobufs-bump-summary.py` with the upstream compare, the merged protobufs PRs, the `.proto` delta and the settings strings that will change. Nothing is pushed to the branch. The regeneration itself is a step of `scheduled-updates.yml`, which runs every 6 hours at :17 and on every push to `main` that changes `gradle/libs.versions.toml`. `values/schema_strings.xml` records the pin it was built from; each run compares that with the catalog and, when they differ, reuses the `scheduled-updates` branch's copy if it was already built for the catalog's pin from the same `schema-strings/` generator and `strings.xml` header, running Gradle only otherwise. The regenerated English goes up to Crowdin in the same run and rides the scheduled PR with the translations. `RepositorySyncTest` checks the file against the registry only while the recorded pin matches the catalog, so the window between a bump merging and the scheduled PR landing is not red.

### Runner Strategy (Four Tiers)
The tiers are named here and the workflows carry the label versions.

- **`ubuntu-slim`** is the cheapest tier, for API and script jobs off the required-check path: the labeler, the PR-close run canceller, changelog, the docs link check, the Play listing upload, release cleanup, and the release and promotion jobs that are only API and script work (tag resolution, the version bump, the flatpak-sources and store-screenshot release assets, the GitHub release update, and the Homebrew and Flathub bumps). Container-backed and starts in seconds, but **single-CPU, unprivileged, x64-only, with a hard 15-minute job cap**, so it fits `gh`/`jq`/`git`/stdlib-`python3`/`github-script` work and nothing needing `sudo`, `apt-get`, Docker, a mounted filesystem, or a long full-history clone. It is a separate, smaller pool, so the required `Check Workflow Status` gates stay off it: a gate queued there holds up a finished build.
- **The pinned Ubuntu LTS arm label** runs the lightweight jobs that break any of those `ubuntu-slim` constraints or sit on the required-check path: PR and merge-queue change detection, `check-metadata`, the status gates, the docs quality gate, the snapshot publish, the Play upload, promotion and rollout, and the GitHub release. Shorter queue times than x64.
- **The pinned Ubuntu LTS x64 label** runs the Gradle-heavy jobs. Every single-runner job in `reusable-check.yml` pins it (`lint-check`, `screenshot-check`, `rb-check`, `test-shards`, `android-check`), as do release builds, Dokka and docs publishing. The `build-desktop` matrix job spans several runners instead, as does `verify-flatpak` in its own workflow. Pin for reproducibility.
- **Desktop runners:** a multi-OS matrix (macOS, Windows, and Ubuntu x64 and arm64) for the `build-desktop` job, `verify-flatpak`, and release packaging. Each matrix lists its own labels, which can trail the pinned LTS labels above. `msstore-publish.yml` and `winget-publish.yml` run on `ubuntu-latest`.

`.github/instructions/ci-workflows.instructions.md` restates the picking rule for anyone editing a workflow file.

### CI Gradle Properties
`gradle.properties` is tuned for local dev (8g heap, 6g Kotlin daemon). CI uses `.github/ci-gradle.properties`, which the `gradle-setup` composite action copies to `~/.gradle/gradle.properties`. Key CI overrides:
- `org.gradle.daemon=false` (single-use runners)
- `kotlin.incremental=false` (fresh checkouts)
- `-Xmx8g` Gradle heap (ParallelGC), `-Xmx6g` Kotlin daemon, no Kotlin/Native compiler daemon
- VFS watching disabled, workers capped at 4
- `org.gradle.isolated-projects=true` for better parallelism

### CI Conventions
- **KMP Smoke Compile:** `./gradlew kmpSmokeCompile` is a lifecycle task (registered in `RootConventionPlugin`) that depends on `compileCommonMainKotlinMetadata`, `compileKotlinJvm`, `compileKotlinIosSimulatorArm64` and `compileTestKotlinIosSimulatorArm64` for every KMP module in the hand-maintained `ALL_MODULES_FULL` list, plus `assembleAndroidDeviceTest` for `:core:database` and `:core:model`, so a device-test APK that fails to dex or package fails here. The commonMain metadata compile catches platform-only APIs that every target resolves but `commonMain` alone does not; the iOS test compile catches backtick test names Kotlin/Native rejects. `scripts/check-module-list.py` fails the PR when that list drifts from `settings.gradle.kts`. CI runs it in `shard-core`.
- **Kotlin warnings fail the test shards:** they pass `-PwarningsAsErrors=true`, which sets `allWarningsAsErrors` on every Kotlin compilation, native and metadata included (`KotlinAndroid.kt`, plus `desktopApp` and `schema-strings`). Reproduce locally with the same flag on the compile or test tasks you touched.
- **`maxParallelForks` CI logic:** `ProjectExtensions.kt` reads the `ci` Gradle property (`providers.gradleProperty("ci")`) and uses full available processors in CI (4 forks on std runners) vs. half locally. All CI invocations pass `-Pci=true`.
- **Detekt report formats:** Detekt.kt checks `project.findProperty("ci") == "true"` and disables the html and md reports in CI; only checkstyle XML and sarif are written for GitHub annotations, one pair per task.
- **`mavenLocal()` gated:** Disabled by default to prevent CI cache poisoning. Pass `-PuseMavenLocal` for local JitPack testing.
- **JUnit parallel execution:** Enabled project-wide with classes running sequentially (`junit.jupiter.execution.parallel.mode.classes.default=same_thread`) to avoid `Dispatchers.setMain()` races. Cross-module parallelism comes from Gradle forks (`maxParallelForks`).
- **Test timeouts:** every Jupiter test and lifecycle method fails after 2 minutes (`junit.jupiter.execution.timeout.default`, `SEPARATE_THREAD` so code that ignores interrupts still fails by name), and every `Test` task stops after 15 minutes, which also covers the JUnit 4 host tests. Both live in `ProjectExtensions.kt`.
- **Test retry:** Develocity plugin's native retry (`develocity.testRetry` on each Test task), configured in `ProjectExtensions.kt` (maxRetries=2, maxFailures=10, `failOnPassedAfterRetry`), so a test that only passes on retry still fails the build. Screenshot tests opt out (maxRetries=0). Don't add the `org.gradle.test-retry` plugin.
- **`fail-fast: false`:** Test sharding does not cancel other shards on failure.
- **Explicit Gradle task paths:** Prefer `androidApp:lintFdroidDebug` over shorthand `lintDebug` in CI.
- **Pull request CI:** `.github/workflows/pull-request.yml` runs on PRs into `main` and `release/**`. Its Gradle jobs skip the `scheduled-updates` and `scheduled-baseline` head branches; the merge queue still runs everything for them.
- **Merge queue hygiene:** `merge-queue.yml`'s `check-changes` job first cancels older runs for the same PR, best effort, because GitHub does not auto-cancel destroyed merge-group runs. It then lists the entry's files from the compare API, with no checkout, and skips the heavy pipeline for docs-only entries (`docs/**`, `fastlane/**`, `obtainium/**`, `*.md`), mirrored by `main-check.yml`'s `paths-ignore`. An entry of 300 or more files, the API's listing cap, runs full CI. Once the gate passes, `Check Workflow Status` posts the required `license/cla` on the group commit if the PR head has it, because cla-assistant.io only checks PRs. `rb-check` runs ONLY in the merge queue. The queue squash-merges groups of up to three entries (`ALLGREEN`, waiting up to 5 minutes to fill one), and its required checks are `Check Workflow Status` and `license/cla`. `main-check.yml` passes `run_lint: false` because every main commit is the squash commit the queue already verified, so main pushes skip lint, `screenshot-check` and `rb-check`, and run the coverage shards, the debug APKs and the desktop packages for the snapshot release. Its concurrency group never cancels a started run: one run executes, one waits, and a newer push replaces only the waiting one.
- **Cache writes:** each cache has its own rule. setup-gradle's Gradle User Home cache is written by `reusable-check.yml` on `main` only (`GRADLE_CACHE_READ_ONLY`), so PRs and the merge queue only read it; outside that workflow, the Google, F-Droid and desktop release builds and `scheduled-baseline.yml` write it and every other caller reads. The Develocity remote build cache is pushed by `push` and `merge_group` builds and by `workflow_dispatch` runs on `main` that have the access key, never by PRs (`MeshtasticDevelocitySettingsPlugin`). `gradle-setup`'s Kotlin/Native (`~/.konan`) cache follows the same `cache_read_only` input: it saves on a key miss only when that is not `'true'`, and a read-only run restores it without saving. It is opt-in through `cache_konan`, which the test shards and the read-only docs builds pass, so only `shard-feature` on `main` writes it. There is no Robolectric cache.
- **Path filtering:** `check-changes` in `pull-request.yml` must include module dirs plus build/workflow entrypoints (`build-logic/**`, `gradle/**`, `.github/workflows/**`, `gradlew`, `settings.gradle.kts`, etc.). It runs three drift guards, each runnable locally with `python3`: `scripts/check-changes-filter.py` (every module root in `settings.gradle.kts` has a `<root>/**` line in the `android` filter), `scripts/check-module-list.py` (`RootConventionPlugin`'s `ALL_MODULES_FULL` matches `settings.gradle.kts`) and `scripts/check-test-shards.py` (every module with tests is in a `reusable-check.yml` shard or exempted).
- **AboutLibraries:** Runs in `offlineMode` by default (no GitHub/SPDX API calls). `release.yml`'s Google and desktop build steps pass `-PaboutLibraries.release=true` to enable remote license fetching; the F-Droid build leaves it off so its output matches F-Droid's reproducible rebuild. Do NOT re-gate on `CI` or `GITHUB_TOKEN` alone.

