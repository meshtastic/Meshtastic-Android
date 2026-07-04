# Data Model: KMP Module Classification & Migration Matrix

## Entity: KMP Module

Each KMP module has the following properties relevant to migration:

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Gradle module path (e.g., `:core:common`) |
| `convention_plugin` | Enum | `meshtastic.kmp.library` or `meshtastic.kmp.feature` |
| `namespace` | String? | Android namespace; null = auto-derived by convention |
| `namespace_override_needed` | Boolean | True if auto-derived namespace doesn't match desired |
| `android_resources` | Enum | `disabled` (default), `enabled`, `enabled_with_prefix` |
| `host_test` | Enum | `none`, `empty`, `with_resources` |
| `device_test` | Boolean | Has `withDeviceTest {}` block |
| `min_sdk_override` | Int? | Non-null only if module overrides default minSdk |
| `extra_jvm_target` | Boolean | Has explicit `jvm()` call (redundant with convention) |
| `jvm_android_source_set` | Boolean | Uses `meshtastic.kmp.jvm.android` plugin |
| `tier` | Enum | `simple`, `standard`, `special` |

## Module Migration Matrix

### Tier 1: Minimal — androidLibrary block becomes empty or removed (6 modules)

Modules where convention handles everything. After migration, the `kotlin {}` block needs no `androidLibrary {}` at all (namespace auto-derived, resources disabled by convention, no tests).

| Module | Current `android {}` content | After migration |
|--------|------------------------------|-----------------|
| `core:di` | namespace, resources=false | Remove `androidLibrary {}` entirely |
| `core:nfc` | namespace, resources=false | Remove `androidLibrary {}` entirely |
| `core:ui` | namespace, resources=false | Remove `androidLibrary {}` entirely |
| `core:navigation` | namespace only | Remove `androidLibrary {}` entirely |
| `feature:settings` | namespace, resources=false | Remove `androidLibrary {}` entirely |
| `feature:messaging` | namespace, resources=false | Remove `androidLibrary {}` entirely |

### Tier 2: Standard — only withHostTest remains (18 modules)

Modules that opt into host tests. The `androidLibrary {}` block only contains `withHostTest {}`.

| Module | withHostTest config | After migration |
|--------|-------------------|-----------------|
| `core:ble` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `core:common` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `core:data` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `core:domain` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `core:model` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `core:network` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `core:service` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `core:takserver` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `feature:connections` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `feature:firmware` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `feature:intro` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `feature:map` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `feature:node` | `{ isIncludeAndroidResources = true }` | `androidLibrary { withHostTest { isIncludeAndroidResources = true } }` |
| `core:datastore` | `{}` (empty) | `androidLibrary { withHostTest {} }` |
| `core:prefs` | `{}` (empty) | `androidLibrary { withHostTest {} }` |
| `core:repository` | `{}` (empty) | `androidLibrary { withHostTest {} }` |
| `core:testing` | `{}` (empty) | `androidLibrary { withHostTest {} }` |
| `feature:wifi-provision` | `{}` (empty) | `androidLibrary { namespace = "org.meshtastic.feature.wifiprovision"; withHostTest {} }` |

### Tier 3: Special — additional configuration (3 modules)

| Module | Special config | After migration |
|--------|---------------|-----------------|
| `core:proto` | `minSdk = 21` | `androidLibrary { minSdk = 21 }` |
| `core:database` | namespace + withHostTest + withDeviceTest | `androidLibrary { withHostTest { isIncludeAndroidResources = true }; withDeviceTest { instrumentationRunner = "..." } }` |
| `core:resources` | resources enabled + prefix + withHostTest | `androidLibrary { androidResources { enable = true; resourcePrefix = "meshtastic_" }; withHostTest { isIncludeAndroidResources = true } }` |

## Convention Plugin Enhancement

### `configureKotlinMultiplatform()` — new default

```
BEFORE (KotlinAndroid.kt:91-101):
  compileSdk = ...
  minSdk = ...
  namespace = auto-derived if null

AFTER:
  compileSdk = ...
  minSdk = ...
  namespace = auto-derived if null
  androidResources.enable = false    ← NEW DEFAULT
```

This eliminates `androidResources.enable = false` from 23 of 27 module build files. Only `core:resources` overrides this to `true`.

## Redundant `jvm()` cleanup

14 modules declare `jvm()` in their `kotlin {}` block despite the convention plugin calling `jvm()` in `configureKotlinMultiplatform()`. These redundant declarations will be removed.

Modules with redundant `jvm()`: `core:ble`, `core:common`, `core:data`, `core:database`, `core:datastore`, `core:di`, `core:domain`, `core:model`, `core:network`, `core:resources`, `core:takserver`, `core:ui`, `feature:map`, `feature:wifi-provision`.

## State Transitions

```
Module State Machine:

[LEGACY]                    [MIGRATED]                [VERIFIED]
android {} inside kotlin {} → androidLibrary {} DSL   → Build passes
                             (or removed if empty)
```

Each module transitions independently. Build verification after each batch.

## Validation Rules

1. **No `android {}` inside `kotlin {}`**: After migration, `grep -rn "kotlin {" -A5 | grep "android {"` must return zero matches in KMP modules.
2. **No redundant `jvm()`**: After cleanup, only convention plugin declares `jvm()`.
3. **Namespace correctness**: All modules must resolve to correct namespace (verified by successful build).
4. **Resources disabled by default**: Only `core:resources` has `androidResources.enable = true`.
5. **Build verification**: `assembleDebug`, `:desktop:packageUberJarForCurrentOS`, `allTests`, and `DESKTOP_ONLY` mode all pass.
