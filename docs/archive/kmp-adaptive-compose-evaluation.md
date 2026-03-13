# KMP Material 3 Adaptive Compose — Evaluation

> Date: 2026-03-10
>
> This evaluation assesses the availability and readiness of Compose Material 3 Adaptive libraries for Kotlin Multiplatform, specifically for enabling shared list-detail layouts (nodes, messaging) across Android and Desktop.

## Executive Summary

**Material 3 Adaptive is available as a multiplatform library** via JetBrains forks, with desktop and iOS targets. Version `1.3.0-alpha05` is built against the exact same CMP and Navigation 3 versions the project already uses. This unblocks moving `ListDetailPaneScaffold`-based screens into `commonMain` and wiring real adaptive layouts on desktop — no more placeholder screens for nodes and messaging.

## Current State in the Project

### What the project uses today

| API | File | Source Set | Maven Coordinates |
|---|---|---|---|
| `ListDetailPaneScaffold` | `app/.../AdaptiveNodeListScreen.kt` | `app` (Android-only) | `androidx.compose.material3.adaptive:adaptive-layout:1.2.0` |
| `ListDetailPaneScaffold` | `feature/messaging/.../AdaptiveContactsScreen.kt` | `androidMain` | `androidx.compose.material3.adaptive:adaptive-layout:1.2.0` |
| `NavigationSuiteScaffold` | `app/.../Main.kt` | `app` (Android-only) | `androidx.compose.material3:material3-adaptive-navigation-suite` (BOM) |
| `currentWindowAdaptiveInfo` | `app/.../Main.kt` | `app` (Android-only) | `androidx.compose.material3.adaptive:adaptive:1.2.0` |

### Imports used across the codebase

```
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
```

### Where the dependencies are declared

- `gradle/libs.versions.toml`: `androidxComposeMaterial3Adaptive = "1.2.0"` → AndroidX (Android-only)
- `app/build.gradle.kts`: `androidMain` only
- `feature/messaging/build.gradle.kts`: `androidMain` only

## JetBrains Multiplatform Adaptive Artifacts

JetBrains publishes multiplatform forks of Material 3 Adaptive with full target coverage:

### Artifact inventory

| JetBrains Artifact | AndroidX Equivalent | Desktop | iOS | Status |
|---|---|---|---|---|
| `org.jetbrains.compose.material3.adaptive:adaptive` | `androidx.compose.material3.adaptive:adaptive` | ✅ | ✅ | Published on Maven Central |
| `org.jetbrains.compose.material3.adaptive:adaptive-layout` | `androidx.compose.material3.adaptive:adaptive-layout` | ✅ | ✅ | Published on Maven Central |
| `org.jetbrains.compose.material3.adaptive:adaptive-navigation` | `androidx.compose.material3.adaptive:adaptive-navigation` | ✅ | ✅ | Published on Maven Central |
| `org.jetbrains.compose.material3.adaptive:adaptive-navigation3` | _(new, no AndroidX equivalent)_ | ✅ | ✅ | Published on Maven Central (1.3.0+ only) |
| `org.jetbrains.compose.material3:material3-adaptive-navigation-suite` | `androidx.compose.material3:material3-adaptive-navigation-suite` | ✅ | ✅ | Bundled with CMP `material3` at `composeMaterial3Version` |

### Package names are identical

The JetBrains forks use the same `androidx.compose.material3.adaptive.*` package names as AndroidX. **No import changes are needed** — only the Maven coordinates in `build.gradle.kts` change.

### Version compatibility matrix

| JB Adaptive Version | CMP Version | Navigation 3 | Kotlin | Match? |
|---|---|---|---|---|
| **`1.3.0-alpha05`** | **`1.11.0-alpha03`** | **`1.1.0-alpha03`** | `2.2.20` | ✅ **Exact match** on CMP + Nav3 |
| `1.2.0` | `1.9.0` | — | `2.1.21` | ❌ Too old for this project |
| `1.1.2` | `1.8.x` | — | — | ❌ Too old |

**`1.3.0-alpha05` is the correct version** — it is built against `foundation:1.11.0-alpha03` and `navigation3-ui:1.1.0-alpha03`, both of which are the exact versions the project uses today.

### `adaptive-navigation3` — new Navigation 3 integration

The `adaptive-navigation3` artifact is a brand-new addition at `1.3.0`. It provides Navigation 3-aware adaptive scaffolding. Its POM shows dependencies on:
- `navigation3-ui-desktop:1.1.0-alpha03` ✅
- `navigationevent-compose-desktop:1.0.1`

This could eventually enable deeper Nav3 + adaptive integration (e.g., `ListDetailPaneScaffold` directly managing Nav3 back stacks), but it's not required for the initial migration.

## What This Enables

### Immediate opportunity: shared `ListDetailPaneScaffold`

The `ListDetailPaneScaffold` and its navigator can move into `commonMain` code. This directly enables:

1. **`AdaptiveNodeListScreen`** — currently in `app` (Android-only) — can be restructured so the scaffold pattern works cross-platform
2. **`AdaptiveContactsScreen`** — currently in `feature:messaging/androidMain` — same opportunity
3. **Desktop gets real list-detail layouts** instead of placeholder text

### Remaining Android-only blockers per file

Even with adaptive layouts available in `commonMain`, each file has additional Android-specific code that must be handled separately:

| File | Android-Only APIs Used | Migration Strategy |
|---|---|---|
| `AdaptiveNodeListScreen.kt` | `BackHandler`, `LocalFocusManager` | `BackHandler` → `expect/actual`; `LocalFocusManager` is already in CMP |
| `AdaptiveContactsScreen.kt` | `BackHandler` (same pattern) | Same as above |
| `NodeListScreen.kt` | `ExperimentalMaterial3ExpressiveApi`, `animateFloatingActionButton`, `LocalContext`, `showToast` | Expressive APIs → standard M3; toast → platform callback |
| `NodeDetailScreen.kt` | `android.Manifest`, `Intent`, `ActivityResultContracts`, `tooling.preview` | Heavy Android — keep in `androidMain`, create desktop variant |
| `Main.kt` (app) | `currentWindowAdaptiveInfo`, `NavigationSuiteScaffold` | App-only, desktop already uses `NavigationRail` — no migration needed |

### `NavigationSuiteScaffold` in desktop

The desktop already uses `NavigationRail` directly (in `DesktopMainScreen.kt`). The `NavigationSuiteScaffold` from the main `material3` group is already available multiplatform via `compose.material3AdaptiveNavigationSuite` in the CMP DSL (`composeMaterial3Version = "1.9.0"`), but it's not needed — the desktop's `NavigationRail` is a deliberate design choice that works better for desktop form factors.

## Risk Assessment

| Factor | Assessment |
|---|---|
| Library stability | Alpha, but same stability tier as CMP `1.11.0-alpha03` and Nav3 `1.1.0-alpha03` already in use |
| API surface stability | `ListDetailPaneScaffold` API is stable in practice (widely adopted since AndroidX `1.0.0`) |
| Build pipeline alignment | `1.3.0-alpha05` is produced by the same JetBrains compose-multiplatform build that produces CMP `1.11.0-alpha03` |
| Breaking change risk | Low — API surface matches AndroidX; only coordinates change |
| Dependency policy alignment | Follows project rule: "alpha only behind hard abstraction seams" (adaptive is behind feature module boundaries) |

## Recommended Approach

### Phase 1 — Add JetBrains adaptive dependencies ✅ DONE

Added to `gradle/libs.versions.toml`:

```toml
jetbrains-adaptive = "1.3.0-alpha05"

jetbrains-compose-material3-adaptive = { module = "org.jetbrains.compose.material3.adaptive:adaptive", version.ref = "jetbrains-adaptive" }
jetbrains-compose-material3-adaptive-layout = { module = "org.jetbrains.compose.material3.adaptive:adaptive-layout", version.ref = "jetbrains-adaptive" }
jetbrains-compose-material3-adaptive-navigation = { module = "org.jetbrains.compose.material3.adaptive:adaptive-navigation", version.ref = "jetbrains-adaptive" }
```

Added to `desktop/build.gradle.kts`:
```kotlin
implementation(libs.jetbrains.compose.material3.adaptive)
implementation(libs.jetbrains.compose.material3.adaptive.layout)
implementation(libs.jetbrains.compose.material3.adaptive.navigation)
```

Desktop compile verified: `./gradlew :desktop:compileKotlin` — **BUILD SUCCESSFUL**.

### Phase 2 — Desktop adaptive contacts screen ✅ DONE

1. Moved `adaptive`, `adaptive-layout`, `adaptive-navigation` dependencies from `androidMain.dependencies` → `commonMain.dependencies` in `feature:messaging/build.gradle.kts` (using JetBrains coordinates, replacing AndroidX adaptive)
2. Created `desktop/.../DesktopAdaptiveContactsScreen.kt` using `ListDetailPaneScaffold` with:
   - List pane: shared `ContactItem` composable with `isActive` highlighting on selected contact
   - Detail pane: real `DesktopMessageContent` — non-paged message list with send input using shared `MessageViewModel`
3. Wired into `DesktopMessagingNavigation.kt` for `ContactsRoutes.ContactsGraph` and `ContactsRoutes.Contacts`
4. Verified: `./gradlew :desktop:compileKotlin :feature:messaging:compileKotlinJvm :app:compileFdroidDebugKotlin` — **BUILD SUCCESSFUL**

### Phase 3 — Desktop adaptive node list screen ✅ DONE

1. Added JetBrains adaptive dependencies to `feature:node/build.gradle.kts` `commonMain.dependencies`
2. Created `desktop/.../DesktopAdaptiveNodeListScreen.kt` using `ListDetailPaneScaffold` with:
   - List pane: shared `NodeItem`, `NodeFilterTextField`, `MainAppBar` composables; context menu for favorite/ignore/mute/remove; `isActive` highlighting
   - Detail pane: real `NodeDetailContent` from commonMain — shared `NodeDetailList` with identity, device actions, position, hardware, notes, admin sections
3. Wired into `DesktopNodeNavigation.kt` for `NodesRoutes.NodesGraph` and `NodesRoutes.Nodes`
4. Metrics log screens (TracerouteLog, NeighborInfoLog, HostMetricsLog) wired as real screens with `MetricsViewModel` (replacing placeholders)
5. Verified: `./gradlew :desktop:compileKotlin :feature:node:compileKotlinJvm :app:compileFdroidDebugKotlin` — **BUILD SUCCESSFUL**

### Phase 4 — Optional: evaluate `adaptive-navigation3`

The new `adaptive-navigation3` artifact may offer cleaner Nav3 integration for list-detail patterns. Evaluate once the basic adaptive migration is stable.

## Decision

**Proceed with JetBrains adaptive `1.3.0-alpha05`.**

The version alignment is perfect, the risk profile matches what the project already accepts for CMP/Nav3/lifecycle, and the payoff is significant: shared list-detail layouts for nodes and messaging across Android and Desktop.

## References

- Maven Central: [`org.jetbrains.compose.material3.adaptive:adaptive`](https://repo1.maven.org/maven2/org/jetbrains/compose/material3/adaptive/adaptive/)
- Maven Central: [`adaptive-navigation3`](https://repo1.maven.org/maven2/org/jetbrains/compose/material3/adaptive/adaptive-navigation3/)
- AndroidX source: [`ListDetailPaneScaffold.kt` in `commonMain`](https://github.com/androidx/androidx/blob/main/compose/material3/adaptive/adaptive-layout/src/commonMain/kotlin/androidx/compose/material3/adaptive/layout/ListDetailPaneScaffold.kt)
- Current project dependency: `androidxComposeMaterial3Adaptive = "1.2.0"` in `gradle/libs.versions.toml`


