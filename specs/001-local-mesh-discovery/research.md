# Research — Local Mesh Discovery

This document captures the main technical decisions for implementing Local Mesh Discovery in Meshtastic-Android.

## R-001 — NeighborInfo packet processing

### Decision

Reuse the existing `NeighborInfo` packet pipeline instead of introducing a discovery-specific parser.

### Evidence in current codebase

- `core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MeshDataHandlerImpl.kt` already routes specialized packet types.
- `core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/NeighborInfoHandlerImpl.kt` decodes and stores the latest local-radio `NeighborInfo` payload.
- `core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/CommandSenderImpl.kt` already exposes `requestNeighborInfo(requestId, destNum)` and records request timing through `NeighborInfoHandler.recordStartTime(...)`.
- `core/model/src/commonMain/kotlin/org/meshtastic/core/model/NeighborInfo.kt` already contains domain helpers for decoding / formatting neighbor info.

### Implementation guidance

- Local Mesh Discovery should subscribe to the same packet stream already used by the rest of the app.
- At the start and/or end of each dwell, the scan engine should request neighbor info from the local radio using the existing command path so topology data is captured even when no spontaneous neighbor report arrives during that dwell.
- The feature should persist both the aggregate count (`neighborInfoCount`) and node-level neighbor mentions (`neighborMentionCount`) so the map and summary can reconstruct topology richness.

### Why this was chosen

This minimizes risk, avoids duplicated protobuf decoding, and guarantees discovery data matches the rest of the app’s interpretation of `NeighborInfo` packets.

### Consequences

- Discovery does not need a new protobuf or low-level radio hook.
- The feature must tolerate the fact that neighbor info may be absent for some presets and degrade gracefully.

---

## R-002 — Radio reboot detection after preset change

### Decision

Treat preset switching as a high-level state-machine concern and rely on the existing BLE transport stack for reconnect behavior.

### Evidence in current codebase

- `core/network/src/commonMain/kotlin/org/meshtastic/core/network/radio/BleReconnectPolicy.kt` already implements exponential backoff and transient/permanent disconnect signaling.
- `core/network/src/commonMain/kotlin/org/meshtastic/core/network/radio/BleRadioTransport.kt` configures the reconnect policy with effectively infinite retry while the selected device remains active.
- Settings already mutate radio config through the existing admin/config path; discovery can reuse that mechanism rather than creating a separate transport loop.

### Implementation guidance

- After dispatching a preset change, Local Mesh Discovery should move to `WaitingForReconnect` and observe shared connection state (for example through `ServiceRepository` / `RadioController`-backed flows already used by the app).
- Dwell time must not begin until the connection is stable again and the feature has enough confirmation that the new preset is active.
- If reconnect never stabilizes within the feature timeout, mark the preset failed, persist partial data, and attempt home-preset restoration.

### Why this was chosen

BLE reconnect behavior is already one of the more sensitive parts of the codebase. Reusing the existing policy keeps discovery from fighting the transport layer.

### Consequences

- Discovery should not create its own reconnect coroutine or scanner loop.
- Session UX must clearly distinguish between “waiting for reconnect” and “actively dwelling”.

---

## R-003 — On-device AI recommendation

### Decision

Define a shared recommendation-engine contract in `commonMain`, use **Gemini Nano via Google AI Edge SDK** only on supported Google-flavor Android devices, and always ship a deterministic fallback.

### Rationale

Gemini Nano is the preferred on-device AI for recommendations, but it is not universally available:

- requires supported Android hardware / OS
- is not appropriate for all flavors (especially `fdroid`)
- can fail due to model availability, permissions, or runtime support

### Implementation guidance

- Create a `DiscoveryRecommendationEngine` interface in `commonMain`.
- Create a deterministic `RuleBasedDiscoveryRecommendationEngine` in `commonMain` and make it the default.
- Add an Android Google-flavor adapter that wraps Google AI Edge SDK when the device, flavor, and runtime environment support Gemini Nano.
- Feed AI only a summarized session payload (counts, rankings, caveats), not raw packet blobs.
- Keep AI opt-in and non-blocking. The session summary must remain usable even when AI is unavailable.

### Why this was chosen

This keeps the feature fully functional without requiring proprietary or hardware-specific AI support.

### Consequences

- AI support becomes an enhancement, not a hard dependency.
- The deterministic ranking engine must be well-designed because it is always the fallback and often the primary path.

---

## R-004 — Navigation integration

### Decision

Integrate Local Mesh Discovery into the existing Settings flow with typed Navigation 3 routes and deep-link support in `DeepLinkRouter`.

### Evidence in current codebase

- `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt` defines shared route families as `@Serializable sealed interface` hierarchies.
- `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt` assembles settings destinations inside `fun EntryProviderScope<NavKey>.settingsGraph(...)`.
- `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/DeepLinkRouter.kt` already maps `/settings/...` sub-paths to typed settings routes.
- `MainActivity` already routes `meshtastic://meshtastic/...` deep links through the shared router.

### Implementation guidance

- Add a typed settings route such as `SettingsRoute.LocalMeshDiscovery` for the main entry point.
- Add a second typed route for session detail if history entries need a stable deep-link target, for example `SettingsRoute.LocalMeshDiscoverySession(sessionId: String)`.
- Extend `SettingsNavigation.settingsGraph(...)` with discovery entries.
- Extend `DeepLinkRouter.settingsSubRoutes` and corresponding tests.

### Why this was chosen

This matches Meshtastic-Android’s Navigation 3 architecture, preserves typed backstack persistence, and keeps discovery reachable from app links and notifications in the same way as existing features.

### Consequences

- The canonical deep link should follow existing hyphenated path conventions 
- Discovery navigation tests should be added beside the existing `DeepLinkRouterTest` and `NavigationConfigTest` coverage.

---

## R-005 — 2.4 GHz hardware gating

### Decision

Resolve 2.4 GHz capability through `DeviceHardwareRepository` plus current-radio metadata instead of hardcoding a device list inside the feature.

### Evidence in current codebase

- `core/repository/src/commonMain/kotlin/org/meshtastic/core/repository/DeviceHardwareRepository.kt` provides `getDeviceHardwareByModel(hwModel, target, forceRefresh)`.
- `core/data/src/commonMain/kotlin/org/meshtastic/core/data/repository/DeviceHardwareRepositoryImpl.kt` already supports cache, remote fetch, bundled JSON fallback, and target-based disambiguation.
- `core/database/src/commonMain/kotlin/org/meshtastic/core/database/entity/DeviceHardwareEntity.kt` stores `hwModel`, `platformioTarget`, `hwModelSlug`, and `tags`.
- `MyNodeEntity` already stores `pioEnv`, which can help target disambiguation.

### Implementation guidance

- Use current-radio hardware model plus the best available target hint (`pioEnv`, reported target, or similar metadata) to query `DeviceHardwareRepository`.
- Determine 2.4 GHz support using a layered heuristic:
  1. explicit capability tags if the hardware dataset gains them,
  2. known target / slug patterns such as `sx1280`, `2.4`, or `2400`,
  3. safe default of **unsupported / unknown** when evidence is insufficient.
- Keep the gating logic in a shared use case so it can be unit tested.

### Why this was chosen

The project already has a hardware metadata pipeline. Reusing it keeps the capability logic consistent with other hardware-aware features.

### Consequences

- Capability detection quality depends on the fidelity of hardware metadata.
- The UI must explain “unsupported” vs “unable to verify” clearly.

---

## R-006 — Map rendering strategy

### Decision

Build discovery map state in `commonMain` and render it through the same CompositionLocal provider pattern already used by the app map feature.

### Evidence in current codebase

- `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/MapViewProvider.kt` defines `LocalMapViewProvider`.
- `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/LocalInlineMapProvider.kt`, `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/LocalNodeTrackMapProvider.kt`, and `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/LocalTracerouteMapProvider.kt` show how shared UI asks the host for platform-specific map rendering.
- `feature/map/src/commonMain/kotlin/org/meshtastic/feature/map/BaseMapViewModel.kt` already exposes node and map filter state in a KMP-friendly way.
- `MainActivity` provides Android map implementations through CompositionLocals.

### Implementation guidance

- Keep session filtering, marker grouping, topology edge preparation, and unmapped-node accounting in `commonMain`.
- Reuse or mirror the existing map-provider abstraction rather than importing Google Maps or OSM APIs into shared code.
- On Desktop/JVM, support either a desktop provider or a placeholder/list fallback. The feature must not assume Android map APIs exist.

### Why this was chosen

This matches the current app architecture and prevents discovery from becoming flavor- or platform-locked.

### Consequences

- Discovery may need a small feature-specific map adapter if its overlay needs differ from the main live map.
- Exported PDF map snapshots are platform-specific and may need graceful fallback.

---

## R-007 — Room KMP schema and migration strategy

### Decision

Add new discovery tables to the existing `MeshtasticDatabase` and version them through the normal Room KMP migration path.

### Evidence in current codebase

- `core/database/src/commonMain/kotlin/org/meshtastic/core/database/MeshtasticDatabase.kt` is the single Room KMP database for persisted radio/app data.
- The project already uses incremental Room versions and `AutoMigration` where possible.
- `DatabaseProvider` / `DatabaseManager` already scope databases per connected device, which is a natural fit for discovery history.

### Implementation guidance

- Add the new entities and DAOs to `MeshtasticDatabase`.
- Bump to the next schema version (`38 -> 39` at the time of writing; use the next available number if this changes before implementation).
- Prefer auto-migration if the change is additive (new tables + indices only).
- Add DAO and migration tests in `core/database` to cover inserts, relation loading, and cascade deletion.
- Keep discovery in the same per-device DB so history automatically follows the connected radio context.

### Why this was chosen

A second database would complicate lifecycle, backup/export, and switching between radios. The existing DB manager already solves those concerns.

### Consequences

- Historical discovery sessions are scoped to the radio/device DB they were recorded under.
- Export becomes the mechanism for cross-device sharing.

---

## Recommended follow-up decisions during implementation

1. Finalize the exact 2.4 GHz capability heuristic once hardware JSON coverage is reviewed.
2. Decide whether discovery needs its own route family or can live entirely inside `SettingsRoute`.
3. Decide whether Android PDF export should capture a rendered map bitmap or fall back to summary-only PDF in v1.
4. Confirm whether Desktop will ship a discovery map provider in the first implementation or a placeholder/list fallback.
