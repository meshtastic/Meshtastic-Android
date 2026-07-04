# Quickstart: KMP Recommended Project Structure Migration

## Prerequisites

- JDK 21 installed
- `ANDROID_HOME` set
- Proto submodule initialized: `git submodule update --init`
- `local.properties` exists: `[ -f local.properties ] || cp secrets.defaults.properties local.properties`
- Clean build baseline: `./gradlew assembleDebug` passes before starting

## Migration Steps

### Step 1: Harden convention plugin defaults

**File**: `build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/KotlinAndroid.kt`

In `configureKotlinMultiplatform()`, add `androidResources.enable = false` as default inside the `pluginManager.withPlugin` block:

```kotlin
pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
    extensions.findByType<KotlinMultiplatformAndroidLibraryTarget>()?.apply {
        compileSdk = configProperties.getProperty("COMPILE_SDK").toInt()
        minSdk = configProperties.getProperty("MIN_SDK").toInt()
        androidResources.enable = false  // ← NEW: Default for all KMP modules
        if (namespace == null) {
            val pkg = this@configureKotlinMultiplatform.path.removePrefix(":").replace(":", ".")
            namespace = "org.meshtastic.$pkg"
        }
    }
}
```

**Verify**: `./gradlew assembleDebug` — should pass (no behavior change yet, modules still override).

### Step 2: Migrate Tier 1 modules (minimal — 6 modules)

For each module, change `android {}` to `androidLibrary {}` inside `kotlin {}`, removing properties now handled by convention.

**Pattern — before**:
```kotlin
kotlin {
    jvm()  // ← remove if present (convention handles it)

    android {
        namespace = "org.meshtastic.core.di"      // ← auto-derived, remove
        androidResources.enable = false             // ← convention default, remove
    }
    // ...
}
```

**Pattern — after**:
```kotlin
kotlin {
    // android target configured by convention plugin (namespace auto-derived, resources disabled)
    sourceSets { /* ... */ }
}
```

**Modules**: `core:di`, `core:nfc`, `core:ui`, `core:navigation`, `feature:messaging`, `feature:settings`

**Verify after each module**: `./gradlew :<module>:assembleDebug :<module>:allTests`
**Verify after batch**: `./gradlew assembleDebug allTests`

### Step 3: Migrate Tier 2 modules (withHostTest — 18 modules)

**Pattern — before**:
```kotlin
kotlin {
    jvm()

    android {
        namespace = "org.meshtastic.core.data"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }
    // ...
}
```

**Pattern — after**:
```kotlin
kotlin {
    androidLibrary {
        withHostTest { isIncludeAndroidResources = true }
    }
    // ...
}
```

**Special case — `feature:wifi-provision`** (namespace mismatch):
```kotlin
kotlin {
    androidLibrary {
        namespace = "org.meshtastic.feature.wifiprovision"
        withHostTest {}
    }
    // ...
}
```

**Verify after batch**: `./gradlew assembleDebug :desktop:packageUberJarForCurrentOS allTests`

### Step 4: Migrate Tier 3 modules (special — 3 modules)

#### `core:proto`:
```kotlin
kotlin {
    androidLibrary {
        minSdk = 21  // ATAK compatibility override
    }
    // ...
}
```

#### `core:database`:
```kotlin
kotlin {
    androidLibrary {
        withHostTest { isIncludeAndroidResources = true }
        withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }
    // ...
}
```

#### `core:resources`:
```kotlin
kotlin {
    androidLibrary {
        androidResources {
            enable = true  // Override convention default
            resourcePrefix = "meshtastic_"
        }
        withHostTest { isIncludeAndroidResources = true }
    }
    // ...
}
```

**Verify**: `./gradlew assembleDebug :desktop:packageUberJarForCurrentOS allTests`

### Step 5: Full verification

```bash
# Full Android build
./gradlew assembleDebug

# Full Desktop build
./gradlew :desktop:packageUberJarForCurrentOS

# All tests
./gradlew allTests

# Desktop-only mode (no Android SDK)
DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS

# Lint and formatting
./gradlew spotlessApply spotlessCheck detekt

# Verify no legacy android {} blocks remain in KMP modules
grep -rn "android {" core/*/build.gradle.kts feature/*/build.gradle.kts | grep -v "widget\|api\|barcode\|androidLibrary\|androidResources\|androidMain\|androidHostTest\|androidDeviceTest\|androidRuntimeClasspath"
# Expected: zero matches
```

### Step 6: Validate success criteria

- [ ] SC-001: Zero `android {}` blocks inside `kotlin {}` in KMP modules
- [ ] SC-002: `assembleDebug` and `:desktop:packageUberJarForCurrentOS` pass
- [ ] SC-003: `allTests` passes with zero regressions
- [ ] SC-004: `DESKTOP_ONLY` build succeeds
- [ ] SC-005: Clean `assembleDebug` time within 5% of baseline
- [ ] SC-006: New module can apply `meshtastic.kmp.library` with no manual `android {}` block
- [ ] SC-007: `configureKotlinMultiplatform()` uses only `KotlinMultiplatformAndroidLibraryTarget` API

## Rollback Plan

If any step produces build failures that cannot be resolved:

**Per-module**: `git checkout -- <module>/build.gradle.kts` to revert a single module migration.

**Per-tier**:
- Tier 1: `git checkout -- core/di/build.gradle.kts core/nfc/build.gradle.kts core/ui/build.gradle.kts core/navigation/build.gradle.kts feature/settings/build.gradle.kts feature/messaging/build.gradle.kts`
- Tier 2: `git checkout -- core/{ble,common,data,domain,model,network,service,takserver,datastore,prefs,repository,testing}/build.gradle.kts feature/{connections,firmware,intro,map,node,wifi-provision}/build.gradle.kts`
- Tier 3: `git checkout -- core/{proto,database,resources}/build.gradle.kts`

**Full rollback**: `git stash` or `git checkout -- .` to revert all changes.

Convention plugin change (Step 1) is backward-compatible — existing explicit `androidResources.enable = false` in modules is a no-op override.
