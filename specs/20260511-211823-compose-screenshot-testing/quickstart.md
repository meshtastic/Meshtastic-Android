# Quickstart: Adding Screenshot Tests

**Feature**: 018-compose-screenshot-testing

This guide explains how to add a new preview composable and wire it into the screenshot test suite.

## Prerequisites

- JDK 21, `ANDROID_HOME` set, proto submodule initialized
- `android.experimental.enableScreenshotTest=true` in `gradle.properties`

## Step 1: Create a Preview Composable

Add a `@Preview` or `@PreviewLightDark` composable in your module's `commonMain`:

```kotlin
// feature/messaging/src/commonMain/kotlin/.../component/MyComponentPreviews.kt

@PreviewLightDark
@Composable
fun MyComponentPreview() {
    AppTheme {
        MyComponent(
            title = "Sample Title",
            subtitle = "Sample subtitle text",
        )
    }
}
```

**Rules**:
- Visibility: public (no modifier) — the screenshot-tests module must import it across module boundaries (`internal` does NOT work across modules)
- Theme: Always wrap in `AppTheme { ... }`
- Data: Use hardcoded synthetic values — never real user data or PII
- Dependencies: No ViewModel, DI, or network access — stateless only
- Determinism: Avoid time-dependent or random data (e.g., `Channel.getRandomKey()`, relative timestamps like "last heard X ago") — these cause flaky diffs

## Step 2: Add a Screenshot Test Wrapper

Create or update a file in the screenshot-tests module:

```kotlin
// screenshot-tests/src/screenshotTest/kotlin/org/meshtastic/screenshots/feature/MessagingScreenshotTests.kt

@PreviewTest
@PreviewLightDark
@Composable
fun MyComponentScreenshotTest() {
    MyComponentPreview()
}
```

**Rules**:
- Must have `@PreviewTest` annotation (from `com.android.tools.screenshot`)
- Must also have the same `@Preview` or `@PreviewLightDark` as the source preview
- Function can be `public` (default) — this is a test, not API surface

## Step 3: Generate Reference Images

```bash
./gradlew :screenshot-tests:updateDebugScreenshotTest
```

Reference images are saved to `screenshot-tests/src/screenshotTestDebug/reference/`.

## Step 4: Validate

```bash
./gradlew :screenshot-tests:validateDebugScreenshotTest
```

If no UI changes were made, this passes. If the rendered output differs from references, it fails and produces an HTML diff report at `screenshot-tests/build/reports/screenshotTest/preview/debug/index.html`.

## Step 5: Commit Reference Images

```bash
git add screenshot-tests/src/screenshotTestDebug/reference/
git commit -m "Add screenshot references for MyComponent"
```

Reference images must be in version control so CI can validate against them.

## Updating After UI Changes

If you intentionally change a component's appearance:

1. Run the update task to regenerate references:
   ```bash
   ./gradlew :screenshot-tests:updateDebugScreenshotTest
   ```
2. Review the updated PNGs in `screenshot-tests/src/screenshotTestDebug/reference/`
3. Commit the updated images
4. Validate:
   ```bash
   ./gradlew :screenshot-tests:validateDebugScreenshotTest
   ```

## Common Issues

**Preview not found by CST**: Ensure the preview function is public (no modifier, not `private` or `internal`) and the `screenshot-tests` module has an `implementation(project(":your:module"))` dependency.

**Theme not applied**: Wrap preview content in `AppTheme { ... }`. The theme is in `core:ui`.

**Reference image diff on CI but not locally**: Reference images should be generated on CI (Ubuntu) or with an identical JDK version. Minor font rendering differences between macOS and Linux are absorbed by the `imageDifferenceThreshold` (0.05%).

**`@PreviewTest` not resolving**: Ensure `screenshotTestImplementation(libs.screenshot.validation.api)` is in `screenshot-tests/build.gradle.kts`.

## File Naming Conventions

| File type | Location | Convention |
|-----------|----------|------------|
| Preview composable | `{module}/src/commonMain/.../component/{Name}Previews.kt` | Group related previews in `*Previews.kt` files |
| Screenshot test wrapper | `screenshot-tests/src/screenshotTest/.../feature/{Module}ScreenshotTests.kt` | One file per source module |
| Reference image | `screenshot-tests/src/screenshotTestDebug/reference/*.png` | Auto-generated names (do not rename) |
