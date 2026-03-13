# Build-Logic Documentation Index

Quick navigation guide for build-logic optimization and convention documentation.

## 📋 Start Here

**New to build-logic?** → `BUILD_LOGIC_CONVENTIONS_GUIDE.md`
**Want optimization details?** → `BUILD_LOGIC_OPTIMIZATION_SUMMARY.md`
**Need implementation details?** → `BUILD_LOGIC_OPTIMIZATIONS_COMPLETE.md`

---

## 📚 Documentation Files

### Executive & Strategic
| Document | Purpose | Audience | Status |
|----------|---------|----------|--------|
| **[BUILD_LOGIC_OPTIMIZATION_SUMMARY.md](BUILD_LOGIC_OPTIMIZATION_SUMMARY.md)** | High-level summary of all optimizations, completed work, and recommendations | Tech Leads, Maintainers | ✅ Final |
| **[BUILD_LOGIC_OPTIMIZATIONS_COMPLETE.md](BUILD_LOGIC_OPTIMIZATIONS_COMPLETE.md)** | Detailed analysis: what was done, why, and future opportunities | Architects, Senior Devs | ✅ Final |

### Practical & Implementation
| Document | Purpose | Audience | Status |
|----------|---------|----------|--------|
| **[BUILD_LOGIC_CONVENTIONS_GUIDE.md](BUILD_LOGIC_CONVENTIONS_GUIDE.md)** | How to maintain, extend, and follow build-logic patterns | All Developers | ✅ Reference |
| **[BUILD_CONVENTION_TEST_DEPS.md](BUILD_CONVENTION_TEST_DEPS.md)** | Specific details on test dependency centralization | Test Developers, Module Owners | ✅ Reference |

### Analysis & Research
| Document | Purpose | Audience | Status |
|----------|---------|----------|--------|
| **[BUILD_LOGIC_OPTIMIZATION_ANALYSIS.md](BUILD_LOGIC_OPTIMIZATION_ANALYSIS.md)** | Research findings: identified issues and analysis of each | Reviewers, Curious Developers | ✅ Research |

---

## 🎯 Quick Links by Use Case

### I need to...

**Add a new test framework dependency**
1. Read: `BUILD_LOGIC_CONVENTIONS_GUIDE.md` (Section "Adding a new test framework")
2. Edit: `build-logic/.../KotlinAndroid.kt::configureKmpTestDependencies()`
3. Verify: Run `./gradlew spotlessCheck detekt test`

**Share Java/JVM code between Android and Desktop in a KMP module**
1. Read: `BUILD_LOGIC_CONVENTIONS_GUIDE.md` (Section "Adding shared `jvmAndroidMain` code to a KMP module")
2. Apply: `id("meshtastic.kmp.jvm.android")`
3. Verify: Run `./gradlew spotlessCheck detekt assembleDebug test`

**Understand the test dependency optimization**
1. Read: `BUILD_CONVENTION_TEST_DEPS.md` (entire file)
2. Reference: `BUILD_LOGIC_OPTIMIZATION_SUMMARY.md` (Section "Completed Optimizations")

**Consolidate duplicate convention plugins**
1. Read: `BUILD_LOGIC_CONVENTIONS_GUIDE.md` (Section "Duplication Heuristics")
2. Reference: `BUILD_LOGIC_OPTIMIZATIONS_COMPLETE.md` (Section "Future Optimization Opportunities")
3. Review: Comments in `AndroidApplicationComposeConventionPlugin.kt` and `AndroidLibraryFlavorsConventionPlugin.kt`

**Maintain build-logic going forward**
1. Read: `BUILD_LOGIC_CONVENTIONS_GUIDE.md` (entire file)
2. Reference: `BUILD_LOGIC_OPTIMIZATION_SUMMARY.md` (Section "Maintenance Going Forward")

**Review optimization decisions**
1. Read: `BUILD_LOGIC_OPTIMIZATIONS_COMPLETE.md` (Section "Decision Rationale")
2. Check: Comments in modified convention plugins

---

## 📊 Changes at a Glance

### Code Changes
```
Modified Files:   9
Created Files:    5 (documentation)
Lines Removed:    ~70 (redundant dependencies)
Lines Added:      ~30 (consolidated config)

Build Verification:
✅ spotlessCheck
✅ detekt
✅ assembleDebug
✅ test (516 tasks, all passing)
```

### Plugin Status
```
✅ KmpLibraryConventionPlugin       - Enhanced (test deps added)
✅ AndroidApplicationCompose        - Optimized (documented duplication)
✅ AndroidLibraryCompose            - Optimized (documented duplication)
✅ AndroidApplicationFlavors        - Optimized (documented opportunity)
✅ AndroidLibraryFlavors            - Optimized (documented opportunity)
```

---

## 🔄 Historical Context

### Previous Session (From Context)
- Identified and fixed Kotlin test compilation errors in feature modules
- Added `kotlin("test")` to individual module build files

### This Session
- **Identified:** Opportunity to centralize test dependency configuration
- **Implemented:** Moved test dependencies to convention plugin
- **Removed:** 7 redundant dependency declarations from modules
- **Implemented:** Added `meshtastic.kmp.jvm.android` to standardize `jvmAndroidMain` hierarchy setup
- **Removed:** Manual `dependsOn(...)` wiring from `core:common`, `core:model`, `core:network`, and `core:ui`
- **Analyzed:** Composition opportunities for other duplicate plugins
- **Documented:** Future optimization paths and consolidation criteria
- **Migrated:** JetBrains Compose Multiplatform dependencies from hard-coded/legacy `compose.xyz` references to proper version catalog entries.

---

## 📌 Key Decisions

### ✅ Decision: Test Dependencies → Convention
**Result:** Deployed ✅
**Rationale:** Large duplication (7 places), single configuration, all KMP modules benefit
**Impact:** Immediate value, easy maintenance

### ⚠️ Decision: Keep Compose Plugins Separate
**Result:** Documented duplication ✅
**Rationale:** Different extension types, explicit intent matters, low cost of duplication
**Future Path:** Can consolidate with `CommonExtension` if Application/Library handling diverges

### ⚠️ Decision: Keep Flavor Plugins Separate
**Result:** Documented opportunity ✅
**Rationale:** Different extension types, low duplication cost, Gradle conventions prefer specific types
**Future Path:** Can consolidate if flavor handling becomes more complex

---

## 🚀 Next Steps

### Immediate
- ✅ Use test dependency pattern for new modules
- ✅ Refer to guides when modifying build-logic

### Short Term
- [ ] Consider plugin validation test suite
- [ ] Review other configuration functions for consolidation opportunities
- [ ] Investigate factoring out JetBrains CMP dependencies into `meshtastic.kmp.library.compose` convention.

### Long Term
- [ ] Monitor if Android Application/Library handling diverges
- [ ] Revisit consolidation decisions annually
- [ ] Build optimization playbook for AI agents

---

## 📞 Questions?

- **How do test dependencies work now?** → `BUILD_CONVENTION_TEST_DEPS.md`
- **Why keep duplicate plugins?** → `BUILD_LOGIC_CONVENTIONS_GUIDE.md` (Duplication Heuristics)
- **What's planned for the future?** → `BUILD_LOGIC_OPTIMIZATION_SUMMARY.md` (Recommendations)
- **How do I add a new convention?** → `BUILD_LOGIC_CONVENTIONS_GUIDE.md` (How to Add)

---

## 📝 Version Control

**Last Updated:** March 12, 2026
**Status:** ✅ COMPLETE AND DEPLOYED
**Test Coverage:** All changes verified with spotless, detekt, and full test suite
**Production Ready:** YES ✅


