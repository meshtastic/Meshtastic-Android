# Implementation Plan: Core Data Layer

**Branch**: `012-core-data` | **Date**: 2026-07-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/012-core-data/spec.md`
**Status**: Migrated — all implementation complete, plan reverse-engineered from existing code.

## Summary

The Core Data Layer provides all concrete implementations for the repository and manager interfaces that feature modules consume. It orchestrates mesh connection lifecycle, packet processing, node management, session tracking, radio configuration, MQTT, firmware data, and XModem transfers. The module is pure `commonMain` Kotlin with Koin DI, depending downward on `core/database`, `core/network`, `core/ble`, `core/model`, and `core/repository`.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Koin 4.2+ (K2 Compiler Plugin), kotlinx.coroutines, kotlinx.atomicfu, kotlinx-collections-immutable, Okio, Kermit logging  
**Storage**: Room KMP via `DatabaseProvider.withDb()` delegation; DataStore KMP for preferences  
**Testing**: KMP `allTests` — 19 test files, ~1,700 LOC; Turbine for Flow testing, Mokkery for mocking  
**Target Platform**: Android, Desktop (JVM), iOS — all via `commonMain`  
**Constraints**: No `java.*`/`android.*` imports in commonMain; all dispatchers via `CoroutineDispatchers`; `safeCatching {}` over `runCatching {}`  
**Scale/Scope**: 40 commonMain files (~10,500 LOC), 0 androidMain files, 19 test files (~1,700 LOC)

## Constitution Check

*GATE: All checks pass — existing production code reviewed against Constitution v1.2.2.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All 40 source files in `commonMain`. Zero platform-specific code. |
| II. Zero Lint Tolerance | ✅ PASS | `detekt-baseline.xml` present. Suppressions limited to `TooManyFunctions`, `LongParameterList`. |
| III. Compose Multiplatform UI | N/A | No UI code in this module. |
| IV. Privacy First | ✅ PASS | Node identifiers anonymized via `anonymize()`. Message content never logged. |
| V. Design Standards Compliance | N/A | No UI code. |
| VI. Verify Before Push | ✅ PASS | 19 test files covering all critical managers and repositories. |
| VII. Coroutine Safety | ✅ PASS | Named dispatchers throughout. `safeCatching {}` used in MQTT and packet handlers. `atomicfu` for session state. |
| VIII. Resource Discipline | N/A | No string/icon resources in data layer. |
| IX. Branch & Scope Hygiene | ✅ PASS | Clean module boundaries. All implementations scoped to `org.meshtastic.core.data`. |

**Gate Result**: ✅ All applicable principles satisfied.

## Project Structure

```
core/data/src/
├── commonMain/kotlin/org/meshtastic/core/data/
│   ├── di/
│   │   └── CoreDataModule.kt                    # Koin module with @ComponentScan
│   ├── datasource/
│   │   ├── NodeInfoReadDataSource.kt             # Interface for reading node info
│   │   ├── NodeInfoWriteDataSource.kt            # Interface for writing node info
│   │   ├── SwitchingNodeInfoReadDataSource.kt    # Switches between real/mock sources
│   │   ├── SwitchingNodeInfoWriteDataSource.kt   # Switches between real/mock sources
│   │   ├── FirmwareReleaseJsonDataSource.kt      # JSON parsing for firmware releases
│   │   ├── FirmwareReleaseLocalDataSource.kt     # Room-backed firmware cache
│   │   ├── DeviceHardwareJsonDataSource.kt       # JSON parsing for hardware catalog
│   │   ├── DeviceHardwareLocalDataSource.kt      # Room-backed hardware cache
│   │   └── BootloaderOtaQuirksJsonDataSource.kt  # OTA bootloader quirks data
│   ├── repository/
│   │   ├── NodeRepositoryImpl.kt                 # Node CRUD, sort, filter, flows
│   │   ├── PacketRepositoryImpl.kt               # Message/packet persistence, paging
│   │   ├── RadioConfigRepositoryImpl.kt          # Radio config state flows
│   │   ├── FirmwareReleaseRepositoryImpl.kt      # Firmware release catalog
│   │   ├── DeviceHardwareRepositoryImpl.kt       # Hardware catalog
│   │   ├── QuickChatActionRepositoryImpl.kt      # Quick chat shortcuts
│   │   ├── MeshLogRepositoryImpl.kt              # Debug mesh log persistence
│   │   └── TracerouteSnapshotRepositoryImpl.kt   # Traceroute result persistence
│   └── manager/
│       ├── MeshConnectionManagerImpl.kt          # Connection lifecycle (436 LOC)
│       ├── FromRadioPacketHandlerImpl.kt         # Top-level packet dispatcher
│       ├── PacketHandlerImpl.kt                  # Per-portnum routing
│       ├── MeshMessageProcessorImpl.kt           # Message persistence + notifications
│       ├── TelemetryPacketHandlerImpl.kt         # Telemetry metric updates
│       ├── AdminPacketHandlerImpl.kt             # Admin response processing
│       ├── StoreForwardPacketHandlerImpl.kt      # Store-and-forward handling
│       ├── NeighborInfoHandlerImpl.kt            # Neighbor info updates
│       ├── MeshDataHandlerImpl.kt                # Generic data packet handling
│       ├── NodeManagerImpl.kt                    # Node cache + Room sync
│       ├── SessionManagerImpl.kt                 # Per-node remote-admin sessions
│       ├── CommandSenderImpl.kt                  # Admin/data packet construction
│       ├── MeshRouterImpl.kt                     # Service action routing
│       ├── MeshActionHandlerImpl.kt              # Service action handler
│       ├── MeshConfigHandlerImpl.kt              # Config response processing
│       ├── MeshConfigFlowManagerImpl.kt          # Request/response correlation
│       ├── MqttManagerImpl.kt                    # MQTT lifecycle
│       ├── XModemManagerImpl.kt                  # XModem file transfer
│       ├── HistoryManagerImpl.kt                 # Sent-packet history
│       ├── MessageFilterImpl.kt                  # Mute/ignore filtering
│       └── DataLayerHeartbeatSender.kt           # Periodic heartbeat
└── commonTest/kotlin/org/meshtastic/core/data/
    ├── repository/
    │   ├── CommonMeshLogRepositoryTest.kt
    │   ├── CommonPacketRepositoryTest.kt
    │   └── CommonNodeRepositoryTest.kt
    └── manager/
        ├── MeshConnectionManagerImplTest.kt
        ├── SessionManagerImplTest.kt
        ├── NodeManagerImplTest.kt
        ├── PacketHandlerImplTest.kt
        ├── FromRadioPacketHandlerImplTest.kt
        ├── MeshMessageProcessorImplTest.kt
        ├── TelemetryPacketHandlerImplTest.kt
        ├── AdminPacketHandlerImplTest.kt
        ├── StoreForwardPacketHandlerImplTest.kt
        ├── MeshDataHandlerTest.kt
        ├── MeshConfigHandlerImplTest.kt
        ├── MeshConfigFlowManagerImplTest.kt
        ├── MeshActionHandlerImplTest.kt
        ├── MessageFilterImplTest.kt
        ├── HistoryManagerImplTest.kt
        └── XModemManagerImplTest.kt
```

## Implementation Phases

### Phase 1 — DI & Data Sources (Complete)

Core infrastructure: Koin module, data source interfaces, switching data sources for real/mock node info, JSON data sources for firmware and hardware catalogs.

### Phase 2 — Repository Implementations (Complete)

All 8 repository implementations providing CRUD, reactive flows, pagination, and caching over the Room database and DataStore.

### Phase 3 — Manager Implementations: Connection & Packet Pipeline (Complete)

The critical path: `MeshConnectionManagerImpl` (436 LOC connection lifecycle), `FromRadioPacketHandlerImpl` → `PacketHandlerImpl` → per-portnum handlers, `NodeManagerImpl`, `CommandSenderImpl`.

### Phase 4 — Manager Implementations: Support Services (Complete)

Supporting managers: `SessionManagerImpl` (atomicfu-backed per-node sessions), `MeshConfigFlowManagerImpl` (request/response correlation), `MqttManagerImpl`, `XModemManagerImpl`, `HistoryManagerImpl`, `MessageFilterImpl`, `DataLayerHeartbeatSender`.

## Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| DI strategy | Koin `@ComponentScan` | Auto-discovers all `@Single` implementations without manual registration |
| Session state | `atomicfu` + `PersistentMap` | Lock-free, thread-safe per-node session storage |
| Database access | `withDb()` indirection | Tolerates database switching; retries on connection-pool-closed |
| Packet routing | When-expression on `PortNum` | Simple, exhaustive, easy to extend for new port numbers |
| Config correlation | `MeshConfigFlowManager` | Suspending request/response pairs with timeout for admin commands |
| Node cache | In-memory `StateFlow<List<Node>>` + Room | Fast UI reads from memory; persistence survives process death |
| MQTT lifecycle | `MqttManagerImpl` wrapping `MqttClient` | Decouples MQTT client lifecycle from connection manager |
| Heartbeat | `DataLayerHeartbeatSender` | Periodic keep-alive to prevent radio sleep during active sessions |
| Time abstraction | Injected `kotlin.time.Clock` | Enables deterministic testing of TTL and timestamp logic |
| Error handling | `safeCatching {}` in handlers | Prevents a single packet processing failure from crashing the pipeline |
| Switching data sources | `SwitchingNodeInfo*DataSource` | Enables mock mode for screenshots and testing without a real radio |
| Firmware/hardware catalogs | JSON + Room cache | Network-fetched catalogs cached locally for offline access |

## Gaps Identified

| Gap | Severity | Recommendation |
|-----|----------|----------------|
| `MqttManagerImpl` has no unit test | ⚠️ Medium | Add tests for connect/subscribe/publish/disconnect lifecycle |
| `MeshRouterImpl` has no unit test | ⚠️ Medium | Add tests for service action routing (send, traceroute, position request) |
| `TracerouteHandlerImpl` has no unit test | ⚠️ Low | Add tests for traceroute snapshot construction |
| `DataLayerHeartbeatSender` has no unit test | ⚠️ Low | Add tests for heartbeat interval and cancellation |
| `NeighborInfoHandlerImpl` has no unit test | ⚠️ Low | Add tests for neighbor info upsert logic |
| No integration test for full packet pipeline | ⚠️ Medium | Add end-to-end test: raw `FromRadio` bytes → persisted `Packet` entity |
| `MeshConnectionManagerImpl` uses `runCatching` in one place | ⚠️ Low | Should use `safeCatching` per Constitution §VII |

