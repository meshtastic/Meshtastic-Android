# Implementation Plan: TAK v2 Protocol Integration

**Branch**: `tak_v2` | **Date**: 2026-05-13 | **Spec**: `specs/005-tak-v2-protocol/spec.md`
**Input**: Feature specification from `/specs/005-tak-v2-protocol/spec.md`
**Status**: Retroactive — documents existing implementation (PR #5434, 99 files, +4698 lines)

## Summary

Upgrades Meshtastic Android's TAK integration from legacy v1 (port 72, PLI + GeoChat only) to TAK v2 (port 78, ATAK_PLUGIN_V2) with zstd dictionary compression and full CoT type coverage. The implementation uses a bidirectional bridge pattern (`TAKMeshIntegration`) that version-gates output based on firmware capability (`Capabilities.supportsTakV2` ≥ 2.8.0) while always accepting inbound traffic on both ports. Compression is provided by the `meshtastic/TAKPacket-SDK` (JitPack) with platform abstraction via expect/actual for iOS stubs.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21 (KMP multi-target)
**Primary Dependencies**: TAKPacket-SDK v0.1.3 (zstd compression), xmlutil (CoT XML parsing), Ktor Network (TCP), zstd-jni 1.5.7-7, Okio (I/O), Koin 4.2+ (DI), Kermit (logging)
**Storage**: App-private filesystem for route KML data packages; bundled .p12/.pem certificates for TLS
**Testing**: `commonTest` (9 test classes, 65+ test methods), 40 XML fixture files in `jvmAndroidMain/resources/tak_test_fixtures/`
**Target Platform**: Android (primary), JVM Desktop (secondary), iOS (stubs only)
**Project Type**: Mobile app — KMP module (`core:takserver`) + UI integration (`feature:settings`)
**Performance Goals**: CoT processing < 100ms; compressed PLI < 100 bytes; fits within ~225-byte usable LoRa payload
**Constraints**: 237-byte raw LoRa MTU (~225 usable after protobuf framing); PARTIAL_WAKE_LOCK for CPU keepalive; mTLS on port 8089
**Scale/Scope**: 28 CoT type mappings, 2 protocol versions, 3 platform targets, 1 new KMP module + UI screen

## Constitution Check

*GATE: ✅ All six principles evaluated and satisfied.*

- **I. Kotlin Multiplatform Core**: ✅ All business logic (TAKMeshIntegration, conversions, type mapper, CoT parser, detail stripper, server manager, models, DI module) resides in `commonMain`. Platform-specific code isolated to:
  - `jvmAndroidMain`: TAKServerJvm (JSSE TLS), TakV2Compressor (zstd-jni via SDK), TakCertLoader, TAKClientConnection
  - `androidMain`: AtakFileWriter (SAF/private dirs), TakPermissionUtil (runtime permissions)
  - `jvmMain`: AtakFileWriter (desktop filesystem), TakPermissionUtil (no-op)
  - `iosMain`: TAKServerIos (no-op), TakV2Compressor (uncompressed stub), AtakFileWriter (stub)
  
- **II. Zero Lint Tolerance**: ✅ Verification commands:
  ```bash
  ./gradlew spotlessApply spotlessCheck detekt :core:takserver:allTests :feature:settings:allTests
  ```

- **III. Compose Multiplatform UI**: ✅ `TAKConfigItemList.kt` uses Compose Multiplatform components (`DropDownPreference`, `SwitchPreference`, `TitledCard`). No direct Android Jetpack Compose imports. Float values use `NumberFormatter.format()` where displayed.

- **IV. Privacy First**: ✅ No PII/location/crypto keys logged. CoT data stays local to device/mesh. `core/proto` submodule not modified (read-only upstream). Certificates bundled as resources, not logged.

- **V. Design Standards Compliance**: ✅ TAK config UI uses M3 components (SwitchPreference, DropDownPreference). Cross-Platform Spec: TAKPacket-SDK defines shared wire protocol behavior; Android-specific UI is N/A for cross-platform spec (ATAK integration is Android/JVM-only; iOS uses stubs).

- **VI. Verify Before Push**: ✅ Local verification:
  ```bash
  ./gradlew spotlessApply spotlessCheck detekt assembleDebug :core:takserver:allTests :feature:settings:allTests
  gh pr checks 5434
  ```

## Project Structure

### Documentation (this feature)

```text
specs/005-tak-v2-protocol/
├── plan.md              # This file
├── research.md          # Phase 0: Technology decisions and rationale
├── data-model.md        # Phase 1: Entity models and state machines
├── quickstart.md        # Phase 1: Developer onboarding guide
├── contracts/           # Phase 1: Wire protocol contracts
│   └── wire-protocol.md
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
core/takserver/
├── build.gradle.kts                          # Module config + TAKPacket-SDK dependency
└── src/
    ├── commonMain/kotlin/org/meshtastic/core/takserver/
    │   ├── di/CoreTakServerModule.kt         # Koin DI wiring
    │   ├── TAKMeshIntegration.kt             # Bidirectional bridge (main orchestrator)
    │   ├── TAKServer.kt                      # Platform interface (expect)
    │   ├── TAKServerManager.kt               # Lifecycle + offline queue
    │   ├── TAKPacketV2Conversion.kt          # CoT ↔ TAKPacketV2 (v2 protocol)
    │   ├── TAKPacketConversion.kt            # CoT ↔ TAKPacket (v1 legacy)
    │   ├── TakV2Compressor.kt               # Zstd compression (expect)
    │   ├── TakV2TypeMapper.kt               # CoT type string ↔ enum (28 mappings)
    │   ├── CoTDetailStripper.kt             # Strip 16 bloat elements for MTU
    │   ├── CoTXmlParser.kt                  # Streaming XML → CoTMessage
    │   ├── CoTXmlDataClasses.kt             # Serializable CoT data model
    │   ├── CoTXmlFrameBuffer.kt             # TCP stream framing
    │   ├── CoTXml.kt                        # CoTMessage → XML serialization
    │   ├── CoTConversion.kt                 # Shared conversion helpers
    │   ├── TakConversionHelpers.kt          # Coordinate scaling utilities
    │   ├── RouteDataPackageGenerator.kt     # Route → KML data package
    │   ├── TAKDataPackageGenerator.kt       # Connection .zip export
    │   ├── TAKModels.kt                     # Domain models
    │   ├── TAKDefaults.kt                   # Constants and defaults
    │   ├── TAKPrefXmlDataClasses.kt         # ATAK preference XML schema
    │   ├── TakFixtureLoader.kt              # Test fixture loading (expect)
    │   ├── TakMeshTestRunner.kt             # In-app diagnostic runner
    │   ├── AtakFileWriter.kt                # Filesystem abstraction (expect)
    │   ├── XmlUtils.kt                      # XML escaping (5 special chars)
    │   └── ZipArchiver.kt                   # ZIP creation (expect)
    ├── commonTest/kotlin/.../
    │   ├── CoTConversionTest.kt
    │   ├── CoTDetailStripperTest.kt
    │   ├── CoTXmlFrameBufferTest.kt
    │   ├── CoTXmlParserTest.kt
    │   ├── CoTXmlTest.kt
    │   ├── TAKDefaultsTest.kt
    │   ├── TAKPacketConversionTest.kt
    │   ├── TAKPacketV2RawDetailTest.kt
    │   └── XmlUtilsTest.kt
    ├── jvmAndroidMain/kotlin/.../
    │   ├── TAKServerJvm.kt                  # JSSE mTLS implementation
    │   ├── TAKClientConnection.kt           # Per-client state machine
    │   ├── TakCertLoader.kt                 # Certificate loading
    │   ├── TakV2Compressor.kt               # Zstd actual (via TAKPacket-SDK)
    │   ├── TakFixtureLoader.kt              # JVM resource loading
    │   └── ZipArchiver.kt                   # java.util.zip actual
    ├── jvmAndroidMain/resources/
    │   ├── tak_certs/                       # Bundled mTLS certificates
    │   └── tak_test_fixtures/               # 40 CoT XML fixtures
    ├── androidMain/kotlin/.../
    │   └── AtakFileWriter.kt                # SAF/private directory writer
    ├── jvmMain/kotlin/.../
    │   └── AtakFileWriter.kt                # Desktop filesystem writer
    └── iosMain/kotlin/.../
        ├── TAKServerIos.kt                  # No-op server
        ├── TakV2Compressor.kt               # Uncompressed stub (flags=0xFF)
        ├── AtakFileWriter.kt                # No-op
        ├── TakFixtureLoader.kt              # No-op
        └── ZipArchiver.kt                   # No-op

feature/settings/src/
├── commonMain/kotlin/.../radio/component/
│   └── TAKConfigItemList.kt                 # Compose UI (team, role, server toggle)
├── commonMain/kotlin/.../tak/
│   └── TakPermissionUtil.kt                 # Permission interface (expect)
├── androidMain/kotlin/.../tak/
│   └── TakPermissionUtil.kt                 # ACCESS_LOCAL_NETWORK (API 37+)
├── jvmMain/kotlin/.../tak/
│   └── TakPermissionUtil.kt                 # No-op
└── iosMain/kotlin/.../tak/
    └── TakPermissionUtil.kt                 # No-op

core/model/src/commonMain/kotlin/.../
└── Capabilities.kt                          # supportsTakV2 (>= 2.8.0)

core/service/src/androidMain/kotlin/.../
└── MeshService.kt                           # PARTIAL_WAKE_LOCK for TAK server
```

**Structure Decision**: KMP multi-module architecture. New `core:takserver` module contains all TAK business logic in `commonMain` with platform actuals for TLS, compression, and filesystem. UI lives in the existing `feature:settings` module. Wake lock integration in existing `core:service`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `jvmAndroidMain` shared source set | TAKPacket-SDK (JitPack) provides JVM-only zstd + protobuf bindings; Android and Desktop share the same JSSE TLS and compression code | Separate `androidMain`/`jvmMain` actuals would duplicate ~500 lines of identical code |
| Regex-based XML stripping (not DOM) | `CoTDetailStripper` uses regex instead of XML DOM to remain dependency-free in `commonMain` and avoid xmlutil dependency for a simple element-removal task | Full DOM parsing adds allocation overhead and requires xmlutil import for a ~30-line utility |
| Branch name `tak_v2` (no prefix) | Pre-existing feature branch; retroactive documentation | N/A — not a new branch |
