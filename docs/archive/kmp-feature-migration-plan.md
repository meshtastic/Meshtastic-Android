# KMP Feature Migration Slice - Plan

**Objective:** Establish standardized patterns for migrating feature modules to full KMP + comprehensive test coverage.

**Status:** Planning

## Current State

✅ **Core Infrastructure Ready:**
- core:testing module with shared test doubles
- All feature modules have KMP structure (jvm() target)
- All features have commonMain UI (Compose Multiplatform)

❌ **Gaps to Address:**
- Incomplete commonTest coverage (only feature:messaging has bootstrap)
- Inconsistent test patterns across features
- No systematic approach for adding ViewModel tests
- Desktop module not fully integrated with all features

## Migration Phases

### Phase 1: Feature commonTest Bootstrap (THIS SLICE)
**Scope:** Establish patterns and add bootstrap tests to key features

Features to bootstrap:
1. feature:settings
2. feature:node
3. feature:intro
4. feature:firmware
5. feature:map

**What constitutes a bootstrap test:**
- ViewModel initialization test
- Simple state flow emission test
- Demonstration of using FakeNodeRepository/FakeRadioController
- Clear path for future expansion

**Effort:** Low (pattern-driven, minimal logic tests)

### Phase 2: Feature-Specific Integration Tests
**Scope:** Add domain-specific test doubles and integration scenarios

Example: feature:messaging might have:
- FakeMessageRepository
- FakeContactRepository
- Message send/receive simulation

**Effort:** Medium (requires understanding feature logic)

### Phase 3: Desktop Feature Completion
**Scope:** Wire all features fully into desktop app

Current status:
- ✅ Settings (~35 screens)
- ✅ Node (adaptive list-detail)
- ✅ Messaging (adaptive contacts)
- ❌ Map (needs implementation)
- ❌ Firmware (needs implementation)

**Effort:** Medium-High

### Phase 4: Remaining Transports
**Scope:** Complete transport layer (Serial/USB, MQTT)

Current:
- ✅ TCP (JVM)
- ❌ Serial/USB
- ❌ MQTT (KMP version)

**Effort:** High

## Standards to Establish

### 1. ViewModel Test Structure
```kotlin
// In src/commonTest/kotlin/
class MyViewModelTest {
    private val fakeRepo = FakeNodeRepository()

    private fun createViewModel(): MyViewModel {
        // Create with fakes
    }

    @Test
    fun testInitialization() = runTest {
        // Verify ViewModel initializes without errors
    }

    @Test
    fun testStateFlowEmissions() = runTest {
        // Test primary state emissions
    }
}
```

### 2. UseCase Test Structure
```kotlin
class MyUseCaseTest {
    private val fakeRadio = FakeRadioController()

    private fun createUseCase(): MyUseCase {
        // Create with fakes
    }

    @Test
    fun testHappyPath() = runTest {
        // Test normal operation
    }

    @Test
    fun testErrorHandling() = runTest {
        // Test error scenarios
    }
}
```

### 3. Feature-Specific Fakes Template
```kotlin
// In core:testing/src/commonMain if reusable
// Otherwise in feature/*/src/commonTest
class FakeMyRepository : MyRepository {
    val callHistory = mutableListOf<String>()

    override suspend fun doSomething() {
        callHistory.add("doSomething")
    }
}
```

## Files to Create

### Core:Testing Extensions
- FakeContactRepository (for feature:messaging)
- FakeMessageRepository (for feature:messaging)
- (Others as needed)

### Feature:Settings Tests
- SettingsViewModelTest.kt
- Build.gradle.kts update (commonTest block if needed)

### Feature:Node Tests
- NodeListViewModelTest.kt
- NodeDetailViewModelTest.kt

### Feature:Intro Tests
- IntroViewModelTest.kt

### Feature:Firmware Tests
- FirmwareViewModelTest.kt

### Feature:Map Tests
- MapViewModelTest.kt

## Success Criteria

✅ All feature modules have commonTest with:
- At least one ViewModel bootstrap test
- Using FakeNodeRepository or similar
- Pattern clear for future expansion

✅ All tests compile cleanly on all targets (JVM, Android)

✅ Documentation updated with examples

✅ Developer guide for adding new tests

## Next Steps After This Slice

1. Measure test coverage (current baseline)
2. Create integration test patterns
3. Add feature-specific fakes to core:testing
4. Complete Desktop feature wiring
5. Address remaining transport layers

## Estimated Effort

- Phase 1: 2-3 hours (pattern establishment + bootstrap)
- Phase 2: 4-6 hours (feature-specific integration)
- Phase 3: 6-8 hours (desktop completion)
- Phase 4: 8-12 hours (transport layer)

**Total:** ~20-30 hours for complete KMP + test coverage

---

**Status:** Ready to implement Phase 1
**Next Action:** Create SettingsViewModelTest pattern and replicate across features

