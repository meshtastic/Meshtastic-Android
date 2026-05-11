# Tasks: Core Database (Room KMP Persistence)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Status**: Migrated — all existing tasks marked complete. Gap tasks marked incomplete.  
**Task Prefix**: `DB-T`

---

## Phase 1 — Schema & Entities

### DB-T001: Room database definition [x]

- **File**: `MeshtasticDatabase.kt` (~141 LOC)
- 11 entities, 7 abstract DAO accessors.
- 35 auto-migrations (v3→v38) with 4 special migration specs.
- `@TypeConverters(Converters::class)`, `@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter)`.
- `configureCommon()` extension: `fallbackToDestructiveMigration(false)` + `setQueryCoroutineContext(ioDispatcher)`.
- **Test**: `MigrationTest.kt` (androidHostTest).

### DB-T002: Node entities [x]

- **Files**: `entity/NodeEntity.kt`, `entity/MyNodeEntity.kt`
- `NodeEntity`: num, user, position, metrics, flags, metadata fields.
- `MyNodeEntity`: local node identity.
- **Test**: `CommonNodeInfoDaoTest.kt`.

### DB-T003: Message entity [x]

- **File**: `entity/Packet.kt`
- Message persistence: data, port_num, contact_key, time, status, reactions.
- **Test**: `CommonPacketDaoTest.kt`.

### DB-T004: Support entities [x]

- **Files**: `entity/MeshLog.kt`, `entity/QuickChatAction.kt`, `entity/FirmwareReleaseEntity.kt`, `entity/DeviceHardwareEntity.kt`, `entity/TracerouteNodePositionEntity.kt`
- Debug logs, quick chat shortcuts, firmware/hardware catalogs, traceroute positions.
- **Test**: Partial — verified via integration.

### DB-T005: Type converters [x]

- **File**: `Converters.kt`
- Proto message ↔ `ByteArray` (blob), `ByteString` ↔ `ByteArray`, enum conversions.
- **Test**: Verified via DAO round-trips.

### DB-T006: Database constants [x]

- **File**: `DatabaseConstants.kt`
- `DB_PREFIX`, `DEFAULT_DB_NAME`, `LEGACY_DB_NAME`, `DEFAULT_CACHE_LIMIT`, `MIN_CACHE_LIMIT`, `MAX_CACHE_LIMIT`.
- **Test**: Used as dependency throughout.

---

## Phase 2 — DAOs

### DB-T007: NodeInfoDao [x]

- **File**: `dao/NodeInfoDao.kt`
- CRUD: insert, update, delete, getAll, getByNum, flow queries.
- Reactive `Flow<List<NodeEntity>>` for node list.
- **Test**: `CommonNodeInfoDaoTest.kt`.

### DB-T008: PacketDao [x]

- **File**: `dao/PacketDao.kt`
- Message queries with paging, contact-key filtering, unread counts.
- **Test**: `CommonPacketDaoTest.kt`.

### DB-T009: MeshLogDao [x]

- **File**: `dao/MeshLogDao.kt`
- Log insertion, paging, auto-eviction by count.
- **Test**: Verified via `CommonMeshLogRepositoryTest.kt` in `core/data`.

### DB-T010: Supporting DAOs [x]

- **Files**: `dao/QuickChatActionDao.kt`, `dao/DeviceHardwareDao.kt`, `dao/FirmwareReleaseDao.kt`, `dao/TracerouteNodePositionDao.kt`
- Standard CRUD + reactive queries for each entity.
- **Test**: Verified via corresponding repository tests in `core/data`.

---

## Phase 3 — Database Manager

### DB-T011: DatabaseManager — core lifecycle [x]

- **File**: `DatabaseManager.kt` (~301 LOC)
- Per-device database cache (`mutableMapOf<String, MeshtasticDatabase>`).
- `switchActiveDatabase(address)`: mutex-serialized, emit-before-close pattern.
- `withDb(block)`: limited parallelism (4), retry on connection-pool-closed.
- `currentDb: StateFlow<MeshtasticDatabase>` with `filterNotNull().stateIn()`.
- **Test**: `DatabaseManagerEvictionTest.kt`.

### DB-T012: LRU eviction [x]

- **File**: `DatabaseManager.kt` (within class)
- `enforceCacheLimit(activeDbName)`: sorted by last-used timestamp, evicts excess.
- `selectEvictionVictims()`: excludes active DB and system DBs.
- `closeCachedDatabase()`: close + remove from cache.
- DataStore-tracked `lastUsedKey(dbName)` with file-metadata fallback.
- **Test**: `DatabaseManagerEvictionTest.kt`.

### DB-T013: Legacy database cleanup [x]

- **File**: `DatabaseManager.kt` (within class)
- One-time cleanup of pre-migration `meshtastic_database` file.
- Idempotent via DataStore `legacyCleanedKey` flag.
- **Test**: `DatabaseManagerLegacyCleanupTest.kt` (androidDeviceTest).

### DB-T014: DatabaseProvider interface [x]

- **File**: `DatabaseProvider.kt`
- Exposes `currentDb`, `withDb()`, `hasDatabaseFor()`.
- **Test**: Interface contract verified via `DatabaseManager`.

### DB-T015: Platform-specific DatabaseBuilder [x]

- **Files**: `commonMain/.../DatabaseBuilder.kt` (expect), `androidMain`, `jvmMain`, `iosMain` (actual)
- Android: `Room.databaseBuilder(context, ...)`.
- JVM: `Room.databaseBuilder(databaseFile)`.
- iOS: `Room.databaseBuilder(NSFileManager path)`.
- **Test**: `MeshtasticDatabaseTest.kt` (androidDeviceTest) verifies Android builder.

### DB-T016: Koin DI module [x]

- **File**: `di/CoreDatabaseModule.kt`
- `@ComponentScan("org.meshtastic.core.database")`.
- Provides `@Named("DatabaseDataStore")` DataStore instance.
- **Test**: Module loads without error.

---

## Gap Tasks (Incomplete)

### DB-T017: Add Converters round-trip tests [x]

- **File to create**: `commonTest/.../ConvertersTest.kt`
- Test proto ↔ ByteArray, ByteString ↔ ByteArray round-trips for all converter methods.
- **Priority**: Low

### DB-T018: Add missing DAO tests [x]

- **Files to create**: `commonTest/.../dao/CommonQuickChatActionDaoTest.kt`, `CommonMeshLogDaoTest.kt`, etc.
- Cover CRUD + reactive query behavior for untested DAOs.
- **Priority**: Medium

### DB-T019: Add withDb() concurrent retry test [x]

- **File to create**: `commonTest/.../DatabaseManagerRetryTest.kt`
- Simulate DB switch during active `withDb()` query; verify retry succeeds.
- **Priority**: Medium

