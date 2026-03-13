# Build-Logic Optimization Analysis

## Identified Issues & Solutions

### 1. **Identical Compose Plugins** (HIGH PRIORITY)
**Problem:** `AndroidApplicationComposeConventionPlugin` and `AndroidLibraryComposeConventionPlugin` are identical.

**Current State:**
- Both apply the same plugins and call `configureAndroidCompose()`
- Only difference in name, which suggests copy-paste

**Solution:** Create a shared `BaseAndroidComposeConventionPlugin` or consolidate logic into `KmpLibraryComposeConventionPlugin`

---

### 2. **Duplicated Flavor Configuration** (MEDIUM PRIORITY)
**Problem:** `AndroidApplicationFlavorsConventionPlugin` and `AndroidLibraryFlavorsConventionPlugin` are nearly identical.

**Current State:**
```kotlin
// ApplicationFlavors
extensions.configure<ApplicationExtension> { configureFlavors(this) }

// LibraryFlavors
extensions.configure<LibraryExtension> { configureFlavors(this) }
```

**Solution:** Both `ApplicationExtension` and `LibraryExtension` are subtypes of `CommonExtension`. Create a base function that works with `CommonExtension`.

---

### 3. **Duplicate Common Android Configuration** (MEDIUM PRIORITY)
**Problem:** Both `AndroidApplicationConventionPlugin` and `AndroidLibraryConventionPlugin` repeat:
- Common plugin applications (lint, detekt, spotless, dokka, kover, test-retry)
- `configureKotlinAndroid()` call
- `configureTestOptions()` call
- Test instrumentation runner setup

**Current State:**
```kotlin
// Both plugins apply identical plugin lists and call same config functions
apply(plugin = "meshtastic.android.lint")
apply(plugin = "meshtastic.detekt")
apply(plugin = "meshtastic.spotless")
apply(plugin = "meshtastic.dokka")
apply(plugin = "meshtastic.kover")
apply(plugin = "org.gradle.test-retry")
configureKotlinAndroid(this)
configureTestOptions()
```

**Solution:** Extract common Android baseline configuration to a shared function.

---

### 4. **Missing Test Configuration Consolidation** (LOW PRIORITY)
**Problem:** Test-related configuration is scattered:
- `AndroidLibraryConventionPlugin`: `testOptions.animationsDisabled = true`
- `AndroidApplicationConventionPlugin`: Same
- Test instrumentation runner set in multiple places
- `configureTestOptions()` called in both, but plugin structure doesn't guarantee execution order

**Solution:** Centralize all test configuration in `configureTestOptions()` function.

---

## Implementation Priority

1. **HIGH:** Consolidate duplicate Compose plugins (saves ~75 lines)
2. **MEDIUM:** Consolidate Flavor plugins (saves ~30 lines)
3. **MEDIUM:** Extract shared Android base config (saves ~50 lines)
4. **LOW:** Verify test configuration centralization (audit `configureTestOptions()`)

## Impact

- **Total lines of code reduced:** ~155 lines
- **Maintainability:** ↑↑ (single source of truth)
- **Risk of inconsistency:** ↓↓ (less duplication)
- **Future changes:** Easier (one place to update)

