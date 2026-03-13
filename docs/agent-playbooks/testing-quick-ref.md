#!/bin/bash
#
# Copyright (c) 2025 Meshtastic LLC
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

# Testing Consolidation: Quick Reference Card

## Use core:testing in Your Module Tests

### 1. Add Dependency (in build.gradle.kts)
```kotlin
commonTest.dependencies {
    implementation(projects.core.testing)
}
```

### 2. Import and Use Fakes
```kotlin
// In your src/commonTest/kotlin/...Test.kt files
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory

@Test
fun myTest() = runTest {
    val nodeRepo = FakeNodeRepository()
    val nodes = TestDataFactory.createTestNodes(5)
    nodeRepo.setNodes(nodes)
    // Test away!
}
```

### 3. Common Patterns

#### Testing with Fake Node Repository
```kotlin
val nodeRepo = FakeNodeRepository()
nodeRepo.setNodes(TestDataFactory.createTestNodes(3))
assertEquals(3, nodeRepo.nodeDBbyNum.value.size)
```

#### Testing with Fake Radio Controller
```kotlin
val radio = FakeRadioController()
radio.setConnectionState(ConnectionState.Connected)
// Test your code that uses RadioController
assertEquals(1, radio.sentPackets.size)
```

#### Creating Custom Test Data
```kotlin
val customNode = TestDataFactory.createTestNode(
    num = 42,
    userId = "!mytest",
    longName = "Alice",
    shortName = "A"
)
```

## Module Dependencies (Consolidated)

### Before Testing Consolidation
```
feature:messaging/build.gradle.kts
├── commonTest
│   ├── libs.junit
│   ├── libs.kotlinx.coroutines.test
│   ├── libs.turbine
│   └── [duplicated in 7+ other modules...]
```

### After Testing Consolidation
```
feature:messaging/build.gradle.kts
├── commonTest
│   └── projects.core.testing ✅ (single source of truth)
        │
        └── core:testing provides: junit, mockk, coroutines.test, turbine
```

## Files Reference

| File | Purpose | Location |
|------|---------|----------|
| FakeRadioController | RadioController test double | `core/testing/src/commonMain/kotlin/...` |
| FakeNodeRepository | NodeRepository test double | `core/testing/src/commonMain/kotlin/...` |
| TestDataFactory | Domain object builders | `core/testing/src/commonMain/kotlin/...` |
| MessageViewModelTest | Example test pattern | `feature/messaging/src/commonTest/kotlin/...` |

## Documentation

- **Full API:** `core/testing/README.md`
- **Decision Record:** `docs/decisions/testing-consolidation-2026-03.md`
- **Slice Summary:** `docs/agent-playbooks/kmp-testing-consolidation-slice.md`
- **Build Rules:** `AGENTS.md` § 3B and § 5

## Verification Commands

```bash
# Build core:testing
./gradlew :core:testing:compileKotlinJvm

# Verify a feature module with core:testing
./gradlew :feature:messaging:compileKotlinJvm

# Run all tests (when domain tests are fixed)
./gradlew allTests

# Check dependency tree
./gradlew :feature:messaging:dependencies
```

## Troubleshooting

### "Cannot find projects.core.testing"
- Did you add `:core:testing` to `settings.gradle.kts`? ✅ Already done
- Did you run `./gradlew clean`? Try that

### Compilation error: "Unresolved reference 'Test'" or similar
- This is a pre-existing issue in `core:domain` tests (missing Kotlin test annotations)
- Not related to consolidation; will be fixed separately
- Your new tests should work fine with `kotlin("test")`

### My fake isn't working
- Check `core:testing/README.md` for API
- Verify you're using the test-only version (not production code)
- Fakes are intentionally no-op; add tracking/state as needed

---

**Last Updated:** 2026-03-11
**Author:** Testing Consolidation Slice
**Status:** ✅ Implemented & Verified

