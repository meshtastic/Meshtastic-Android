# Tasks: Core Model (Domain Models)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Status**: Migrated — all existing tasks marked complete. Gap tasks marked incomplete.  
**Task Prefix**: `MDL-T`

---

## Phase 1 — Domain Models

### MDL-T001: Node domain model [x]

- **File**: `Node.kt` (~231 LOC)
- 25+ properties: num, user, position, snr, rssi, lastHeard, deviceMetrics, environmentMetrics, powerMetrics, paxcounter, publicKey, notes, etc.
- Computed: `isOnline`, `colors`, `isUnknownUser`, `hasPKC`, `mismatchKey`, `validPosition`, `distance()`, `bearing()`, `gpsString()`, `getTelemetryStrings()`.
- Companion: `createFallback()`, `getRelayNode()`, `ERROR_BYTE_STRING`.
- Extension: `isUnmessageableRole()`.
- **Test**: Partial — verified via integration across all feature modules.

### MDL-T002: DataPacket [x]

- **File**: `DataPacket.kt`
- Mesh data packet representation: data, from, to, time, id, dataType, status.
- `nodeNumToDefaultId()`: converts node number to `!hex` format.
- **Test**: Verified via integration.

### MDL-T003: Message + Contact + Channel [x]

- **Files**: `Message.kt`, `Contact.kt`, `Channel.kt`
- `Message`: chat message with sender, timestamp, status, reactions.
- `Contact`: contact/channel representation with unread count, mute settings.
- `Channel`: channel config model with name, settings, role.
- **Test**: `ChannelTest.kt` (androidDeviceTest), verified via messaging feature.

### MDL-T004: ConnectionState + SessionStatus [x]

- **Files**: `ConnectionState.kt`, `SessionStatus.kt`
- `ConnectionState`: Disconnected, DeviceSleep, Connected (enum).
- `SessionStatus`: Active, Expired, NotEstablished (sealed).
- **Test**: Verified via service and remote admin features.

### MDL-T005: DeviceVersion + Capabilities [x]

- **Files**: `DeviceVersion.kt`, `Capabilities.kt`
- `DeviceVersion`: parses `major.minor.patch.hash` firmware strings.
- `Capabilities`: feature flags derived from version (managed mode, remote admin, etc.).
- **Test**: `DeviceVersionTest.kt`, `CapabilitiesTest.kt`.

### MDL-T006: Network models [x]

- **Files**: `NetworkFirmwareRelease.kt`, `NetworkDeviceHardware.kt`, `BootloaderOtaQuirk.kt`
- JSON-deserialized models for firmware release API and hardware catalog.
- **Test**: Verified via firmware update feature.

### MDL-T007: Supporting domain types [x]

- **Files**: `NodeSortOption.kt`, `NodeInfo.kt`, `MyNodeInfo.kt`, `InterfaceId.kt`, `DeviceType.kt`, `DeviceHardware.kt`, `MeshLog.kt`, `MqttConnectionState.kt`, `MqttProbeStatus.kt`, `MqttJsonPayload.kt`, `Reaction.kt`, `RouteDiscovery.kt`, `TracerouteOverlay.kt`, `NeighborInfo.kt`, `ChannelOption.kt`, `TAK.kt`, `MeshActivity.kt`, `EventEdition.kt`, `RadioController.kt`, `RadioNotConnectedException.kt`
- Various supporting domain types used across feature modules.
- **Test**: `ChannelOptionTest.kt`, `RouteDiscoveryTest.kt`.

### MDL-T008: Service action models [x]

- **Files**: `service/ServiceAction.kt`, `service/TracerouteResponse.kt`
- `ServiceAction`: sealed class for all service commands (send, config, traceroute, etc.).
- `TracerouteResponse`: traceroute result model.
- **Test**: Verified via service action routing.

---

## Phase 2 — Utilities & Extensions

### MDL-T009: ChannelSet URL encoding/decoding [x]

- **File**: `util/ChannelSet.kt`
- Encode: `ChannelSet` protobuf → `https://meshtastic.org/e/#<base64url>`.
- Decode: URL → `ChannelSet` protobuf.
- Error: `MalformedMeshtasticUrlException` for invalid URLs.
- **Test**: `ChannelSetTest.kt` (androidDeviceTest).

### MDL-T010: MeshDataMapper [x]

- **File**: `util/MeshDataMapper.kt`
- Maps proto messages (User, Position, DeviceMetrics, etc.) to domain models.
- Requires `NodeIdLookup` for node num → display name.
- **Test**: Verified via data layer integration.

### MDL-T011: Time and date utilities [x]

- **Files**: `util/TimeUtils.kt`, `util/DateTimeUtils.kt`, `util/TimeConstants.kt`
- Time formatting, relative time strings, epoch conversions, online threshold.
- Platform actuals in `jvmAndroidMain/DateTimeActuals.kt`.
- **Test**: Verified via UI integration.

### MDL-T012: Distance and location utilities [x]

- **Files**: `util/DistanceExtensions.kt`, `util/LocationUtils.kt`, `util/GeoConstants.kt`, `util/UnitConversions.kt`
- Haversine distance, bearing calculation, GPS formatting, metric/imperial conversion.
- **Test**: Verified via map and node detail features.

### MDL-T013: Common utilities and extensions [x]

- **Files**: `util/CommonUtils.kt`, `util/Extensions.kt`, `util/DebugUtils.kt`, `util/RandomUtils.kt`
- Node ID formatting, hex encoding, general Kotlin extensions, debug helpers.
- `RandomUtils`: expect/actual for platform-specific random generation.
- **Test**: `CommonUtilsTest.kt`.

### MDL-T014: Proto and byte extensions [x]

- **Files**: `util/WireExtensions.kt`, `util/ByteStringExtensions.kt`, `util/ByteStringSerializer.kt`
- Wire protobuf extensions, Okio ByteString helpers, kotlinx.serialization support.
- **Test**: Verified via data layer integration.

### MDL-T015: URL and URI utilities [x]

- **Files**: `util/UriUtils.kt`, `util/MeshtasticUrlConstants.kt`
- URL construction, Meshtastic URL scheme constants.
- **Test**: Verified via onboarding and channel sharing features.

### MDL-T016: SfppHasher [x]

- **File**: `util/SfppHasher.kt`
- Short-fast position-packet hasher for deduplication.
- **Test**: `SfppHasherTest.kt`.

### MDL-T017: SharedContact + NodeIdLookup [x]

- **Files**: `util/SharedContact.kt`, `util/NodeIdLookup.kt`
- Android sharing helper, node num → name lookup interface.
- **Test**: `SharedContactTest.kt` (androidDeviceTest).

---

## Gap Tasks (Incomplete)

### MDL-T018: Add Node domain model unit tests [ ]

- **File to create**: `commonTest/.../NodeTest.kt`
- Test `isOnline` boundary values, `distance()` with known coordinates, `bearing()` cardinal directions, `colors` contrast, `createFallback()`, `getRelayNode()`.
- **Priority**: Medium

### MDL-T019: Add MeshDataMapper tests [ ]

- **File to create**: `commonTest/.../util/MeshDataMapperTest.kt`
- Test proto → domain mapping for User, Position, DeviceMetrics, EnvironmentMetrics.
- **Priority**: Medium

### MDL-T020: Migrate ChannelSet tests to commonTest [ ]

- **File to create**: `commonTest/.../util/ChannelSetTest.kt`
- URL round-trip test should run on all platforms, not just Android.
- **Priority**: Medium

### MDL-T021: Add distance/location utility tests [ ]

- **File to create**: `commonTest/.../util/DistanceExtensionsTest.kt`
- Metric ↔ imperial conversion, distance formatting for known values.
- **Priority**: Low

### MDL-T022: Add DataPacket + Message tests [ ]

- **File to create**: `commonTest/.../DataPacketTest.kt`
- Test `nodeNumToDefaultId`, equality, display formatting.
- **Priority**: Low

