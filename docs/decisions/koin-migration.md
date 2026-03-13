# Decision: Hilt → Koin Migration

> Date: 2026-02-20 to 2026-03-09 | Status: **Complete**

## Context

Hilt (Dagger) was the strongest remaining barrier to KMP adoption — it requires Android-specific annotation processing and can't run in `commonMain`.

## Decision

Migrated to **Koin 4.2.0-RC1** with the **K2 Compiler Plugin** (`io.insert-koin.compiler.plugin`) and later upgraded to **0.4.0**.

Key choices:
- `@KoinViewModel` replaces `@HiltViewModel`; `koinViewModel()` replaces `hiltViewModel()`
- `@Module` + `@ComponentScan` in `commonMain` modules (valid 2026 KMP pattern)
- `@KoinWorker` replaces `@HiltWorker` for WorkManager
- `@InjectedParam` replaces `@Assisted` for factory patterns
- Root graph assembly centralized in `AppKoinModule`; shared modules expose annotated definitions
- **Koin 0.4.0 A1 Compile Safety Disabled:** Meshtastic heavily utilizes dependency inversion across KMP modules (e.g., interfaces defined in `core:repository` are implemented in `core:data`). Koin 0.4.0's per-module A1 validation strictly enforces that all dependencies must be explicitly provided or included locally, breaking this clean architecture. We have globally disabled A1 `compileSafety` in `KoinConventionPlugin` to properly rely on Koin's A3 full-graph validation at the composition root (`startKoin`).

## Gotchas Discovered

1. **K2 Compiler Plugin signature collision:** Multiple `@Single` providers with identical JVM signatures in the same `@Module` cause `ClassCastException`. Fix: split into separate `@Module` classes.
2. **Circular dependencies:** `Lazy<T>` injection can still `StackOverflowError` if `Lazy` is accessed too early (e.g., in `init` coroutine). Fix: pass dependencies as function parameters instead.
3. **Robolectric `KoinApplicationAlreadyStartedException`:** Call `stopKoin()` in `onTerminate`.

## Consequences

- Hilt completely removed
- All 23 KMP modules can contain Koin-annotated definitions
- Desktop bootstraps its own `DesktopKoinModule` with stubs + real implementations
- 11 passthrough Android ViewModel wrappers eliminated

## Archive

Full migration plan: [`archive/koin-migration-plan.md`](../archive/koin-migration-plan.md)
