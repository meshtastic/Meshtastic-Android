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
  - **No Float formatting:** Formats like `%N$.1f` pass through unsubstituted. Pre-format in Kotlin using `NumberFormatter.format(value, decimalPlaces)` from `core:common` and pass as a string argument (`%N$s`).
  - **Percent Literals:** Use bare `%` (not `%%`) for literal percent signs in CMP-consumed strings.
- **Workflow to Add a String:**
  1. Add to `core/resources/src/commonMain/composeResources/values/strings.xml`.
  2. Use the generated `org.meshtastic.core.resources.<key>` symbol.
  3. Validate UI presentation.

## 3. Tooling & Capabilities
- **Image Loading:** Use `libs.coil` (Coil Compose) in feature modules. Configuration/Networking for Coil (`coil-network-ktor3`) happens strictly in the `app` and `desktop` host modules.
- **QR Codes:** Use `rememberQrCodePainter` from `core:ui/commonMain` powered by `qrcode-kotlin`. No ZXing or Android Bitmap APIs in shared code.

## Reference Anchors
- **Shared Strings:** `core/resources/src/commonMain/composeResources/values/strings.xml`
- **Platform abstraction contract:** `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/MapViewProvider.kt`
- **Provider wiring:** `app/src/main/kotlin/org/meshtastic/app/MainActivity.kt`
