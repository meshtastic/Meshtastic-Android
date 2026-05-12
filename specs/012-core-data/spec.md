# Feature Specification: Core Data Layer

**Feature Branch**: `012-core-data`  
**Created**: 2026-07-27  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `core/data` module

## Summary

The Core Data Layer is the central orchestration hub of Meshtastic-Android. It provides concrete implementations of all repository and manager interfaces defined in `core/repository`, bridging the mesh radio transport layer (`core/network`) with the persistence layer (`core/database`) and the feature modules. The module handles mesh connection lifecycle, packet routing and processing, node management, session tracking, radio configuration, MQTT integration, firmware release data, XModem file transfer, and telemetry. All implementations reside in `commonMain` with Koin DI component scan.

## Goals

1. **Centralized data orchestration** — provide a single module with all `*Impl` classes that feature modules depend on via interface-only injection.
2. **Mesh connection lifecycle** — manage the full connect → handshake → config complete → operational → disconnect lifecycle with status notifications.
3. **Packet processing pipeline** — decode, route, and persist all `FromRadio` packets through a layered handler chain (packet → message → telemetry → admin → store-forward → neighbor).
4. **Node management** — maintain an in-memory node database backed by Room, with identity, position, telemetry, and hardware metadata.
5. **Session management** — track per-node remote-admin sessions with TTL, passkey rotation, and automatic expiry aligned with firmware's 300s TTL.

## Non-Goals

- Transport-level concerns (BLE, TCP, Serial) — handled by `core/network`.
- Database schema definitions or DAO interfaces — handled by `core/database`.
- Domain model definitions — handled by `core/model`.
- UI or ViewModel logic — handled by `feature/*` modules.
- Proto message definitions — handled by `core/proto` (read-only upstream).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Mesh Connection Lifecycle (Priority: P1)

When the app connects to a Meshtastic radio, the data layer orchestrates the full handshake: device identification, configuration exchange, node DB loading, and transition to `CONNECTED` state. The connection manager coordinates with the radio interface, node manager, notifications, and analytics.

**Why this priority**: Every feature depends on an active mesh connection. Without a successful handshake, no data flows.

**Independent Test**: Can be validated by simulating a radio connection and verifying state transitions through Disconnected → DeviceSleep → Connected.

**Acceptance Scenarios**:

1. **Given** a radio transport is available, **When** the connection manager starts, **Then** it transitions through `Disconnected → DeviceSleep → Connected` as handshake packets arrive.
2. **Given** a config-complete packet is received, **When** the handshake finishes, **Then** the node DB is saved, MQTT is started if configured, and connection state is set to `Connected`.
3. **Given** the radio disconnects unexpectedly, **When** the transport reports disconnection, **Then** the connection manager transitions to `DeviceSleep` and analytics are reported.
4. **Given** multiple connect/disconnect cycles occur, **When** each cycle completes, **Then** no coroutine leaks or state corruption occurs.

---

### User Story 2 — Packet Processing Pipeline (Priority: P1)

When the radio receives a mesh packet, it flows through a pipeline of handlers: `FromRadioPacketHandler` dispatches to `PacketHandler`, which routes to `MeshMessageProcessor`, `TelemetryPacketHandler`, `AdminPacketHandler`, `StoreForwardPacketHandler`, and `NeighborInfoHandler` based on port number.

**Why this priority**: All mesh data (messages, telemetry, admin responses, traceroutes) enters the app through this pipeline.

**Independent Test**: Feed a `FromRadio` proto to `handleFromRadio()` and verify the correct handler processes it and persists the result.

**Acceptance Scenarios**:

1. **Given** a `FromRadio` packet with a `MESH_PACKET` variant, **When** processed by `FromRadioPacketHandler`, **Then** it is decoded and dispatched to `PacketHandler`.
2. **Given** a packet with `PortNum.TEXT_MESSAGE_APP`, **When** routed by `PacketHandler`, **Then** `MeshMessageProcessor` persists it as a `Packet` entity and triggers notifications.
3. **Given** a packet with `PortNum.TELEMETRY_APP`, **When** routed, **Then** `TelemetryPacketHandler` updates the node's device/environment/power metrics.
4. **Given** a packet with `PortNum.ADMIN_APP`, **When** routed, **Then** `AdminPacketHandler` processes admin responses and updates radio configuration.
5. **Given** a store-and-forward history packet, **When** processed, **Then** `StoreForwardPacketHandler` persists it without duplicate notification.

---

### User Story 3 — Node Management (Priority: P1)

The node manager maintains a reactive in-memory cache of all mesh nodes, synchronized with Room persistence. It handles node discovery, identity updates, position changes, and last-heard timestamps.

**Why this priority**: Node data is displayed across 6+ feature screens. Stale or missing node data degrades the entire UX.

**Independent Test**: Simulate node info packets and verify the node cache updates and Room persists correctly.

**Acceptance Scenarios**:

1. **Given** a `NodeInfo` packet is received, **When** `NodeManager` processes it, **Then** the node is upserted in both the in-memory cache and Room database.
2. **Given** a node's user identity changes (new long_name/short_name), **When** the update is processed, **Then** the cached `Node` reflects the new identity immediately.
3. **Given** a node has not been heard for >15 minutes, **When** `isOnline` is evaluated, **Then** it returns `false`.
4. **Given** `loadCachedNodeDB()` is called on startup, **Then** all persisted nodes are loaded into the in-memory cache.

---

### User Story 4 — Radio Configuration Management (Priority: P2)

The radio config repository manages the local copy of the connected device's configuration (LoRa, device, display, network, Bluetooth, position, power, security). Configuration changes are sent via admin packets and confirmed via response packets.

**Why this priority**: Settings screens depend on accurate config state. Stale config leads to user confusion.

**Independent Test**: Simulate config response packets and verify the repository state updates.

**Acceptance Scenarios**:

1. **Given** a `Config` admin response is received, **When** `MeshConfigHandler` processes it, **Then** the corresponding config flow in `RadioConfigRepository` is updated.
2. **Given** the user changes a config value, **When** the change is sent via `CommandSender`, **Then** the admin packet is constructed and sent to the radio.
3. **Given** a `MeshConfigFlowManager` is active, **When** config responses arrive, **Then** they are correlated with pending requests and the flow completes.

---

### User Story 5 — Session Management for Remote Admin (Priority: P2)

The session manager tracks per-node remote-admin sessions, including passkey storage, TTL tracking, and automatic expiry detection. This enables the remote admin feature to maintain sessions across navigation without re-authenticating.

**Why this priority**: Remote admin is a power-user feature. Session management prevents passkey conflicts when administering multiple nodes.

**Independent Test**: Create sessions for multiple nodes and verify TTL expiry and passkey rotation.

**Acceptance Scenarios**:

1. **Given** a remote-admin session is established with node A, **When** the passkey response arrives, **Then** `SessionManager` stores the passkey mapped to node A's num.
2. **Given** sessions exist for nodes A and B, **When** node B's session is accessed, **Then** node A's passkey is not overwritten (per-node isolation).
3. **Given** a session is 240+ seconds old, **When** `statusFlow(nodeNum)` is observed, **Then** it emits `Expired`.
4. **Given** a session receives a refreshed passkey, **When** the refresh is processed, **Then** the TTL resets to 300s.

---

### Edge Cases

- What happens when `FromRadio` contains an unknown `payloadVariant`? It is logged and ignored.
- What happens when the packet handler receives a packet with `from == 0`? It is treated as from the local node.
- What happens when MQTT reconnects mid-session? `MqttManagerImpl` re-subscribes to all configured topics.
- What happens when `withDb()` is called during a database switch? The connection-pool-closed exception is caught and retried once with the new DB instance.

## Architecture

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| `MeshConnectionManagerImpl` | `manager/MeshConnectionManagerImpl.kt` | Full mesh connection lifecycle: handshake, config exchange, state machine |
| `FromRadioPacketHandlerImpl` | `manager/FromRadioPacketHandlerImpl.kt` | Top-level `FromRadio` dispatcher — routes to packet, config, nodeinfo handlers |
| `PacketHandlerImpl` | `manager/PacketHandlerImpl.kt` | Per-portnum routing: text → message processor, telemetry → telemetry handler, etc. |
| `MeshMessageProcessorImpl` | `manager/MeshMessageProcessorImpl.kt` | Message persistence, notification dispatch, contact-aware filtering |
| `TelemetryPacketHandlerImpl` | `manager/TelemetryPacketHandlerImpl.kt` | Device/environment/power metric updates on nodes |
| `AdminPacketHandlerImpl` | `manager/AdminPacketHandlerImpl.kt` | Admin response processing, config/module updates |
| `NodeManagerImpl` | `manager/NodeManagerImpl.kt` | In-memory node cache + Room sync, identity updates, position tracking |
| `SessionManagerImpl` | `manager/SessionManagerImpl.kt` | Per-node remote-admin session tracking with TTL and passkey rotation |
| `CommandSenderImpl` | `manager/CommandSenderImpl.kt` | Constructs and sends admin/data packets to the radio |
| `MeshRouterImpl` | `manager/MeshRouterImpl.kt` | Service action routing (send message, request position, traceroute, etc.) |
| `MeshConfigHandlerImpl` | `manager/MeshConfigHandlerImpl.kt` | Config/module response processing and flow updates |
| `MeshConfigFlowManagerImpl` | `manager/MeshConfigFlowManagerImpl.kt` | Coroutine-based config request/response correlation |
| `MqttManagerImpl` | `manager/MqttManagerImpl.kt` | MQTT connection lifecycle management |
| `XModemManagerImpl` | `manager/XModemManagerImpl.kt` | XModem file transfer protocol for firmware updates |
| `HistoryManagerImpl` | `manager/HistoryManagerImpl.kt` | Sent-packet history for retry and deduplication |
| `MessageFilterImpl` | `manager/MessageFilterImpl.kt` | Message filtering (mute, ignore, contact-level rules) |
| `NodeRepositoryImpl` | `repository/NodeRepositoryImpl.kt` | Node CRUD, sort, filter, reactive flows |
| `PacketRepositoryImpl` | `repository/PacketRepositoryImpl.kt` | Message/packet CRUD, paging, unread counts |
| `RadioConfigRepositoryImpl` | `repository/RadioConfigRepositoryImpl.kt` | Radio config state flows (LoRa, device, display, etc.) |
| `FirmwareReleaseRepositoryImpl` | `repository/FirmwareReleaseRepositoryImpl.kt` | Firmware release data from JSON + local cache |
| `DeviceHardwareRepositoryImpl` | `repository/DeviceHardwareRepositoryImpl.kt` | Hardware catalog from JSON + local cache |
| `CoreDataModule` | `di/CoreDataModule.kt` | Koin module with component scan + explicit providers |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST implement `MeshConnectionManager` interface with full connect/handshake/disconnect lifecycle.
- **FR-002**: System MUST process all `FromRadio` packet variants (MESH_PACKET, CONFIG_COMPLETE_ID, MY_INFO, NODE_INFO, METADATA, MQTTCLIENT_PROXY_MESSAGE, CLIENT_NOTIFICATION).
- **FR-003**: System MUST route decoded mesh packets by `PortNum` to the appropriate handler (text, telemetry, admin, traceroute, store-forward, neighbor-info, position).
- **FR-004**: System MUST persist messages as `Packet` entities in Room and emit notification events for new messages.
- **FR-005**: System MUST maintain an in-memory `Node` cache synchronized with Room, supporting reactive `Flow<List<Node>>` access.
- **FR-006**: System MUST track per-node remote-admin sessions with 300s TTL, passkey storage, and automatic expiry.
- **FR-007**: System MUST manage radio configuration state as `StateFlow` per config type (LoRa, device, display, network, Bluetooth, position, power, security).
- **FR-008**: System MUST support XModem file transfer for firmware updates via `XModemManager`.
- **FR-009**: System MUST provide `CommandSender` for constructing and sending admin/data packets to the radio.
- **FR-010**: System MUST filter messages based on contact settings (muted, ignored, message-filtering disabled).
- **FR-011**: System MUST manage MQTT connection lifecycle (connect, subscribe, publish, disconnect) aligned with radio config.
- **FR-012**: System MUST provide switching data sources between real and mock node-info read/write sources.
- **FR-013**: System MUST handle traceroute snapshot persistence and overlay construction.
- **FR-014**: System MUST maintain firmware release and device hardware catalogs with JSON + local DB caching.

### Non-Functional Requirements

- **NFR-001**: All implementations MUST reside in `commonMain` source set (Constitution §I).
- **NFR-002**: All coroutines MUST use named dispatchers from `CoroutineDispatchers` — no `Dispatchers.IO` directly (Constitution §VII).
- **NFR-003**: Error handling MUST use `safeCatching {}` where applicable (Constitution §VII).
- **NFR-004**: Session state MUST use `atomicfu` for thread-safe access to the `PersistentMap`.
- **NFR-005**: Database access via `withDb()` MUST tolerate connection-pool-closed races during DB switching.
- **NFR-006**: Node cache updates MUST be conflated to avoid overwhelming UI collectors during rapid mesh updates.

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 40 files (~10,500 LOC) | All manager and repository implementations |
| `commonTest` | 19 files (~1,700 LOC) | Unit tests for managers and repositories |
| `androidMain` | 0 files | No platform-specific code |

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged — message content is never logged
- [x] Node identifiers are anonymized in log output via `anonymize()` utility
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 7 repository implementations pass their corresponding unit tests.
- **SC-002**: Connection lifecycle state machine correctly transitions through all states for BLE, TCP, and Serial transports.
- **SC-003**: Packet processing pipeline routes all known `PortNum` values to the correct handler.
- **SC-004**: Session manager correctly isolates per-node passkeys — concurrent sessions for 2+ nodes do not interfere.
- **SC-005**: `NodeManager.loadCachedNodeDB()` populates the in-memory cache from Room within 500ms for 100 nodes.
- **SC-006**: `MessageFilter` correctly suppresses notifications for muted/ignored contacts.
- **SC-007**: XModem transfer handles NAK retries and completes a simulated firmware file.
- **SC-008**: Config flow manager correlates request/response pairs within firmware's response timeout.
- **SC-009**: All 19 existing test files pass with `allTests` target.
- **SC-010**: `MqttManager` reconnects within 5s after network recovery.

## Assumptions

- All business logic resides in `commonMain` source set.
- Koin DI with `@ComponentScan` auto-discovers all `@Single`-annotated implementations.
- `core/repository` defines the interface contracts; this module provides the implementations.
- Room database access is via `DatabaseProvider.withDb()` — implementations never hold direct DAO references.
- The `Clock` dependency is injected for testability (no `System.currentTimeMillis()` calls).

