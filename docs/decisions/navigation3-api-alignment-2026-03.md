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

**Remaining APIs we're NOT using broadly yet:**

| API | Purpose | Status in project |
|---|---|---|
| `sceneStrategies: List<SceneStrategy<T>>` | Allows NavDisplay to render multi-pane Scenes | ⚠️ Partially used — `MeshtasticNavDisplay` applies dialog/list-detail/supporting/single strategies |
| `SceneStrategy<T>` interface | Custom scene calculation from backstack entries | ❌ Not used |
| `DialogSceneStrategy` | Renders `entry<T>(metadata = dialog())` entries as overlay Dialogs | ✅ Enabled in shared host wrapper |
| `SceneDecoratorStrategy<T>` | Wraps/decorates scenes with additional UI | ❌ Not used |
| `NavEntry.metadata` | Attaches typed metadata to entries (transitions, dialog hints, Scene classification) | ❌ Not used |
| `NavDisplay.TransitionKey` / `PopTransitionKey` / `PredictivePopTransitionKey` | Per-entry custom transitions via metadata | ❌ Not used |
| `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` params | Default transition animations for NavDisplay | ⚠️ Partially used — shared forward/pop crossfade adopted; predictive-pop custom spec not yet used |
| `sharedTransitionScope: SharedTransitionScope?` | Shared element transitions between scenes | ❌ Not used |
| `entryDecorators: List<NavEntryDecorator<T>>` | Wraps entry content with additional behavior | ✅ Used via `MeshtasticNavDisplay` (`SaveableStateHolder` + `ViewModelStore`) |

**APIs we ARE using correctly:**

| API | Usage |
|---|---|
| `MeshtasticNavDisplay(...)` wrapper around `NavDisplay` | Both `app/Main.kt` and `desktop/DesktopMainScreen.kt` |
| `rememberNavBackStack(SavedStateConfiguration, startKey)` | Backstack persistence |
| `entryProvider<NavKey> { entry<T> { ... } }` | All feature graph registrations |
| `NavigationBackHandler` from `navigationevent-compose` | Used in `AdaptiveListDetailScaffold` |

### 2. ViewModel Scoping (`lifecycle-viewmodel-navigation3` `2.11.0-alpha02`)

**Current status:** Adopted. `MeshtasticNavDisplay` applies `rememberViewModelStoreNavEntryDecorator()` with `rememberSaveableStateHolderNavEntryDecorator()`, so `koinViewModel()` instances are entry-scoped and clear on pop.

### 3. Material 3 Adaptive — Nav3 Scene Integration

**Current status:** Adopted for shared host-level strategies. `MeshtasticNavDisplay` uses adaptive Navigation 3 scene strategies (`rememberListDetailSceneStrategy`, `rememberSupportingPaneSceneStrategy`) with draggable pane expansion handles, while feature-level scaffold composition remains valid for route-specific layouts.

### 4. NavigationSuiteScaffold (`1.11.0-alpha05`)

**Status:** ✅ Adopted (2026-03-26). `MeshtasticNavigationSuite` now uses `NavigationSuiteScaffold` with `calculateFromAdaptiveInfo()` and custom `NavigationSuiteType` coercion. No further alignment needed.

## Prioritized Opportunities

### P0: Add `ViewModelStoreNavEntryDecorator` to NavDisplay (high-value, low-risk)

**Status:** ✅ Adopted (2026-03-26). Each backstack entry now gets its own `ViewModelStoreOwner` via `rememberViewModelStoreNavEntryDecorator()`. ViewModels obtained via `koinViewModel()` are automatically cleared when their entry is popped. Encapsulated in `MeshtasticNavDisplay` in `core:ui/commonMain`.

**Impact:** Fixes subtle ViewModel leaks where popped entries retain their ViewModel in the Activity/Window store. Eliminates the need for manual `key = "metrics-$destNum"` ViewModel keying patterns over time.

### P1: Add default NavDisplay transitions (medium-value, low-risk)

**Status:** ✅ Adopted (2026-03-26). A shared 350 ms crossfade (`fadeIn` + `fadeOut`) is applied for both forward and pop navigation via `MeshtasticNavDisplay`. This replaces the library's platform defaults (Android: 700 ms fade; Desktop: no animation) with a faster, consistent transition.

**Impact:** Immediate UX improvement on both Android and Desktop. Desktop now has visible navigation transitions.

### P2: Adopt `DialogSceneStrategy` for navigation-driven dialogs (medium-value, medium-risk)

**Status:** ✅ Adopted (2026-03-26). `MeshtasticNavDisplay` includes `DialogSceneStrategy` in `sceneStrategies` before `SinglePaneSceneStrategy`. Feature modules can now use `entry<T>(metadata = DialogSceneStrategy.dialog()) { ... }` to render entries as overlay Dialogs with proper backstack lifecycle and predictive-back support.

**Impact:** Cleaner dialog lifecycle management available for future dialog routes. Existing dialogs via `AlertHost` are unaffected.

### Consolidation: `MeshtasticNavDisplay` shared wrapper

**Status:** ✅ Adopted (2026-03-26). A new `MeshtasticNavDisplay` composable in `core:ui/commonMain` encapsulates the standard `NavDisplay` configuration:
- Entry decorators: `rememberSaveableStateHolderNavEntryDecorator` + `rememberViewModelStoreNavEntryDecorator`
- Scene strategies: `DialogSceneStrategy` + adaptive list-detail/supporting pane strategies + `SinglePaneSceneStrategy`
- Transition specs: 350 ms crossfade (forward + pop)

Both `app/Main.kt` and `desktop/DesktopMainScreen.kt` now call `MeshtasticNavDisplay` instead of configuring `NavDisplay` directly. The `lifecycle-viewmodel-navigation3` dependency was moved from host modules to `core:ui`.

### P3: Per-entry transition metadata (low-value until Scene adoption)

Individual entries can declare custom transitions via `entry<T>(metadata = NavDisplay.transitionSpec { ... })`. This is most useful when different route types should animate differently (e.g., detail screens slide in, settings screens fade).

**Impact:** Polish improvement. Low priority until default transitions (P1) are established. Now unblocked by P1 adoption.

### Deferred: Scene-based multi-pane layout

Additional route-level Scene metadata adoption is deferred. The project now applies shared adaptive scene strategies in `MeshtasticNavDisplay`, and feature-level `AdaptiveListDetailScaffold` remains valid for route-specific layouts. Revisit custom per-route `SceneStrategy` policies when multi-pane route classification needs expand.

## Decision

~~Adopt **P0** (ViewModel scoping) and **P1** (default transitions) now. Defer P2/P3 and Scene-based multi-pane until the JetBrains adaptive fork adds `MaterialListDetailSceneStrategy`.~~

**Updated 2026-03-26:** P0, P1, and P2 adopted and consolidated into `MeshtasticNavDisplay` in `core:ui/commonMain`. P3 (per-entry transitions) is available for incremental adoption by feature modules. Scene-based multi-pane remains deferred.

## References

- Navigation 3 source: `navigation3-ui` `1.1.0-beta01` (inspected from Gradle cache)
- [`NavDisplay.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/main:navigation3/navigation3-ui/src/commonMain/kotlin/androidx/navigation3/ui/NavDisplay.kt) (upstream)
- [`SceneStrategy.kt`](https://cs.android.com/androidx/platform/frameworks/support/+/main:navigation3/navigation3-ui/src/commonMain/kotlin/androidx/navigation3/scene/SceneStrategy.kt) (upstream)
- Material 3 Adaptive JetBrains fork: `org.jetbrains.compose.material3.adaptive` `1.3.0-alpha06`
