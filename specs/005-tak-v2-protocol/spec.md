# Feature Specification: TAK v2 Protocol Integration

**Feature Branch**: `tak_v2`  
**Created**: 2026-05-13  
**Status**: Draft  
**Input**: User description: "TAK v2 protocol support with zstd compression, extended CoT types, legacy fallback, and ATAK/iTAK interoperability"  
**Cross-Platform Spec**: Android + JVM (desktop) with iOS stubs; wire protocol defined by [TAKPacket-SDK](https://github.com/meshtastic/TAKPacket-SDK) (external SDK, not a `design/features/` doc)

## Summary

This feature upgrades the Meshtastic Android app's TAK (Team Awareness Kit) integration from the legacy v1 protocol (port 72, PLI + GeoChat only) to the new TAK v2 protocol (port 78, ATAK_PLUGIN_V2) with zstd dictionary compression and support for all CoT payload types. The upgrade enables Meshtastic mesh radios to relay rich tactical data—markers, routes, drawn shapes, emergencies, tasks, and ranging—between ATAK/iTAK clients, not just position reports and chat messages. The system auto-detects firmware capability and falls back to v1 for older radios, ensuring backward compatibility in mixed-firmware deployments.

## Goals

1. **Full CoT type coverage**: Support all standard CoT event types over mesh — including PLI, GeoChat, Marker, Route, DrawnShape (circle, ellipse, freeform, polygon, rectangle, telestration), Aircraft, Casevac, Emergency, Task, Ranging, Alert, Delete, Chat Receipts, and Waypoints — not just PLI and GeoChat. TakV2TypeMapper covers 23 CoT types + 4 HOW types + a default CotType_Other fallback (28 total mappings)
2. **Efficient wire encoding**: Use zstd dictionary compression and CoT detail stripping to fit rich CoT payloads within the LoRa MTU constraint (237 bytes raw, ~225 bytes usable after protobuf framing overhead)
3. **Backward compatibility**: Auto-detect firmware version and gracefully fall back to legacy TAKPacket (v1) for radios running firmware < 2.8.0
4. **Reliable TAK server operation**: Maintain a local TLS/mTLS TAK server that ATAK and iTAK clients can connect to, with wake lock protection against Android battery optimization
5. **Route interoperability**: Bridge ATAK's route CoT limitation by generating KML data packages for auto-import into ATAK's monitored directory

## Non-Goals

- Implementing a full TAK Server with mission sync, federation, or enterprise features
- Supporting TAK protocols over non-mesh transports (WiFi direct, Bluetooth peer-to-peer)
- Modifying the Meshtastic firmware or protobuf schema (consumed read-only from upstream)
- Providing a standalone TAK client UI within the Meshtastic app (ATAK/iTAK remain the clients)
- Supporting CoT streaming to remote TAK servers over the internet

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Send Rich Tactical Data Over Mesh (Priority: P1)

A TAK operator using ATAK connects to the Meshtastic app's built-in TAK server and drops a marker, draws a shape, or creates a route on the ATAK map. The Meshtastic app converts the CoT event into a compressed TAKPacketV2 and transmits it over the mesh. All other mesh nodes with TAK-connected clients see the marker/shape/route appear on their maps.

**Why this priority**: This is the core value proposition — extending TAK's situational awareness beyond PLI to include all tactical overlays over LoRa mesh.

**Independent Test**: Connect two Meshtastic radios (firmware >= 2.8.0) each with ATAK connected. Drop a hostile marker on one ATAK instance and verify it appears on the other with correct type, icon, and position.

**Acceptance Scenarios**:

1. **Given** two mesh nodes running firmware >= 2.8.0 with ATAK clients connected, **When** a user places a marker (type a-h-G) on one ATAK instance, **Then** the marker appears on the remote ATAK with correct hostile type, position, and callsign within one mesh transmission cycle
2. **Given** a mesh node with firmware >= 2.8.0, **When** an ATAK user creates a route with 3 waypoints, **Then** the route is transmitted over port 78 and a KML data package is generated on the receiving node for ATAK import
3. **Given** a TAKPacketV2 payload exceeding the ~225-byte usable mesh payload after compression, **When** the system attempts transmission, **Then** it drops the packet and logs a warning rather than fragmenting or corrupting the message

---

### User Story 2 - Legacy Fallback for Mixed Firmware (Priority: P1)

A team has a mix of older radios (firmware 2.7.x) and newer radios (firmware 2.8.0+). The app detects each radio's capability and uses the appropriate protocol version. Legacy radios still relay PLI and GeoChat. Newer radios exchange all CoT types.

**Why this priority**: Mixed-firmware deployments are the reality during any firmware upgrade cycle; breaking backward compatibility would render the feature unusable for most teams.

**Independent Test**: Connect one node with firmware 2.7.x and verify that PLI and GeoChat still work via legacy port 72, while markers/shapes are dropped with a user-visible warning.

**Acceptance Scenarios**:

1. **Given** a radio running firmware < 2.8.0, **When** an ATAK user sends a PLI update, **Then** the app encodes it as a legacy TAKPacket on port 72 (ATAK_PLUGIN)
2. **Given** a radio running firmware < 2.8.0, **When** an ATAK user drops a marker (non-PLI, non-GeoChat CoT), **Then** the app drops the event and logs a warning indicating the legacy protocol does not support this type
3. **Given** a radio running firmware >= 2.8.0, **When** a legacy TAKPacket arrives on port 72 from an older mesh node, **Then** the app correctly decodes and forwards it to the connected ATAK client

---

### User Story 3 - TAK Server Lifecycle and Reliability (Priority: P2)

A user enables the TAK server from the Meshtastic app settings. The server starts, accepts ATAK/iTAK connections via mTLS on port 8089, and remains operational even when Android applies battery optimizations. Users can also export connection data packages so clients can configure themselves.

**Why this priority**: Without a reliable always-on local server, TAK clients cannot maintain connectivity, making all other features unreliable.

**Independent Test**: Enable TAK server from settings, connect ATAK via the exported data package, verify connection persists after screen off for 10 minutes.

**Acceptance Scenarios**:

1. **Given** the TAK server is disabled, **When** the user enables it in settings, **Then** a TLS listener starts on port 8089 and the UI shows the server as active with 0 connected clients
2. **Given** the TAK server is running with ATAK connected, **When** the device screen turns off and battery optimization activates, **Then** the partial wake lock keeps the CPU active and the ATAK connection is maintained
3. **Given** the TAK server is running, **When** the user taps "Export Data Package," **Then** a valid .zip data package is generated containing server certificates and connection configuration importable by both ATAK and iTAK

---

### User Story 4 - TAK Configuration UI (Priority: P2)

A user navigates to the TAK module configuration screen to enable/disable the TAK server, select their team color and role, export data packages, and run diagnostic tests to verify mesh-to-TAK connectivity.

**Why this priority**: Users need a way to configure and troubleshoot TAK integration without command-line tools.

**Independent Test**: Navigate to Settings > Module Configuration > TAK, toggle server on/off, change team/role, and verify settings persist across app restart.

**Acceptance Scenarios**:

1. **Given** the user is on the TAK configuration screen, **When** they select a team color and role, **Then** outgoing PLI packets include the selected team and role values
2. **Given** the user taps "Run Test," **When** the test runner executes, **Then** results show per-CoT-type byte sizes and success/failure status for each fixture

---

### User Story 5 - Inbound Dual-Path Tolerance (Priority: P3)

A v2-capable node receives packets from both v1 (port 72) and v2 (port 78) mesh traffic. All inbound traffic is decoded and forwarded to connected TAK clients regardless of the local radio's firmware version.

**Why this priority**: Ensures no tactical data is lost in mixed deployments where some nodes only send v1.

**Independent Test**: Send a legacy TAKPacket (port 72) to a node running firmware 2.8.0+ and verify the connected ATAK client receives the PLI.

**Acceptance Scenarios**:

1. **Given** a node with firmware >= 2.8.0, **When** it receives a TAKPacket on port 72, **Then** the packet is decoded and broadcast to connected ATAK clients as valid CoT XML
2. **Given** a node with firmware >= 2.8.0, **When** it receives a TAKPacketV2 on port 78, **Then** the packet is decompressed, decoded, and broadcast to connected ATAK clients

---

### Edge Cases

- What happens when zstd decompression produces a payload exceeding MAX_DECOMPRESSED_SIZE? → Packet is rejected to prevent memory exhaustion
- What happens when the TAK server port 8089 is already in use? → Server start fails gracefully with user-visible error
- What happens when a CoT XML contains malformed or hostile content (XML injection)? → XML is sanitized/stripped before processing; CoTDetailStripper removes 16 known bloat elements before mesh transmission
- What happens when ATAK sends a CoT type not recognized by TakV2TypeMapper? → Falls back to CotType_Other with the raw type string preserved for round-trip fidelity
- What happens when an uncompressed packet arrives (flags byte 0xFF from TAK_TRACKER firmware)? → Treated as raw protobuf, bypassing zstd decompression
- What happens when the user denies the ACCESS_LOCAL_NETWORK permission on Android 17+? → TAK server cannot bind to localhost; server start fails with a user-visible error explaining the permission requirement
- What happens when a connected ATAK client disconnects and reconnects within 5 minutes? → Offline message queue (50-message cap, 5-minute TTL) replays missed messages on reconnect
- What happens on iOS where zstd compression is not yet available? → iOS uses uncompressed TAK_TRACKER mode (flags=0xFF); payloads exceeding ~225 bytes (the usable mesh MTU) are dropped, which in practice limits iOS to PLI, simple GeoChat, and small markers

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| TAKMeshIntegration | `core/takserver/…/TAKMeshIntegration.kt` (commonMain) | Bidirectional bridge between TAK server and mesh network |
| TAKServer | `core/takserver/…/TAKServer.kt` (commonMain expect) | Platform-agnostic TLS listener interface for ATAK/iTAK connections |
| TAKServerJvm | `core/takserver/…/TAKServerJvm.kt` (jvmAndroidMain actual) | JVM/Android TLS server implementation |
| TAKServerIos | `core/takserver/…/TAKServerIos.kt` (iosMain actual) | iOS stub server implementation |
| TAKServerManager | `core/takserver/…/TAKServerManager.kt` (commonMain) | Lifecycle manager for the TAK server (start/stop/broadcast) with 10-second keepalive interval |
| TAKPacketV2Conversion | `core/takserver/…/TAKPacketV2Conversion.kt` (commonMain) | CoTMessage ↔ TAKPacketV2 conversion for all CoT types |
| TAKPacketConversion | `core/takserver/…/TAKPacketConversion.kt` (commonMain) | Legacy CoTMessage ↔ TAKPacket (v1) conversion (PLI + GeoChat) |
| TakV2Compressor | `core/takserver/…/TakV2Compressor.kt` (expect/actual) | Zstd dictionary compression (JVM/Android via TAKPacket-SDK); iOS stub (uncompressed only) |
| TakV2TypeMapper | `core/takserver/…/TakV2TypeMapper.kt` (commonMain) | CoT type string ↔ enum mapping: 23 CoT types + 4 HOW types + default fallback |
| CoTDetailStripper | `core/takserver/…/CoTDetailStripper.kt` (commonMain) | Strips 16 bloat XML elements from CoT before mesh transmission to fit MTU |
| RouteDataPackageGenerator | `core/takserver/…/RouteDataPackageGenerator.kt` (commonMain) | Converts route CoT to ATAK-importable KML data packages |
| CoTXmlParser | `core/takserver/…/CoTXmlParser.kt` (commonMain) | Streaming XML parser for inbound CoT from ATAK clients |
| XmlUtils | `core/takserver/…/XmlUtils.kt` (commonMain) | XML escaping/sanitization utilities (5 special characters) |
| AtakFileWriter | `core/takserver/…/AtakFileWriter.kt` (expect/actual) | Platform filesystem access: androidMain (SAF/private dirs), jvmMain (desktop filesystem), iosMain (stub) |
| TAKConfigItemList | `feature/settings/…/TAKConfigItemList.kt` (commonMain) | Compose UI for TAK module configuration |
| TakPermissionUtil | `feature/settings/…/TakPermissionUtil.kt` (expect/actual) | Platform-specific permission handling (Android, iOS, JVM) |
| MeshService (wake lock) | `core/service/MeshService.kt` (androidMain) | Partial wake lock for reliable TAK server operation |
| Capabilities | `core/model/Capabilities.kt` (commonMain) | Firmware version detection for v2 support gating |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST detect firmware version and use TAKPacketV2 (port 78) for radios >= 2.8.0, falling back to TAKPacket (port 72) for older firmware
- **FR-002**: System MUST support encoding and decoding of all mapped CoT types (23 CoT types + 4 HOW types + CotType_Other fallback), including but not limited to: PLI, GeoChat, Marker, Route, DrawnShape subtypes (circle, ellipse, freeform, polygon, rectangle, telestration), Aircraft, Casevac, Emergency, Task, Ranging, Alert, Delete, Chat Receipts, and Waypoints
- **FR-003**: System MUST compress TAKPacketV2 payloads using zstd with pre-trained dictionaries (separate dictionaries for aircraft vs non-aircraft types)
- **FR-004**: System MUST reject compressed payloads exceeding MAX_DECOMPRESSED_SIZE to prevent memory exhaustion attacks
- **FR-005**: System MUST accept inbound packets on both port 72 (legacy) and port 78 (v2) regardless of local firmware version
- **FR-006**: System MUST run a local TLS/mTLS server on port 8089 accepting ATAK and iTAK client connections
- **FR-007**: System MUST hold a partial wake lock while the TAK server is active to prevent Android CPU throttling
- **FR-008**: System MUST generate valid KML data packages for route CoT events for ATAK file import
- **FR-009**: System MUST export .zip data packages containing connection certificates (server.p12, client.p12, ca.pem) and server configuration for client setup, importable by both ATAK and iTAK
- **FR-010**: System MUST sanitize inbound CoT XML to prevent XML injection attacks (escaping &, <, >, ", ')
- **FR-011**: System MUST preserve the full CoT type string for round-trip fidelity when the type maps to CotType_Other
- **FR-012**: System MUST drop unsupported CoT types (non-PLI, non-GeoChat) on legacy firmware with a logged warning
- **FR-013**: System MUST strip bloat XML detail elements (16 known element types: color, strokeColor, strokeWeight, fillColor, labels_on, usericon, model, shape, height, height_unit, fileshare, __video, archive, precisionlocation, tog, _flow-tags_) from outbound CoT before mesh transmission to maximize payload fit within MTU
- **FR-014**: System MUST maintain an offline message queue (50-message cap, 5-minute per-message TTL) to replay missed messages when ATAK clients reconnect after brief disconnections. When the cap is reached, the oldest message is evicted (FIFO). Messages older than 5 minutes are silently discarded on dequeue.
- **FR-015**: System MUST send keepalive packets to connected TAK clients at 10-second intervals (below ATAK's 15-second stale threshold) to maintain connection liveness
- **FR-016**: System MUST request ACCESS_LOCAL_NETWORK permission on Android 17+ (API 37) for TAK server localhost binding, and display a user-visible error if the permission is denied

### Non-Functional Requirements

- **NFR-001**: Compressed TAKPacketV2 payloads MUST fit within the usable mesh payload (~225 bytes after protobuf framing within the 237-byte raw LoRa MTU) for single-packet transmission
- **NFR-002**: TAK server connection MUST survive screen-off and Doze mode for at least 30 minutes without disconnection
- **NFR-003**: CoT message round-trip (ATAK → mesh → remote ATAK) MUST complete within the mesh network's standard transmission latency (no added processing delay > 100ms)
- **NFR-004**: Route data packages MUST be written to app-private or cache directories (no MANAGE_EXTERNAL_STORAGE required); ATAK integration relies on content sharing or documented import paths

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | All business logic: TAKMeshIntegration, conversions, models, parser, server manager, detail stripper, XML utils, config UI | All business logic and UI per Constitution §I, §III |
| `androidMain` | MeshService wake lock, AtakFileWriter (Android filesystem/SAF), TakPermissionUtil (runtime permissions) | Platform-specific Android APIs |
| `jvmAndroidMain` | TAKServerJvm TLS implementation, TakV2Compressor (zstd via TAKPacket-SDK), TakCertLoader, TakFixtureLoader | Shared JVM/Android TLS, compression, and I/O |
| `jvmMain` | AtakFileWriter (desktop filesystem), TakPermissionUtil (no-op) | Desktop platform support for file operations |
| `iosMain` | TAKServerIos, TakV2Compressor (stub — uncompressed TAK_TRACKER mode only), AtakFileWriter (stub), TakFixtureLoader | Platform stubs pending Swift SDK integration |

## Design Standards Compliance

- [x] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [x] M3 component selection verified (SwitchPreference, DropDownPreference, TitledCard used)
- [x] Accessibility: TalkBack semantics on TAK config controls, standard touch targets
- [x] Typography: M3 scale for hierarchy in TAK config screen

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed (CoT data stays local to device/mesh)
- [x] No new network calls that transmit user data (TAK server is local-only, listening on loopback + LAN)
- [x] Proto submodule (`core/proto`) not modified (read-only upstream, pegged to master)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All mapped CoT types (28 total mappings across 23 CoT types, 4 HOW types, and default fallback) successfully round-trip encode/decode through the test fixture suite
- **SC-002**: Compressed payload size for typical PLI messages stays below 100 bytes (well within ~225B usable mesh payload)
- **SC-003**: Mixed-firmware mesh (2.7.x + 2.8.0+) maintains PLI and GeoChat exchange without errors for legacy nodes
- **SC-004**: TAK server maintains ATAK client connections for 30+ minutes with device screen off
- **SC-005**: Route CoT events result in importable KML data packages appearing in ATAK within one mesh cycle
- **SC-006**: Test fixture suite (40 XML fixtures across 9 test classes, 65+ test methods) covering all CoT types passes with correct round-trip encoding/decoding

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` (from `core/ui/icon/`)
- Float values pre-formatted with `NumberFormatter.format()` (CMP constraint)
- Firmware version is available from the connected radio's metadata at connection time
- ATAK clients support standard TAK Server protocol (TLS on port 8089, data package import)
- Zstd dictionaries are pre-trained and bundled as binary resources (not trained at runtime)
- The 237-byte raw LoRa MTU is a hard limit imposed by the radio hardware; usable payload is ~225 bytes after protobuf framing
- Route data packages are written to app-private/cache directories (no broad filesystem permissions required)
- iOS implementation uses uncompressed TAK_TRACKER mode (flags=0xFF) pending platform-specific zstd library integration via Swift SDK interop
- Desktop (JVM) has partial TAK support: filesystem operations via `jvmMain` AtakFileWriter, TLS server via `jvmAndroidMain`
- Android 17+ (API 37) requires ACCESS_LOCAL_NETWORK permission for TAK server localhost binding
