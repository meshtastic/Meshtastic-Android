# Desktop & Multi-Target Roadmap

> Date: 2026-03-11
>
> Desktop is the first non-Android target, but every decision here is designed to benefit **all future targets** (iOS, web, etc.). The guiding principle: solve problems in `commonMain` or behind shared interfaces — never in a target-specific way when it can be avoided.

## Current State

### What works today

| Layer | Status |
|---|---|
| Desktop scaffold | ✅ Compiles, runs, Navigation 3 shell with NavigationRail |
| Koin bootstrap | ✅ Full DI graph — stubs for all repository interfaces |
| Core KMP modules with `jvm()` | ✅ 16/16 (all core KMP modules) |
| Feature modules with `jvm()` | ✅ 6/6 — all feature modules compile on JVM |
| CI JVM smoke compile | ✅ 16 core + 6 feature modules + `desktop:test` |
| Repository stubs for non-Android | ✅ Full set in `desktop/src/main/kotlin/org/meshtastic/desktop/stub/` |
| Navigation 3 shell | ✅ Shared routes, NavigationRail, NavDisplay with placeholder screens |
| JetBrains lifecycle/nav3 forks | ✅ `org.jetbrains.androidx.lifecycle` + `org.jetbrains.androidx.navigation3` |
| Real settings feature screens | ✅ ~35 settings composables wired via `DesktopSettingsNavigation.kt` (all config + module screens) |
| Real node feature screens | ✅ Adaptive node list with real `NodeDetailContent`, TracerouteLog, NeighborInfoLog, HostMetricsLog |
| Real messaging feature screens | ✅ Adaptive contacts list with real `DesktopMessageContent` (non-paged message view with send) |
| Real connections screen | ✅ `DesktopConnectionsScreen` with TCP address entry, connection state display |
| Real TCP transport | ✅ Shared `StreamFrameCodec` + `TcpTransport` in `core:network`, used by both `app` and `desktop` |
| Mesh service controller | ✅ `DesktopMeshServiceController` — full `want_config` handshake, config/nodeinfo exchange |
| Remaining feature screens | ❌ Map, chart-based metrics (DeviceMetrics, etc.) |
| Remaining transport | ❌ Serial/USB, MQTT |

### Module JVM target inventory

**Core modules with `jvm()` target (16):**
`core:proto`, `core:common`, `core:model`, `core:repository`, `core:di`, `core:navigation`, `core:resources`, `core:datastore`, `core:database`, `core:domain`, `core:prefs`, `core:network`, `core:data`, `core:ble`, `core:service`, `core:ui`

**Core modules that are Android-only by design (3):**
`core:api` (AIDL), `core:barcode` (camera), `core:nfc` (NFC hardware)

**Feature modules (6) — all have `jvm()` target and compile on JVM:**
`feature:intro`, `feature:messaging`, `feature:map`, `feature:node`, `feature:settings`, `feature:firmware`

**Modules with `jvmMain` source sets (hand-written actuals):**
`core:common` (4 files), `core:model` (via `jvmAndroidMain`, 3 files), `core:network` (via `jvmAndroidMain`, 1 file — `TcpTransport.kt`), `core:repository` (1 file — `Location.kt`), `core:ui` (6 files — QR, clipboard, HTML, platform utils, time tick, dynamic color)

**Desktop feature wiring:**
`feature:settings` — fully wired with ~35 real composables via `DesktopSettingsNavigation.kt`, including 5 desktop-specific config screens (Device, Position, Network, Security, ExternalNotification). Other features remain placeholder.

---

## KMP Gaps — Resolved

These were pre-existing issues where `commonMain` code used symbols only available on Android. The JVM target surfaced them during Phase 1; all have been fixed.

### `feature:node` ✅ Fixed
- `formatUptime()` moved from `core:model/androidMain` → `commonMain` (pure `kotlin.time` — no platform deps)
- Material 3 Expressive APIs (`ExperimentalMaterial3ExpressiveApi`, `titleMediumEmphasized`, `IconButtonDefaults.mediumIconSize`, `shapes` param) replaced with standard Material 3 equivalents
- `androidMain/DateTimeUtils.kt` renamed to `AndroidDateTimeUtils.kt` to avoid JVM class name collision

### `feature:settings` ✅ Fixed
- Material 3 dependency wiring corrected (CMP `compose.material3` in commonMain)

**Fix pattern applied:** When `commonMain` code references APIs not in Compose Multiplatform, use the standard Material 3 equivalent. Don't create expect/actual wrappers unless the behavior genuinely differs by platform.

---

## Phased Roadmap

### Phase 0 — No-op Stubs for Repository Interfaces (target-agnostic foundation)

**Goal:** Let any non-Android target bootstrap a full Koin DI graph without crashing.

**Approach:** Create a `NoopStubs.kt` file in `desktop/` that provides no-op/empty implementations of every repository interface the graph requires. These are explicitly "does nothing" implementations — they return empty flows, no-op on mutations, and log warnings on write calls. This unblocks DI graph assembly for desktop AND establishes the stub pattern future targets will reuse.

**Why target-agnostic:** When iOS arrives, it will need the same stubs initially. The interfaces are all in `commonMain` already, so the stub pattern is inherently shared. Once real implementations exist (e.g., serial transport for desktop, CoreBluetooth for iOS), they replace the stubs per-target.

**Interfaces to stub (priority order):**

| Interface | Module | Notes |
|---|---|---|
| `ServiceRepository` | `core:repository` | Connection state, mesh packets, errors |
| `NodeRepository` | `core:repository` | Node DB, our node info |
| `RadioConfigRepository` | `core:repository` | Channel/config flows |
| `RadioInterfaceService` | `core:repository` | Raw radio bytes |
| `RadioController` | `core:model` | High-level radio commands |
| `PacketRepository` | `core:repository` | Message/packet queries |
| `MeshLogRepository` | `core:repository` | Log storage |
| `MeshServiceNotifications` | `core:repository` | Notifications (no-op on desktop) |
| `PacketHandler` | `core:repository` | Packet dispatch |
| `CommandSender` | `core:repository` | Command dispatch |
| `AlertManager` | `core:ui` | Alert dialog state |
| Preference interfaces | `core:repository` | `UiPrefs`, `MapPrefs`, `MeshPrefs`, etc. |

### Phase 1 — Add `jvm()` Target to Feature Modules ✅ COMPLETE

**Goal:** Feature modules compile on JVM, unblocking desktop (and future JVM-based targets) from using shared ViewModels and UI.

**Result:** All 6 feature modules have `jvm()` target and compile clean on JVM. KMP gaps discovered during this phase (Material 3 Expressive APIs, `formatUptime` placement) have been resolved.

**CI update:** All 6 feature module `:compileKotlinJvm` tasks added to the JVM smoke compile step.

### Phase 2 — Desktop Koin Graph Assembly

**Goal:** Desktop boots with a complete Koin graph — stubs for all platform services, real implementations where possible (database, datastore, network).

**Approach:** Create `desktop/src/main/kotlin/org/meshtastic/desktop/di/DesktopKoinModule.kt` that mirrors `AppKoinModule` but uses:
- No-op stubs for radio/BLE/notifications
- Real Room KMP database (already has JVM constructor)
- Real DataStore preferences (already KMP)
- Real Ktor HTTP client (already KMP in `core:network`)
- Real firmware release repository (network + database)

This pattern directly transfers to iOS: replace `DesktopKoinModule` with `IosKoinModule`, swap stubs for CoreBluetooth-backed implementations.

### Phase 3 — Shared Navigation Shell 🔄 IN PROGRESS

**Goal:** Desktop shows a real multi-screen app with navigation, not a smoke report.

**Completed:**
- ✅ Switched Navigation 3 + lifecycle artifacts to JetBrains multiplatform forks (`org.jetbrains.androidx.navigation3` `1.1.0-alpha03`, `org.jetbrains.androidx.lifecycle` `2.10.0-alpha08`)
- ✅ Desktop app shell with `NavigationRail` for top-level destinations (Conversations, Nodes, Map, Settings, Connections)
- ✅ `NavDisplay` + `entryProvider` pattern matching the Android app's nav graph shape
- ✅ `SavedStateConfiguration` with polymorphic `SerializersModule` for non-Android NavKey serialization
- ✅ Shared routes from `core:navigation` used for both Android and Desktop navigation
- ✅ Placeholder screens for all top-level destinations
- ✅ **`feature:settings` wired with real composables** — ~30 screens including DeviceConfiguration, ModuleConfiguration, Administration, CleanNodeDatabase, FilterSettings, radio config routes (User, Channels, Power, Display, LoRa, Bluetooth), and module config routes (MQTT, Serial, StoreForward, RangeTest, Telemetry, CannedMessage, Audio, RemoteHardware, NeighborInfo, AmbientLighting, DetectionSensor, Paxcounter, StatusMessage, TrafficManagement, TAK)
- ✅ Desktop-specific top-level settings screen (`DesktopSettingsScreen.kt`) replacing Android-only `SettingsScreen`

**Remaining:**
- ~~Wire real feature composables from `feature:node`, `feature:messaging`, and `feature:map` into the desktop nav graph~~ → node and messaging done; map still placeholder
- ~~Some settings config sub-screens still use placeholders (Device Config, Position, Network, Security, ExtNotification, Debug, About)~~ → 5 config screens replaced with real desktop implementations; Debug and About remain placeholders
- Platform-specific screens (map, BLE scan) show "not available" placeholders
- Evaluate sidebar/tab hybrid for secondary navigation within features

### Phase 4 — Real Transport Layer 🔄 IN PROGRESS

**Goal:** Desktop can actually talk to a Meshtastic radio.

**Completed:**
- ✅ `DesktopRadioInterfaceService` — TCP socket transport with auto-reconnect, heartbeat, and backoff retry
- ✅ `DesktopMeshServiceController` — orchestrates the full `want_config` handshake (config → channels → nodeinfo exchange)
- ✅ `DesktopConnectionsScreen` — TCP address entry, service-level connection state display, recent addresses
- ✅ Transport state architecture — transport layer (`RadioInterfaceService`) reports binary connected/disconnected; service layer (`ServiceRepository`) manages Connecting state during handshake

**Transports (in priority order):**

| Transport | Platform | Library | Status |
|---|---|---|---|
| TCP | Desktop (JVM) | Ktor/Okio | ✅ Implemented |
| Serial/USB | Desktop (JVM) | jSerialComm | ❌ Not started |
| MQTT | All (KMP) | Ktor/MQTT | ❌ Not started |
| BLE | iOS | Kable/CoreBluetooth | ❌ Not started |
| BLE | Desktop | Kable (JVM) | ❌ Not started |

**Architecture:** The `RadioInterfaceService` contract in `core:repository` already defines the transport abstraction. Each transport is an implementation of that interface, registered via Koin. Desktop initially gets serial + TCP. iOS gets BLE.

### Phase 5 — Feature Parity Roadmap

| Feature | Desktop | iOS | Web |
|---|---|---|---|
| Node list | Phase 3 | Phase 3 | Later |
| Messaging | Phase 3 | Phase 3 | Later |
| Settings | Phase 3 | Phase 3 | Later |
| Map | Phase 4+ (MapLibre) | Phase 4+ (MapKit) | Later |
| Firmware update | Phase 5+ | Phase 5+ | N/A |
| BLE scanning | Phase 5+ (Kable) | Phase 3 (CoreBluetooth) | N/A |
| NFC/Barcode | N/A | Later | N/A |

---

## Cross-Target Design Principles

1. **Solve in `commonMain` first.** If logic doesn't need platform APIs, it belongs in `commonMain`. Period.
2. **Interfaces in `commonMain`, implementations per-target.** The repository pattern is already established — extend it.
3. **Stubs are a valid first implementation.** Every target starts with no-op stubs, then graduates to real implementations. This is intentional, not lazy.
4. **Feature modules stay target-agnostic in `commonMain`.** Android-specific UI goes in `androidMain`, desktop-specific UI goes in `jvmMain`, iOS-specific UI goes in `iosMain`.
5. **Transport is a pluggable adapter.** BLE, serial, TCP, MQTT are all implementations of the same radio interface contract.
6. **CI validates every target.** If a module declares `jvm()`, CI compiles it on JVM. No exceptions.

---

## Execution Status (updated 2026-03-11)

1. ✅ Create this roadmap document
2. ✅ Create no-op repository stubs in `desktop/stub/NoopStubs.kt` (all 30+ interfaces)
3. ✅ Create desktop Koin module in `desktop/di/DesktopKoinModule.kt`
4. ✅ Add `jvm()` to all 6 feature modules — **6/6 compile clean on JVM**
5. ✅ Update CI to include all feature module JVM smoke compile (6 modules)
6. ✅ Update docs: `AGENTS.md`, `.github/copilot-instructions.md`, `docs/agent-playbooks/task-playbooks.md`
7. ✅ Fix KMP debt in `feature:node` (Material 3 Expressive → standard M3, `formatUptime` → commonMain)
8. ✅ Fix KMP debt in `feature:settings` (dependency wiring)
9. ✅ Move `ConnectionsViewModel` to `core:ui` commonMain
10. ✅ Split `UIViewModel` into shared `BaseUIViewModel` + Android adapter
11. ✅ Switch Navigation 3 to JetBrains fork (`org.jetbrains.androidx.navigation3:navigation3-ui:1.1.0-alpha03`)
12. ✅ Switch lifecycle-runtime-compose and lifecycle-viewmodel-compose to JetBrains forks (`org.jetbrains.androidx.lifecycle:2.10.0-alpha08`)
13. ✅ Implement desktop Navigation 3 shell with `NavigationRail` + `NavDisplay` + placeholder screens
14. ✅ Wire `feature:settings` composables into desktop nav graph (~30 real screens)
15. ✅ Create desktop-specific `DesktopSettingsScreen` (replaces Android-only `SettingsScreen`)
16. ✅ Delete passthrough Android ViewModel wrappers (11 wrappers removed)
17. ✅ Migrate `feature:node` UI components from `androidMain` → `commonMain`
18. ✅ Migrate `feature:settings` UI components from `androidMain` → `commonMain`
19. ✅ Wire `feature:node` composables into the desktop nav graph (real `DesktopNodeListScreen` with shared `NodeListViewModel`, `NodeItem`, `NodeFilterTextField`)
20. ✅ Wire `feature:messaging` composables into the desktop nav graph (real `DesktopContactsScreen` with shared `ContactsViewModel`)
21. ✅ Add `feature:node`, `feature:messaging`, `feature:map` module dependencies to `desktop/build.gradle.kts`
22. ✅ Add JetBrains Material 3 Adaptive (`1.3.0-alpha05`) to version catalog and desktop module — see [`docs/kmp-adaptive-compose-evaluation.md`](./kmp-adaptive-compose-evaluation.md)
23. ✅ Create `DesktopAdaptiveContactsScreen` using `ListDetailPaneScaffold` (contacts list + message detail placeholder)
24. ✅ Create `DesktopAdaptiveNodeListScreen` using `ListDetailPaneScaffold` (node list + node detail placeholder, context menu)
25. ✅ Provide Ktor `HttpClient` (Java engine) in desktop Koin module — fixes `ApiServiceImpl` → `DeviceHardwareRemoteDataSource` → `IsOtaCapableUseCase` → `SettingsViewModel` injection chain
26. ✅ Wire real `NodeDetailContent` from commonMain into adaptive node list detail pane (replacing placeholder)
27. ✅ Move `ContactItem.kt` from `feature:messaging/androidMain` → `commonMain` (pure M3, no Android deps)
28. ✅ Extract `MetricLogComponents.kt` (shared `MetricLogItem`/`DeleteItem`) and move `TracerouteLog`, `NeighborInfoLog`, `TimeFrameSelector`, `HardwareModelExtensions` to commonMain
29. ✅ Wire TracerouteLog, NeighborInfoLog, HostMetricsLog as real screens in `DesktopNodeNavigation.kt` (replacing placeholders) with `MetricsViewModel` registered in desktop Koin module
30. ✅ Move `MessageBubble.kt` from `feature:messaging/androidMain` → `commonMain` (pure Compose, zero Android deps, made public)
31. ✅ Build `DesktopMessageContent` composable — non-paged message list with send input for contacts detail pane (replaces placeholder)
32. ✅ Add `getMessagesFlow()` to `MessageViewModel` — non-paged `Flow<List<Message>>` for desktop (avoids paging-compose dependency)
33. ✅ Implement `DesktopRadioInterfaceService` — TCP socket transport with auto-reconnect, heartbeat, and configurable backoff retry
34. ✅ Implement `DesktopMeshServiceController` — mesh service lifecycle orchestrator wiring `want_config` handshake chain (config → channels → nodeinfo)
35. ✅ Create `DesktopConnectionsScreen` — TCP address entry UI with service-level connection state display and recent address history
36. ✅ Fix transport state architecture — removed transport-level `Connecting` emission that blocked `want_config` handshake; transport now reports binary connected/disconnected, service layer owns the Connecting state during config exchange
37. ✅ Create 5 desktop-specific config screens replacing placeholders: `DesktopDeviceConfigScreen` (role, rebroadcast, timezone via JVM `ZoneId`), `DesktopPositionConfigScreen` (fixed position, GPS, position flags — omits Android Location), `DesktopNetworkConfigScreen` (WiFi, Ethernet, IPv4 — omits QR/NFC), `DesktopSecurityConfigScreen` (keys, admin, key regeneration via JVM `SecureRandom` — omits file export), `DesktopExternalNotificationConfigScreen` (GPIO, ringtone — omits MediaPlayer/file import)
38. ✅ **Transport Deduplication:** Extracted `StreamFrameCodec` (commonMain) and `TcpTransport` (jvmAndroidMain) into `core:network` — eliminates ~450 lines of duplicated framing/TCP code between `app` and `desktop`. `StreamInterface` and `TCPInterface` in `app` now delegate to shared codec/transport. `DesktopRadioInterfaceService` reduced from 455 → 178 lines. Added `StreamFrameCodecTest` in `core:network/commonTest`.
39. ✅ **EmojiPickerDialog — unified commonMain implementation:** Replaced the `expect`/`actual` split with a single fully-featured emoji picker in `core:ui/commonMain`. Features: 9 category tabs with bidirectional scroll-tab sync, keyword search, recently-used tracking (persisted via `EmojiPickerViewModel`/`CustomEmojiPrefs`), Fitzpatrick skin-tone selector, and ~1000+ emoji catalog with `EmojiData.kt`. Deleted Android `EmojiPicker.kt` (AndroidView wrapper), `CustomRecentEmojiProvider.kt`, and JVM `EmojiPickerDialog.kt` (flat grid). Removed `androidx-emoji2-emojipicker` and `guava` dependencies from `core:ui`.
40. ✅ **Messaging component migration:** Moved `MessageActions.kt`, `MessageActionsBottomSheet.kt`, `Reaction.kt` (minus previews), `DeliveryInfoDialog.kt` from `feature:messaging/androidMain` → `commonMain`. Extracted `MessageStatusIcon` from `MessageItem.kt` into shared `MessageStatusIcon.kt`. Removed `ExperimentalMaterial3ExpressiveApi` (Android-only). Preview functions remain in `androidMain/ReactionPreviews.kt`.
41. ✅ **PositionLog table migration:** Extracted `PositionLogHeader`, `PositionItem`, `PositionList` composables from `feature:node/androidMain` into shared `PositionLogComponents.kt` in `commonMain`. Android `PositionLogScreen` with CSV export stays in `androidMain`.

### Next: Connections UI, chart migration, remaining screens, and serial transport
Desktop now has:
- **TCP connectivity** with full `want_config` handshake and config exchange
- **Shared transport layer** — `StreamFrameCodec` and `TcpTransport` in `core:network` used by both `app` and `desktop`
- **Shared messaging components** — `MessageActions`, `ReactionRow`, `ReactionDialog`, `MessageStatusIcon`, `DeliveryInfo` all in commonMain
- **Shared position log** — `PositionLogHeader`, `PositionItem`, `PositionList` in commonMain
- Adaptive list-detail screens for **nodes** (with real `NodeDetailContent`) and **contacts** (with real `DesktopMessageContent`)
- Real screens for **TracerouteLog**, **NeighborInfoLog**, **HostMetricsLog** metrics
- ~35 real **settings** screens (all config + module routes — only Debug Panel and About remain placeholder)

Next priorities:
- **Connections UI Unification:** Create `feature:connections` to merge the fragmented Android and Desktop connection screens, abstracting discovery mechanisms (BLE, USB, TCP) behind a shared interface.
- Evaluate KMP charting replacement for Vico (DeviceMetrics, EnvironmentMetrics, SignalMetrics, PowerMetrics, PaxMetrics)
- Wire serial/USB transport for direct radio connection on Desktop
- Wire MQTT transport for cloud relay operation
- **Hardware Abstraction:** Abstract `core:barcode` and `core:nfc` into `commonMain` interfaces with `androidMain` implementations.
- **iOS CI:** Turn on iOS compilation (`iosArm64()`, `iosSimulatorArm64()`) in the GitHub Actions CI pipeline to ensure the shared codebase remains LLVM-compatible.
- **Dependency Tracking:** Track stable releases for currently required alpha/RC dependencies (Compose Multiplatform `1.11.0-alpha03` for Adaptive layouts, Koin `4.2.0-RC1` for K2 plugin). Do not downgrade these prematurely as they enable critical KMP functionality.


