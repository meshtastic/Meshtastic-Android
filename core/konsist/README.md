# `:core:konsist`

## Overview

The `:core:konsist` module is the **architecture-guard** module. It holds no production code — only [Konsist](https://docs.konsist.lemonappdev.com/) tests that scan every module's Kotlin source from disk and assert the repo's KMP boundary rules from `AGENTS.md`.

Konsist is JVM-only, so the tests live in `jvmTest` (they cannot go in `commonTest`).

## What It Checks

Each test class scans the whole project from disk (`Konsist.scopeFromProject()`), skipping agent worktrees nested under `.claude/`, so one class covers every module in the repo:

| Test | Rule |
|---|---|
| `CommonMainFrameworkBoundaryTest` | No `android.*` or `java.*` imports in any `commonMain`. Shared code uses the KMP equivalents (Okio, atomicfu, `Mutex`, …). |
| `ModuleBoundaryTest` | No core module imports a feature, and no feature imports another feature outside an allowlist. |
| `ExpectDeclarationTest` | `expect` classes and objects are exactly an allowlisted set. |
| `CoroutineScopeConstructionTest` | Production code constructs a `CoroutineScope` only at allowlisted sites. |
| `CoreDataManagerSeamTest` | In `core:data`, `manager` and the storage packages (`repository`, `datasource`) never name each other. |
| `CancellationExceptionSourceTest` | No source names `kotlin.coroutines.cancellation.CancellationException`; use the `kotlinx.coroutines` one. |
| `KotlinTimeSourceTest` | No source uses kotlinx-datetime's `Instant` or `Clock`; use `kotlin.time`. |
| `MeasurementSystemSourceTest` | `DisplayConfig.DisplayUnits` never reaches display code, and OS unit resolution is read only inside the units provider. |
| `TemperatureFormattingTest` | A temperature is never formatted with a hand-written `%f` degree template. |
| `BleAddressLoggingTest` | A BLE address never reaches log text, exception text or a log tag without `anonymize`. |

Every class also asserts its scan matched files, so a scope that empties out fails rather than passing vacuously.

## Running

```shell
./gradlew :core:konsist:allTests
```

The tests execute on the JVM target only (`:core:konsist:jvmTest`). They run in CI as part of the standard `allTests` baseline gate, so a regression anywhere in the repo fails this module's tests.
