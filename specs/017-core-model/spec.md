# Feature Specification: Core Model (Domain Models)

**Feature Branch**: `017-core-model`  
**Created**: 2026-07-27  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `core/model` module

## Summary

Core Model defines the domain models, utility functions, and extensions used throughout Meshtastic-Android. It contains 57 `commonMain` files (plus platform actuals) providing the `Node` domain model (231 LOC), `DataPacket`, `Message`, `Contact`, `Channel`, `DeviceVersion`, `ConnectionState`, `SessionStatus`, and 25+ utility extensions for time, distance, coordinates, URL parsing, channel set encoding, and proto wire extensions. This module has no UI and no persistence — it is pure domain logic consumed by all other modules.

## Goals

1. **Unified domain vocabulary** — provide a single source of truth for mesh network domain types (Node, Channel, Contact, Message, etc.).
2. **Rich Node model** — aggregate user info, position, telemetry, hardware metadata, and computed properties (online status, distance, bearing, colors) in one data class.
3. **Utility library** — provide shared utility functions for time formatting, unit conversion, GPS formatting, URL construction/parsing, and proto extensions.
4. **Channel & URL handling** — encode/decode Meshtastic channel configuration URLs using protobuf + base64.
5. **Cross-platform** — all models and utilities in `commonMain` with minimal platform actuals.

## Non-Goals

- Persistence (no Room entities or DAOs) — handled by `core/database`.
- Business logic or data flow orchestration — handled by `core/data`.
- UI rendering or formatting beyond string generation — handled by feature modules.
- Proto message definitions — handled by `core/proto` (read-only upstream).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Node Domain Model (Priority: P1)

The `Node` data class is the primary domain model representing a mesh network participant. It aggregates user identity, position, device metrics, environment metrics, power metrics, and computed properties like `isOnline`, `distance`, `bearing`, `colors`, and `capabilities`.

**Why this priority**: `Node` is referenced by every feature module. Incorrect domain logic propagates everywhere.

**Independent Test**: Pure data class — fully testable without dependencies.

**Acceptance Scenarios**:

1. **Given** a node with `lastHeard` within the online threshold, **When** `isOnline` is evaluated, **Then** it returns `true`.
2. **Given** a node with `lastHeard` older than 15 minutes, **When** `isOnline` is evaluated, **Then** it returns `false`.
3. **Given** two nodes with valid positions, **When** `distance(other)` is called, **Then** it returns the distance in meters.
4. **Given** two nodes with valid positions, **When** `bearing(other)` is called, **Then** it returns the bearing in degrees.
5. **Given** a node num, **When** `colors` is accessed, **Then** it returns a foreground/background color pair derived from the num.
6. **Given** a node with `hw_model == UNSET`, **When** `isUnknownUser` is checked, **Then** it returns `true`.
7. **Given** `createFallback(nodeNum, prefix)`, **When** called, **Then** a Node with a generated user ID and long_name is returned.

---

### User Story 2 — Channel Set URL Encoding/Decoding (Priority: P1)

The `ChannelSet` utility encodes and decodes Meshtastic channel configuration URLs. These URLs allow users to share channel settings via QR codes and deep links.

**Why this priority**: Channel sharing is a core onboarding flow. Encoding bugs break channel provisioning.

**Independent Test**: Pure encoding/decoding — fully testable.

**Acceptance Scenarios**:

1. **Given** a `ChannelSet` protobuf, **When** encoded to URL, **Then** the result matches `https://meshtastic.org/e/#<base64>`.
2. **Given** a valid Meshtastic URL, **When** decoded, **Then** the `ChannelSet` protobuf is reconstructed.
3. **Given** a malformed URL, **When** decode is attempted, **Then** `MalformedMeshtasticUrlException` is thrown.
4. **Given** URL encoding uses base64url (no padding), **When** a URL is generated, **Then** it is safe for URL embedding.

---

### User Story 3 — Device Version & Capabilities (Priority: P2)

`DeviceVersion` parses firmware version strings. `Capabilities` derives feature availability from the firmware version (e.g., managed mode, remote admin, removal, etc.).

**Why this priority**: Feature gates depend on accurate version parsing. Wrong capabilities hide available features.

**Independent Test**: Pure string parsing — fully testable.

**Acceptance Scenarios**:

1. **Given** a firmware version string "2.5.19.abc1234", **When** parsed by `DeviceVersion`, **Then** major=2, minor=5, patch=19.
2. **Given** a firmware version ≥ minimum for managed mode, **When** `capabilities.hasManagedMode` is checked, **Then** it returns `true`.
3. **Given** an unknown firmware version string, **When** parsed, **Then** it falls back to a zero version without crashing.

---

### Edge Cases

- What happens when `Node.distance()` is called with invalid positions? Returns `null`.
- What happens when `ChannelSet` URL contains an unrecognized scheme? `MalformedMeshtasticUrlException` is thrown.
- What happens when `DataPacket.nodeNumToDefaultId()` receives 0? It generates a valid ID string.
- What happens when `CommonUtils` formats a negative node number? The hex formatting handles it correctly.

## Architecture

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| `Node` | `Node.kt` (231 LOC) | Primary domain model: user, position, metrics, computed properties |
| `DataPacket` | `DataPacket.kt` | Mesh data packet representation with node ID helpers |
| `Message` | `Message.kt` | Chat message domain model |
| `Contact` | `Contact.kt` | Contact/channel representation |
| `Channel` | `Channel.kt` | Channel configuration model |
| `ConnectionState` | `ConnectionState.kt` | App-level connection state enum |
| `DeviceVersion` | `DeviceVersion.kt` | Firmware version parser |
| `Capabilities` | `Capabilities.kt` | Feature capability flags derived from version |
| `SessionStatus` | `SessionStatus.kt` | Remote admin session status |
| `NodeSortOption` | `NodeSortOption.kt` | Node list sort options |
| `RadioController` | `RadioController.kt` | Radio control interface |
| `ChannelSet` | `util/ChannelSet.kt` | Channel URL encoding/decoding |
| `MeshDataMapper` | `util/MeshDataMapper.kt` | Proto → domain model mapping |
| `TimeUtils` | `util/TimeUtils.kt` | Time formatting utilities |
| `DateTimeUtils` | `util/DateTimeUtils.kt` | Date/time formatting |
| `DistanceExtensions` | `util/DistanceExtensions.kt` | Distance string formatting |
| `UnitConversions` | `util/UnitConversions.kt` | Metric ↔ imperial conversions |
| `LocationUtils` | `util/LocationUtils.kt` | Lat/long calculations |
| `GeoConstants` | `util/GeoConstants.kt` | Geographic constants |
| `Extensions` | `util/Extensions.kt` | General Kotlin extensions |
| `WireExtensions` | `util/WireExtensions.kt` | Proto Wire extensions |
| `ByteStringExtensions` | `util/ByteStringExtensions.kt` | Okio ByteString helpers |
| `ByteStringSerializer` | `util/ByteStringSerializer.kt` | kotlinx.serialization for ByteString |
| `CommonUtils` | `util/CommonUtils.kt` | Node ID formatting, hex utils |
| `SfppHasher` | `util/SfppHasher.kt` | Short-fast position-packet hasher |
| `NodeIdLookup` | `util/NodeIdLookup.kt` | Node num → display name resolution |
| `SharedContact` | `util/SharedContact.kt` | Shared contact for Android sharing |
| `UriUtils` | `util/UriUtils.kt` | URI construction helpers |
| `RandomUtils` | `util/RandomUtils.kt` | Random generation (expect/actual) |
| `TimeConstants` | `util/TimeConstants.kt` | Time-related constants |
| `MeshtasticUrlConstants` | `util/MeshtasticUrlConstants.kt` | URL scheme constants |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `Node` MUST compute `isOnline` based on `lastHeard` vs the online time threshold.
- **FR-002**: `Node` MUST compute `distance(other)` in meters between two nodes with valid positions.
- **FR-003**: `Node` MUST compute `bearing(other)` in degrees between two nodes with valid positions.
- **FR-004**: `Node` MUST derive foreground/background color pair from `num` using brightness-based contrast.
- **FR-005**: `Node.createFallback()` MUST generate a valid Node with generated user ID and display name.
- **FR-006**: `ChannelSet` MUST encode channel config to `https://meshtastic.org/e/#<base64url>` format.
- **FR-007**: `ChannelSet` MUST decode valid Meshtastic URLs back to `ChannelSet` protobuf.
- **FR-008**: `DeviceVersion` MUST parse firmware version strings in `major.minor.patch.hash` format.
- **FR-009**: `Capabilities` MUST derive feature availability flags from firmware version comparisons.
- **FR-010**: `SfppHasher` MUST produce consistent hashes for position packet deduplication.

### Non-Functional Requirements

- **NFR-001**: All models and utilities MUST reside in `commonMain` (Constitution §I).
- **NFR-002**: Platform actuals MUST be limited to `DateTimeActuals` and `RandomUtils` (JVM/Android/iOS).
- **NFR-003**: No business logic beyond domain model computations — no repository or service calls.
- **NFR-004**: `isUnmessageableRole()` MUST cover all non-messageable device roles.

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 57 files (~4,500 LOC) | All models, utilities, extensions |
| `commonTest` | 6 files (~400 LOC) | ChannelOption, SfppHasher, CommonUtils, DeviceVersion, RouteDiscovery, Capabilities |
| `androidDeviceTest` | 3 files (~200 LOC) | SharedContact, ChannelSet, Channel tests |
| `jvmAndroidMain` | 2 files (~100 LOC) | DateTimeActuals, RandomUtils |
| `androidMain` | 2 files (~80 LOC) | UriBridge, PosixTimeZoneUtils |
| `iosMain` | 1 file (~20 LOC) | Noop stubs |

## Privacy Assessment

- [x] No PII in model definitions — node names are user-controlled, not PII
- [x] Position data is domain-level only — no logging or transmission from this module
- [x] Channel keys are not logged or serialized beyond URL encoding

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `Node.isOnline` correctly evaluates against the time threshold for boundary values.
- **SC-002**: `Node.distance()` returns accurate distance (±1m) for known coordinate pairs.
- **SC-003**: `ChannelSet` URL round-trip: encode → decode produces identical protobuf.
- **SC-004**: `DeviceVersion` parses all known firmware version string formats.
- **SC-005**: `SfppHasher` produces consistent hashes across platforms (no platform-specific behavior).
- **SC-006**: All 9 existing test files pass with `allTests` target.

## Assumptions

- Proto messages are from `core/proto` — Wire-based protobuf classes.
- `onlineTimeThreshold()` is defined as a function (not a constant) to allow clock-relative computation.
- `MeshDataMapper` requires `NodeIdLookup` for node num → display name resolution.
- All display formatting methods return plain strings — Compose formatting is in UI modules.

