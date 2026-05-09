# Feature Specification: Core Network & Radio Transport

**Feature Branch**: `014-core-network`  
**Created**: 2026-07-27  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `core/network` module

## Summary

Core Network provides the radio transport layer, MQTT connectivity, network monitoring, service discovery, and HTTP client infrastructure for Meshtastic-Android. The module implements a multi-transport architecture supporting BLE, TCP, Serial, and Mock radios through a unified `RadioTransport` interface. It includes the Meshtastic stream framing codec (START1/START2 protocol), BLE reconnect policy with exponential backoff, MQTT client integration for mesh-to-internet bridging, mDNS/NSD service discovery for local Meshtastic devices, network connectivity monitoring, and HTTP client configuration for firmware/hardware API access. Platform-specific code handles Android USB/Serial, mDNS, and network monitoring, while the transport core is in `commonMain`.

## Goals

1. **Multi-transport radio connectivity** — support BLE, TCP, Serial (Android), and Mock transports through a unified `RadioTransport` interface.
2. **Reliable BLE transport** — implement automatic reconnection with configurable backoff, rate limiting, and firmware handshake awareness.
3. **Stream framing** — encode/decode the Meshtastic START1/START2 + length-prefix protocol for serial and TCP byte streams.
4. **MQTT mesh bridging** — connect to MQTT brokers for mesh-to-internet packet relay with topic-based routing.
5. **Network awareness** — monitor connectivity state and discover local Meshtastic devices via mDNS/NSD.
6. **API client** — configure Ktor HTTP client for firmware release and device hardware catalog fetches.

## Non-Goals

- BLE scanning and GATT abstraction — handled by `core/ble`.
- Packet decoding or protobuf parsing — handled by `core/data`.
- Radio configuration management — handled by `core/data` repositories.
- UI for transport selection — handled by `feature/device-connections`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — BLE Radio Transport Connection (Priority: P1)

The BLE radio transport scans for a Meshtastic device, establishes a GATT connection, discovers the Meshtastic service profile, and sets up characteristic observation for incoming radio data. It handles automatic reconnection on disconnection with configurable backoff.

**Why this priority**: BLE is the primary transport for mobile users. ~90% of connections use BLE.

**Independent Test**: Can be validated with mock `BleConnection` and `BleScanner` fakes.

**Acceptance Scenarios**:

1. **Given** a Meshtastic device is advertising, **When** `BleRadioTransport.connect(address)` is called, **Then** the transport scans, connects, discovers the service profile, and begins observing `FromRadio`.
2. **Given** the BLE connection drops, **When** `BleReconnectPolicy` triggers, **Then** reconnection is attempted with exponential backoff (configurable via `BleReconnectPolicy`).
3. **Given** reconnection is rate-limited (>5 attempts in 30s), **When** the limit is hit, **Then** reconnection pauses and the callback is notified.
4. **Given** a `ToRadio` packet is ready to send, **When** `sendToRadio(bytes)` is called, **Then** the payload is written to the `ToRadio` characteristic with retry.
5. **Given** the connection is active, **When** `FromRadio` notifications arrive, **Then** they are forwarded to the `RadioTransportCallback`.

---

### User Story 2 — Stream Framing (TCP/Serial) (Priority: P1)

The `StreamFrameCodec` implements the Meshtastic START1/START2 framing protocol used by both TCP and Serial transports. It encodes outbound payloads into framed packets and decodes inbound byte streams back into complete payloads.

**Why this priority**: TCP and Serial transports rely on correct framing. A framing bug corrupts all communication.

**Independent Test**: Fully testable in isolation — pure function on byte arrays.

**Acceptance Scenarios**:

1. **Given** a payload of N bytes, **When** `frameAndSend()` is called, **Then** the output is `[0x94][0xC3][MSB(N)][LSB(N)][payload]`.
2. **Given** a byte stream with valid framing, **When** each byte is fed to `processInputByte()`, **Then** the complete payload is delivered via `onPacketReceived`.
3. **Given** a corrupted stream (missing START2), **When** the sync is lost, **Then** the state machine resets and logs an error.
4. **Given** a frame with length > 512, **When** the length field is parsed, **Then** the packet is rejected and sync is reset.
5. **Given** concurrent writers, **When** `frameAndSend()` is called from multiple coroutines, **Then** the write mutex prevents interleaved frames.

---

### User Story 3 — MQTT Mesh Bridging (Priority: P2)

The MQTT repository manages connections to MQTT brokers for mesh-to-internet packet relay. It subscribes to topics based on the device's channel configuration and publishes outbound mesh packets as MQTT messages.

**Why this priority**: MQTT enables internet-connected mesh communication. Incorrect topic handling causes message loss.

**Independent Test**: Can be tested with mock `MqttClient`.

**Acceptance Scenarios**:

1. **Given** MQTT is configured on the radio, **When** `connect()` is called, **Then** the MQTT client connects with the configured broker, port, username, and password.
2. **Given** an active MQTT connection, **When** the radio publishes a `MqttClientProxyMessage`, **Then** the message is published to the correct topic.
3. **Given** subscribed topics, **When** an inbound MQTT message arrives, **Then** it is decoded (protobuf or JSON) and forwarded to the radio.
4. **Given** a network disconnection, **When** the MQTT client disconnects, **Then** the state transitions to `Disconnected` and reconnection is attempted.
5. **Given** a JSON-encoded MQTT message, **When** decoded, **Then** `MqttJsonPayload` is constructed with the service envelope.

---

### User Story 4 — Network Monitoring & Service Discovery (Priority: P2)

The network repository exposes a reactive `Flow<Boolean>` for network availability and a `Flow<List<DiscoveredService>>` for mDNS-discovered Meshtastic services.

**Why this priority**: Network state informs MQTT connectivity decisions and TCP transport availability.

**Independent Test**: Android `ConnectivityManager` can be mocked; NSD requires integration test.

**Acceptance Scenarios**:

1. **Given** the device has internet connectivity, **When** `networkAvailable` is collected, **Then** it emits `true`.
2. **Given** connectivity changes, **When** the network state transitions, **Then** `networkAvailable` emits the new state with deduplication.
3. **Given** a Meshtastic device is advertising via mDNS, **When** service discovery is active, **Then** it appears in `resolvedList`.

---

### User Story 5 — TCP Radio Transport (Priority: P2)

The TCP transport connects to a Meshtastic device over WiFi using the stream framing protocol on port 4403. It wraps `TcpTransport` with wake bytes and heartbeat sending.

**Why this priority**: TCP is the secondary transport for desktop and WiFi-connected devices.

**Independent Test**: Can be tested with a loopback TCP server.

**Acceptance Scenarios**:

1. **Given** a Meshtastic device is reachable at `host:4403`, **When** `TcpRadioTransport.connect()` is called, **Then** the transport sends wake bytes and begins stream frame decoding.
2. **Given** an active TCP connection, **When** a `ToRadio` payload is ready, **Then** it is framed and sent via the stream codec.
3. **Given** the TCP socket disconnects, **When** the transport detects the error, **Then** the callback is notified and reconnection can be attempted.

---

### Edge Cases

- What happens when BLE reconnection is attempted while Bluetooth is disabled? `UnmetRequirementException` is classified and surfaced to the callback.
- What happens when `StreamFrameCodec` receives a zero-length packet? It delivers an empty `ByteArray` via `onPacketReceived`.
- What happens when MQTT subscription fails? The error is logged and the connection state remains active (best-effort).
- What happens when serial USB device is disconnected during write? `IOException` is caught and reported as a transport error.

## Architecture

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| `BleRadioTransport` | `radio/BleRadioTransport.kt` (506 LOC) | BLE-based radio transport with reconnection |
| `BleReconnectPolicy` | `radio/BleReconnectPolicy.kt` | Configurable reconnection backoff and rate limiting |
| `StreamTransport` | `radio/StreamTransport.kt` | Base class for stream-framed transports (TCP, Serial) |
| `StreamFrameCodec` | `transport/StreamFrameCodec.kt` (154 LOC) | START1/START2 framing encode/decode |
| `HeartbeatSender` | `transport/HeartbeatSender.kt` | Periodic heartbeat for stream transports |
| `TcpRadioTransport` | `radio/TcpRadioTransport.kt` (jvmAndroidMain) | TCP transport on port 4403 |
| `TcpTransport` | `transport/TcpTransport.kt` (jvmAndroidMain) | Raw TCP socket wrapper |
| `SerialRadioTransport` | `radio/SerialRadioTransport.kt` (androidMain) | USB serial transport |
| `MockRadioTransport` | `radio/MockRadioTransport.kt` | Test/demo transport |
| `NopRadioTransport` | `radio/NopRadioTransport.kt` | No-op transport for unconnected state |
| `BaseRadioTransportFactory` | `radio/BaseRadioTransportFactory.kt` | Factory for creating transport instances |
| `MQTTRepositoryImpl` | `repository/MQTTRepositoryImpl.kt` (312 LOC) | MQTT client lifecycle and topic management |
| `NetworkRepositoryImpl` | `repository/NetworkRepositoryImpl.kt` | Network availability + service discovery flows |
| `NetworkMonitor` | `repository/NetworkMonitor.kt` | Interface for connectivity monitoring |
| `ServiceDiscovery` | `repository/ServiceDiscovery.kt` | Interface for mDNS/NSD discovery |
| `FirmwareReleaseRemoteDataSource` | `FirmwareReleaseRemoteDataSource.kt` | Ktor HTTP fetch for firmware releases |
| `DeviceHardwareRemoteDataSource` | `DeviceHardwareRemoteDataSource.kt` | Ktor HTTP fetch for hardware catalog |
| `HttpClientDefaults` | `HttpClientDefaults.kt` | Ktor client configuration (timeouts, content negotiation) |
| `ApiService` | `service/ApiService.kt` | REST API service abstraction |
| `CoreNetworkModule` | `di/CoreNetworkModule.kt` | Koin DI module with JSON provider |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST implement `RadioTransport` interface for BLE, TCP, Serial, Mock, and Nop transports.
- **FR-002**: BLE transport MUST scan, connect, discover Meshtastic profile, and observe `FromRadio` notifications.
- **FR-003**: BLE transport MUST implement automatic reconnection with `BleReconnectPolicy` (configurable backoff, rate limiting).
- **FR-004**: System MUST implement `StreamFrameCodec` with START1 (0x94) / START2 (0xC3) / 2-byte length / payload framing.
- **FR-005**: `StreamFrameCodec` MUST reject frames with payload > 512 bytes (`MAX_TO_FROM_RADIO_SIZE`).
- **FR-006**: `StreamFrameCodec.frameAndSend()` MUST be thread-safe via internal mutex.
- **FR-007**: TCP transport MUST connect to port 4403 and send wake bytes before framing begins.
- **FR-008**: System MUST implement `MQTTRepository` with connect, subscribe, publish, disconnect, and connection state tracking.
- **FR-009**: MQTT client MUST subscribe to topic patterns based on radio channel configuration.
- **FR-010**: MQTT client MUST decode both protobuf and JSON-encoded inbound messages.
- **FR-011**: System MUST provide `NetworkMonitor` with reactive connectivity state.
- **FR-012**: System MUST provide `ServiceDiscovery` for mDNS/NSD-based local device discovery.
- **FR-013**: System MUST provide Ktor HTTP client for firmware and hardware catalog API access.
- **FR-014**: `HeartbeatSender` MUST send periodic keep-alive packets on stream transports.
- **FR-015**: Serial transport MUST handle USB device attach/detach events via `UsbBroadcastReceiver`.
- **FR-016**: BLE transport MUST request high connection priority for latency-sensitive operations (firmware update).

### Non-Functional Requirements

- **NFR-001**: All shared transport logic MUST reside in `commonMain` (Constitution §I).
- **NFR-002**: Platform-specific transports (Serial, USB) MUST be in `androidMain`; TCP in `jvmAndroidMain`.
- **NFR-003**: BLE reconnect backoff MUST not exceed configured maximum delay.
- **NFR-004**: MQTT JSON parsing MUST use `safeCatching {}` (Constitution §VII).
- **NFR-005**: HTTP client MUST use lenient JSON with `ignoreUnknownKeys = true`.
- **NFR-006**: Stream codec debug output MUST use line-buffered `Logger.d` for readability.

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 23 files (~3,200 LOC) | Transport interfaces, BLE transport, stream codec, MQTT, network, HTTP |
| `commonTest` | 7 files (~600 LOC) | BLE transport, reconnect policy, stream codec, MQTT tests |
| `jvmAndroidMain` | 2 files (~300 LOC) | TCP transport + socket |
| `jvmMain` | 3 files (~250 LOC) | JVM network monitor, service discovery, serial transport |
| `jvmTest` | 1 file (~100 LOC) | JVM service discovery test |
| `androidMain` | 14 files (~1,100 LOC) | USB/Serial, Android network monitor, NSD, connectivity |

## Privacy Assessment

- [x] No PII logged — device addresses anonymized, MQTT credentials not logged
- [x] MQTT topic patterns do not contain user identifiers
- [x] Proto submodule not modified

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `StreamFrameCodec` round-trips: frame → parse produces the original payload for any size 0–512.
- **SC-002**: BLE transport successfully connects, observes, and sends data with mock `BleConnection` in tests.
- **SC-003**: `BleReconnectPolicy` correctly backs off after disconnection — delays increase up to the configured maximum.
- **SC-004**: MQTT repository connects to broker and subscribes to configured topics within 5s.
- **SC-005**: BLE reconnect crash test verifies no crash on rapid connect/disconnect cycles.
- **SC-006**: Network monitor emits correct state transitions on connectivity changes.
- **SC-007**: All 8 existing test files pass with `allTests` target.
- **SC-008**: Stream codec handles all edge cases: zero-length packets, oversized frames, lost sync recovery.
- **SC-009**: HTTP client fetches firmware release JSON and parses it correctly.

## Assumptions

- BLE is the primary transport (~90% of connections); TCP and Serial are secondary.
- `core/ble` provides `BleConnection`, `BleScanner`, and `BleConnectionFactory` via Koin injection.
- MQTT client library is wrapped behind `MqttClient` interface from the `:mqtt` module.
- USB serial uses `usb-serial-for-android` library (Android-only).
- TCP port 4403 is the standard Meshtastic TCP service port.
- mDNS service type for Meshtastic is `_meshtastic._tcp.local.`.

