# Build Convention: Test Dependencies for KMP Modules

## Summary

We've centralized test dependency configuration for Kotlin Multiplatform (KMP) modules by creating a new build convention plugin function. This eliminates code duplication across all feature and core modules.

## Changes Made

### 1. **New Convention Function** (`build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/KotlinAndroid.kt`)

Added `configureKmpTestDependencies()` function that automatically configures test dependencies for all KMP modules:

```kotlin
internal fun Project.configureKmpTestDependencies() {
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.apply {
            val commonTest = findByName("commonTest") ?: return@apply
            commonTest.dependencies {
                implementation(kotlin("test"))
            }

            // Configure androidHostTest if it exists
            val androidHostTest = findByName("androidHostTest")
            androidHostTest?.dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```

**Benefits:**
- Single source of truth for test framework dependencies
- Automatically applied to all KMP modules using `meshtastic.kmp.library`
- Reduces build.gradle.kts boilerplate across 7+ feature modules

### 2. **Plugin Integration** (`build-logic/convention/src/main/kotlin/KmpLibraryConventionPlugin.kt`)

Updated `KmpLibraryConventionPlugin` to call the new function:

```kotlin
configureKotlinMultiplatform()
configureKmpTestDependencies()  // NEW
configureAndroidMarketplaceFallback()
```

### 3. **Removed Duplicate Dependencies**

Removed manual `implementation(kotlin("test"))` declarations from:
- `feature/messaging/build.gradle.kts`
- `feature/firmware/build.gradle.kts`
- `feature/intro/build.gradle.kts`
- `feature/map/build.gradle.kts`
- `feature/node/build.gradle.kts`
- `feature/settings/build.gradle.kts`
- `feature/connections/build.gradle.kts`

Each module now only declares project-specific test dependencies:
```kotlin
commonTest.dependencies {
    implementation(projects.core.testing)
    // kotlin("test") is now added by convention!
}
```

## Impact

### Before
- 7+ feature modules each manually adding `implementation(kotlin("test"))` to `commonTest.dependencies`
- 7+ feature modules each manually adding `implementation(kotlin("test"))` to `androidHostTest` source sets
- High risk of inconsistency or missing dependencies in new modules

### After
- Single configuration in `build-logic/` applies to all KMP modules
- Guaranteed consistency across all feature modules
- Future modules automatically benefit from this convention
- Build.gradle.kts files are cleaner and more focused on module-specific dependencies

## Testing

Verified with:
```bash
./gradlew :feature:node:testAndroidHostTest :feature:settings:testAndroidHostTest
# BUILD SUCCESSFUL
```

The convention plugin automatically provides `kotlin("test")` to all commonTest and androidHostTest source sets in KMP modules.

## Future Considerations

If additional test framework dependencies are needed across all KMP modules (e.g., new assertion libraries, mocking frameworks), they can be added to `configureKmpTestDependencies()` in one place, automatically benefiting all KMP modules.

This follows the established pattern in the project for convention plugins, as seen with:
- `configureComposeCompiler()` - centralizes Compose compiler configuration
- `configureKotlinAndroid()` - centralizes Kotlin/Android base configuration
- Koin, Detekt, Spotless conventions - all follow this pattern

