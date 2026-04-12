# Screenshot Testing Quick Reference

## Commands

```bash
# Update reference images (generate baselines)
./gradlew updateGoogleDebugScreenshotTest    # Google flavor
./gradlew updateFdroidDebugScreenshotTest    # F-Droid flavor
./gradlew updateScreenshotTest               # All flavors

# Validate against references (run tests)
./gradlew validateGoogleDebugScreenshotTest  # Google flavor
./gradlew validateFdroidDebugScreenshotTest  # F-Droid flavor
./gradlew validateScreenshotTest             # All flavors
```

## File Locations

```
app/src/screenshotTest/kotlin/org/meshtastic/app/preview/
  - BasicComponentPreviews.kt          ← Buttons, text, icons
  - ExtendedComponentPreviews.kt       ← Cards, inputs, dialogs, chips

app/src/screenshotTest/kotlin/org/meshtastic/app/
  - CoreComponentScreenshotTests.kt    ← Test definitions

app/build/reports/screenshotTest/preview/{variant}/
  - index.html                         ← Open in browser to view results
  - diffs/                             ← Visual comparison images
  - images/                            ← Reference & actual screenshots
```

## Basic Preview Pattern

> **Important:** Previews use Android-only `@Preview` annotations and must
> live in an Android source set (e.g. `screenshotTest`), **not** in `commonMain`.

```kotlin
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class MultiPreview

@MultiPreview
@Composable
fun MyComponentPreview() {
    MeshtasticTheme(isDarkMode) {
        Surface {
            MyComponent()
        }
    }
}
```

## Basic Screenshot Test Pattern

```kotlin
import com.android.tools.screenshot.PreviewTest

class MyComponentScreenshotTests {
    @PreviewTest
    @Preview(showBackground = true)
    fun MyComponentScreenshot() {
        MyComponentPreview()
    }
}
```

## IDE Integration (Android Studio)

1. Click green gutter icon next to `@PreviewTest`
2. Select "Run 'ScreenshotTests'" to validate
3. Select "Add/Update Reference Images" to approve changes
4. View diffs in "Screenshot" tab

## Workflow

1. **Create Preview** → `core/ui/src/commonMain/kotlin/.../preview/`
2. **Create Test** → `app/src/screenshotTest/kotlin/.../`
3. **Update References** → `./gradlew updateGoogleDebugScreenshotTest`
4. **Validate Changes** → `./gradlew validateGoogleDebugScreenshotTest`
5. **Review Diff Report** → Open `app/build/reports/screenshotTest/preview/googleDebug/index.html`
6. **Approve & Commit** → `./gradlew updateGoogleDebugScreenshotTest && git add app/src/screenshotTest*/`

## Key Parameters

| Parameter | Value | Example |
|-----------|-------|---------|
| `uiMode` | `Configuration.UI_MODE_NIGHT_NO` | Light theme |
| `uiMode` | `Configuration.UI_MODE_NIGHT_YES` | Dark theme |
| `device` | `Devices.PHONE` | Phone device |
| `device` | `Devices.TABLET` | Tablet device |
| `fontScale` | `1f`, `1.2f`, `1.5f` | Font scaling |

## Troubleshooting

**Ambiguous task error?** → Specify flavor: `updateGoogleDebugScreenshotTest`

**SDK not found?** → Add to `local.properties`: `sdk.dir=/path/to/android/sdk`

**Reference images not found?** → Verify file in `app/src/screenshotTest{Variant}/reference/`

**Function name changed?** → Delete old reference images and regenerate

## Documentation

- Full guide: `docs/SCREENSHOT_TESTING.md`
- Android docs: https://developer.android.com/studio/preview/compose-screenshot-testing
