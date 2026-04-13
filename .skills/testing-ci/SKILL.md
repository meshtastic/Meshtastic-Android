# Skill: Testing and CI Verification

## Description
Guidelines and commands for verifying code changes locally and understanding the Meshtastic-Android CI pipeline. Use this to determine which testing matrix is needed based on the change type.

## 1) Baseline local verification order

Run in this order for routine changes to ensure code formatting, analysis, and basic compilation:

```bash
./gradlew clean
./gradlew spotlessCheck
./gradlew spotlessApply
./gradlew detekt
./gradlew assembleDebug
./gradlew test allTests
```

> **Why `test allTests` and not just `test`:**
> In KMP modules, the `test` task name is **ambiguous**. Gradle matches both `testAndroid` and
> `testAndroidHostTest` and refuses to run either, silently skipping KMP modules.
> `allTests` is the `KotlinTestReport` lifecycle task registered by the KMP plugin.
> Conversely, `allTests` does **not** cover pure-Android modules (`:app`, `:core:api`, etc.), which is why both `test` and `allTests` are needed.

*Note: If testing Compose UI on the JVM (Robolectric) with Java 21, pin tests to `@Config(sdk = [34])` to avoid SDK 35 compatibility crashes.*

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

## 4) CI Pipeline Architecture

CI is defined in `.github/workflows/reusable-check.yml` and structured as four parallel job groups:

1. **`lint-check`** — Runs spotless, detekt, Android lint, and KMP smoke compile in a single Gradle invocation (avoids 3x cold-start overhead). Uses `fetch-depth: 0` (full clone) for spotless ratcheting and version code calculation. Produces `cache_read_only` output and computed `version_code` for downstream jobs.
2. **`test-shards`** — A 4-shard matrix that runs tests in parallel (depends on `lint-check`):
   - `shard-core`: `allTests` for all `core:*` KMP modules.
   - `shard-feature`: `allTests` for all `feature:*` KMP modules.
   - `shard-app`: Explicit test tasks for pure-Android/JVM modules (`app`, `desktop`, `core:barcode`).
   - `shard-screenshot`: `validateGoogleDebugScreenshotTest` — visual regression tests for CMP UI components. On failure, writes a step summary listing failed tests and links to the `reports-shard-screenshot` artifact.
   Each shard generates Kover XML coverage (except `shard-screenshot`) and uploads test results + coverage to Codecov with per-shard flags.
   Downstream jobs use `fetch-depth: 1` and receive `VERSION_CODE` from lint-check via env var, enabling shallow clones.
3. **`android-check`** — Builds APKs for all flavors (depends on `lint-check`).
4. **`build-desktop`** — Multi-OS matrix (`macos-latest`, `windows-latest`, `ubuntu-24.04`, `ubuntu-24.04-arm`) that builds desktop distributions via `createDistributable` (depends on `lint-check`).

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
- **Cache writes:** Trusted on `main` and merge queue runs; other refs use read-only cache.
- **Path filtering:** `check-changes` in `pull-request.yml` must include module dirs plus build/workflow entrypoints (`build-logic/**`, `gradle/**`, `.github/workflows/**`, `gradlew`, `settings.gradle.kts`, etc.).
- **AboutLibraries:** Runs in `offlineMode` by default (no GitHub/SPDX API calls). Release builds pass `-PaboutLibraries.release=true` via Fastlane/Gradle CLI to enable remote license fetching. Do NOT re-gate on `CI` or `GITHUB_TOKEN` alone.

## 5) Screenshot Testing (Compose Preview)

The project uses the Google `com.android.compose.screenshot` plugin (v0.0.1-alpha14) for visual regression testing of CMP UI components.

### KMP Limitations
- **Android-only plugin**: Only works with `com.android.application` / `com.android.library` — not `com.android.kotlin.multiplatform.library`. Tests live in the `app` module.
- **AndroidX `@Preview` required**: The plugin recognizes `androidx.compose.ui.tooling.preview.Preview` only, not the JetBrains `org.jetbrains.compose.ui.tooling.preview.Preview`.
- Preview files use Android-specific annotations (e.g. `uiMode = Configuration.UI_MODE_NIGHT_YES`) and must live in `app/src/screenshotTest/`, **never** in `commonMain`.

### File Layout
```
app/src/screenshotTest/kotlin/org/meshtastic/app/
  ├── CoreComponentScreenshotTests.kt        # @PreviewTest classes (26 test files total)
  ├── PreferenceScreenshotTests.kt
  ├── NodeInfoScreenshotTests.kt
  ├── WifiProvisionScreenshotTests.kt
  ├── ...                                    # (26 test files, 169 @PreviewTest methods)
  └── preview/
      ├── BasicComponentPreviews.kt          # Buttons, text, icons, @MultiPreview definition
      ├── ExtendedComponentPreviews.kt       # Cards, inputs, dialogs, chips
      ├── NodeDataInfoPreviews.kt            # Distance, LastHeard, Hops, Channel, SNR/RSSI
      ├── WifiProvisionComponentPreviews.kt  # Wi-Fi provision re-exports
      ├── NodeDetailComponentPreviews.kt     # Node detail re-exports
      └── ...                                # (27 preview files total)

app/src/screenshotTestGoogleDebug/reference/  # 2,366 committed .png baselines
app/build/reports/screenshotTest/preview/     # HTML diff reports (generated at validation time)
```

### Commands
```bash
# Generate/update reference images
./gradlew updateGoogleDebugScreenshotTest    # Google flavor
./gradlew updateFdroidDebugScreenshotTest    # F-Droid flavor

# Validate against references
./gradlew validateGoogleDebugScreenshotTest
./gradlew validateFdroidDebugScreenshotTest
```

> Do NOT use bare `updateDebugScreenshotTest` — it is ambiguous with product flavors.

### Writing a Preview + Test
1. Create a preview composable in `app/src/screenshotTest/.../preview/` using `@MultiPreview` and wrapping in `AppTheme`.
2. Create a test class in `app/src/screenshotTest/.../` with methods annotated `@PreviewTest` + `@MultiPreview` + `@Composable` that call the preview composable.
3. Run `updateGoogleDebugScreenshotTest` to generate baselines, commit the `.png` files.

### @MultiPreview Coverage
The `@MultiPreview` annotation (defined in `BasicComponentPreviews.kt`) generates a full cross-product of configuration variants:
- **Theme**: Light and Dark (`uiMode = Configuration.UI_MODE_NIGHT_YES`)
- **Font scale**: 1x (default) and 2x (`fontScale = 2f`)
- **Device form factor**: Phone (default), Foldable (`673dp x 841dp`), Tablet (`1280dp x 800dp`)
- **Layout direction**: LTR (default) and RTL (light + dark)

This produces **14 variants per test method** (2 themes × 2 font scales × 3 devices + 2 RTL). With **169 test methods** across **26 test files**, the total is **2,366 reference images**.

### Component Coverage
Screenshot tests cover components from the following modules:
- **`core:ui`** — Buttons, text, icons, cards, preferences, dialogs, telemetry, node info, alerts, utility components
- **`feature:node`** — Node detail components, metrics, status icons, data info
- **`feature:messaging`** — QuickChat, reactions, message input, message actions, delivery info
- **`feature:connections`** — Empty state, connecting device info, segmented bar, device list
- **`feature:settings`** — AppInfo, Appearance, Persistence, Privacy sections, radio config dialogs
- **`feature:wifi-provision`** — All 19 Wi-Fi provisioning UI components

### Convention Plugin
- `meshtastic.screenshot.testing` (in `build-logic/convention`) configures the experimental flag and `screenshotTestImplementation` dependencies.
- Must be applied **after** `alias(libs.plugins.screenshot)` in the consumer's `plugins {}` block.

### CI Integration
- Screenshot validation runs in a **dedicated `shard-screenshot` CI shard** (separate from unit tests in `shard-app`) via `:app:validateGoogleDebugScreenshotTest`.
- The shard runs on `ubuntu-24.04` alongside other test shards with `fail-fast: false`, so a screenshot failure does not cancel unit test shards.
- On failure, a `GITHUB_STEP_SUMMARY` annotation lists the names of failed tests and directs reviewers to download the `screenshot-diffs` artifact for diff images.
- The diff report (HTML with side-by-side expected/actual/diff views) is located at `app/build/reports/screenshotTest/preview/` inside the artifact.

### CI Failure Behavior
When screenshot tests fail in CI, behavior depends on the PR context:
- **Fork PRs**: CI hard-fails with an error message instructing the contributor to run `./gradlew updateGoogleDebugScreenshotTest` locally and push the updated images.
- **Same-repo PRs / merge queue / main**: CI hard-fails with instructions to update references locally.
- A dedicated `screenshot-diffs` artifact is uploaded containing the diff images for debugging.

### Image Difference Threshold
- Configured at `app/build.gradle.kts` via `testOptions.screenshotTests.imageDifferenceThreshold = 0.02f` (2%).
- **Rationale**: Compose Preview Screenshot Testing uses layoutlib for host-side rendering, but anti-aliasing and font hinting differ between macOS (where developers typically generate references) and Linux (CI runner). A 2% threshold absorbs these sub-pixel differences without masking real regressions.
- Emoji characters (e.g. `🔔` in `QuickChatRow`) render with different glyphs across platforms and are the primary source of visual diff. Avoid emoji literals in preview data where possible.
- Revisit this threshold when the plugin exits alpha or if false negatives appear.

### Reference Image Generation
- **OS guidance**: Reference images are best generated on the same OS as CI (`ubuntu-24.04`). Generating locally on macOS is acceptable because the 2% threshold absorbs rendering differences.
- **Determinism**: Preview composables must use fixed/deterministic data. Avoid `Random`, `currentTime()`, or other non-deterministic values in preview parameters. Use hardcoded constants instead. For relative-time displays (e.g. `LastHeardInfo`), use `nowSeconds - offset` so the rendered text (e.g. "5m") remains constant across runs.
- **Updating references**: Run `./gradlew updateGoogleDebugScreenshotTest`, then commit the updated `.png` files under `app/src/screenshotTestGoogleDebug/reference/`.

### Debugging CI Failures
1. **Check the job summary**: The `shard-screenshot` job writes a step summary listing which tests failed. Look for the "Screenshot Test Failures" section in the PR checks.
2. **Download the artifact**: Download the `screenshot-diffs` artifact (just the diff PNGs) or `reports-shard-screenshot` (full reports) from the workflow run's artifacts page (retained for 7 days).
3. **Inspect diff images**: Each failed test has `_expected.png`, `_actual.png`, and `_diff.png` files.
4. **Common causes**:
   - Font rendering differences (macOS vs Linux) — usually within threshold; if not, let CI regenerate.
   - Non-deterministic preview data (random values, timestamps) — fix the preview to use constants.
   - Emoji rendering (platform-specific glyphs) — avoid emoji in preview data or increase threshold.
   - Genuine UI regression — update the component or regenerate references if the change is intentional.

## 6) Shell & Tooling Conventions
- **Terminal Pagers:** When running shell commands like `git diff` or `git log`, ALWAYS use `--no-pager` (e.g., `git --no-pager diff`) to prevent getting stuck in an interactive prompt.
- **Text Search:** Prefer `rg` (ripgrep) over `grep` or `find` for fast text searching across the codebase.

## 7) Agent/Developer Guidance
- Start with the smallest set that validates your touched area.
- If unable to run full validation locally, report exactly what ran and what remains.
- Keep documentation synced in `AGENTS.md` and `.skills/` directories.
