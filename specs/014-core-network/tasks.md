# Tasks: Core Network & Radio Transport

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Status**: Migrated — all existing tasks marked complete. Gap tasks marked incomplete.  
**Task Prefix**: `NET-T`

---

## Phase 1 — Transport Interfaces & Stream Codec

### NET-T001: Koin DI module + JSON provider [x]

- **File**: `di/CoreNetworkModule.kt`
- Provides lenient `Json` instance with `ignoreUnknownKeys`, `coerceInputValues`.
- `@ComponentScan("org.meshtastic.core.network")` for auto-discovery.
- **Test**: Module loads without error.

### NET-T002: StreamFrameCodec [x]

- **File**: `transport/StreamFrameCodec.kt` (~154 LOC)
- START1 (0x94) / START2 (0xC3) / 2-byte-length / payload framing.
- Byte-at-a-time state machine for decode; mutex-protected `frameAndSend()` for encode.
- Max payload 512 bytes; sync recovery on corruption.
- **Test**: `StreamFrameCodecTest.kt` — covers round-trip, oversized, zero-length, lost sync.

### NET-T003: HeartbeatSender [x]

- **File**: `transport/HeartbeatSender.kt`
- Periodic keep-alive for stream transports (TCP, Serial).
- Configurable interval.
- **Test**: Verified via transport integration.

### NET-T004: NopRadioTransport [x]

- **File**: `radio/NopRadioTransport.kt`
- No-op implementation for unconnected state.
- **Test**: Trivial — no behavior to test.

### NET-T005: StreamTransport base class [x]

- **File**: `radio/StreamTransport.kt`
- Shared base for TCP and Serial transports.
- Integrates `StreamFrameCodec` and `HeartbeatSender`.
- **Test**: Verified via `StreamTransportTest.kt`.

### NET-T006: BaseRadioTransportFactory [x]

- **File**: `radio/BaseRadioTransportFactory.kt`
- Factory for creating transport instances by type (BLE, TCP, Serial, Mock).
- **Test**: Verified via integration.

---

## Phase 2 — BLE Radio Transport

### NET-T007: BleRadioTransport [x]

- **File**: `radio/BleRadioTransport.kt` (~506 LOC)
- Complete BLE transport: scan → connect → profile discovery → FromRadio observation → ToRadio write.
- Integrates `BleConnection`, `BleScanner`, `BleConnectionFactory`, `MeshtasticRadioProfile`.
- Handles disconnect detection and callback notification.
- High connection priority request for firmware updates.
- **Test**: `BleRadioTransportTest.kt` — covers connect, send, receive, disconnect.

### NET-T008: BleReconnectPolicy [x]

- **File**: `radio/BleReconnectPolicy.kt`
- Configurable exponential backoff for reconnection.
- Rate limiting: >5 attempts in 30s triggers pause.
- **Test**: `BleReconnectPolicyTest.kt`, `ReconnectBackoffTest.kt` — covers backoff progression, rate limiting.

### NET-T009: BLE reconnect crash resilience [x]

- **File**: (tested within `BleRadioTransport`)
- Rapid connect/disconnect cycles must not crash.
- Mutex protection for concurrent state access.
- **Test**: `BleRadioTransportReconnectCrashTest.kt` — rapid cycle stress test.

---

## Phase 3 — Secondary Transports

### NET-T010: TcpRadioTransport + TcpTransport [x]

- **Files**: `jvmAndroidMain/.../TcpRadioTransport.kt`, `TcpTransport.kt`
- TCP connection on port 4403 with wake bytes.
- Stream-framed encoding/decoding via `StreamFrameCodec`.
- **Test**: Verified via manual integration (no automated test).

### NET-T011: SerialRadioTransport (Android) [x]

- **Files**: `androidMain/.../SerialRadioTransport.kt`, `SerialConnection*.kt`, `UsbManager.kt`, `UsbBroadcastReceiver.kt`
- USB serial transport using `usb-serial-for-android`.
- Handles USB attach/detach events, probe table, serial parameters.
- **Test**: Verified via Android device testing (no automated test).

### NET-T012: USB Repository (Android) [x]

- **File**: `androidMain/.../UsbRepository.kt`
- USB device enumeration and permission management.
- **Test**: Verified via device testing.

### NET-T013: MockRadioTransport [x]

- **File**: `radio/MockRadioTransport.kt`
- Controllable transport for tests and demo mode.
- **Test**: Used as a dependency in other tests.

### NET-T014: Android transport factory [x]

- **File**: `androidMain/.../AndroidRadioTransportFactory.kt`
- Extends `BaseRadioTransportFactory` with Serial and TCP support.
- **Test**: Verified via app integration.

---

## Phase 4 — MQTT & Network Infrastructure

### NET-T015: MQTTRepositoryImpl [x]

- **File**: `repository/MQTTRepositoryImpl.kt` (~312 LOC)
- MQTT client lifecycle: connect, subscribe (topic patterns from channel config), publish, disconnect.
- Decodes protobuf and JSON inbound messages.
- Connection state as `StateFlow`.
- Semaphore-based concurrency control.
- **Test**: `MQTTRepositoryImplTest.kt` — covers basic lifecycle.

### NET-T016: NetworkRepositoryImpl [x]

- **File**: `repository/NetworkRepositoryImpl.kt` (~62 LOC)
- Exposes `networkAvailable: Flow<Boolean>` and `resolvedList: Flow<List<DiscoveredService>>`.
- `shareIn` with `WhileSubscribed` for lifecycle awareness.
- **Test**: Verified via integration.

### NET-T017: Network monitoring platform implementations [x]

- **Files**: `androidMain/.../AndroidNetworkMonitor.kt`, `ConnectivityManager.kt`, `jvmMain/.../JvmNetworkMonitor.kt`
- Android: `ConnectivityManager` callback; JVM: periodic polling.
- **Test**: Verified via platform integration.

### NET-T018: Service discovery (mDNS/NSD) [x]

- **Files**: `repository/ServiceDiscovery.kt`, `DiscoveredService.kt`, `androidMain/.../AndroidServiceDiscovery.kt`, `NsdManager.kt`, `jvmMain/.../JvmServiceDiscovery.kt`
- Android: NSD API; JVM: JmDNS.
- **Test**: `JvmServiceDiscoveryTest.kt` — JVM-only integration test.

### NET-T019: HTTP client + remote data sources [x]

- **Files**: `HttpClientDefaults.kt`, `KermitHttpLogger.kt`, `FirmwareReleaseRemoteDataSource.kt`, `DeviceHardwareRemoteDataSource.kt`, `service/ApiService.kt`
- Ktor client with lenient JSON, content negotiation, timeouts.
- Fetches firmware releases and hardware catalog from GitHub API.
- **Test**: Verified via firmware update feature integration.

### NET-T020: MQTT logging bridge [x]

- **File**: `repository/KermitMqttLogger.kt`
- Bridges MQTT client logging to Kermit.
- **Test**: Verified via MQTT operation logging.

### NET-T021: Network constants [x]

- **File**: `repository/NetworkConstants.kt`
- MQTT, TCP, and network-related constants.
- **Test**: Used as dependencies throughout.

---

## Gap Tasks (Incomplete)

### NET-T022: Add TcpRadioTransport unit tests [ ]

- **File to create**: `jvmAndroidTest/.../TcpRadioTransportTest.kt`
- Test with loopback TCP server: connect, frame, send, receive, disconnect.
- **Priority**: Medium

### NET-T023: Add SerialRadioTransport instrumented tests [ ]

- **File to create**: `androidDeviceTest/.../SerialRadioTransportTest.kt`
- Test USB attach/detach event handling; serial parameter configuration.
- **Priority**: Medium

### NET-T024: Expand MQTT test coverage [ ]

- **File to extend**: `commonTest/.../MQTTRepositoryImplTest.kt`
- Add tests: topic pattern construction, JSON decode, protobuf decode, reconnect, subscription failure.
- **Priority**: Medium

### NET-T025: Add HeartbeatSender unit test [ ]

- **File to create**: `commonTest/.../HeartbeatSenderTest.kt`
- Test periodic interval, cancellation, edge cases.
- **Priority**: Low

### NET-T026: Add HTTP remote data source tests [ ]

- **File to create**: `commonTest/.../FirmwareReleaseRemoteDataSourceTest.kt`
- Test with Ktor `MockEngine`: success, error, malformed JSON.
- **Priority**: Low

