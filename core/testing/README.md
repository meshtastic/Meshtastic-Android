/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

# `:core:testing` — Shared Test Doubles and Utilities

## Purpose

The `:core:testing` module provides lightweight, reusable test doubles (fakes, builders, factories) and testing utilities for **all** KMP modules. This module **consolidates testing dependencies** into a single, well-controlled location to:

- **Reduce duplication**: Shared fakes (e.g., `FakeNodeRepository`, `FakeRadioController`) used across multiple modules.
- **Keep dependency graph clean**: All test doubles and libraries are defined once; modules depend on `:core:testing` instead of scattered test deps.
- **Enable KMP-wide test patterns**: Every module (`commonTest`, `androidUnitTest`, JVM tests) can reuse the same fakes.
- **Maintain purity**: Core business logic modules (e.g., `core:domain`, `core:data`) depend on `:core:testing` via `commonTest`, avoiding test-code leakage into production.

## Dependency Strategy

```
┌─────────────────────────────────────┐
│    core:testing                     │
│  (only deps: core:model,            │
│   core:repository, test libs)       │
└──────────────┬──────────────────────┘
               ↑
               │ (commonTest dependency)
        ┌──────┴─────────────┬────────────────────┐
        │                    │                    │
   core:domain          feature:messaging    feature:node
   core:data            feature:settings     feature:firmware
   (etc.)               (etc.)
```

### Key Design Rules

1. **`:core:testing` has NO dependencies on heavy modules**: It only depends on:
   - `core:model` — Domain types (Node, User, etc.)
   - `core:repository` — Interfaces (NodeRepository, etc.)
   - Test libraries (`kotlin("test")`, `mockk`, `kotlinx.coroutines.test`, `turbine`, `junit`)

2. **No circular dependencies**: Modules that depend on `:core:testing` (in `commonTest`) cannot be dependencies of `:core:testing` itself.

3. **`:core:testing` is NOT part of the app bundle**: It's declared in `commonTest` sourceSet only, so it never appears in release APKs or final JARs.

## What's Included

### Test Doubles (Fakes)

#### `FakeRadioController`
A no-op implementation of `RadioController` for unit tests. Tracks method calls and state changes.

```kotlin
val radioController = FakeRadioController()
radioController.setConnectionState(ConnectionState.Connected)
assertEquals(1, radioController.sentPackets.size)
```

#### `FakeNodeRepository`
An in-memory implementation of `NodeRepository` for isolated testing.

```kotlin
val nodeRepo = FakeNodeRepository()
nodeRepo.setNodes(TestDataFactory.createTestNodes(5))
assertEquals(5, nodeRepo.nodeDBbyNum.value.size)
```

### Test Builders & Factories

#### `TestDataFactory`
Factory methods for creating domain objects with sensible defaults.

```kotlin
val node = TestDataFactory.createTestNode(num = 42, longName = "Alice")
val nodes = TestDataFactory.createTestNodes(10)
```

### Test Utilities

#### Flow collection helper
```kotlin
val emissions = flow { emit(1); emit(2) }.toList()
assertEquals(listOf(1, 2), emissions)
```

## Usage Examples

### Testing a ViewModel (in `feature:messaging/src/commonTest`)

```kotlin
class MessageViewModelTest {
    private val nodeRepository = FakeNodeRepository()

    @Test
    fun testLoadsNodesCorrectly() = runTest {
        nodeRepository.setNodes(TestDataFactory.createTestNodes(3))
        val viewModel = createViewModel(nodeRepository)
        assertEquals(3, viewModel.nodeCount.value)
    }
}
```

### Testing a UseCase (in `core:domain/src/commonTest`)

```kotlin
class SendMessageUseCaseTest {
    private val radioController = FakeRadioController()

    @Test
    fun testSendsPacket() = runTest {
        val useCase = SendMessageUseCase(radioController)
        useCase.sendMessage(testPacket)
        assertEquals(1, radioController.sentPackets.size)
    }
}
```

## Adding New Test Doubles

When adding a new fake to `:core:testing`:

1. **Implement the interface** from `core:model` or `core:repository`.
2. **Track side effects** (e.g., `sentPackets`, `calledMethods`) for test assertions.
3. **Provide test helpers** (e.g., `setNodes()`, `clear()`) to manipulate state.
4. **Document with examples** in the class KDoc.

Example:

```kotlin
/**
 * A test double for [SomeRepository].
 */
class FakeSomeRepository : SomeRepository {
    val callHistory = mutableListOf<String>()

    override suspend fun doSomething(value: String) {
        callHistory.add(value)
    }

    // Test helpers
    fun getCallCount() = callHistory.size
    fun clear() = callHistory.clear()
}
```

## Dependency Maintenance

### When adding a new module:
- If it has `commonTest` tests, add `implementation(projects.core.testing)` to its `commonTest.dependencies`.
- Do NOT add heavy modules (e.g., `core:database`) to `:core:testing`'s dependencies.

### When a test needs a mock:
- Check `:core:testing` first for an existing fake.
- If none exists, consider adding it there (if it's reusable) vs. using `mockk()` inline.

### When updating interfaces:
- Update corresponding fakes in `:core:testing` to match new method signatures.
- Keep fakes no-op; don't replicate business logic.

## Files

```
core/testing/
├── build.gradle.kts              # Lightweight, minimal dependencies
├── README.md                      # This file
└── src/commonMain/kotlin/org/meshtastic/core/testing/
    ├── FakeRadioController.kt     # RadioController test double
    ├── FakeNodeRepository.kt      # NodeRepository test double
    └── TestDataFactory.kt         # Builders and factories
```

## See Also

- `AGENTS.md` §3B: KMP platform purity guidelines (relevant for test code).
- `docs/kmp-status.md`: KMP module status and targets.
- `.github/copilot-instructions.md`: Build and test commands.

