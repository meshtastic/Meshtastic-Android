# Implementation Plan — Node List Layout

## Overview

Add a density-switching system to the existing node list in `feature/node/`. Users choose between a **Complete** layout (all fields shown) and a **Compact** layout (user-configurable field toggles with adaptive sizing). The feature is entirely `commonMain` — no platform-specific code required.

## Technical Context

| Area | Choice |
|---|---|
| Language | Kotlin 2.3+ |
| UI | Compose Multiplatform + Material 3 Adaptive |
| Navigation | JetBrains Navigation 3 typed `NavKey` routes |
| DI | Koin 4.2+ K2 compiler plugin |
| Persistence | DataStore via `core:prefs` for toggle/density state |
| Data source | Room KMP via `core:database` (read-only from layout perspective) |
| Build | Gradle Kotlin DSL + convention plugins in `build-logic/` |

## Module Impact

This feature modifies existing modules rather than creating a new one:

```text
feature/node/                          ← Primary changes
├── src/commonMain/kotlin/org/meshtastic/feature/node/
│   ├── component/
│   │   ├── NodeItem.kt                ← Existing — refactor to "Complete" role
│   │   ├── NodeItemCompact.kt         ← NEW — compact row composable
│   │   └── NodeListHelp.kt            ← NEW — help bottom sheet
│   ├── list/
│   │   ├── NodeListScreen.kt          ← Modify — density-aware delegation
│   │   ├── NodeListViewModel.kt       ← Modify — expose density + toggle state
│   │   └── NodeFilterPreferences.kt   ← Modify — add layout preferences
│   └── model/
│       └── NodeListDensity.kt         ← NEW — enum COMPLETE / COMPACT

core/prefs/                            ← Preference keys
├── src/commonMain/kotlin/org/meshtastic/core/prefs/
│   └── ui/UiPrefsImpl.kt             ← Modify — add layout DataStore keys

feature/settings/                      ← Settings UI
├── src/commonMain/kotlin/org/meshtastic/feature/settings/
│   └── app/
│       └── NodeLayoutSettings.kt      ← NEW — settings section composable

core/ui/                               ← Shared components (if any new)
├── src/commonMain/kotlin/org/meshtastic/core/ui/component/
│   └── LoRaSignalStrengthMeter.kt     ← NEW if not already present
```

## Integration Points

### Preference Keys

Add to `core:prefs` DataStore:

| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `nodeListDensity` | `String` (enum name) | `"COMPLETE"` | Active density mode |
| `shouldShowPower` | `Boolean` | `true` | Compact: battery visibility |
| `shouldShowLastHeard` | `Boolean` | `true` | Compact: last heard row |
| `lastHeardIsRelative` | `Boolean` | `false` | Compact: relative vs absolute time |
| `shouldShowLocation` | `Boolean` | `true` | Compact: distance + bearing |
| `shouldShowHops` | `Boolean` | `true` | Compact: hop count |
| `shouldShowSignal` | `Boolean` | `true` | Compact: signal strength |
| `shouldShowChannel` | `Boolean` | `true` | Compact: channel number |
| `shouldShowRole` | `Boolean` | `true` | Compact: device role icons |
| `shouldShowTelemetry` | `Boolean` | `true` | Compact: log/telemetry icons |

### Navigation

No new routes needed. The settings section is embedded within the existing `SettingsRoute.AppSettings` screen. The help sheet is a `ModalBottomSheet` triggered from `NodeListScreen`.

### Data Flow

1. User selects density in Settings → DataStore write
2. `NodeListViewModel` collects density + toggle StateFlows
3. `NodeListScreen` delegates to `NodeItem` or `NodeItemCompact` based on density
4. `NodeItemCompact` reads toggle state to conditionally render rows
5. `lineCount` derived from toggles drives adaptive circle sizing

## Design Constraints

- All UI lives in `commonMain` — layout density is not platform-specific
- Existing `NodeItem` composable is refactored to serve as the Complete layout with no toggle logic
- `NodeItemCompact` is a new composable, not a modified version of `NodeItem`
- The live preview in Settings reuses the same composables with a sample node from the database
- All strings added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` exclusively
- `getSnrColor` function must be accessible from both layout variants

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Scroll performance degrades with 200+ compact rows | Low | Use stable `key` in `LazyColumn`, avoid unnecessary recompositions via `derivedStateOf` |
| Toggle state out of sync between Settings and NodeList | Low | Both screens observe the same DataStore flows |
| Existing `NodeItem` refactor breaks current behavior | Medium | Add snapshot/screenshot tests for Complete layout before modifying |
| Live preview renders incorrectly without database nodes | Low | Show placeholder text when no nodes exist |
