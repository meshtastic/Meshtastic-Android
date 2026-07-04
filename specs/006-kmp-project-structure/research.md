# Research: KMP Recommended Project Structure Alignment

## R1: `android {}` vs `androidLibrary {}` inside `kotlin {}`

**Decision**: Use `androidLibrary {}` as the canonical DSL accessor for the KMP Android target.

**Rationale**: The `androidLibrary {}` accessor is the documented API in the [KMP recommended structure guide](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html) and the [KMP-App-Template](https://github.com/Kotlin/KMP-App-Template). While `android {}` inside `kotlin {}` is a transitional alias that still works (used by kotlinconf-app), `androidLibrary {}` is the forward-looking name that clearly distinguishes between:
- `kotlin { androidLibrary {} }` — KMP module Android target (new plugin)
- `android {}` — top-level Android extension (legacy `com.android.library` or `com.android.application`)

Using `androidLibrary {}` eliminates ambiguity and signals the project uses the recommended patterns.

**Alternatives considered**:
- Keep `android {}` inside `kotlin {}` (works but ambiguous, transitional)
- Use programmatic API only in convention plugins (already done for compileSdk/minSdk; per-module overrides still need DSL)

## R2: What properties belong in `kotlin.androidLibrary {}` vs convention plugin

**Decision**: Convention plugin (`configureKotlinMultiplatform()`) handles:
- `compileSdk` ✅ (already done)
- `minSdk` ✅ (already done, default from config.properties)
- `namespace` auto-derivation ✅ (already done when null)
- `androidResources.enable = false` as default ← NEW (absorb into convention)

Module `build.gradle.kts` handles only overrides:
- `namespace` when it differs from auto-derived (e.g., `feature.wifiprovision` vs `feature.wifi-provision`)
- `minSdk` override (only `core:proto` with minSdk 21)
- `androidResources { enable = true; resourcePrefix = "..." }` (only `core:resources`)
- `withHostTest {}` / `withDeviceTest {}` (per-module opt-in)

**Rationale**: The convention plugin already handles compileSdk/minSdk/namespace via `KotlinMultiplatformAndroidLibraryTarget`. Adding `androidResources.enable = false` as the convention default eliminates the most common per-module boilerplate (23 of 27 modules set this).

**Alternatives considered**:
- Move all config to convention plugin, including withHostTest (rejected: test opt-in should be explicit per-module)
- Keep all config in module files (rejected: adds repetitive boilerplate across 27 modules)

## R3: Convention plugin audit — what needs changing

**Decision**: The `configureKotlinMultiplatform()` function in `KotlinAndroid.kt` is already well-structured. One enhancement needed:

1. **Add `androidResources.enable = false` as default** in the `pluginManager.withPlugin` block alongside compileSdk/minSdk/namespace. This eliminates the most common boilerplate line across 23 modules.

The `KmpLibraryConventionPlugin.kt`, `KmpFeatureConventionPlugin.kt`, and `KmpJvmAndroidConventionPlugin.kt` do NOT need changes — they compose correctly and delegate to `configureKotlinMultiplatform()`.

**Rationale**: The convention plugin chain is already correct:
- `KmpLibraryConventionPlugin` applies `com.android.kotlin.multiplatform.library` (correct plugin)
- `configureKotlinMultiplatform()` uses `KotlinMultiplatformAndroidLibraryTarget` API (correct API)
- `isDesktopOnly` guard is properly implemented
- The only gap is that individual modules repeat `androidResources.enable = false`

**Alternatives considered**:
- Rewrite convention plugins to use DSL instead of programmatic API (rejected: programmatic API via `KotlinMultiplatformAndroidLibraryTarget` is cleaner in convention plugins and already works)
- Add `withHostTest {}` to convention by default (rejected: not all modules need host tests, and some configure `isIncludeAndroidResources = true`)

## R4: Module-by-module android {} block classification

**Decision**: Categorize all 27 modules into migration tiers based on complexity.

### Tier 1: Simple — namespace + androidResources.enable = false (6 modules)
These modules only set namespace and disable resources. After convention absorbs `androidResources.enable = false`, these only need namespace (or nothing if auto-derived matches).

| Module | namespace | Auto-derivable? |
|--------|-----------|-----------------|
| `core:di` | `org.meshtastic.core.di` | ✅ Yes |
| `core:nfc` | `org.meshtastic.core.nfc` | ✅ Yes |
| `core:ui` | `org.meshtastic.core.ui` | ✅ Yes |
| `core:navigation` | `org.meshtastic.core.navigation` | ✅ Yes |
| `feature:messaging` | `org.meshtastic.feature.messaging` | ✅ Yes |
| `feature:settings` | `org.meshtastic.feature.settings` | ✅ Yes |

### Tier 2: Namespace + resources disabled + withHostTest (18 modules)
These add `withHostTest {}` opt-in. The `androidLibrary {}` block will keep withHostTest.

| Module | withHostTest config |
|--------|-------------------|
| `core:ble` | `{ isIncludeAndroidResources = true }` |
| `core:common` | `{ isIncludeAndroidResources = true }` |
| `core:data` | `{ isIncludeAndroidResources = true }` |
| `core:domain` | `{ isIncludeAndroidResources = true }` |
| `core:model` | `{ isIncludeAndroidResources = true }` |
| `core:network` | `{ isIncludeAndroidResources = true }` |
| `core:service` | `{ isIncludeAndroidResources = true }` |
| `core:takserver` | `{ isIncludeAndroidResources = true }` |
| `feature:connections` | `{ isIncludeAndroidResources = true }` |
| `feature:firmware` | `{ isIncludeAndroidResources = true }` |
| `feature:intro` | `{ isIncludeAndroidResources = true }` |
| `feature:map` | `{ isIncludeAndroidResources = true }` |
| `feature:node` | `{ isIncludeAndroidResources = true }` |
| `core:datastore` | `{}` (empty) |
| `core:prefs` | `{}` (empty) |
| `core:repository` | `{}` (empty) |
| `core:testing` | `{}` (empty) |
| `feature:wifi-provision` | `{}` (empty) |

### Tier 3: Special cases (3 modules)
| Module | Special config |
|--------|---------------|
| `core:proto` | `minSdk = 21` override (for ATAK compatibility) |
| `core:database` | `namespace` + `withHostTest` + `withDeviceTest { instrumentationRunner }` |
| `core:resources` | `androidResources { enable = true; resourcePrefix = "meshtastic_" }` + `withHostTest` |

### Not affected (correctly Android-only)
- `core:api` — `meshtastic.android.library`, AIDL, publishing
- `core:barcode` — `meshtastic.android.library`, Android-only
- `feature:widget` — `meshtastic.android.library`, Glance widgets
- `app/` — `com.android.application`
- `desktop/` — JVM only
- `screenshot-tests/` — Android test module

## R5: DESKTOP_ONLY mode compatibility

**Decision**: No changes needed to DESKTOP_ONLY mode. The migration is purely a DSL rename.

**Rationale**: 
- `isDesktopOnly` guard in `KmpLibraryConventionPlugin` skips `com.android.kotlin.multiplatform.library` plugin application entirely
- `configureKotlinMultiplatform()` creates inert placeholder `androidMain` source set in desktop-only mode
- The `kotlin { androidLibrary {} }` DSL accessor in module build files is provided by the plugin — when the plugin isn't applied (desktop-only), the accessor doesn't exist
- The existing `android {}` accessor inside `kotlin {}` works the same way — so the rename from `android {}` to `androidLibrary {}` has identical desktop-only behavior

**Key validation**: After migration, `DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS` must succeed.

## R6: `jvm()` declaration in module build files

**Decision**: Remove redundant `jvm()` from module build files as a cleanup during migration — the convention plugin handles it. Calling `jvm()` twice is a no-op (Kotlin Gradle Plugin is idempotent for target declarations), so removing it is safe. This reduces boilerplate and makes convention ownership clear.

**Alternatives considered**: Keep `jvm()` in all modules for explicitness (rejected: inconsistent since some modules already omit it, and convention plugins are the source of truth).

## R7: Namespace auto-derivation coverage

**Decision**: Rely on convention plugin auto-derivation for most modules; only override where the derived name doesn't match.

| Module path | Auto-derived namespace | Needed namespace | Match? |
|-------------|----------------------|------------------|--------|
| `:core:ble` | `org.meshtastic.core.ble` | `org.meshtastic.core.ble` | ✅ |
| `:core:common` | `org.meshtastic.core.common` | `org.meshtastic.core.common` | ✅ |
| `:core:data` | `org.meshtastic.core.data` | `org.meshtastic.core.data` | ✅ |
| `:core:database` | `org.meshtastic.core.database` | `org.meshtastic.core.database` | ✅ |
| `:core:datastore` | `org.meshtastic.core.datastore` | `org.meshtastic.core.datastore` | ✅ |
| `:core:di` | `org.meshtastic.core.di` | `org.meshtastic.core.di` | ✅ |
| `:core:domain` | `org.meshtastic.core.domain` | `org.meshtastic.core.domain` | ✅ |
| `:core:model` | `org.meshtastic.core.model` | (none set — auto-derived) | ✅ |
| `:core:navigation` | `org.meshtastic.core.navigation` | `org.meshtastic.core.navigation` | ✅ |
| `:core:network` | `org.meshtastic.core.network` | `org.meshtastic.core.network` | ✅ |
| `:core:nfc` | `org.meshtastic.core.nfc` | `org.meshtastic.core.nfc` | ✅ |
| `:core:prefs` | `org.meshtastic.core.prefs` | `org.meshtastic.core.prefs` | ✅ |
| `:core:proto` | `org.meshtastic.core.proto` | (none set — auto-derived) | ✅ |
| `:core:repository` | `org.meshtastic.core.repository` | (none set — auto-derived) | ✅ |
| `:core:resources` | `org.meshtastic.core.resources` | (none set — auto-derived) | ✅ |
| `:core:service` | `org.meshtastic.core.service` | `org.meshtastic.core.service` | ✅ |
| `:core:takserver` | `org.meshtastic.core.takserver` | `org.meshtastic.core.takserver` | ✅ |
| `:core:testing` | `org.meshtastic.core.testing` | `org.meshtastic.core.testing` | ✅ |
| `:core:ui` | `org.meshtastic.core.ui` | `org.meshtastic.core.ui` | ✅ |
| `:feature:connections` | `org.meshtastic.feature.connections` | `org.meshtastic.feature.connections` | ✅ |
| `:feature:firmware` | `org.meshtastic.feature.firmware` | `org.meshtastic.feature.firmware` | ✅ |
| `:feature:intro` | `org.meshtastic.feature.intro` | `org.meshtastic.feature.intro` | ✅ |
| `:feature:map` | `org.meshtastic.feature.map` | `org.meshtastic.feature.map` | ✅ |
| `:feature:messaging` | `org.meshtastic.feature.messaging` | `org.meshtastic.feature.messaging` | ✅ |
| `:feature:node` | `org.meshtastic.feature.node` | `org.meshtastic.feature.node` | ✅ |
| `:feature:settings` | `org.meshtastic.feature.settings` | `org.meshtastic.feature.settings` | ✅ |
| `:feature:wifi-provision` | `org.meshtastic.feature.wifi.provision` | `org.meshtastic.feature.wifiprovision` | ❌ |

**Finding**: Only `feature:wifi-provision` has a namespace mismatch — the module path contains a hyphen which auto-derives as `org.meshtastic.feature.wifi.provision` (with dot separator) but the module currently uses `org.meshtastic.feature.wifiprovision` (no dot). This module MUST explicitly set `namespace` in its `androidLibrary {}` block.

All other modules that currently set `namespace` explicitly match the auto-derived value and can safely rely on the convention plugin's auto-derivation. However, being explicit about namespace provides documentation value and prevents silent changes if the module is moved.

**Decision**: Remove explicit `namespace` from modules where it matches auto-derivation. Keep it only for `feature:wifi-provision`. This reduces boilerplate and delegates ownership to the convention plugin.
