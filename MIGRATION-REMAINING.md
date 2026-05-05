# SDK Migration — Remaining Work

> Auto-generated from migration session. Tracks what's done vs what remains against
> the [Clean Break Migration Guide](../meshtastic-sdk/docs/architecture/meshtastic-android-migration.md).

---

## Summary

**Completed:** ~70% of the Clean Break migration. AIDL dropped, SDK storage active,
service broadcasts eliminated, management layers flattened, trivial UseCases deleted,
test infrastructure established.

**Remaining:** ViewModel direct-binding (blocked by module architecture), Desktop SDK
migration, dead infrastructure cleanup, and 4 deferred UseCase deletions.

**Net change so far:** ~52 files changed, +162 / -2,395 lines across all sessions.

---

## What's Done (by migration doc phase)

### Phase 1: Environment & Dependency Alignment ✅
- SDK composite build integrated (`settings.gradle.kts`)
- Wire proto types used throughout (`org.meshtastic.proto.*`)
- `sdk-core`, `sdk-proto`, `sdk-transport-*`, `sdk-storage-sqldelight`, `sdk-testing` all wired

### Phase 2: One-Time Data Migration ✅
- Room auto-migration 38→39 with `AutoMigration38to39` spec
- `onPostMigrate` copies favorites/notes/ignored/muted/manuallyVerified from `nodes` → `node_metadata`
- `NodeMetadataEntity` + `NodeMetadataDao` created
- `SdkNodeRepositoryImpl` enriches SDK nodes with persisted metadata

### Phase 3: The Great Deletion (partial) ✅
- `ServiceBroadcasts` — deleted (both `core/service` android + `core/repository` common)
- `MeshConnectionManagerImpl` — deleted (438 LOC)
- `MeshConnectionManager` interface — deleted
- `NodeRepositoryImpl` (old Room-backed) — deleted (290 LOC)
- `NodeInfoWriteDataSource` + `SwitchingNodeInfoWriteDataSource` — deleted
- AIDL — already removed in prior session

### Phase 4: RadioClient as Core Dependency ✅
- `RadioClientProvider` implemented in `app/` with BLE/TCP/Serial support
- Exposed as `StateFlow<RadioClient?>` for reactive observation
- Auto-reconnect enabled
- `SdkClientLifecycle` interface bridges to `core/service` without reverse dependency

### Phase 5: Thin Foreground Service ✅
- `MeshService` stripped to lifecycle holder + notification management
- Uses `ServiceRepository` for connection state (bridged from SDK)
- `MeshServiceOrchestrator` simplified to TAK lifecycle + notifications + DB init + widget

### Phase 6: UI & Domain (partial)
- **6.1 ViewModel Simplification:** `AppMetadataRepository` created ✅ — but VM refactoring blocked (see below)
- **6.2 UseCase Decimation:** 8 trivial UseCases deleted ✅ — 4 deferred, complex ones kept

### Phase 7: UI/VM Direct Binding
- POC VMs exist (`SdkNodeListViewModel`, `SdkConfigViewModel`, etc.) ✅
- Production VMs still use repository layer (blocked — see below)

### Phase 8: Feature Integrations ✅
- Location publishing moved to `SdkStateBridge`
- TAK integration preserved (uses `ServiceAction` dispatch through `SdkStateBridge`)

### Phase 9: Testing Strategy ✅
- `sdk-testing` dependency added to `app` and `core/data`
- `TestRadioClientProvider` created with `FakeRadioTransport(autoHandshake=true)`
- Integration test validates connect → handshake → node injection → observation

---

## What Remains

### 1. ViewModel Direct-Binding (Phase 6.1 — BLOCKED)

**Blocker:** Feature modules (`feature/node`, `feature/settings`, `feature/messaging`, etc.)
are KMP `commonMain` and cannot depend on the SDK directly. Only the `app` module has
`implementation(libs.sdk.core)`. The `RadioClientProvider` lives in `app/`.

**Current state:** Feature VMs inject `NodeRepository`, `ServiceRepository`,
`RadioConfigRepository`, `RadioController` — all of which are already SDK-backed thin
adapters populated by `SdkStateBridge`. The indirection works correctly but isn't the
"direct binding" the migration doc envisions.

**To unblock (choose one):**
1. **Option A — SDK dependency in `core/repository`:** Add `api(libs.sdk.core)` to
   `core/repository/build.gradle.kts`. Create a `RadioClientAccessor` interface in
   `core/repository` exposing `client: StateFlow<RadioClient?>`. Feature modules can then
   inject it. Trade-off: couples `core/repository` to SDK API surface.
2. **Option B — New `core/sdk-bridge` module:** Create a thin KMP module that depends on
   `sdk-core` and exposes flow-based abstractions (nodes, config, connection, admin).
   Feature modules depend on this instead of raw `RadioClient`. More modular but adds a module.
3. **Option C — Move VMs to `app`:** Move production VMs out of `feature/*` into `app/`.
   Breaks KMP desktop/iOS target sharing. Not recommended.

**VMs to migrate (22 total):**
| Tier | VMs | Current Params | Target |
|------|-----|----------------|--------|
| Critical (5) | RadioConfigVM, SettingsVM, MessageVM, MetricsVM, NodeListVM | 9-19 | 3-5 |
| Moderate (6) | NodeDetailVM, ContactsVM, BaseMapVM, DebugVM, ChannelVM, FilterSettingsVM | 3-8 | 2-3 |
| Simple (3) | CleanNodeDatabaseVM, QuickChatVM, CompassVM | 1-4 | 1-2 |

### 2. Dead Infrastructure Cleanup (Phase 3 — BLOCKED by Desktop)

**Blocker:** Desktop's `DesktopKoinModule` manually creates `DirectRadioControllerImpl`,
which pulls in the entire old packet-routing chain via Koin.

**Files blocked from deletion (~10 files, ~2,000 LOC):**
- `MeshRouterImpl` + `MeshRouter` interface
- `MeshDataHandlerImpl` + `MeshDataHandler` interface
- `AdminPacketHandlerImpl` + `AdminPacketHandler` interface
- `PacketHandlerImpl` + `PacketHandler` interface
- `MeshConfigFlowManagerImpl` + `MeshConfigFlowManager` interface (gutted but present)
- `MeshActionHandlerImpl` + `MeshActionHandler` interface
- `CommandSenderImpl` + `CommandSender` interface
- `DirectRadioControllerImpl`

**To unblock:** Migrate Desktop to use SDK's `RadioClient` + TCP/Serial transport.
Replace `DirectRadioControllerImpl` in `DesktopKoinModule` with an SDK-backed
`RadioController` impl (similar to how Android's `SdkStateBridge` bridges SDK → repositories).

### 3. Deferred UseCase Deletions (4 remaining)

These UseCases have real logic and depend on the VM migration to be safely inlined:

| UseCase | Reason Kept |
|---------|-------------|
| `EnsureRemoteAdminSessionUseCase` | Session passkey management — needs SDK `admin.session` API |
| `ObserveRemoteAdminSessionStatusUseCase` | Session status observation — needed until VMs use SDK directly |
| `CleanNodeDatabaseUseCase` | Node cleanup logic with age/unknown filtering |
| `IsOtaCapableUseCase` | OTA capability check (firmware + device model) |

Additionally kept (complex orchestration, not candidates for deletion):
- `RadioConfigUseCase`, `MeshLocationUseCase`, `ImportProfileUseCase`,
  `ExportProfileUseCase`, `ExportSecurityConfigUseCase`, `InstallProfileUseCase`,
  `SetMeshLogSettingsUseCase`, `ExportDataUseCase`

### 4. Remaining Phase C Items (deferred)

| Item | Description | Status |
|------|-------------|--------|
| C3 | Move raw packet forwarding — VMs observe `client.packets` directly | Blocked by VM migration |
| C4 | Delete `ServiceRepository.emitMeshPacket()` / `meshPacketFlow` | Blocked by C3 |
| C5 | Further simplify `MeshServiceOrchestrator` | Minor — mostly done |
| C6 | Remove `SharedRadioInterfaceService` | Complex — SDK owns transport but address management still used |

### 5. Room Table Cleanup

The old `nodes`, `my_node`, and `metadata` Room tables still exist in the schema
(data was copied to `node_metadata` in migration 38→39). A future migration should
DROP these tables to reduce DB size.

### 6. `NodeInfoReadDataSource` Cleanup

`NodeInfoReadDataSource` interface and `SwitchingNodeInfoReadDataSource` impl are still
referenced by `MeshLogRepositoryImpl` (for resolving node names in log entries).
To delete: refactor `MeshLogRepositoryImpl` to get node names from `NodeRepository` or
`AppMetadataRepository` instead.

---

## Recommended Execution Order

1. **Desktop SDK migration** — unblocks item #2 (dead code deletion, ~2,000 LOC)
2. **Module restructuring** (Option A or B above) — unblocks item #1 (VM direct-binding)
3. **VM migration** — migrate 22 VMs to use RadioClient directly (per-VM PRs)
4. **UseCase cleanup** — delete 4 deferred UseCases after VM migration
5. **Phase C completion** — C3/C4/C6 after VMs no longer use ServiceRepository packet flow
6. **Room table cleanup** — DROP legacy tables in a final migration

---

## What STAYS (permanent architecture)

These components are NOT candidates for deletion — they serve app-local purposes
the SDK doesn't cover:

- `PacketRepository` — message persistence (SDK doesn't persist chat history)
- `MeshLogRepository` — debug logging (app-local concern)
- `QuickChatActionRepository` — quick-chat templates (app preference)
- `DeviceHardwareRepository` / `FirmwareReleaseRepository` — GitHub API clients
- `NodeMetadataDao` / `AppMetadataRepository` — favorites, notes, ignore, mute
- `MeshServiceOrchestrator` (simplified) — TAK lifecycle, notifications, DB init
- `SdkStateBridge` (reduced) — SDK → repository bridging, location publishing, TAK dispatch
- `RadioClientProvider` — SDK client lifecycle management
- `ContactSettings` table — app-local mute/read state per contact
