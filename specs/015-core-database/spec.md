# Feature Specification: Core Database (Room KMP Persistence)

**Feature Branch**: `015-core-database`  
**Created**: 2026-07-27  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `core/database` module

## Summary

Core Database provides the Room KMP persistence layer for Meshtastic-Android. It defines the `MeshtasticDatabase` schema (11 entities, 7 DAOs, 35 auto-migrations from v3→v38), a `DatabaseManager` with per-device database instances and LRU eviction, per-platform `DatabaseBuilder` implementations, and type converters. The module enables per-device data isolation — each connected Meshtastic device gets its own Room database file — with configurable cache limits and automatic eviction of least-recently-used database files.

## Goals

1. **Per-device persistence** — maintain separate Room database files per Meshtastic device address for data isolation.
2. **Schema evolution** — support forward migration via Room auto-migrations across 35+ schema versions.
3. **LRU eviction** — automatically close and delete least-recently-used database files when the cache limit is exceeded.
4. **Cross-platform** — provide `DatabaseBuilder` implementations for Android, JVM/Desktop, and iOS via expect/actual.
5. **Reactive access** — expose the current database as `StateFlow<MeshtasticDatabase>` for reactive consumers.

## Non-Goals

- Business logic for reading/writing data — handled by `core/data` repositories.
- Domain model definitions — handled by `core/model`.
- Query logic beyond DAO definitions — complex queries are in the repository layer.
- Database encryption — not currently implemented.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Per-Device Database Switching (Priority: P1)

When the user connects to a different Meshtastic device, the `DatabaseManager` switches the active database to the one associated with the new device's address. A new database is created if none exists for that address.

**Why this priority**: Per-device isolation prevents data cross-contamination between devices.

**Independent Test**: Can be tested by switching addresses and verifying different databases are active.

**Acceptance Scenarios**:

1. **Given** the app connects to device with address "AA:BB:CC", **When** `switchActiveDatabase("AA:BB:CC")` is called, **Then** a database named `meshtastic_AA_BB_CC` is opened (or created).
2. **Given** the active DB is device A, **When** switching to device B, **Then** `currentDb` emits the new database and the previous is not closed synchronously (race-safe).
3. **Given** the same address is requested twice, **When** `switchActiveDatabase` is called again, **Then** it is a no-op (fast path).
4. **Given** a null/blank address, **When** `switchActiveDatabase(null)` is called, **Then** the default database is used.

---

### User Story 2 — LRU Cache Eviction (Priority: P2)

The database manager enforces a configurable cache limit. When the number of per-device databases exceeds the limit, the least-recently-used database files are closed and deleted.

**Why this priority**: Without eviction, device storage fills up as users connect to many different nodes.

**Independent Test**: Testable by creating N databases, setting limit to M < N, and verifying eviction.

**Acceptance Scenarios**:

1. **Given** the cache limit is 5 and 6 databases exist, **When** eviction runs, **Then** the least-recently-used database is closed and its file is deleted.
2. **Given** the active database would be evicted by LRU order, **When** eviction runs, **Then** the active database is protected from eviction.
3. **Given** the cache limit is changed from 5 to 3, **When** `setCacheLimit(3)` is called, **Then** eviction runs asynchronously and removes excess databases.
4. **Given** the cache limit is set to a value outside bounds, **When** clamped, **Then** it is constrained to `[MIN_CACHE_LIMIT, MAX_CACHE_LIMIT]`.

---

### User Story 3 — Schema Migration (Priority: P1)

The Room database supports forward migration from schema version 3 through 38 using auto-migrations. Special migrations handle table deletions and column removals.

**Why this priority**: Schema migration failures cause data loss. The 35-version migration chain must be reliable.

**Independent Test**: Android-only instrumented test (`MigrationTest`).

**Acceptance Scenarios**:

1. **Given** a database at schema version N (3 ≤ N < 38), **When** the app opens it, **Then** Room auto-migrates to version 38 without data loss.
2. **Given** auto-migration from v12→v13, **When** the migration runs, **Then** the legacy `NodeInfo` and `MyNodeInfo` tables are deleted.
3. **Given** auto-migration from v29→v30, **When** the migration runs, **Then** the `reply_id` column is removed from `packet`.
4. **Given** a destructive migration is required, **When** `fallbackToDestructiveMigration(dropAllTables = false)` is configured, **Then** only the affected tables are recreated.

---

### User Story 4 — Database Access via withDb() (Priority: P1)

Consumers access the active database through `DatabaseManager.withDb()`, which provides the current database instance. It tolerates connection-pool-closed races during database switching by retrying once.

**Why this priority**: `withDb()` is the single entry point for all database access. Race safety is critical.

**Independent Test**: Can be tested by simulating a database switch during a `withDb()` call.

**Acceptance Scenarios**:

1. **Given** an active database, **When** `withDb { db.nodeInfoDao().getAll() }` is called, **Then** the query executes on the current database.
2. **Given** a database switch occurs between capturing `db` and executing the query, **When** "Connection pool is closed" is thrown, **Then** `withDb` retries once with the new database instance.
3. **Given** no database is active (`_currentDb` is null), **When** `withDb` is called, **Then** it returns `null` immediately.
4. **Given** concurrent `withDb` calls, **When** executing, **Then** parallelism is limited to 4 via `limitedIo` dispatcher.

---

### Edge Cases

- What happens when deleting a database file fails (permission error)? The error is logged but does not crash; `runCatching` is used for best-effort cleanup.
- What happens when a legacy database exists after migration? `cleanupLegacyDbIfNeeded` deletes it on first switch, then marks cleanup as complete via DataStore.
- What happens when `hasDatabaseFor()` checks for a non-existent address? It checks both `dbName` and `dbName.db` file paths (platform-agnostic).
- What happens when the database directory doesn't exist? `listExistingDbNames()` returns an empty list.

## Architecture

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| `MeshtasticDatabase` | `MeshtasticDatabase.kt` (141 LOC) | Room database definition: 11 entities, 7 DAOs, 35 auto-migrations |
| `DatabaseManager` | `DatabaseManager.kt` (301 LOC) | Per-device DB management: switch, LRU eviction, withDb(), legacy cleanup |
| `DatabaseProvider` | `DatabaseProvider.kt` | Interface for database access (currentDb, withDb) |
| `DatabaseBuilder` | `DatabaseBuilder.kt` (expect/actual) | Platform-specific Room database builder |
| `DatabaseConstants` | `DatabaseConstants.kt` | DB prefix, limits, legacy name constants |
| `Converters` | `Converters.kt` | Room type converters (ByteString ↔ ByteArray, proto ↔ blob) |
| `MeshtasticDatabaseConstructor` | `MeshtasticDatabaseConstructor.kt` | Room KMP database constructor |
| `CoreDatabaseModule` | `di/CoreDatabaseModule.kt` | Koin DI module |
| **Entities** | | |
| `NodeEntity` | `entity/NodeEntity.kt` | Mesh node persistence |
| `MyNodeEntity` | `entity/MyNodeEntity.kt` | Local node identity |
| `Packet` | `entity/Packet.kt` | Message/packet persistence |
| `MeshLog` | `entity/MeshLog.kt` | Debug mesh log entries |
| `QuickChatAction` | `entity/QuickChatAction.kt` | Quick chat shortcuts |
| `FirmwareReleaseEntity` | `entity/FirmwareReleaseEntity.kt` | Cached firmware releases |
| `DeviceHardwareEntity` | `entity/DeviceHardwareEntity.kt` | Cached hardware catalog |
| `TracerouteNodePositionEntity` | `entity/TracerouteNodePositionEntity.kt` | Traceroute position snapshots |
| **DAOs** | | |
| `NodeInfoDao` | `dao/NodeInfoDao.kt` | Node CRUD with reactive queries |
| `PacketDao` | `dao/PacketDao.kt` | Message queries with paging support |
| `MeshLogDao` | `dao/MeshLogDao.kt` | Log insertion and paging |
| `QuickChatActionDao` | `dao/QuickChatActionDao.kt` | Quick chat CRUD |
| `DeviceHardwareDao` | `dao/DeviceHardwareDao.kt` | Hardware catalog CRUD |
| `FirmwareReleaseDao` | `dao/FirmwareReleaseDao.kt` | Firmware release CRUD |
| `TracerouteNodePositionDao` | `dao/TracerouteNodePositionDao.kt` | Traceroute position CRUD |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST define a Room database with 11 entities and 7 DAOs.
- **FR-002**: System MUST support auto-migration from schema version 3 to 38 (35 migration steps).
- **FR-003**: System MUST provide per-device database instances keyed by Bluetooth address.
- **FR-004**: System MUST implement LRU eviction with configurable cache limit (min/max bounds).
- **FR-005**: System MUST expose the active database as `StateFlow<MeshtasticDatabase>`.
- **FR-006**: System MUST provide `withDb()` with connection-pool-closed retry logic.
- **FR-007**: System MUST clean up legacy database files on first use after migration.
- **FR-008**: System MUST provide platform-specific `DatabaseBuilder` via expect/actual.
- **FR-009**: System MUST provide `hasDatabaseFor(address)` to check if a device has persisted data.
- **FR-010**: System MUST track database last-used timestamps via DataStore preferences.
- **FR-011**: System MUST protect the active database from LRU eviction.
- **FR-012**: System MUST configure Room with `fallbackToDestructiveMigration(dropAllTables = false)`.
- **FR-013**: System MUST set query coroutine context to `ioDispatcher` for all Room operations.
- **FR-014**: System MUST support paging via `PagingSourceDaoReturnTypeConverter`.

### Non-Functional Requirements

- **NFR-001**: All entity, DAO, and database definitions MUST reside in `commonMain` (Constitution §I).
- **NFR-002**: Database switching MUST be serialized via `Mutex` to prevent race conditions.
- **NFR-003**: `withDb()` parallelism MUST be limited to 4 concurrent operations via `limitedParallelism`.
- **NFR-004**: Database file eviction MUST be best-effort — failures are logged, not propagated.
- **NFR-005**: Legacy database cleanup MUST be idempotent (DataStore flag prevents repeat cleanup).

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 23 files (~2,800 LOC) | Database, entities, DAOs, manager, converters |
| `commonTest` | 3 files (~300 LOC) | NodeInfoDao, PacketDao, eviction tests |
| `androidDeviceTest` | 2 files (~200 LOC) | Full DB test, legacy cleanup test |
| `androidHostTest` | 1 file (~100 LOC) | Migration test |
| `androidMain` | 2 files (~100 LOC) | Android DatabaseBuilder, Android DI module |
| `jvmMain` | 1 file (~50 LOC) | JVM DatabaseBuilder |
| `iosMain` | 1 file (~50 LOC) | iOS DatabaseBuilder |

## Privacy Assessment

- [x] Database files contain mesh node data (addresses, positions) — stored locally only
- [x] Database file names derived from Bluetooth MAC are anonymized in logs
- [x] No database exports or cloud sync

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 35 auto-migrations execute without data loss from v3→v38.
- **SC-002**: `DatabaseManager` correctly switches between 3+ per-device databases.
- **SC-003**: LRU eviction removes exactly the right number of databases when cache limit is exceeded.
- **SC-004**: Active database is never evicted regardless of LRU order.
- **SC-005**: `withDb()` retries successfully after a connection-pool-closed race.
- **SC-006**: All 6 existing test files pass.
- **SC-007**: Database builder compiles and produces valid Room instances on all 3 platforms.

## Assumptions

- Room KMP (`androidx.room3`) is the persistence library.
- Per-device database naming convention: `meshtastic_{sanitized_address}`.
- Default cache limit is defined in `DatabaseConstants.DEFAULT_CACHE_LIMIT`.
- `ioDispatcher` is the coroutine context for all Room query execution.
- DataStore preferences are used for cache limit and legacy cleanup tracking.
- Schema exports are enabled (`exportSchema = true`) for migration validation.

