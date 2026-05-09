# Quickstart — Node List Layout

## Purpose

This guide helps a Meshtastic-Android contributor bootstrap, navigate, test, and debug the Node List Layout feature.

## Prerequisites

- **JDK 21**
- **Android SDK** installed and `ANDROID_HOME` available to Gradle
- **Git submodule initialized** for `core/proto`
- A working `local.properties` file (copy from `secrets.defaults.properties` if needed)

## Workspace Bootstrap

```bash
git submodule update --init
[ -f local.properties ] || cp secrets.defaults.properties local.properties
```

## Feature Access Path

Once implemented, the feature is accessible at:

- **Settings UI**: Settings > App Settings > Node Layout
- **Node List**: The Nodes tab renders rows based on the selected density

No new navigation routes or deep links are required.

## Key Files

| File | Purpose |
|------|---------|
| `feature/node/src/commonMain/.../model/NodeListDensity.kt` | `COMPLETE` / `COMPACT` enum |
| `feature/node/src/commonMain/.../component/NodeItem.kt` | Complete row composable (existing, refactored) |
| `feature/node/src/commonMain/.../component/NodeItemCompact.kt` | Compact row composable (new) |
| `feature/node/src/commonMain/.../component/NodeListHelp.kt` | Help bottom sheet (new) |
| `feature/node/src/commonMain/.../list/NodeListScreen.kt` | Density-aware list delegation |
| `feature/node/src/commonMain/.../list/NodeListViewModel.kt` | Exposes density + toggle state |
| `feature/node/src/commonMain/.../list/NodeFilterPreferences.kt` | Layout preference integration |
| `core/prefs/src/commonMain/.../ui/UiPrefsImpl.kt` | DataStore preference keys |
| `feature/settings/src/commonMain/.../NodeLayoutSettings.kt` | Settings section UI (new) |
| `core/resources/src/commonMain/composeResources/values/strings.xml` | Toggle labels, help text |

## Test Commands

### Run feature module tests (KMP)

```bash
./gradlew :feature:node:allTests
./gradlew :feature:settings:allTests
```

### Run core prefs tests

```bash
./gradlew :core:prefs:allTests
```

### Full verification

```bash
./gradlew spotlessApply detekt assembleDebug test allTests
```

### Compile-only check (fast)

```bash
./gradlew :feature:node:compileKotlinJvm :feature:settings:compileKotlinJvm
```

## Development Workflow

1. **Start with preferences** — add DataStore keys in `UiPrefsImpl.kt` and verify defaults with a unit test.
2. **Build the compact composable** — create `NodeItemCompact.kt` with hardcoded toggles first, then wire to DataStore.
3. **Wire the list** — modify `NodeListScreen.kt` to switch between `NodeItem` and `NodeItemCompact`.
4. **Build settings UI** — create `NodeLayoutSettings.kt` with the picker, toggles, and live preview.
5. **Add the help sheet** — create `NodeListHelp.kt` with signal strength documentation.
6. **Test** — run `allTests` for both `feature:node` and `feature:settings`.

## Debugging Tips

- **Toggle not persisting**: Check that the DataStore key string matches `NodeListLayoutPreferences` enum value exactly.
- **Chip size wrong**: Verify `lineCount` derivation — it counts *toggle state*, not *data presence*.
- **Live preview empty**: The preview requires at least one node in the Room database. Connect to a radio or use test fixtures.
- **LazyColumn jank**: Ensure stable `key` parameters are set on `LazyColumn` items. Profile with Layout Inspector if needed.

## Logging

This feature does not require custom logging beyond standard Compose recomposition debugging. Use Android Studio Layout Inspector to diagnose rendering issues.
