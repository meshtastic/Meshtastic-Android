# Implementation Plan: Core Model (Domain Models)

**Branch**: `017-core-model` | **Date**: 2026-07-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/017-core-model/spec.md`
**Status**: Migrated — all implementation complete, plan reverse-engineered from existing code.

## Summary

Core Model is a pure domain layer with 57 commonMain files (~4,500 LOC) defining all domain types, utility functions, and extensions. It has no external dependencies beyond proto definitions and Okio. The module is the most stable in the codebase — changes are infrequent and typically additive (new fields, new utilities).

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Wire protobuf (`core/proto`), Okio (ByteString), kotlinx.serialization (ByteStringSerializer)  
**Testing**: 6 commonTest + 3 androidDeviceTest files, ~600 LOC  
**Target Platform**: Android, Desktop (JVM), iOS  
**Constraints**: Pure domain — no DI, no coroutines, no persistence, no network  
**Scale/Scope**: 57 commonMain files (~4,500 LOC), 5 platform files (~200 LOC), 9 test files (~600 LOC)

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | 57 of 57 domain files in `commonMain`. Platform actuals limited to DateTime + Random. |
| II. Zero Lint Tolerance | ✅ PASS | `detekt-baseline.xml` present. `@Suppress("MagicNumber")` on `Node`. |
| VII. Coroutine Safety | N/A | No coroutines in this module. |
| IX. Branch & Scope Hygiene | ✅ PASS | Module scoped to `org.meshtastic.core.model`. |

**Gate Result**: ✅ All applicable principles satisfied.

## Project Structure

```
core/model/src/
├── commonMain/kotlin/org/meshtastic/core/model/
│   ├── Node.kt (231 LOC)           # Primary domain model
│   ├── DataPacket.kt                # Mesh packet representation
│   ├── Message.kt                   # Chat message model
│   ├── Contact.kt                   # Contact representation
│   ├── Channel.kt                   # Channel config model
│   ├── DeviceVersion.kt             # Firmware version parser
│   ├── Capabilities.kt              # Feature capability flags
│   ├── ConnectionState.kt           # Connection state enum
│   ├── SessionStatus.kt             # Remote admin session status
│   ├── NodeSortOption.kt            # Sort options
│   ├── RadioController.kt           # Radio control interface
│   ├── ... (15+ more domain types)
│   ├── service/
│   │   ├── ServiceAction.kt         # Service action sealed class
│   │   └── TracerouteResponse.kt    # Traceroute result model
│   └── util/
│       ├── ChannelSet.kt            # URL encode/decode
│       ├── MeshDataMapper.kt        # Proto → domain mapping
│       ├── TimeUtils.kt             # Time formatting
│       ├── Extensions.kt            # General extensions
│       ├── ... (15+ more utilities)
├── commonTest/ (6 files)
├── androidDeviceTest/ (3 files)
├── jvmAndroidMain/ (2 files — DateTime, Random)
├── androidMain/ (2 files — Uri, TimeZone)
└── iosMain/ (1 file — noop)
```

## Implementation Phases

### Phase 1 — Domain Models (Complete)

Core domain types: `Node`, `DataPacket`, `Message`, `Contact`, `Channel`, `ConnectionState`, `DeviceVersion`, `Capabilities`, `SessionStatus`, plus ~15 supporting types.

### Phase 2 — Utilities & Extensions (Complete)

25+ utility files: time/date formatting, distance/coordinate calculations, URL encoding, proto extensions, byte string helpers, hashing, random generation.

## Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Node model | Single data class with 25+ properties | Aggregates all node-related data for convenient access |
| `isOnline` | Computed property vs `lastHeard` threshold | Simple, predictable, no caching needed |
| Colors | num-based deterministic generation | Consistent per-node colors without needing a color assignment system |
| Position validation | `latitude_i != 0 && longitude_i != 0` | Matches firmware convention; (0,0) treated as "no position" |
| Channel URL | Base64url without padding | Safe for URL embedding, matches Meshtastic web client |
| Version parsing | Regex-based `DeviceVersion` | Handles `major.minor.patch.hash` and `major.minor.patch` formats |
| Platform actuals | DateTime + Random only | Minimal surface; everything else is pure Kotlin |

## Gaps Identified

| Gap | Severity | Recommendation |
|-----|----------|----------------|
| `Node.distance()` has no unit test | ⚠️ Medium | Add test with known coordinate pairs |
| `Node.bearing()` has no unit test | ⚠️ Medium | Add test with cardinal direction verification |
| `Node.colors` has no test | ⚠️ Low | Verify contrast ratio for light/dark backgrounds |
| `DataPacket` has no test | ⚠️ Low | Test `nodeNumToDefaultId` conversion |
| `Message` has no test | ⚠️ Low | Test equality and display formatting |
| `Channel` has only androidDeviceTest | ⚠️ Low | Consider migrating to commonTest |
| `ChannelSet` has only androidDeviceTest | ⚠️ Medium | URL round-trip test should be in commonTest for cross-platform validation |
| `MeshDataMapper` has no test | ⚠️ Medium | Add tests for proto → domain mapping correctness |
| `TimeUtils` has no test | ⚠️ Low | Add formatting boundary tests |
| `DistanceExtensions` has no test | ⚠️ Low | Add metric ↔ imperial conversion tests |

