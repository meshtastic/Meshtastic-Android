# Skill: Meshtastic Design Standards

## Description
Android-specific guidance for applying the Meshtastic design standards. All visual rules, color palettes, accessibility requirements, and cross-platform conventions live upstream.

> **Source of truth:** [`meshtastic/design/standards/`](https://github.com/meshtastic/design/tree/main/standards)
> Read `meshtastic_design_standards_latest.md` for the full spec (colors, M3 mapping, accessibility, units/locale, agent checklist).
> If this skill diverges from upstream, **upstream wins**.

## 1. How to Use the Standards

Before implementing any UI:
1. **Read the upstream standards** — they include a full agent implementation checklist
2. **Check the settings validation doc** — [`meshtastic/design/validation/settings-validation-android.md`](https://github.com/meshtastic/design/blob/main/validation/settings-validation-android.md) has field-by-field validation rules for every config/module setting
3. Apply the Android-specific mappings below

## 2. Android / Compose Mappings

### Theme Tokens
The upstream standards define M3 role mappings (Section 8). In this codebase:
- Theme is defined in `core/ui/` — `MeshtasticTheme` composable
- Use M3 tokens (`MaterialTheme.colorScheme.primary`, `.surface`, etc.) — never raw hex values
- Dynamic color (Android 12+): supported in the `google` flavor via `dynamicColorScheme()`

### Brand Colors → Compose
| Standard Name | Hex | Compose Usage |
|---------------|-----|---------------|
| Primary | `#2C2D3C` | `MaterialTheme.colorScheme.primary` |
| Accent | `#67EA94` | `MaterialTheme.colorScheme.tertiary` (never as text on light bg) |
| Green 600 | `#3FB86D` | Use for success text on light backgrounds |
| Error | `#E05252` | `MaterialTheme.colorScheme.error` |
| Link | `#9BA8E0` | Blue 400 — for clickable text |

### Key Rules (Android-specific)
- **Icons:** Use `MeshtasticIcons` (from `core/ui/icon/`), not `material.icons.Icons`
- **Touch targets:** 44×44dp minimum (M3 default is 48dp — compliant)
- **Typography:** Default body = 16sp. Support Dynamic Type via `MaterialTheme.typography`
- **Message bubbles:** Use `onSurface` for text color, never node identity colors

## 3. App Icons

- Launcher icons: separate SVGs (foreground/background), 108px square, logo 58px wide/high
- Generate with [Image Asset Studio](https://developer.android.com/studio/write/image-asset-studio#create-adaptive). Name: `ic_launcher2`
- Action bar: `logo/svg/Mesh_Logo_White.svg`, 0% padding, HOLO_DARK theme, named `app_icon`

## 4. Settings Validation Reference

When implementing or modifying settings screens, consult the upstream validation spec:
[`settings-validation-android.md`](https://github.com/meshtastic/design/blob/main/validation/settings-validation-android.md)

It documents every field's:
- Valid ranges and byte limits
- UI component type (stepper, picker, secure input, etc.)
- Dirty-tracking patterns
- Edge cases (e.g., BLE PIN must be exactly 6 digits, Wi-Fi SSID max 32 UTF-8 bytes)
