# SDK Migration — Status & Remaining Work

> Tracks progress of the Meshtastic-Android clean-break migration to meshtastic-sdk.
> Updated: 2026-05-05

---

## Summary

**Completed:** ~100% of the Clean Break migration. AIDL dropped, SDK is sole radio path,
transport layer fully deleted, Desktop uses shared SDK bridge, dead infrastructure gone,
POC ViewModels removed, NodeInfoReadDataSource eliminated, Room legacy tables dropped,
NodeManager merged into SdkNodeRepositoryImpl, MeshActivity restored.

**Remaining:** Optional VM parameter slimming and test coverage for new bridge code.

**Net change:** 170 files changed, +4,601 / -16,963 lines (net -12,362 LOC removed)

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
- Room migration 39→40: DROP legacy `nodes`, `my_node`, `metadata` tables
- `SdkNodeRepositoryImpl` implements unified NodeRepository + NodeIdLookup
- SDK storage (SqlDelight) is source of truth for node data
- `AppMetadataRepository` provides firmware/hardware/model info
- NodeManagerImpl deleted — logic merged into SdkNodeRepositoryImpl

### Desktop ✅
- Fully cut over to SDK via shared KMP bridge
- `DesktopRadioClientProvider` manages TCP/Serial transport
- No transport stubs needed — SDK handles everything

### NodeManager Merge ✅
- NodeManager interface eliminated — all methods merged into NodeRepository
- `SdkNodeRepositoryImpl` now binds [NodeRepository, NodeIdLookup]
- Single in-memory StateFlow — no duplicate maps
- Metadata enrichment on every write (favorites, notes, ignore, mute)
- `NodeManagerImpl.kt` deleted (377 LOC)
- `NodeManager.kt` interface deleted (82 LOC)

### MeshActivity Restoration ✅
- `meshActivityFlow` added to ServiceRepository interface
- Emit `Send` from SdkPacketHandler.sendToRadio() and SdkRadioController.sendMessage()
- Emit `Receive` from ServiceRepositoryImpl.emitMeshPacket()
- UIViewModel.meshActivity wired to serviceRepository.meshActivityFlow
- Connection icon animation fully functional

### Dead Code Removal ✅
- Removed 7 dead methods from NodeManager/NodeRepository interfaces (~220 LOC)
- Deleted `NodeInfo` data class (kept MeshUser, Position, DeviceMetrics, EnvironmentMetrics)
- Renamed `NodeInfo.kt` → `MeshModels.kt`
- Removed dead `nodeManager` parameter from MeshServiceOrchestrator

### Error Handling ✅
- SdkStateBridge: ServiceAction dispatch wrapped in try/catch (prevents bridge death)
- Favorite/Ignore/Mute: local state update only applied on admin call success
- SdkRadioController: sendMessage + sendRemoteAdmin log errors before re-throwing
- ImportContact: guarded with runCatching

### UseCases Deleted ✅
- ProcessRadioResponse (tests only — impl kept, has real packet parsing logic)
- AdminActions (tests only — impl kept, has real reboot/reset logic)
- SetMeshLogSettings (tests only — impl kept)
- CleanNodeDatabase (tests only — impl kept)
- IsOtaCapable (tests only — impl kept)
- EnsureRemoteAdminSession (tests only — impl kept, complex concurrency)

---

## What Remains (optional, quality-of-life)

### 1. VM Parameter Slimming
VMs currently inject SDK-backed adapters (RadioController, NodeRepository, etc.)
which work correctly. Direct SDK injection would reduce params but isn't required:

| VM | Current Params | Could Be |
|----|---------------|----------|
| RadioConfigVM | 15 | 8-10 |
| SettingsVM | 12 | 8-10 |
| MessageVM | 12 | 6-8 |
| NodeListVM | 9 | 5-6 |
| NodeDetailVM | 7 | 4-5 |

### 2. Test Coverage
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
