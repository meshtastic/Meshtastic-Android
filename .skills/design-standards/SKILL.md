# Skill: Meshtastic Design Standards

## Design Standards

All UI must comply with the [Meshtastic Client Design Standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md). Fetch and review this document before making any UI changes.

### Brand Colors

- **Primary/Foreground:** `#2C2D3C` (RGB 44 45 60)
- **Secondary/Background/Accent:** `#67EA94` (RGB 103 234 148)

### Extended Color Palette

#### Neutral Scale (derived from Primary `#2C2D3C`)

| Name | Hex | Usage |
|------|-----|-------|
| Neutral 950 | `#0F1017` | Darkest background |
| Neutral 900 | `#1A1B26` | Dark mode background |
| Neutral 800 | `#2C2D3C` | **Primary** — dark mode surface / light mode text |
| Neutral 700 | `#3D3E50` | Dark mode elevated surface |
| Neutral 600 | `#555668` | Dark mode secondary text |
| Neutral 500 | `#6E7082` | Placeholder text |
| Neutral 400 | `#9496A6` | Disabled / tertiary |
| Neutral 300 | `#B8BAC8` | Borders (light mode) |
| Neutral 200 | `#D5D6E0` | Dividers |
| Neutral 100 | `#ECEDF3` | Light mode surface / card |
| Neutral 50  | `#F5F6FA` | Light mode background |

#### Green Scale (derived from Accent `#67EA94`)

| Name | Hex | Usage |
|------|-----|-------|
| Green 100 | `#E5FCEE` | Success tint background |
| Green 300 | `#B5F5CE` | Light highlight |
| Green 400 | `#8FF0B2` | Hover / active accent |
| Green 500 | `#67EA94` | **Accent** — primary action / brand highlight |
| Green 600 | `#3FB86D` | Text on light backgrounds |
| Green 700 | `#2D8F52` | Strong / dark green text |

#### Semantic Colors

| Name | Hex | Usage |
|------|-----|-------|
| Info | `#5C6BC0` | Informational indicators / links |
| Info Light | `#E8EAF6` | Info tint background |
| Warning | `#E8A33E` | Caution / attention |
| Warning Light | `#FFF3E0` | Warning tint background |
| Error | `#E05252` | Errors / destructive actions |
| Error Light | `#FDEAEA` | Error tint background |

### Accessibility

All foreground/background pairings must meet WCAG AA contrast (4.5:1 minimum). Use `Green 600` (`#3FB86D`) or `Green 700` (`#2D8F52`) for green text on light backgrounds — never the raw accent `#67EA94`, which does not meet contrast requirements on white.

### Android App Icons

- Launcher icons use separate SVGs for foreground and background, 108px square, with the logo 58px wide/high.
- Regenerate with [Image Asset Studio](https://developer.android.com/studio/write/image-asset-studio#create-adaptive). Name the icon `ic_launcher2`.
- Action bar icons: import `logo/svg/Mesh_Logo_White.svg` with 0% padding, HOLO_DARK theme, named `app_icon`.
