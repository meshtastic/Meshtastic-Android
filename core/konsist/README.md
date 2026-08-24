# `:core:konsist`

## Overview

The `:core:konsist` module is the **architecture-guard** module. It holds no production code — only [Konsist](https://docs.konsist.lemonappdev.com/) tests that scan every module's Kotlin source from disk and assert the repo's KMP boundary rules from `AGENTS.md`.

Konsist is JVM-only, so the tests live in `jvmTest` (they cannot go in `commonTest`).

## What It Checks

`CommonMainFrameworkBoundaryTest` enforces the framework-bleed rule for shared code — any file in a `commonMain` source set must never depend on platform APIs:

- no `android.*` imports in `commonMain`
- no `java.*` imports in `commonMain`

Shared code must use the KMP equivalents instead (Okio, kotlinx-datetime, atomicfu, `Mutex`, …). Because Konsist scans the whole project from disk (`Konsist.scopeFromProject()`), this single test class covers every module in the repo.

## Running

```bash
./gradlew :core:konsist:allTests
```

The tests execute on the JVM target only. They run in CI as part of the standard `allTests` baseline gate, so a framework-bleed regression anywhere in the repo fails this module's tests.
