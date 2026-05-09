# Tasks: Core Data Layer

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Status**: Migrated — all existing tasks marked complete. Gap tasks marked incomplete.  
**Task Prefix**: `DAT-T`

---

## Phase 1 — DI & Data Sources

### DAT-T001: Koin DI module setup [x]

- **File**: `core/data/src/commonMain/kotlin/org/meshtastic/core/data/di/CoreDataModule.kt`
- Created `CoreDataModule` with `@ComponentScan("org.meshtastic.core.data")`.
- Provides `MeshDataMapper` and `Clock.System` as explicit singletons.
- **Test**: Module loads without error in app startup.

### DAT-T002: Node info data source interfaces [x]

- **Files**: `datasource/NodeInfoReadDataSource.kt`, `NodeInfoWriteDataSource.kt`
- Defined read/write interfaces for node info access abstraction.
- **Test**: Interface contracts verified via implementations.

### DAT-T003: Switching data source implementations [x]

- **Files**: `datasource/SwitchingNodeInfoReadDataSource.kt`, `SwitchingNodeInfoWriteDataSource.kt`
- Delegates to real or mock data sources based on configuration.
- Enables mock mode for screenshots and UI testing.
- **Test**: Verified switching behavior in integration tests.

### DAT-T004: Firmware release JSON + local data sources [x]

- **Files**: `datasource/FirmwareReleaseJsonDataSource.kt`, `FirmwareReleaseLocalDataSource.kt`
- JSON parsing for GitHub release API responses.
- Room-backed local cache for offline access.
- **Test**: JSON parsing verified with sample payloads.

### DAT-T005: Device hardware JSON + local data sources [x]

- **Files**: `datasource/DeviceHardwareJsonDataSource.kt`, `DeviceHardwareLocalDataSource.kt`
- Hardware catalog parsing from JSON.
- Room-backed local cache.
- **Test**: JSON parsing verified with sample payloads.

### DAT-T006: Bootloader OTA quirks data source [x]

- **File**: `datasource/BootloaderOtaQuirksJsonDataSource.kt`
- Parses bootloader-specific OTA quirks for firmware update compatibility.
- **Test**: Verified with known quirk entries.

---

## Phase 2 — Repository Implementations

### DAT-T007: NodeRepositoryImpl [x]

- **File**: `repository/NodeRepositoryImpl.kt` (~291 LOC)
- Implements `NodeRepository` with reactive node list, sort, filter, identity updates.
- Uses `Lifecycle`-scoped coroutine sharing for node flows.
- **Test**: `CommonNodeRepositoryTest.kt` — covers node CRUD and flow emissions.

### DAT-T008: PacketRepositoryImpl [x]

- **File**: `repository/PacketRepositoryImpl.kt`
- Implements `PacketRepository` with paging support, unread counts, contact-aware queries.
- **Test**: `CommonPacketRepositoryTest.kt` — covers message persistence and queries.

### DAT-T009: RadioConfigRepositoryImpl [x]

- **File**: `repository/RadioConfigRepositoryImpl.kt`
- Manages `StateFlow` per config type (LoRa, device, display, network, etc.).
- **Test**: Verified via `MeshConfigHandlerImplTest.kt` integration.

### DAT-T010: FirmwareReleaseRepositoryImpl [x]

- **File**: `repository/FirmwareReleaseRepositoryImpl.kt`
- Fetches from remote, caches locally, exposes reactive flow.
- **Test**: Verified via integration with firmware update feature.

### DAT-T011: QuickChatActionRepositoryImpl [x]

- **File**: `repository/QuickChatActionRepositoryImpl.kt`
- CRUD for quick chat shortcuts with Room persistence.
- **Test**: Verified via messaging feature integration.

### DAT-T012: MeshLogRepositoryImpl [x]

- **File**: `repository/MeshLogRepositoryImpl.kt`
- Debug mesh log persistence with paging auto-eviction.
- **Test**: `CommonMeshLogRepositoryTest.kt`.

### DAT-T013: TracerouteSnapshotRepositoryImpl [x]

- **File**: `repository/TracerouteSnapshotRepositoryImpl.kt`
- Traceroute result persistence with position snapshots.
- **Test**: Verified via node detail metrics feature.

### DAT-T014: DeviceHardwareRepositoryImpl [x]

- **File**: `repository/DeviceHardwareRepositoryImpl.kt`
- Hardware catalog with JSON + Room caching.
- **Test**: Verified via device connections feature.

---

## Phase 3 — Manager Implementations: Connection & Packet Pipeline

### DAT-T015: MeshConnectionManagerImpl [x]

- **File**: `manager/MeshConnectionManagerImpl.kt` (~436 LOC)
- Full connection lifecycle: handshake, config exchange, state transitions.
- Coordinates with radio interface, node manager, notifications, analytics.
- **Test**: `MeshConnectionManagerImplTest.kt`.

### DAT-T016: FromRadioPacketHandlerImpl [x]

- **File**: `manager/FromRadioPacketHandlerImpl.kt`
- Top-level `FromRadio` proto dispatcher. Routes by `payloadVariant`.
- **Test**: `FromRadioPacketHandlerImplTest.kt`.

### DAT-T017: PacketHandlerImpl [x]

- **File**: `manager/PacketHandlerImpl.kt`
- Routes decoded `MeshPacket` by `PortNum` to specialized handlers.
- **Test**: `PacketHandlerImplTest.kt`.

### DAT-T018: MeshMessageProcessorImpl [x]

- **File**: `manager/MeshMessageProcessorImpl.kt`
- Persists text messages, triggers notifications, handles contact settings.
- **Test**: `MeshMessageProcessorImplTest.kt`.

### DAT-T019: TelemetryPacketHandlerImpl [x]

- **File**: `manager/TelemetryPacketHandlerImpl.kt`
- Updates device, environment, and power metrics on node entries.
- **Test**: `TelemetryPacketHandlerImplTest.kt`.

### DAT-T020: AdminPacketHandlerImpl [x]

- **File**: `manager/AdminPacketHandlerImpl.kt`
- Processes admin responses: config, module, channel, metadata.
- **Test**: `AdminPacketHandlerImplTest.kt`.

### DAT-T021: NodeManagerImpl [x]

- **File**: `manager/NodeManagerImpl.kt`
- In-memory node cache with Room synchronization and identity updates.
- **Test**: `NodeManagerImplTest.kt`.

### DAT-T022: CommandSenderImpl [x]

- **File**: `manager/CommandSenderImpl.kt`
- Constructs and sends admin/data packets to the radio.
- **Test**: Verified via `MeshActionHandlerImplTest.kt` integration.

---

## Phase 4 — Manager Implementations: Support Services

### DAT-T023: SessionManagerImpl [x]

- **File**: `manager/SessionManagerImpl.kt` (~118 LOC)
- Per-node remote-admin session tracking with `atomicfu` + `PersistentMap`.
- 300s TTL aligned with firmware, 240s active threshold.
- **Test**: `SessionManagerImplTest.kt`.

### DAT-T024: MeshConfigFlowManagerImpl [x]

- **File**: `manager/MeshConfigFlowManagerImpl.kt`
- Coroutine-based config request/response correlation with timeout.
- **Test**: `MeshConfigFlowManagerImplTest.kt`.

### DAT-T025: MessageFilterImpl + HistoryManagerImpl [x]

- **Files**: `manager/MessageFilterImpl.kt`, `manager/HistoryManagerImpl.kt`
- Mute/ignore/filter logic and sent-packet history for deduplication.
- **Test**: `MessageFilterImplTest.kt`, `HistoryManagerImplTest.kt`.

### DAT-T026: StoreForwardPacketHandlerImpl + NeighborInfoHandlerImpl [x]

- **Files**: `manager/StoreForwardPacketHandlerImpl.kt`, `manager/NeighborInfoHandlerImpl.kt`
- Store-and-forward history persistence, neighbor info upserts.
- **Test**: `StoreForwardPacketHandlerImplTest.kt`.

---

## Gap Tasks (Incomplete)

### DAT-T027: Add MqttManagerImpl unit tests [ ]

- **File to create**: `commonTest/.../manager/MqttManagerImplTest.kt`
- Cover connect/subscribe/publish/disconnect lifecycle.
- Verify reconnect on network recovery.
- **Priority**: Medium

### DAT-T028: Add MeshRouterImpl unit tests [ ]

- **File to create**: `commonTest/.../manager/MeshRouterImplTest.kt`
- Cover service action routing: send message, request position, traceroute, admin commands.
- **Priority**: Medium

### DAT-T029: Add full pipeline integration test [ ]

- **File to create**: `commonTest/.../integration/PacketPipelineIntegrationTest.kt`
- End-to-end: raw `FromRadio` bytes → handler chain → persisted entity → notification.
- **Priority**: Medium

### DAT-T030: Add DataLayerHeartbeatSender + NeighborInfoHandler tests [ ]

- **Files to create**: `commonTest/.../manager/DataLayerHeartbeatSenderTest.kt`, `NeighborInfoHandlerImplTest.kt`
- Cover heartbeat interval, cancellation, neighbor info upsert.
- **Priority**: Low

