# Skill: Meshtastic Design Standards

## Description
Android-specific guidance for applying the Meshtastic design standards. All visual rules, color palettes, accessibility requirements, and cross-platform conventions live upstream.

> **Source of truth:** [`meshtastic/design/standards/`](https://github.com/meshtastic/design/tree/master/standards)
> Read `meshtastic_design_standards_latest.md` (a pointer to the current versioned spec — `meshtastic_design_standards_v1_4.md` at time of writing) for the full spec (colors, M3 mapping, accessibility, units/locale, agent checklist).
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
- Dynamic color (Android 12+): on by default via `dynamicColorScheme()` (`AppTheme(dynamicColor = true)` for all flavors); the Android `actual` is gated by SDK ≥ S, while desktop/iOS return null

### Brand Colors → Compose
| Standard Name | Hex | Compose Usage |
|---------------|-----|---------------|
| Primary | `#2C2D3C` | `MaterialTheme.colorScheme.primary` |
| Accent | `#67EA94` | `MaterialTheme.colorScheme.tertiary` (never as text on light bg) |
| Green 600 | `#3FB86D` | Use for success text on light backgrounds |
| Error | `#E05252` | `MaterialTheme.colorScheme.error` |
| Link | `#9BA8E0` | Blue 400 — for clickable text |

> Raw palette defined in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/theme/CustomColors.kt` (e.g. `N800`, `G500`, `G600`, `E500`, `B400`) — reference the M3 token, not the raw `CustomColors` entry, in UI code.

### Key Rules (Android-specific)
- **Icons:** Use `MeshtasticIcons` (from `core/ui/icon/`), not `material.icons.Icons`
- **Touch targets:** 44×44dp minimum (M3 default is 48dp — compliant)
- **Typography:** Default body = 16sp. Support Dynamic Type via `MaterialTheme.typography`
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
