---
name: design-standards
description: Apply the Meshtastic design standards on Android - brand colors, Material 3 tokens, MeshtasticIcons and accessibility - with the upstream `meshtastic/design` standards as the source of truth. Use this whenever you choose a color, icon, spacing value or contrast level, and before claiming a screen matches the design.
---

# Skill: Meshtastic Design Standards

## Description
Android-specific guidance for applying the Meshtastic design standards. All visual rules, color palettes, accessibility requirements, and cross-platform conventions live upstream.

> **Source of truth:** [`meshtastic/design/standards/`](https://github.com/meshtastic/design/tree/master/standards)
> That index names the current version; open it from there for the full spec (colors, M3 mapping, accessibility, units/locale, agent checklist). Don't fetch `meshtastic_design_standards_latest.md` over HTTP — it is a symlink, and GitHub returns its target's filename, 35 bytes, with HTTP 200.
> If this skill diverges from upstream, **upstream wins**.

## 1. How to Use the Standards

Before implementing any UI:
1. **Read the upstream standards** — they include a full agent implementation checklist
2. **Check the settings validation matrix** — [`meshtastic/design/standards/audits/settings-validation-matrix.md`](https://github.com/meshtastic/design/blob/master/standards/audits/settings-validation-matrix.md) has field-by-field validation rules for every config/module setting
3. Apply the Android-specific mappings below

## 2. Android / Compose Mappings

### Theme Tokens
The upstream standards define M3 role mappings (Section 8). In this codebase:
- Theme is defined in `core/ui/` — the `AppTheme` composable (`core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/theme/Theme.kt`)
- Use M3 tokens (`MaterialTheme.colorScheme.primary`, `.surface`, etc.) — never raw hex values
- Dynamic color (Android 12+) is opt-in: the theme picker's Dynamic option stores `MODE_DYNAMIC`, and `MainActivity` passes `AppTheme(dynamicColor = theme == MODE_DYNAMIC)`. The default theme follows the system with the brand scheme. The Android `dynamicColorScheme()` actual is gated by SDK ≥ S; desktop and iOS return null.

### Brand Colors → Compose
The M3 roles map to palette steps, not to the two brand colors directly (`Color.kt`, upstream Section 8):

| Standard Name | Hex | Compose Usage |
|---------------|-----|---------------|
| Primary (Neutral 800) | `#2C2D3C` | `onSurface` / `onBackground` in light mode, not `colorScheme.primary` |
| Accent (Green 500) | `#67EA94` | `colorScheme.primary` in dark mode; light `primary` is Green 700 `#2D8F52`. Never text on a light background |
| Green 600 | `#3FB86D` | Success text on light backgrounds |
| Error 500 | `#E05252` | Brand error for non-text indicators; `colorScheme.error` is Error 600 `#BA1A1A` light and Error 300 `#FFB4AB` dark |
| Link | `#5C6BC0` / `#9BA8E0` | Blue 600 light, Blue 400 dark, for clickable text |

> Raw palette defined in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/theme/CustomColors.kt` (e.g. `N800`, `G500`, `G600`, `G700`, `E500`, `B400`); the scheme values are in `Color.kt`. Reference the M3 token, not the raw `CustomColors` entry, in UI code.

### Key Rules (Android-specific)
- **Icons:** Use `MeshtasticIcons` (from `core/ui/icon/`), not `material.icons.Icons`
- **Touch targets:** 44×44dp minimum (M3 default is 48dp, which complies)
- **Typography:** Default body = 16sp. Scale with the system font size (upstream asks for up to 200% without clipping) via `MaterialTheme.typography`
- **Message bubbles:** Use `onSurface` for text color, never node identity colors

## 3. App Icons

- Launcher icon: `ic_launcher` — adaptive (`androidApp/src/main/res/mipmap-anydpi/ic_launcher.xml`) with foreground/background layers. The Play-Store listing asset is `androidApp/src/main/ic_launcher2-playstore.png`.
- When regenerating, use [Image Asset Studio](https://developer.android.com/studio/write/image-asset-studio#create-adaptive) and keep the `ic_launcher` resource name.

## 4. Settings Validation Reference

When implementing or modifying settings screens, consult the upstream validation spec:
[`settings-validation-android.md`](https://github.com/meshtastic/design/blob/master/standards/audits/data/settings-validation-android.md)

It documents every field's:
- Valid ranges and byte limits
- UI component type (stepper, picker, secure input, etc.)
- Dirty-tracking patterns
- Edge cases (e.g., BLE PIN must be exactly 6 digits, Wi-Fi SSID max 32 UTF-8 bytes)
