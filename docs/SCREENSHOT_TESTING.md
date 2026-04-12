# Multiplatform Preview Screenshot Testing Guide

This guide explains how to use Compose Preview Screenshot Testing in the Meshtastic-Android project for CMP (Compose Multiplatform) UI components.

## Overview

Screenshot testing automatically validates UI appearance across different device configurations and themes by comparing generated screenshots against approved reference images. This prevents visual regressions when code changes.

**Important**: Compose Screenshot Testing is Android-only. Desktop and iOS use CMP but require separate testing strategies (future implementation).

## Quick Start

### 1. Write a Composable Preview

Create previews in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/preview/`:

```kotlin
package org.meshtastic.core.ui.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@androidx.compose.ui.tooling.preview.Preview(name = "Light", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
annotation class MultiPreview

@MultiPreview
@Composable
fun MyComponentPreview() {
    MeshtasticTheme(isSystemInDarkTheme()) {
        Surface {
            MyComponent()
        }
    }
}
```

### 2. Create a Screenshot Test

Create tests in `app/src/screenshotTest/kotlin/org/meshtastic/app/`:

```kotlin
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

class MyComponentScreenshotTests {
    @PreviewTest
    @Preview(showBackground = true)
    fun MyComponentScreenshot() {
        MyComponentPreview()
    }
}
```

### 3. Generate Reference Images

Generate baseline images for your previews:

```bash
# Generate for all variants
./gradlew updateScreenshotTest

# Generate for specific flavor
./gradlew updateGoogleDebugScreenshotTest
./gradlew updateFdroidDebugScreenshotTest
```

Reference images are stored in: `app/src/screenshotTest{Variant}/reference/`

### 4. Run Validation Tests

Compare current UI against reference images:

```bash
# Validate all variants
./gradlew validateScreenshotTest

# Validate specific flavor
./gradlew validateGoogleDebugScreenshotTest
./gradlew validateFdroidDebugScreenshotTest
```

View results: `app/build/reports/screenshotTest/preview/{variant}/index.html`

## IDE Integration (Android Studio Otter 3 Feature Drop Canary 4+)

Modern Android Studio versions provide IDE integration:

1. **Gutter Icons**: Green icons appear next to `@PreviewTest` functions
2. **Generate References**: Click gutter icon → "Add/Update Reference Images"
3. **Run Tests**: Click gutter icon → "Run 'ScreenshotTests'"
4. **View Diffs**: Compare Reference, Actual, and Diff images side-by-side in the **Screenshot** tab

## Advanced: Multi-Preview Patterns

### Theme Variations

```kotlin
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreview

@ThemePreview
@Composable
fun ComponentPreview() { /* ... */ }
```

### Device Variations

```kotlin
@Preview(name = "Phone", device = Devices.PHONE)
@Preview(name = "Tablet", device = Devices.TABLET)
@Preview(name = "Compact", device = "spec:width=320dp,height=570dp")
annotation class DevicePreview

@DevicePreview
@Composable
fun ComponentPreview() { /* ... */ }
```

### Font Scale Variations

```kotlin
@Preview(name = "100%", fontScale = 1f)
@Preview(name = "120%", fontScale = 1.2f)
@Preview(name = "150%", fontScale = 1.5f)
annotation class FontScalePreview

@FontScalePreview
@Composable
fun ComponentPreview() { /* ... */ }
```

### Combined Multi-Preview

```kotlin
@Preview(name = "Light Phone", device = Devices.PHONE)
@Preview(name = "Light Tablet", device = Devices.TABLET)
@Preview(name = "Dark Phone", device = Devices.PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dark Tablet", device = Devices.TABLET, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ComprehensivePreview

@ComprehensivePreview
@Composable
fun ComponentPreview() { /* ... */ }
```

## Workflow: Updating Previews

When UI intentionally changes:

### 1. View Diff Report

```bash
./gradlew validateGoogleDebugScreenshotTest
open app/build/reports/screenshotTest/preview/googleDebug/index.html
```

### 2. Review Visual Changes

Use IDE or HTML report to inspect:
- **Reference**: Original approved image
- **Actual**: Current UI render
- **Diff**: Highlighted changes (red overlay)
- **Attributes**: Metadata (device, theme, match %)

### 3. Approve Changes

Option A - IDE:
- Click gutter icon → "Add/Update Reference Images"
- Select previews to approve in dialog
- Click "Add"

Option B - Gradle:
```bash
./gradlew updateGoogleDebugScreenshotTest
```

### 4. Commit Changes

```bash
git add app/src/screenshotTest{Variant}/reference/
git commit -m "update: UI component design refinements"
```

## File Organization

```
app/
├── src/
│   ├── main/
│   ├── androidTest/
│   ├── screenshotTest/
│   │   └── kotlin/org/meshtastic/app/
│   │       ├── CoreComponentScreenshotTests.kt
│   │       ├── DialogScreenshotTests.kt
│   │       └── NavigationScreenshotTests.kt
│   └── screenshotTest{Variant}/
│       └── reference/
│           ├── org.meshtastic.app.CoreComponentScreenshotTests_light_da39a3ee_*.png
│           ├── org.meshtastic.app.CoreComponentScreenshotTests_dark_da39a3ee_*.png
│           └── ... (one per preview variation)
└── build/
    └── reports/
        └── screenshotTest/
            └── preview/
                └── {variant}/
                    ├── index.html          ← Open this in browser
                    ├── diffs/
                    └── images/

core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/
├── preview/
│   ├── BasicComponentPreviews.kt
│   ├── DialogPreviews.kt
│   ├── NavigationPreviews.kt
│   └── ThemePreviews.kt
└── component/
    └── (existing composables)
```

## Preview File Naming Convention

Reference images use a hash of preview parameters:

```
com.example.app.MyComponentScreenshotTests_PreviewName_hash1_hash2_0.png
```

- `hash1`: Function location hash
- `hash2`: Preview parameters hash
- `0`: Variant index (for multi-preview)

**Important**: Renaming `@PreviewTest` functions breaks image associations. You must regenerate references if renaming.

## Best Practices

### ✅ Do

- **Organize by feature**: Group related component tests
- **Test edge cases**: Empty states, error states, loading states
- **Use meaningful names**: `ButtonVariantsPreview`, not `Preview1`
- **Include theme variations**: Light and dark mode previews
- **Test locales**: Different text lengths for i18n
- **Commit references**: Include `.png` files in version control
- **Review diffs**: Always inspect visual changes before approving

### ❌ Don't

- **Test network calls**: Use @PreviewTest only for UI
- **Include dynamic data**: Use fixed test data in previews
- **Forget to commit references**: Without images, tests can't validate
- **Update references blindly**: Always review changes first
- **Test on device**: Screenshot testing generates emulated images only

## Troubleshooting

### No SDK location error

```
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable...
```

Solution: Set Android SDK path in `local.properties`:
```properties
sdk.dir=/Users/your_name/Library/Android/sdk
```

### Task is ambiguous

```
Task 'updateDebugScreenshotTest' is ambiguous...
Candidates are: 'updateFdroidDebugScreenshotTest', 'updateGoogleDebugScreenshotTest'
```

Solution: Specify the flavor:
```bash
./gradlew updateGoogleDebugScreenshotTest  # or updateFdroidDebugScreenshotTest
```

### Reference images not found

Ensure tests are run from `app/src/screenshotTest/` (not `androidTest/`):
```
app/src/screenshotTest/  ✅ Correct
app/src/androidTest/      ❌ Wrong
```

### Reference image association lost after rename

After renaming a `@PreviewTest` function, old reference images are orphaned:

Solution: Delete old references, regenerate new ones:
```bash
rm app/src/screenshotTest{Variant}/reference/old_function_name_*.png
./gradlew updateGoogleDebugScreenshotTest
```

## CI/CD Integration

### GitHub Actions Example

```yaml
- name: Generate screenshot references
  run: ./gradlew updateGoogleDebugScreenshotTest

- name: Run screenshot validation tests
  run: ./gradlew validateGoogleDebugScreenshotTest

- name: Upload test report
  if: always()
  uses: actions/upload-artifact@v3
  with:
    name: screenshot-test-report
    path: app/build/reports/screenshotTest/preview/
```

### Pre-commit Hook

```bash
#!/bin/bash
./gradlew validateScreenshotTest || {
    echo "Screenshot tests failed. Review changes before committing."
    exit 1
}
```

## Resources

- [Android Screenshot Testing Documentation](https://developer.android.com/studio/preview/compose-screenshot-testing)
- [Release Notes - alpha14](https://developer.android.com/studio/preview/compose-screenshot-testing-release-notes#alpha14)
- [Issue Tracker](https://issuetracker.google.com/issues?q=status:open+componentid:1581441)
- [Compose Preview Documentation](https://developer.android.com/develop/ui/compose/tooling/previews)

## Known Limitations

1. **Android-only**: Desktop, iOS, and other non-Android targets not supported
2. **Limited device options**: Use standard `Devices` or custom spec dimensions
3. **No custom rendering**: Uses standard Compose rendering pipeline
4. **Emulator-only**: Cannot test device-specific hardware behaviors

## Future Enhancements

- [ ] Desktop/iOS screenshot testing (custom solution)
- [ ] Baseline comparison metrics (% change detection)
- [ ] Automated visual regression detection in CI
- [ ] Performance profiling integration
- [ ] Accessibility validation (color contrast, text size)

## Questions?

For issues or questions, refer to:
- GitHub Issues: https://issuetracker.google.com/issues/new?component=192708&template=840533
- AGENTS.md: Local development guidelines
- Team Slack: #ui-development channel
