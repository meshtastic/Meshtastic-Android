# Quickstart: TAK v2 Protocol Integration

## Prerequisites

- JDK 21
- Android Studio (latest stable)
- `ANDROID_HOME` environment variable set
- Git submodule initialized: `git submodule update --init`
- `local.properties` exists: `cp secrets.defaults.properties local.properties`

## Build & Test

```bash
# Full verification
./gradlew spotlessApply spotlessCheck detekt assembleDebug :core:takserver:allTests :feature:settings:allTests

# TAK module tests only
./gradlew :core:takserver:allTests

# Quick compile check (no tests)
./gradlew :core:takserver:compileKotlinJvm
```

## Module Overview

The TAK v2 implementation lives primarily in `core/takserver/`:

| Layer | Location | Purpose |
|-------|----------|---------|
| Business Logic | `core/takserver/src/commonMain/` | All conversion, compression, parsing, server management |
| Platform (JVM) | `core/takserver/src/jvmAndroidMain/` | TLS server, zstd compression, cert loading |
| Platform (Android) | `core/takserver/src/androidMain/` | File writer (SAF) |
| Platform (iOS) | `core/takserver/src/iosMain/` | Stubs (uncompressed mode) |
| UI | `feature/settings/.../TAKConfigItemList.kt` | Config screen |
| Wake Lock | `core/service/.../MeshService.kt` | CPU keepalive |
| Version Gate | `core/model/.../Capabilities.kt` | `supportsTakV2` flag |

## Key Entry Points

### Starting the TAK Server

The TAK server is started via `TAKMeshIntegration.start(scope)` which is called from `MeshService` when a device connects. The lifecycle:

1. `MeshService.onStartCommand()` → acquires wake lock
2. `TAKMeshIntegration.start(scope)` → wires up three collection jobs:
   - Inbound: TAK client → mesh
   - Outbound: mesh → TAK clients
   - Config: team/role sync
3. `TAKServerManager.start(scope)` → starts TLS listener on port 8089

### Adding a New CoT Type

1. Add enum value to `TakV2TypeMapper` bidirectional map
2. Add test fixture XML in `src/jvmAndroidMain/resources/tak_test_fixtures/`
3. Verify round-trip in `TAKPacketV2RawDetailTest` or `CoTConversionTest`
4. Run: `./gradlew :core:takserver:allTests`

### Testing with ATAK

1. Enable TAK server in Settings → Module Configuration → TAK
2. Tap "Export Data Package" → share to ATAK
3. In ATAK: Settings → Network → TAK Servers → Import
4. Verify connection count shows 1 in Meshtastic UI

## Architecture Flow

```
ATAK Client ──TLS──▶ TAKServer (port 8089)
                         │
                         ▼
                  TAKServerManager
                         │
                         ▼ inboundMessages
                  TAKMeshIntegration
                    │            │
          ┌─────────┘            └──────────┐
          ▼                                 ▼
   sendCoTToMeshV2()              sendCoTToMeshV1()
   (fw ≥ 2.8.0)                  (fw < 2.8.0)
          │                                 │
          ▼                                 ▼
   TakV2Compressor                 TAKPacketConversion
   (zstd + dict)                   (raw protobuf)
          │                                 │
          ▼                                 ▼
   Port 78 (ATAK_PLUGIN_V2)       Port 72 (ATAK_PLUGIN)
          │                                 │
          └────────────┬────────────────────┘
                       ▼
                  Mesh Network (LoRa)
```

## Test Fixtures

38 XML fixture files in `src/jvmAndroidMain/resources/tak_test_fixtures/` covering:
- PLI: 6 variants (basic, full, itak, stationary, takaware, webtak)
- GeoChat: 3 variants (broadcast, DM, simple)
- Markers: 6 variants (2525, goto, goto-itak, icon-set, spot, tank)
- Drawings: 7 variants (circle, ellipse, freeform, polygon, rectangle, rectangle-itak, telestration)
- Routes: 2 variants (3wp, itak-3wp)
- Aircraft: 2 variants (ADSB, hostile)
- Ranging: 3 variants (bullseye, circle, line)
- Others: casevac (2), emergency (2), alert (1), delete (1), chat-receipt (2), task (1), waypoint (1)

## Dependency Graph

```
core:takserver
├── core:repository (mesh packet access)
├── core:common (dispatchers, utilities)
├── core:di (Koin module registration)
├── core:model (Capabilities, domain models)
├── core:proto (protobuf definitions)
├── xmlutil (XML parsing/serialization)
├── TAKPacket-SDK v0.1.3 (zstd compression, JVM only)
├── zstd-jni 1.5.7-7 (native zstd, platform-specific)
├── okio (I/O)
├── ktor-network (TCP)
├── kotlinx-datetime
└── kermit (logging)
```
