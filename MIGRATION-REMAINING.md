# SDK Migration — Status & Remaining Work

> Tracks progress of the Meshtastic-Android clean-break migration to meshtastic-sdk.
> Updated: 2026-05-05

---

## Summary

**Completed:** ~93% of the Clean Break migration. AIDL dropped, SDK is sole radio path,
transport layer fully deleted, Desktop uses shared SDK bridge, dead infrastructure gone,
POC ViewModels removed, NodeInfoReadDataSource eliminated.

**Remaining:** Room table cleanup, optional VM parameter slimming, and test coverage for
new bridge code.

**Net change:** 159 files changed, +3,684 / -15,460 lines (net -11,776 LOC removed)

---

## Architecture (current state)

```
┌──────────────────────────────────────────────────────────────┐
│  Feature VMs (NodeList, Settings, RadioConfig, Messages...)  │
│  inject: RadioController, NodeRepository, ServiceRepository  │
└───────────────────────────────┬──────────────────────────────┘
                                │
┌───────────────────────────────▼──────────────────────────────┐
│  SDK-Backed Adapters (core/data)                             │
│  SdkRadioController, SdkStateBridge, SdkNodeRepositoryImpl  │
│  SdkPacketHandler, SdkRadioInterfaceService                  │
│  MessagePersistenceHandler                                   │
└───────────────────────────────┬──────────────────────────────┘
                                │
┌───────────────────────────────▼──────────────────────────────┐
│  RadioClientAccessor (platform-specific providers)           │
│  Android: RadioClientProvider (app/)                          │
│  Desktop: DesktopRadioClientProvider (desktop/)              │
└───────────────────────────────┬──────────────────────────────┘
                                │
┌───────────────────────────────▼──────────────────────────────┐
│  meshtastic-sdk                                              │
│  RadioClient → MeshEngine → Transport (BLE/TCP/Serial)       │
└──────────────────────────────────────────────────────────────┘
```

---

## What's Done

### Infrastructure ✅
- AIDL completely removed
- SDK composite build integrated
- `RadioClientProvider` (Android) + `DesktopRadioClientProvider` (Desktop)
- `SdkClientLifecycle` bridges to service layer
- SDK `sendRaw(ToRadio)` API added for MQTT/XModem

### Transport Layer ✅
- **Fully deleted:** BleRadioTransport, TcpRadioTransport, SerialRadioTransport, StreamTransport, HeartbeatSender, StreamFrameCodec, all transport factories, BleReconnectPolicy, TcpTransport
- `RadioInterfaceService` slimmed to device-address surface only
- `SdkRadioInterfaceService` created (thin adapter over RadioPrefs + RadioClientAccessor)
- `NoopRadioInterfaceService` deleted (superseded by SdkRadioInterfaceService)
- `JvmUsbScanner` migrated to SDK's `JvmSerialPorts.list()`

### Pipeline ✅
- **Fully deleted:** CommandSender, MeshRouter, MeshActionHandler, PacketHandlerImpl, MeshDataHandlerImpl, MeshConnectionManager, MeshConfigFlowManager, ServiceBroadcasts, DirectRadioControllerImpl, broadcast Constants.kt
- `SdkRadioController` is sole RadioController impl
- `SdkStateBridge` bridges SDK events → repositories
- `SdkPacketHandler` routes MeshPackets via `client.send()`, raw ToRadio via `client.sendRaw()`

### POC Code ✅
- **Deleted:** SdkConfigViewModel, SdkMessagingViewModel, SdkTelemetryViewModel, RadioClientViewModel, SdkNodeListViewModel
- All POC diagnostic logging removed from Main.kt
- Dead test fakes removed (app/test/Fakes.kt)

### Data Layer ✅
- Room migration 38→39: NodeMetadata persistence
- `SdkNodeRepositoryImpl` enriches SDK nodes with persisted favorites/notes/ignore
- SDK storage (SqlDelight) is source of truth for node data
- `AppMetadataRepository` provides firmware/hardware/model info

### Desktop ✅
- Fully cut over to SDK via shared KMP bridge
- `DesktopRadioClientProvider` manages TCP/Serial transport
- No transport stubs needed — SDK handles everything

### UseCases Deleted ✅
- ProcessRadioResponse (tests only — impl kept, has real packet parsing logic)
- AdminActions (tests only — impl kept, has real reboot/reset logic)
- SetMeshLogSettings (tests only — impl kept)
- CleanNodeDatabase (tests only — impl kept)
- IsOtaCapable (tests only — impl kept)
- EnsureRemoteAdminSession (tests only — impl kept, complex concurrency)

---

## What Remains

### 1. Room Table Cleanup (medium priority — unblocked)
- Migration 39→40: DROP legacy `nodes`, `my_node` tables
- Remove old `NodeEntity`, `MyNodeEntity` Room entities + `NodeInfoDao`
- SDK SqlDelight is already source of truth; Room tables are redundant
- **No longer blocked:** `NodeInfoReadDataSource` eliminated, `PacketRepositoryImpl`
  no longer depends on `NodeInfoDao`
- Remaining internal consumers: `MeshtasticDatabase.nodeInfoDao()` abstract method,
  `CommonNodeInfoDaoTest`, `CommonPacketDaoTest`, `MigrationTest`
- Requires: Room schema migration file, entity deletion, DAO deletion, test updates

### 2. VM Parameter Slimming (optional, quality-of-life)
VMs currently inject SDK-backed adapters (RadioController, NodeRepository, etc.)
which work correctly. Direct SDK injection would reduce params but isn't required:

| VM | Current Params | Could Be |
|----|---------------|----------|
| RadioConfigVM | 15 | 8-10 |
| SettingsVM | 12 | 8-10 |
| MessageVM | 12 | 6-8 |
| NodeListVM | 9 | 5-6 |
| NodeDetailVM | 7 | 4-5 |

### 3. NodeManager Merge (optional)
`NodeManager` (25 methods, 8+ consumers) could merge into `SdkNodeRepositoryImpl`.
Currently SDK feeds it via SdkStateBridge. Works fine as-is.

### 4. MeshActivity Restoration (cosmetic)
`UIViewModel.meshActivity` currently emits `emptyFlow()`. Could be restored by
having `SdkStateBridge` emit send/receive events when SDK delivers/receives packets.
Purely cosmetic — affects connection icon animation only.

### 5. Test Coverage
- New code (`SdkRadioInterfaceService`, `SdkPacketHandler`, `MessagePersistenceHandler`)
  has no dedicated tests yet (existing integration tests cover happy paths)
- UseCase tests were deleted with the impls — should add back for kept impls

---

## What STAYS (permanent architecture)

These components are NOT migration candidates:

- `PacketRepository` — message persistence (SDK doesn't persist chat history)
- `MeshLogRepository` — debug logging (app-local)
- `QuickChatActionRepository` — quick-chat templates
- `DeviceHardwareRepository` / `FirmwareReleaseRepository` — GitHub API
- `NodeMetadataDao` / `AppMetadataRepository` — favorites, notes, ignore, mute
- `MeshServiceOrchestrator` — TAK lifecycle, notifications, DB init, widget
- `SdkStateBridge` — SDK → repository bridging, notifications, TAK dispatch
- `MqttManager` / `HistoryManager` / `XModemManager` — real features
- `TelemetryPacketHandler` / `NeighborInfoHandler` / `TracerouteHandler` — packet processors
- `ContactSettings` — per-contact mute/read state
- `SessionManager` — per-node admin session passkey management
