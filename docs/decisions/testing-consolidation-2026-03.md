<!--
 - Copyright (c) 2026 Meshtastic LLC
 -
 - This program is free software: you can redistribute it and/or modify
 - it under the terms of the GNU General Public License as published by
 - the Free Software Foundation, either version 3 of the License, or
 - (at your option) any later version.
 -
 - This program is distributed in the hope that it will be useful,
 - but WITHOUT ANY WARRANTY; without even the implied warranty of
 - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 - GNU General Public License for more details.
 -
 - You should have received a copy of the GNU General Public License
 - along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -->

# Testing Consolidation: `core:testing` Module

**Date:** 2026-03-11
**Status:** Implemented
**Scope:** KMP test consolidation across all core and feature modules

## Overview

Created `core:testing` as a lightweight, reusable module for **shared test doubles, fakes, and utilities** across all Meshtastic-Android KMP modules. This consolidates testing dependencies and keeps the module dependency graph clean.

## Design Principles

### 1. Lightweight Dependencies Only
```
core:testing
├── depends on: core:model, core:repository
├── depends on: kotlin("test"), kotlinx.coroutines.test, turbine, junit
└── does NOT depend on: core:database, core:data, core:domain
```

**Rationale:** `core:database` has KSP processor dependencies that can slow builds. Isolating `core:testing` with minimal deps ensures:
- Fast compilation of test infrastructure
- No circular dependency risk
- Modules depending on `core:testing` (via `commonTest`) don't drag heavy transitive deps

### 2. No Production Code Leakage
- `:core:testing` is declared **only in `commonTest` sourceSet**, never in `commonMain`
- Test code never appears in APKs or release JARs
- Strict separation between production and test concerns

### 3. Dependency Graph
```
┌─────────────────────┐
│  core:testing       │
│  (light: model,     │
│   repository)       │
└──────────┬──────────┘
           │ (commonTest only)
      ┌────┴─────────┬───────────────┐
      ↓              ↓               ↓
 core:domain    feature:messaging  feature:node
 core:data      feature:settings   etc.
```

Heavy modules (`core:domain`, `core:data`) depend on `:core:testing` in their test sources, **not** vice versa.

## Consolidation Strategy

### What Was Unified

**Before:**
```kotlin
// Each module's build.gradle.kts had scattered test deps
commonTest.dependencies {
    implementation(libs.junit)
    implementation(libs.mockk)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.turbine)
}
```

**After:**
```kotlin
// All modules converge on single dependency
commonTest.dependencies {
    implementation(projects.core.testing)
}
// core:testing re-exports all test libraries
```

### Modules Updated
- ✅ `core:domain` — test doubles for domain logic
- ✅ `feature:messaging` — commonTest bootstrap
- ✅ `feature:settings`, `feature:node`, `feature:intro`, `feature:map`, `feature:firmware`

## What's Included

### Test Doubles (Fakes)
- **`FakeRadioController`** — No-op `RadioController` with call tracking
- **`FakeNodeRepository`** — In-memory `NodeRepository` for isolated tests
- *(Extensible)* — Add new fakes as needed

### Test Builders & Factories
- **`TestDataFactory`** — Create domain objects (nodes, users) with sensible defaults
  ```kotlin
  val node = TestDataFactory.createTestNode(num = 42)
  val nodes = TestDataFactory.createTestNodes(count = 10)
  ```

### Test Utilities
- **Flow collection helper** — `flow.toList()` for assertions

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| **Dependency Duplication** | Each module lists test libs separately | Single consolidated dependency |
| **Build Purity** | Test deps scattered across modules | One central, curated source |
| **Dependency Graph** | Risk of circular deps or conflicting versions | Clean, acyclic graph with minimal weights |
| **Reusability** | Fakes live in test sources of single module | Shared across all modules via `core:testing` |
| **Maintenance** | Updating test libs touches multiple files | Single `core:testing/build.gradle.kts` |

## Maintenance Guidelines

### Adding a New Test Double
1. Implement the interface from `core:model` or `core:repository`
2. Add call tracking for assertions (e.g., `sentPackets`, `callHistory`)
3. Provide test helpers (e.g., `setNodes()`, `clear()`)
4. Document with KDoc and example usage

### When Adding a New Module with Tests
- Add `implementation(projects.core.testing)` to its `commonTest.dependencies`
- Reuse existing fakes; create new ones only if genuinely reusable

### When Updating Repository Interfaces
- Update corresponding fakes in `:core:testing` to match new signatures
- Fakes remain no-op; don't replicate business logic

## Files & Documentation

- **`core/testing/build.gradle.kts`** — Minimal dependencies, KMP targets
- **`core/testing/README.md`** — Comprehensive usage guide with examples
- **`AGENTS.md`** — Updated with `:core:testing` description and testing rules
- **`feature/messaging/src/commonTest/`** — Bootstrap example test

## Next Steps

1. **Monitor compilation times** — Verify that isolating `core:testing` improves build speed
2. **Add more fakes as needed** — As feature modules add comprehensive tests, add fakes to `core:testing`
3. **Consider feature-specific extensions** — If a feature needs heavy, specialized test setup, keep it local; don't bloat `core:testing`
4. **Cross-module test sharing** — Enable tests across modules to reuse fakes (e.g., integration tests)

## Related Documentation

- `core/testing/README.md` — Detailed usage and API reference
- `AGENTS.md` § 3B — Testing rules and KMP purity
- `.github/copilot-instructions.md` — Build commands
- `docs/kmp-status.md` — KMP module status

