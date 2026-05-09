# Implementation Plan: Core Network & Radio Transport

**Branch**: `014-core-network` | **Date**: 2026-07-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/014-core-network/spec.md`
**Status**: Migrated — all implementation complete, plan reverse-engineered from existing code.

## Summary

Core Network implements the multi-transport radio architecture (BLE, TCP, Serial, Mock), Meshtastic stream framing, MQTT mesh bridging, network monitoring, mDNS service discovery, and HTTP client infrastructure. The BLE transport (506 LOC) is the most complex component, with automatic reconnection and firmware handshake awareness.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Kable (via `core/ble`), Ktor (HTTP), kotlinx.serialization, kotlinx.coroutines, Kermit, MQTT client library  
**Testing**: KMP `allTests` — 7 commonTest + 1 jvmTest files, ~700 LOC; Turbine, Mokkery  
**Target Platform**: Android, Desktop (JVM), iOS (partial)  
**Constraints**: BLE/MQTT/stream logic in `commonMain`; TCP in `jvmAndroidMain`; Serial in `androidMain`  
**Scale/Scope**: 23 commonMain files (~3,200 LOC), 16 platform files (~1,650 LOC), 8 test files (~700 LOC)

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | BLE transport, stream codec, MQTT in `commonMain`. Serial/USB correctly in `androidMain`. |
| II. Zero Lint Tolerance | ✅ PASS | `detekt-baseline.xml` present. Suppressions: `TooManyFunctions`, `TooGenericExceptionCaught`. |
| III. Compose Multiplatform UI | N/A | No UI code. |
| IV. Privacy First | ✅ PASS | Device addresses not logged; MQTT credentials suppressed. |
| VI. Verify Before Push | ⚠️ PARTIAL | Good coverage for BLE transport and stream codec; gaps in TCP, Serial, Mock. |
| VII. Coroutine Safety | ✅ PASS | `safeCatching` in MQTT; `NonCancellable` for disconnect cleanup; mutex for codec writes. |
| IX. Branch & Scope Hygiene | ✅ PASS | Module cleanly scoped. Transport factory pattern enables extension. |

**Gate Result**: ✅ All applicable principles satisfied.

## Project Structure

```
core/network/src/
├── commonMain/kotlin/org/meshtastic/core/network/
│   ├── di/CoreNetworkModule.kt
│   ├── radio/
│   │   ├── BleRadioTransport.kt          # 506 LOC — BLE transport with reconnect
│   │   ├── BleReconnectPolicy.kt         # Backoff configuration
│   │   ├── StreamTransport.kt            # Base for framed transports
│   │   ├── MockRadioTransport.kt         # Test/demo transport
│   │   ├── NopRadioTransport.kt          # No-op transport
│   │   └── BaseRadioTransportFactory.kt  # Factory for transport creation
│   ├── transport/
│   │   ├── StreamFrameCodec.kt           # 154 LOC — START1/START2 framing
│   │   └── HeartbeatSender.kt            # Keep-alive for stream transports
│   ├── repository/
│   │   ├── MQTTRepositoryImpl.kt         # 312 LOC — MQTT lifecycle
│   │   ├── MQTTRepository.kt             # Interface
│   │   ├── NetworkRepositoryImpl.kt      # Network + discovery flows
│   │   ├── NetworkRepository.kt          # Interface
│   │   ├── NetworkMonitor.kt             # Connectivity interface
│   │   ├── ServiceDiscovery.kt           # mDNS interface
│   │   ├── DiscoveredService.kt          # Service discovery model
│   │   ├── NetworkConstants.kt           # Network-related constants
│   │   └── KermitMqttLogger.kt           # MQTT → Kermit logging bridge
│   ├── service/ApiService.kt             # REST API abstraction
│   ├── HttpClientDefaults.kt             # Ktor client configuration
│   ├── KermitHttpLogger.kt               # Ktor → Kermit logging
│   ├── FirmwareReleaseRemoteDataSource.kt
│   └── DeviceHardwareRemoteDataSource.kt
├── commonTest/ (7 files — BLE transport, reconnect, stream codec, MQTT)
├── jvmAndroidMain/ (2 files — TCP transport + socket)
├── jvmMain/ (3 files — network monitor, service discovery, serial)
├── jvmTest/ (1 file — service discovery)
└── androidMain/ (14 files — USB/Serial, NSD, connectivity, DI)
```

## Implementation Phases

### Phase 1 — Transport Interfaces & Stream Codec (Complete)

Core abstractions: `RadioTransport` interface, `StreamFrameCodec` (START1/START2 protocol), `HeartbeatSender`, `NopRadioTransport`.

### Phase 2 — BLE Radio Transport (Complete)

The primary transport: `BleRadioTransport` (506 LOC) with scan → connect → profile discovery → observation pipeline. `BleReconnectPolicy` for automatic reconnection with configurable exponential backoff and rate limiting.

### Phase 3 — Secondary Transports (Complete)

TCP transport (`jvmAndroidMain`), Serial/USB transport (`androidMain`), Mock transport for testing. Transport factory for runtime transport selection.

### Phase 4 — MQTT & Network Infrastructure (Complete)

`MQTTRepositoryImpl` (312 LOC) with broker connection, topic management, protobuf/JSON decoding. Network monitoring, mDNS service discovery, and HTTP client for API access.

## Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Transport interface | Unified `RadioTransport` | All transports share the same contract for data flow |
| BLE reconnect | Configurable `BleReconnectPolicy` | Allows tuning backoff for different use cases |
| Stream framing | Byte-at-a-time state machine | Handles partial reads and stream corruption recovery |
| Frame max size | 512 bytes (`MAX_TO_FROM_RADIO_SIZE`) | Matches firmware's maximum protobuf message size |
| TCP port | 4403 (hardcoded) | Standard Meshtastic TCP service port |
| MQTT library | Wrapped `MqttClient` from `:mqtt` module | Isolates MQTT library choice from the network layer |
| JSON config | Lenient + `ignoreUnknownKeys` | Tolerates server-side schema changes |
| Write thread safety | Mutex in `StreamFrameCodec` | Prevents interleaved frame corruption |
| Wake bytes | 4x START1 before TCP connect | Rouses sleeping Meshtastic devices |
| Reconnect rate limit | >5 attempts in 30s | Prevents aggressive retry loops that drain battery |

## Gaps Identified

| Gap | Severity | Recommendation |
|-----|----------|----------------|
| `TcpRadioTransport` has no unit test | ⚠️ Medium | Add test with loopback server |
| `SerialRadioTransport` has no unit test | ⚠️ Medium | Add Android instrumented test |
| `MockRadioTransport` has no unit test | ⚠️ Low | Trivial but documents the mock contract |
| MQTT test coverage is minimal (1 file) | ⚠️ Medium | Add tests for topic management, JSON/protobuf decode, reconnect |
| `HeartbeatSender` has no unit test | ⚠️ Low | Add test for interval and cancellation |
| No end-to-end transport integration test | ⚠️ Medium | Test: create transport → connect → send → receive → disconnect |
| `FirmwareReleaseRemoteDataSource` has no test | ⚠️ Low | Add test with Ktor mock engine |

