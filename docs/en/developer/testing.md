---
title: Testing
parent: Developer Guide
nav_order: 7
last_updated: 2026-09-19
description: Testing strategy for the Meshtastic KMP project — test categories, screenshot pipeline, baseline profiles, and CI integration.
aliases:
  - tests
  - unit-tests
  - screenshot-tests
---

# Testing

Testing strategy and practices for the Meshtastic KMP project.

## Test Categories

### KMP Unit Tests (`commonTest`)

Shared tests that run on all platforms:

```shell
./gradlew allTests
```

- Business logic tests
- Data model validation
- Search/ranking algorithm tests
- Route serialization tests

### Android Host Tests

Android-specific tests that run on JVM:

```shell
./gradlew test
```

- ViewModel tests
- Repository tests with Room fakes
- Android-specific integration tests

### Compose UI Tests

Compose Multiplatform UI test framework:

```kotlin
@Test
fun myScreenTest() = runComposeUiTest {
    setContent { MyScreen() }
    onNodeWithText("Expected").assertIsDisplayed()
}
```

Located in `commonTest` or `jvmTest` source sets.

### Screenshot Tests

Uses Android Gradle Plugin's native (layoutlib) screenshot testing framework, split across two modules:

- **`:screenshot-tests`** — the **visual-regression gate**. CI runs `validateDebugScreenshotTest` on it; reframing one of these baselines is a real diff to review. Holds atomic, dual-purpose components.
- **`:docs-screenshots`** — **generate-only**, *not* validated in CI. Holds doc-framed compositions whose framing is tuned for the docs site, so reframing a doc image never churns the regression gate.

```shell
./gradlew :screenshot-tests:updateDebugScreenshotTest    # record regression goldens
./gradlew :screenshot-tests:validateDebugScreenshotTest  # compare against goldens (CI gate)
./gradlew :docs-screenshots:updateDebugScreenshotTest    # record doc-framed composition images
./gradlew :screenshot-tests:copyDocsScreenshots          # copy doc images from BOTH modules into docs/assets
```

Rendering is host-deterministic here (layoutlib): a local `update` produces references byte-identical to CI, so locally-recorded goldens pass `validate`. See `docs/assets/screenshots/README.md` for which module a new screenshot belongs in.

#### Marketing screenshots

The store-listing screenshots (Play, F-Droid, IzzyOnDroid, and the desktop app's Flathub listing) are generated too, by a third module: **`:marketing-screenshots`** is a plain JVM program, not a test. It renders the app's own `commonMain` screens offscreen with Compose Desktop's `ImageComposeScene` over one sample mesh and captures the real MapLibre map (basemap plus the app's node chips) through maplibre-compose's `MapSnapshotter`. Every shot is the raw screen, as [Play's listing rules](https://support.google.com/googleplay/android-developer/answer/9866151) require - no device frame, no caption banner - at one size per form factor. It has no tests, so `./gradlew test` never touches it, and CI never runs it. The one command:

```shell
./gradlew :marketing-screenshots:updateMarketingScreenshots
```

That writes `1_messages.png` … `5_channels.png` into five folders under `fastlane/metadata/android/en-US/images/`, and `meshtastic-desktop-01-nodes.png` … `meshtastic-desktop-05-settings.png` into the Flathub folder, reproducibly - the screens are byte-identical between runs; a wide map can differ by a few antialiased label-edge pixels, see below:

| Folder | Size | Window | Uploaded by |
| --- | --- | --- | --- |
| `phoneScreenshots/` | 1080×1920 @2.5x | 432×768 dp, compact: bottom navigation bar | `fastlane supply` |
| `sevenInchScreenshots/` | 1080×1920 @1.8x | 600×1067 dp, medium: navigation rail, one pane | `fastlane supply` |
| `tenInchScreenshots/` | 2560×1440 @2x | 1280×720 dp, expanded: rail, list beside detail | `fastlane supply` |
| `chromebookScreenshots/` | 1920×1080 @1x | expanded | hand, in Play Console |
| `xrScreenshots/` | 1920×1200 @1x (8:5) | expanded | hand, in Play Console |
| `desktopApp/packaging/linux/screenshots/` | 1280×800 @1x (16:10) | expanded: rail, list beside detail | Flathub, through `metainfo.xml` |

The screens are composed in the app's own adaptive shell (`NavigationSuiteScaffold`, `ListDetailPaneScaffold`, `AdaptiveTwoPane`) with the same window-class calculations the app uses, so the form factors are data - a size, a density, a shot list and an output folder in `FormFactor.kt` - and every layout difference between them is the app's own. Neither `fastlane supply` nor the Play Developer API has a Chromebook or XR slot, so those two folders are ignored by supply and F-Droid and uploaded by hand. The desktop set lists nodes, messages, map, connections and settings, the five `<screenshot>` entries in `desktopApp/packaging/linux/org.meshtastic.MeshtasticDesktop.metainfo.xml`, whose `<image>` URLs are `raw.githubusercontent.com` links pinned to a commit: regenerating it is two commits, first the PNGs (with the generator and doc changes that produced them), then the metainfo pointing its URLs at that first commit's full SHA, because the URLs cannot name a commit that contains them. **After the PR squash-merges, re-pin them to the merge commit**: a squash leaves the branch commits out of `main`, and a deleted branch leaves the SHA they named unreferenced and eventually collectable, which would blank the Flathub listing. The basemap is the app's default Liberty style, labels included. MapLibre packs glyphs into an atlas in tile-arrival order, so on the wide layouts a label's antialiased edge can land one level off between generations - a dozen pixels, invisible; the generator captures from fresh runtimes until two agree and warns if they never do, and a regenerated wide map may not `cmp` the previous one. Commit whichever run produced it.

Locales: `-PmarketingLocales=en-US,de-DE` renders each locale in turn after switching the JVM default locale, the same switch the desktop app makes, so the app's strings, numbers and dates follow. Only `en-US` goes into `fastlane/` - everything there is read straight from git by F-Droid and IzzyOnDroid - and every other locale lands in `marketing-screenshots/build/marketing-screenshots/<locale>/images/` in the same layout, ready for a later `supply` run, with the desktop set beside it in `<locale>/desktop/`. The sample prose (the thread, the conversation previews, the framed variant's captions) is in `marketing-screenshots/src/main/composeResources/values/strings.xml`, which `crowdin.yml`'s first rule already globs like every other `composeResources` strings file, so a translated conversation needs no configuration change; until Crowdin fills a locale's `values-xx/strings.xml`, that locale's chat text stays English while the UI around it is translated.

`-PmarketingFramed=true` also writes the framed 1242×2484 phone variants - bezel, drawn status bar and a caption banner - under `marketing-screenshots/build/marketing-screenshots/framed/<locale>/`, for the website and social posts. They never go into `fastlane/`.

The map needs a Vulkan loader the JVM can find. On a stock Ubuntu nothing is required; in a Nix dev shell, which replaces `LD_LIBRARY_PATH` on entry, pass `-PmarketingLibraryPath=/usr/lib/x86_64-linux-gnu` (the system loader plus the GPU's ICD). No display is needed or used.

### Baseline Profile / Startup Performance

The `:baselineprofile` module (#5735) generates a [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/overview) for `:androidApp`, AOT-compiling the hot startup paths so ART doesn't pay the JIT cost on first launch. It targets the `google` flavor (the variant most users run).

The Macrobenchmark generator (`BaselineProfileGenerator`) and the before/after benchmark (`StartupBenchmark`) live in `baselineprofile/src/main/kotlin/org/meshtastic/baselineprofile/`. Both run on a device/emulator:

```shell
./gradlew :androidApp:generateGoogleReleaseBaselineProfile   # Generate the profile (commit the output)
./gradlew :androidApp:benchmarkGoogleReleaseBaselineProfile  # Quantify the cold-start win
```

The generated profile is merged into `androidApp/src/googleRelease/generated/baselineProfiles/` and packaged into release builds via `androidx.profileinstaller`.

> ℹ️ **Note:** The journey covers cold start only (launch → first frame), because CI has no paired radio. Post-connection screens (node list, map, message thread) are not yet AOT-compiled.

Extending the journey past cold start needs a fake transport or a connected radio wired into the harness.

## Test Organization

```text
feature/my-feature/src/
├── commonTest/kotlin/org/meshtastic/feature/myfeature/
│   ├── MyBusinessLogicTest.kt
│   └── MyModelTest.kt
└── jvmTest/kotlin/org/meshtastic/feature/myfeature/
    └── MyDesktopSpecificTest.kt
```

## Testing Guidelines

### DO

- Write tests in `commonTest` when possible (runs everywhere)
- Test business logic independently from UI
- Use fakes/stubs instead of mocks where practical
- Test edge cases: empty states, error states, boundary values
- Test deep link routing in `DeepLinkRouterTest`
- Keep tests fast — no network, no disk I/O in unit tests

### DON'T

- Don't test framework behavior (Compose internals, Room queries)
- Don't create tests that depend on other feature modules
- Don't use `Thread.sleep` — use coroutine test dispatchers
- Don't rely on test execution order

## Running Tests

```shell
# All KMP tests
./gradlew allTests

# Specific module
./gradlew :feature:docs:allTests

# Code quality
./gradlew spotlessCheck detekt

# Full verification
./gradlew spotlessCheck detekt kmpSmokeCompile test allTests
```

## CI Integration

Tests run automatically on:
- Pull request creation/update
- Push to `main`
- Pre-release validation

Single-runner jobs in `reusable-check.yml` run on `ubuntu-26.04` with JDK 25 and Gradle caching. Two jobs use a matrix: `test-shards` splits into `shard-core`, `shard-feature` and `shard-app`, and `build-desktop` runs across macOS, Windows and Linux, still pinned to `ubuntu-24.04`/`-arm`. Flatpak verification is its own workflow, not a job here. The ARM (`ubuntu-26.04-arm`) and container-backed `ubuntu-slim` runners carry the lightweight utility workflows — see `.skills/testing-ci/SKILL.md` for the four-tier rule, which still quotes the older labels.
