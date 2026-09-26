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

## Test categories

### KMP unit tests (`commonTest`)

Shared tests that run on all platforms:

```shell
./gradlew allTests
```

- Business logic tests
- Data model validation
- Search/ranking algorithm tests
- Route serialization tests

### Android host tests

Android-specific tests that run on JVM:

```shell
./gradlew test
```

- ViewModel tests
- Repository tests with Room fakes
- Android-specific integration tests

### Compose UI tests

Compose Multiplatform UI test framework:

```kotlin
@Test
fun myScreenTest() = runComposeUiTest {
    setContent { MyScreen() }
    onNodeWithText("Expected").assertIsDisplayed()
}
```

Located in `commonTest` or `jvmTest` source sets.

### Screenshot tests

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

#### Store screenshots

The store-listing screenshots (Play, F-Droid, IzzyOnDroid, and the desktop app's Flathub listing) are taken from the real apps, connected to Demo Mode's hidden showcase mesh (`/connections?address=mshowcase`, `MockScenario.SHOWCASE` in `:core:network`), rather than drawn. Every screen is reached by its deep link, so the flow does not depend on the display language, and each shot is kept once the window has stopped changing.

- **Android: `:store-screenshots`**, a UiAutomator 2.4 test module targeting `:androidApp`. For each surface `fastlane supply` uploads it sets the display size and density, relaunches the debug app through its shell-only `AutomationLauncher` alias with `skip_onboarding` and `skip_connect_confirm`, and saves the five listing shots, full screen with a SystemUI demo-mode status bar, to `/data/local/tmp/store-screenshots` on the device.
- **Desktop: `store-screenshots/capture-desktop.sh`** runs the real desktop debug build on an Xvfb display, one launch per screen with that screen's deep link, and saves the five Flathub shots. The map needs Skiko's OpenGL renderer and Skiko refuses any GL adapter named `llvmpipe` or `virgl`, so Mesa runs GL through zink over lavapipe.

On an emulator or device, one flavor at a time:

```shell
./gradlew :store-screenshots:connectedFdroidDebugAndroidTest
adb pull /data/local/tmp/store-screenshots/. fastlane/metadata/android/en-US/images/
```

| Folder | Size | Window | Uploaded by |
| --- | --- | --- | --- |
| `phoneScreenshots/` | 1080×1920 @400 dpi | compact: bottom navigation bar | `fastlane supply` |
| `sevenInchScreenshots/` | 1080×1920 @288 dpi | medium: navigation rail, one pane | `fastlane supply` |
| `tenInchScreenshots/` | 2560×1440 @320 dpi | expanded: rail, list beside detail | `fastlane supply` |
| `desktopApp/packaging/linux/screenshots/` | 1280×800 | expanded: rail, list beside detail | Flathub, through the release assets `metainfo.xml` names |

`.github/workflows/store-screenshots.yml` runs both on hosted runners, per flavor for Android (google for the Play listing, fdroid for the committed tree), on every internal release, on demand, and on pull requests that touch the renderer or the showcase mesh. The release pipeline attaches the captures to the release, publishes the Play listing from them on production, and opens a self-merging PR that writes the fdroid and desktop sets back here (`RELEASE_PROCESS.md`).

### Baseline Profile / startup performance

The `:baselineprofile` module (#5735) generates a [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/overview) for `:androidApp`, AOT-compiling the hot startup paths so ART doesn't pay the JIT cost on first launch. It targets the `google` flavor (the variant most users run).

The Macrobenchmark generator (`BaselineProfileGenerator`) and the before/after benchmark (`StartupBenchmark`) live in `baselineprofile/src/main/kotlin/org/meshtastic/baselineprofile/`. Both run on a device/emulator:

```shell
./gradlew :androidApp:generateGoogleReleaseBaselineProfile   # Generate the profile (commit the output)
./gradlew :androidApp:benchmarkGoogleReleaseBaselineProfile  # Quantify the cold-start win
```

The generated profile is merged into `androidApp/src/googleRelease/generated/baselineProfiles/` and packaged into release builds via `androidx.profileinstaller`.

> ℹ️ **Note:** The journey covers cold start only (launch → first frame), because CI has no paired node. Post-connection screens (node list, map, message thread) aren't yet AOT-compiled.

Extending the journey past cold start needs a fake transport or a connected node wired into the harness.

## Test organization

```text
feature/my-feature/src/
├── commonTest/kotlin/org/meshtastic/feature/myfeature/
│   ├── MyBusinessLogicTest.kt
│   └── MyModelTest.kt
└── jvmTest/kotlin/org/meshtastic/feature/myfeature/
    └── MyDesktopSpecificTest.kt
```

## Testing guidelines

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

## Running tests

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

## CI integration

Tests run automatically on:
- Pull request creation/update
- Push to `main`
- Pre-release validation

Single-runner jobs in `reusable-check.yml` run on `ubuntu-26.04` with JDK 25 and Gradle caching. Two jobs use a matrix: `test-shards` splits into `shard-core`, `shard-feature` and `shard-app`, and `build-desktop` runs across macOS, Windows and Linux, still pinned to `ubuntu-24.04`/`-arm`. Flatpak verification is its own workflow, not a job here. The ARM (`ubuntu-26.04-arm`) and container-backed `ubuntu-slim` runners carry the lightweight utility workflows — see `.skills/testing-ci/SKILL.md` for the four-tier rule, which still quotes the older labels.
