# Implementation Plan: Core Database (Room KMP Persistence)

**Branch**: `015-core-database` | **Date**: 2026-07-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/015-core-database/spec.md`
**Status**: Migrated — all implementation complete, plan reverse-engineered from existing code.

## Summary

Core Database defines the Room KMP schema, per-device database management with LRU eviction, 35 auto-migrations, 11 entities, 7 DAOs, and cross-platform database builders. The `DatabaseManager` (301 LOC) is the central piece — it manages a cache of open databases, tracks usage via DataStore, and enforces configurable limits.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Room KMP (`androidx.room3`), DataStore KMP, Okio, kotlinx.coroutines, Kermit  
**Testing**: 3 commonTest files, 2 androidDeviceTest files, 1 androidHostTest file; ~600 LOC total  
**Target Platform**: Android, Desktop (JVM), iOS  
**Constraints**: Schema must auto-migrate; `dropAllTables = false` for destructive fallback; all entities in `commonMain`  
**Scale/Scope**: 23 commonMain files (~2,800 LOC), 4 platform files (~200 LOC), 6 test files (~600 LOC)

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All entities, DAOs, and database definitions in `commonMain`. Platform-specific builders via expect/actual. |
| II. Zero Lint Tolerance | ✅ PASS | `detekt-baseline.xml` present. `@Suppress("TooManyFunctions")` on `DatabaseManager`. |
| VII. Coroutine Safety | ✅ PASS | Mutex serialization for DB switching. `limitedParallelism(4)` for `withDb()`. `CancellationException` propagated. |
| IX. Branch & Scope Hygiene | ✅ PASS | Module scoped to `org.meshtastic.core.database`. |

**Gate Result**: ✅ All applicable principles satisfied.

## Project Structure

```
core/database/src/
├── commonMain/kotlin/org/meshtastic/core/database/
│   ├── di/CoreDatabaseModule.kt
│   ├── MeshtasticDatabase.kt       # 141 LOC — schema definition, 35 auto-migrations
│   ├── DatabaseManager.kt          # 301 LOC — per-device management, LRU eviction
│   ├── DatabaseProvider.kt          # Interface
│   ├── DatabaseBuilder.kt          # expect declaration
│   ├── DatabaseConstants.kt        # Naming, limits
│   ├── Converters.kt               # Type converters
│   ├── MeshtasticDatabaseConstructor.kt
│   ├── entity/ (8 entity files)
│   └── dao/ (7 DAO files)
├── commonTest/ (3 files)
├── androidDeviceTest/ (2 files)
├── androidHostTest/ (1 file — MigrationTest)
├── androidMain/ (2 files — builder, DI)
├── jvmMain/ (1 file — builder)
├── iosMain/ (1 file — builder)
└── schemas/ (exported Room schemas for migration validation)
```

## Implementation Phases

### Phase 1 — Schema & Entities (Complete)

11 Room entities covering nodes, messages, logs, quick chat, firmware, hardware, traceroutes. Type converters for proto ↔ blob and ByteString ↔ ByteArray.

### Phase 2 — DAOs (Complete)

7 DAOs with reactive `Flow`-based queries, paging support (`PagingSourceDaoReturnTypeConverter`), and bulk operations.

### Phase 3 — Database Manager (Complete)

`DatabaseManager` (301 LOC): per-device database cache, `switchActiveDatabase()` with mutex serialization, `withDb()` with retry, LRU eviction, legacy cleanup, DataStore-backed preferences. Platform-specific `DatabaseBuilder` expect/actual.

## Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| ORM | Room KMP (`androidx.room3`) | Official Google ORM with KMP support; mature migration system |
| Per-device isolation | Separate DB files per BLE address | Prevents data cross-contamination; enables clean device removal |
| Cache strategy | In-memory `mutableMapOf` with LRU eviction | Fast access; bounded storage via configurable limit |
| DB switching | Emit new DB before closing old | Prevents "connection pool closed" races with active collectors |
| Retry on closed pool | Single retry in `withDb()` | Handles the narrow race between DB switch and in-flight queries |
| Concurrency limit | `limitedParallelism(4)` for `withDb()` | Prevents SQLite connection pool exhaustion |
| Migration strategy | Auto-migration with `exportSchema = true` | Simplest path; schema exports enable validation testing |
| Destructive fallback | `dropAllTables = false` | Preserves data in unaffected tables during emergency fallback |

## Gaps Identified

| Gap | Severity | Recommendation |
|-----|----------|----------------|
| No commonTest for `Converters` | ⚠️ Low | Add round-trip tests for proto ↔ blob, ByteString ↔ ByteArray |
| Only 2 of 7 DAOs have unit tests | ⚠️ Medium | Add tests for `MeshLogDao`, `QuickChatActionDao`, `DeviceHardwareDao`, `FirmwareReleaseDao`, `TracerouteNodePositionDao` |
| Migration test is Android-only | ⚠️ Low | Room KMP migration testing is currently Android-only; acceptable limitation |
| `DatabaseManager.close()` uses `runCatching` not `safeCatching` | ⚠️ Low | Minor constitution deviation; acceptable in cleanup paths |
| No test for concurrent `withDb()` retry behavior | ⚠️ Medium | Add test that simulates DB switch during query execution |

