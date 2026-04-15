# Build-Logic Convention Patterns & Guidelines

Quick reference for maintaining and extending the build-logic convention system.

## Core Principles

1. **DRY (Don't Repeat Yourself)**: Extract common configuration into functions
2. **Clarity Over Cleverness**: Explicit intent in `build.gradle.kts` files matters
3. **Single Responsibility**: Each convention plugin has one clear purpose
4. **Test-Driven**: Configuration changes must pass `spotlessCheck`, `detekt`, and tests

## Convention Plugin Architecture

```
build-logic/
├── convention/
│   ├── src/main/kotlin/
│   │   ├── KmpFeatureConventionPlugin.kt          # KMP feature modules (composes library + compose + koin + common deps)
│   │   ├── KmpLibraryConventionPlugin.kt          # KMP modules: core libraries
│   │   ├── KmpLibraryComposeConventionPlugin.kt   # KMP Compose Multiplatform setup
│   │   ├── KmpJvmAndroidConventionPlugin.kt       # Opt-in jvmAndroidMain hierarchy for Android + desktop JVM
│   │   ├── AndroidApplicationConventionPlugin.kt   # Main app
│   │   ├── AndroidLibraryConventionPlugin.kt       # Android-only libraries
│   │   ├── AndroidApplicationComposeConventionPlugin.kt
│   │   ├── AndroidLibraryComposeConventionPlugin.kt
│   │   ├── org/meshtastic/buildlogic/
│   │   │   ├── KotlinAndroid.kt                 # Base Kotlin/Android config
│   │   │   ├── AndroidCompose.kt                # Compose setup
│   │   │   ├── FlavorResolution.kt              # Flavor configuration
│   │   │   ├── MeshtasticFlavor.kt              # Flavor definitions
│   │   │   ├── Detekt.kt                        # Static analysis
│   │   │   ├── Spotless.kt                      # Code formatting
│   │   │   └── ... (other config modules)
```

## How to Add a New Convention

### Example: Adding a new test framework dependency

**Current Pattern (GOOD ✅):**

If all KMP modules need a dependency, add it to `KotlinAndroid.kt::configureKmpTestDependencies()`:

```kotlin
internal fun Project.configureKmpTestDependencies() {
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.apply {
            val commonTest = findByName("commonTest") ?: return@apply
            commonTest.dependencies {
                implementation(kotlin("test"))
                // NEW: Add here once, applies to all ~15 KMP modules
                implementation(libs.library("new-test-framework"))
            }
            // ... androidHostTest setup
        }
    }
}
```

**Result:** All 15 feature and core modules automatically get the dependency ✅

### Example: Adding shared `jvmAndroidMain` code to a KMP module

**Current Pattern (GOOD ✅):**

If a KMP module needs Java/JVM APIs shared between Android and desktop JVM, apply the opt-in convention plugin instead of manually creating source sets and `dependsOn(...)` edges:

```kotlin
plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    id("meshtastic.kmp.jvm.android")
}

kotlin {
    jvm()
    android { /* ... */ }

    sourceSets {
        commonMain.dependencies { /* ... */ }
        jvmMain.dependencies { /* jvm-only additions */ }
        androidMain.dependencies { /* android-only additions */ }
    }
}
```

**Why:** The convention uses Kotlin's hierarchy template API to create `jvmAndroidMain` without the `Default Kotlin Hierarchy Template Not Applied Correctly` warning triggered by hand-written `dependsOn(...)` graphs.

### Example: Creating a new KMP feature module

**Current Pattern (GOOD ✅):**

Use `meshtastic.kmp.feature` for any `feature:*` module. It composes `kmp.library` + `kmp.library.compose` + `koin` and provides all the common Compose/Lifecycle/Koin/Android dependencies that every feature needs:

```kotlin
plugins {
    alias(libs.plugins.meshtastic.kmp.feature)
    // Optional: add only if this feature needs serialization
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

kotlin {
    jvm()
    android {
        namespace = "org.meshtastic.feature.yourfeature"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            // Only module-SPECIFIC deps here
            implementation(projects.core.common)
            implementation(projects.core.model)
            implementation(projects.core.ui)
        }
        androidMain.dependencies {
            // Only Android-specific extras here
        }
    }
}
```

**What the plugin provides automatically:**
- `commonMain`: `compose-multiplatform-material3`, `jetbrains-lifecycle-viewmodel-compose`, `jetbrains-lifecycle-runtime-compose`, `koin-compose-viewmodel`, `kermit`
- `androidMain`: `androidx-compose-bom` (platform), `accompanist-permissions`, `androidx-activity-compose`, `androidx-compose-material3`, `androidx-compose-ui-text`, `androidx-compose-ui-tooling-preview`
- `commonTest`: `core:testing`

**Why:** Eliminates ~15 duplicate dependency declarations per feature module (modelled after Now in Android's `AndroidFeatureImplConventionPlugin`).

### Example: Adding Android-specific test config

**Pattern:** Test options (`animationsDisabled`, `testInstrumentationRunner`, `unitTests.isReturnDefaultValues`) are centralized in `configureKotlinAndroid()` via `CommonExtension`, so they apply to both app and library modules automatically. To add new test config, update `KotlinAndroid.kt::configureKotlinAndroid()`:

```kotlin
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        testOptions {
            animationsDisabled = true
            unitTests.isReturnDefaultValues = true
            // NEW: Add shared test options here
        }
    }
}
```

## Duplication Heuristics

**When to consolidate (DRY):**
- ✅ Configuration appears in 3+ convention plugins
- ✅ The duplication changes together (same reasons to update)
- ✅ Extraction doesn't require complex type gymnastics
- ✅ Underlying Gradle extension is the same (`CommonExtension`)

**When to keep separate (Clarity):**
- ✅ Different Gradle extension types (`ApplicationExtension` vs `LibraryExtension`)
- ✅ Plugin intent is explicit in `build.gradle.kts` usage
- ✅ Duplication is small (<50 lines) and stable
- ✅ Future divergence between app/library handling is plausible

**Examples in codebase:**

| Duplication | Status | Reasoning |
|-------------|--------|-----------|
| `AndroidApplicationComposeConventionPlugin` ≈ `AndroidLibraryComposeConventionPlugin` | **Kept Separate** | Different extension types; small duplication; explicit intent |
| `AndroidApplicationFlavorsConventionPlugin` ≈ `AndroidLibraryFlavorsConventionPlugin` | **Kept Separate** | Different extension types; small duplication; explicit intent |
| `configureKmpTestDependencies()` (7 modules) | **Consolidated** | Large duplication; single source of truth; all KMP modules benefit |
| `jvmAndroidMain` hierarchy setup (4 modules) | **Consolidated** | Shared KMP hierarchy pattern; avoids manual `dependsOn(...)` edges and hierarchy warnings |
| `PUBLISHED_MODULES` set (4 usages) | **Consolidated** | Was repeated as `listOf(...)` in 4 places; now a single `setOf(...)` constant in `KotlinAndroid.kt` |
| `SHARED_COMPILER_ARGS` list (2 code paths) | **Consolidated** | Eliminates duplicated `-opt-in` flags between KMP target compilations and `KotlinCompile` task configuration |

## Testing Convention Changes

After modifying a convention plugin, verify:

```bash
# 1. Code quality
./gradlew spotlessCheck detekt

# 2. Compilation
./gradlew assembleDebug assembleRelease

# 3. Tests
./gradlew test                          # All unit tests
./gradlew :feature:messaging:jvmTest    # Feature module tests
./gradlew :feature:node:testAndroidHostTest # Android host tests
```

## Documentation Requirements

When you add/modify a convention:

1. **Add Kotlin docs** to the function:
   ```kotlin
   /**
    * Configure test dependencies for KMP modules.
    *
    * Automatically applies kotlin("test") to:
    * - commonTest source set (all targets)
    * - androidHostTest source set (Android-only)
    *
    * Usage: Called automatically by KmpLibraryConventionPlugin
    */
   internal fun Project.configureKmpTestDependencies() { ... }
   ```

2. **Update AGENTS.md** if convention affects developers
3. **Update this guide** if pattern changes

## Performance Tips

- **Configuration-time:** Convention logic runs during Gradle configuration (0.5-2s)
- **Build-time:** No impact (conventions don't execute tasks)
- **Optimization focus:** Minimize `extensions.configure()` blocks (lazy evaluation is preferred)

### Good ✅
```kotlin
extensions.configure<KotlinMultiplatformExtension> {
    // Single block for all source set configuration
    sourceSets.apply {
        commonTest.dependencies { /* ... */ }
        androidHostTest?.dependencies { /* ... */ }
    }
}
```

### Avoid ❌
```kotlin
// Multiple blocks - slower configuration
extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.getByName("commonTest").dependencies { /* ... */ }
}
extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.getByName("androidHostTest").dependencies { /* ... */ }
}
```

## Common Pitfalls

### ❌ **Mistake: Adding dependencies in the wrong place**
```kotlin
// WRONG: Adds to ALL modules, not just KMP
extensions.configure<Project> {
    dependencies { add("implementation", ...) } // Global!
}

// RIGHT: Scoped to specific source set/module type
commonTest.dependencies { implementation(...) }
```

### ❌ **Mistake: Extension type mismatch**
```kotlin
// WRONG: LibraryExtension isn't a subtype of ApplicationExtension
extensions.configure<ApplicationExtension> {
    // Won't apply to library modules
}

// RIGHT: Use CommonExtension or specific types
extensions.configure<CommonExtension> {
    // Applies to both
}
```

### ❌ **Mistake: Side effects during configuration**
```kotlin
// WRONG: Eager task configuration at plugin-apply time
tasks.withType<Test> {
    // Can realize tasks too early
}

// RIGHT: Lazy, configuration-cache-friendly wiring
tasks.withType<Test>().configureEach {
    // Applies to existing and future tasks lazily
}
```

## Related Files

- `AGENTS.md` - Development guidelines (Section 3.B testing, Section 4.A build protocol)
- `build-logic/convention/build.gradle.kts` - Convention plugin build config

