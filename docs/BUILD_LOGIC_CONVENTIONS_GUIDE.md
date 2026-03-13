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
│   │   ├── KmpLibraryConventionPlugin.kt        # KMP modules: features, core
│   │   ├── KmpJvmAndroidConventionPlugin.kt     # Opt-in jvmAndroidMain hierarchy for Android + desktop JVM
│   │   ├── AndroidApplicationConventionPlugin.kt # Main app
│   │   ├── AndroidLibraryConventionPlugin.kt     # Android-only libraries
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

### Example: Adding Android-specific test config

**Pattern:** Add to `AndroidLibraryConventionPlugin.kt`:

```kotlin
extensions.configure<LibraryExtension> {
    configureKotlinAndroid(this)
    testOptions.apply {
        animationsDisabled = true
        // NEW: Android-specific test config
        unitTests.isIncludeAndroidResources = true
    }
}
```

**Alternative:** If it applies to both app and library, consider extracting a function:

```kotlin
internal fun Project.configureAndroidTestOptions() {
    extensions.configure<CommonExtension> {
        testOptions.apply {
            animationsDisabled = true
            // Shared test options
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
// WRONG: Task configuration during plugin apply (too early)
tasks.withType<Test> {
    // This runs before build.gradle.kts is parsed!
}

// RIGHT: Use afterEvaluate if needed
afterEvaluate {
    tasks.withType<Test> {
        // Runs after all configuration
    }
}
```

## Related Files

- `AGENTS.md` - Development guidelines (Section 3.B testing, Section 4.A build protocol)
- `docs/BUILD_LOGIC_OPTIMIZATIONS_COMPLETE.md` - History of optimizations
- `build-logic/convention/build.gradle.kts` - Convention plugin build config
- `.github/copilot-instructions.md` - Build & test commands


