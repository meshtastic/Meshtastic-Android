<!--
 - Copyright (c) 2026 Meshtastic LLC
 -
 - This program is free software: you can redistribute it and/or modify
 - it under the terms of the GNU General Public License as published by
 - the Free Software Foundation, either version 3 of the License, or
 - (at your option) any later version.
 -->

# Navigation 3 & Material 3 Adaptive — API Alignment Audit

**Date:** 2026-03-26
**Status:** Active
**Scope:** Adoption of Navigation 3 `1.1.0-beta01` Scene APIs, transition metadata, ViewModel scoping, and Material 3 Adaptive integration.
**Supersedes:** [`navigation3-parity-2026-03.md`](navigation3-parity-2026-03.md) Alpha04 Changelog section (versions updated).

## Current Dependency Baseline

| Library | Version | Group |
|---|---|---|
| Navigation 3 UI | `1.1.0-beta01` | `org.jetbrains.androidx.navigation3:navigation3-ui` |
| Navigation Event | `1.1.0-alpha01` | `org.jetbrains.androidx.navigationevent:navigationevent-compose` |
| Lifecycle ViewModel Navigation3 | `2.11.0-alpha02` | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` |
| Material 3 Adaptive | `1.3.0-alpha06` | `org.jetbrains.compose.material3.adaptive:adaptive*` |
| Material 3 Adaptive Navigation Suite | `1.11.0-alpha05` | `org.jetbrains.compose.material3:material3-adaptive-navigation-suite` |
| Compose Multiplatform | `1.11.0-beta01` | `org.jetbrains.compose` |
| Compose Multiplatform Material 3 | `1.11.0-alpha05` | `org.jetbrains.compose.material3:material3` |

## API Audit: What's Available vs. What We Use

### 1. NavDisplay — Scene Architecture (available since `1.1.0-alpha04`, stable in `beta01`)

**Available APIs we're NOT using:**

| API | Purpose | Status in project |
|---|---|---|
| `sceneStrategies: List<SceneStrategy<T>>` | Allows NavDisplay to render multi-pane Scenes | ❌ Not used — defaulting to `SinglePaneSceneStrategy` |
| `SceneStrategy<T>` interface | Custom scene calculation from backstack entries | ❌ Not used |
| `DialogSceneStrategy` | Renders `entry<T>(metadata = dialog())` entries as overlay Dialogs | ❌ Not used — dialogs handled manually |
| `SceneDecoratorStrategy<T>` | Wraps/decorates scenes with additional UI | ❌ Not used |
| `NavEntry.metadata` | Attaches typed metadata to entries (transitions, dialog hints, Scene classification) | ❌ Not used |
| `NavDisplay.TransitionKey` / `PopTransitionKey` / `PredictivePopTransitionKey` | Per-entry custom transitions via metadata | ❌ Not used |
| `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` params | Default transition animations for NavDisplay | ❌ Not used — no transitions at all |
| `sharedTransitionScope: SharedTransitionScope?` | Shared element transitions between scenes | ❌ Not used |
| `entryDecorators: List<NavEntryDecorator<T>>` | Wraps entry content with additional behavior | ❌ Not used (defaulting to `SaveableStateHolderNavEntryDecorator`) |

**APIs we ARE using correctly:**

| API | Usage |
|---|---|
| `NavDisplay(backStack, entryProvider, modifier)` | Both `app/Main.kt` and `desktop/DesktopMainScreen.kt` |
| `rememberNavBackStack(SavedStateConfiguration, startKey)` | Backstack persistence |
| `entryProvider<NavKey> { entry<T> { ... } }` | All feature graph registrations |
| `NavigationBackHandler` from `navigationevent-compose` | Used in `AdaptiveListDetailScaffold` |

### 2. ViewModel Scoping (`lifecycle-viewmodel-navigation3` `2.11.0-alpha02`)

**Key finding:** The `ViewModelStoreNavEntryDecorator` is available and provides automatic per-entry ViewModel scoping tied to backstack lifetime. The project declares this dependency in `desktop/build.gradle.kts` but does **not** pass it as an `entryDecorator` to `NavDisplay`.

Currently, `koinViewModel()` calls inside `entry<T>` blocks use the nearest `ViewModelStoreOwner` from the composition — which is the Activity/Window level. This means:
- ViewModels are **not** automatically cleared when their entry is popped from the backstack.
- The project works around this with manual `key = "metrics-$destNum"` parameter keying.

**Opportunity:** Adding `rememberViewModelStoreNavEntryDecorator()` to `NavDisplay.entryDecorators` would give each backstack entry its own `ViewModelStoreOwner`, so `koinViewModel()` calls would be automatically scoped to the entry's lifetime.

### 3. Material 3 Adaptive — Nav3 Scene Integration

**Key finding:** The JetBrains `adaptive-navigation` artifact at `1.3.0-alpha06` does **NOT** include `MaterialListDetailSceneStrategy`. That API only exists in the Google AndroidX version (`androidx.compose.material3.adaptive:adaptive-navigation:1.3.0-alpha09+`).

This means the project **cannot** currently use the official M3 Adaptive Scene bridge through `NavDisplay(sceneStrategies = ...)`. The current approach of hosting `ListDetailPaneScaffold` inside `entry<T>` blocks (via `AdaptiveListDetailScaffold`) is the correct pattern for the JetBrains fork at this version.

**When to revisit:** Monitor the JetBrains adaptive fork for `MaterialListDetailSceneStrategy` inclusion. It will likely arrive when the JetBrains fork catches up to the AndroidX `1.3.0-alpha09+` feature set.

### 4. NavigationSuiteScaffold (`1.11.0-alpha05`)

**Status:** ✅ Adopted (2026-03-26). `MeshtasticNavigationSuite` now uses `NavigationSuiteScaffold` with `calculateFromAdaptiveInfo()` and custom `NavigationSuiteType` coercion. No further alignment needed.

## Prioritized Opportunities

### P0: Add `ViewModelStoreNavEntryDecorator` to NavDisplay (high-value, low-risk)

Gives each backstack entry its own ViewModel scope. ViewModels are automatically cleared when their entry is popped. This is the primary intended usage of `lifecycle-viewmodel-navigation3`.

**Impact:** Fixes subtle ViewModel leaks where popped entries retain their ViewModel in the Activity/Window store. Eliminates the need for manual `key = "metrics-$destNum"` ViewModel keying patterns over time.

**Files:** `app/Main.kt`, `desktop/DesktopMainScreen.kt`, `core/ui/build.gradle.kts` (dependency).

### P1: Add default NavDisplay transitions (medium-value, low-risk)

Currently there are zero animations when navigating between entries. The `NavDisplay` supports `transitionSpec`, `popTransitionSpec`, and `predictivePopTransitionSpec` parameters for default transitions (slide, fade, etc.).

**Impact:** Immediate UX improvement on both Android and Desktop with ~10 lines of code.

**Files:** `app/Main.kt`, `desktop/DesktopMainScreen.kt` (or a shared composable wrapper in `core:ui`).

### P2: Adopt `DialogSceneStrategy` for navigation-driven dialogs (medium-value, medium-risk)

Routes that render as dialogs (e.g., confirmation dialogs, share sheet) can use `entry<T>(metadata = DialogSceneStrategy.dialog()) { ... }` instead of manual `Dialog` composable wrapping. This keeps them on the backstack with proper predictive-back support.

**Impact:** Cleaner dialog lifecycle management. Currently low urgency since most dialogs use `AlertHost`.

### P3: Per-entry transition metadata (low-value until Scene adoption)

Individual entries can declare custom transitions via `entry<T>(metadata = NavDisplay.transitionSpec { ... })`. This is most useful when different route types should animate differently (e.g., detail screens slide in, settings screens fade).

**Impact:** Polish improvement. Low priority until default transitions (P1) are established.

### Deferred: Scene-based multi-pane layout

The `MaterialListDetailSceneStrategy` is not available in the JetBrains adaptive fork at `1.3.0-alpha06`. The project's `AdaptiveListDetailScaffold` wrapper is the correct approach for now. Revisit when the JetBrains fork includes the Scene bridge, or consider writing a custom `SceneStrategy` that integrates with the existing `ListDetailPaneScaffold`.

## Decision

Adopt **P0** (ViewModel scoping) and **P1** (default transitions) now. Defer P2/P3 and Scene-based multi-pane until the JetBrains adaptive fork adds `MaterialListDetailSceneStrategy`.

## References

- Navigation 3 source: `navigation3-ui` `1.1.0-beta01` (inspected from Gradle cache)
- [`NavDisplay.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/main:navigation3/navigation3-ui/src/commonMain/kotlin/androidx/navigation3/ui/NavDisplay.kt) (upstream)
- [`SceneStrategy.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/main:navigation3/navigation3-ui/src/commonMain/kotlin/androidx/navigation3/scene/SceneStrategy.kt) (upstream)
- Material 3 Adaptive JetBrains fork: `org.jetbrains.compose.material3.adaptive` `1.3.0-alpha06`
