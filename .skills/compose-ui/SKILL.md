# Skill: Compose Multiplatform (CMP) UI

## Description
Guidelines for building shared UI, adaptive layouts, and handling strings/resources in Meshtastic-Android. The codebase uses Material 3 Adaptive.

## 1. UI Components & Layouts
- **Material 3 / Adaptive:** Use `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)` to support Large (1200dp) and XL (1600dp) breakpoints. Investigate 3-pane "Power User" scenes using Navigation 3 Scenes and draggable dividers for desktop/tablets.
- **Dialogs & Alerts:** Use centralized components like `AlertHost(alertManager)` from `core:ui/commonMain`. Do NOT trigger alerts inline or duplicate alert logic. Use `SharedDialogs(uiViewModel)` for general popups.
- **Placeholders:** Use `PlaceholderScreen(name)` from `core:ui/commonMain` for unimplemented desktop/JVM features.
- **Theme Picker:** Use `ThemePickerDialog` from `feature:settings/commonMain`.
- **Platform Implementations:** Inject platform-specific behavior (e.g., Map providers) via `CompositionLocal` from the `app` or `desktop` shells. Do not tightly couple Google Maps/osmdroid dependencies to `commonMain`.

## 2. Strings & Resources
- **Multiplatform Resources:** MUST use `core:resources` (e.g., `stringResource(Res.string.your_key)`). Never use hardcoded strings.
- **ViewModels/Coroutines:** Use the asynchronous `getStringSuspend(Res.string.your_key)`. NEVER use blocking `getString()` in a coroutine context.
- **Formatting Constraints:** CMP `stringResource` only supports `%N$s` (string) and `%N$d` (integer).
  - **No Float formatting:** Formats like `%N$.1f` pass through unsubstituted. Pre-format in Kotlin using `NumberFormatter.format(value, decimalPlaces)` from `core:common` and pass as a string argument (`%N$s`):
    ```kotlin
    val formatted = NumberFormatter.format(batteryLevel, 1) // "73.5"
    stringResource(Res.string.battery_percent, formatted)   // uses %1$s
    ```
  - **Percent Literals:** Use bare `%` (not `%%`) for literal percent signs in CMP-consumed strings.

### String Formatting Decision Tree
Choose the right tool for the job:

| Scenario | Tool | Example |
|----------|------|---------|
| **Metric display** (temp, voltage, %, signal) | `MetricFormatter.*` | `MetricFormatter.temperature(25.0f, isFahrenheit)` → `"77.0°F"` |
| **Simple number + unit** | `NumberFormatter` + interpolation | `"${NumberFormatter.format(val, 1)} dB"` |
| **Localized template from strings.xml** | `stringResource(Res.string.key, preFormattedArgs)` | `stringResource(Res.string.battery, formatted)` |
| **Non-composable template** (notifications, plain functions) | `formatString(template, args)` | `formatString(template, label, value)` |
| **Hex formatting** | `formatString` | `formatString("!%08x", nodeNum)` |
| **Date/time** | `DateFormatter` | `DateFormatter.format(instant)` |

**Rules:**
1. **NEVER use `%.Nf` in strings.xml** — CMP cannot substitute them. Use `%N$s` and pre-format floats.
2. **Prefer `MetricFormatter`** over scattered `formatString("%.1f°C", temp)` calls.
3. **`formatString` (expect/actual)** is still needed for: hex formats, multi-arg templates fetched at runtime, and chart axis formatters. It works on both JVM and iOS.
4. **`NumberFormatter`** always uses `.` as decimal separator — intentional for mesh networking precision.

- **Workflow to Add a String:**
  1. Add to `core/resources/src/commonMain/composeResources/values/strings.xml`.
  2. Use the generated `org.meshtastic.core.resources.<key>` symbol.
  3. Validate UI presentation.

## 3. Tooling & Capabilities
- **Image Loading:** Use `libs.coil` (Coil Compose) in feature modules. Configuration/Networking for Coil (`coil-network-ktor3`) happens strictly in the `app` and `desktop` host modules.
- **QR Codes:** Use `rememberQrCodePainter` from `core:ui/commonMain` powered by `qrcode-kotlin`. No ZXing or Android Bitmap APIs in shared code.

## 4. Compose Previews
- **Preview in commonMain:** CMP 1.11+ supports `@Preview` in `commonMain` via `compose-multiplatform-ui-tooling-preview`. Place preview functions alongside their composables.
- **Import:** Use `androidx.compose.ui.tooling.preview.Preview`. The JetBrains-prefixed import (`org.jetbrains.compose.ui.tooling.preview.Preview`) is deprecated.

## 5. Dialog & State Patterns
- **Dialog State Preservation:** Use `rememberSaveable` for dialog state (search queries, selected tabs, expanded flags) to preserve across configuration changes. Boolean and String types are auto-saveable — no custom `Saver` needed.

## Reference Anchors
- **Shared Strings:** `core/resources/src/commonMain/composeResources/values/strings.xml`
- **Platform abstraction contract:** `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/MapViewProvider.kt`
- **Provider wiring:** `app/src/main/kotlin/org/meshtastic/app/MainActivity.kt`
