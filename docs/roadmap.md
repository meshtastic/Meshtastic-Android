# Roadmap

> Last updated: 2026-03-16

Forward-looking priorities for the Meshtastic KMP multi-target effort. For current state, see [`kmp-status.md`](./kmp-status.md). For the full gap analysis, see [`decisions/architecture-review-2026-03.md`](./decisions/architecture-review-2026-03.md).

## Architecture Health (Immediate)

These items address structural gaps identified in the March 2026 architecture review. They are prerequisites for safe multi-target expansion.

| Item | Impact | Effort | Status |
|---|---|---|---|
| Purge `java.util.Locale` from `commonMain` (3 files) | High | Low | ✅ |
| Replace `ConcurrentHashMap` in `commonMain` (3 files) | High | Low | ✅ |
| Create `core:testing` shared test fixtures | Medium | Low | ✅ |
| Add feature module `commonTest` (settings, node, messaging) | Medium | Medium | ✅ |
| Desktop Koin `checkModules()` integration test | Medium | Low | ✅ |
| Auto-wire Desktop ViewModels via K2 Compiler (eliminate manual wiring) | Medium | Low | ✅ |
| **Migrate to JetBrains Compose Multiplatform dependencies** | High | Low | ✅ |

## Active Work

### Desktop Feature Completion (Phase 4)

**Objective:** Complete desktop wiring for all features and ensure full integration.

**Current State (March 2026):**
- ✅ **Settings:** ~35 screens with real configuration, including theme/about parity and desktop language picker support
- ✅ **Nodes:** Adaptive list-detail with node management
- ✅ **Messaging:** Adaptive contacts with message view + send
- ✅ **Connections:** Dynamic discovery of platform-supported transports (TCP)
- ❌ **Map:** Placeholder only, needs MapLibre or alternative
- ⚠️ **Firmware:** Placeholder wired into nav graph; native DFU not applicable to desktop
- ⚠️ **Intro:** Onboarding flow (may not apply to desktop)

**Implementation Steps:**

1.  **Tier 1: Core Wiring (Essential)**
    -   Complete Map integration (MapLibre or equivalent)
    -   Verify all features accessible via navigation
    -   Test navigation flows end-to-end
2.  **Tier 2: Polish (High Priority)**
    -   Additional desktop-specific settings polish
    -   Keyboard shortcuts
    -   Window management
    -   State persistence
3.  **Tier 3: Advanced (Nice-to-have)**
    -   Performance optimization
    -   Advanced map features
    -   Theme customization
    -   Multi-window support

| Transport | Platform | Status |
|---|---|---|
| TCP | Desktop (JVM) | ✅ Done — shared `StreamFrameCodec` + `TcpTransport` in `core:network` |
| Serial/USB | Desktop (JVM) | ❌ Next — jSerialComm |
| MQTT | All (KMP) | ❌ Planned — Ktor/MQTT (currently Android-only via Eclipse Paho) |
| BLE | Desktop | ❌ Future — Kable (JVM) |
| BLE | iOS | ❌ Future — Kable/CoreBluetooth |

### Desktop Feature Gaps

| Feature | Status |
|---|---|
| Settings | ✅ ~35 real screens (7 desktop-specific) + desktop locale picker with in-place recomposition |
| Node list | ✅ Adaptive list-detail with real `NodeDetailContent` |
| Messaging | ✅ Adaptive contacts with real message view + send |
| Connections | ✅ Unified shared UI with dynamic transport detection |
| Metrics logs | ✅ TracerouteLog, NeighborInfoLog, HostMetricsLog |
| Map | ❌ Needs MapLibre or equivalent |
| Charts | ✅ Vico KMP charts wired in commonMain (Device, Environment, Signal, Power, Pax) |
| Debug Panel | ✅ Real screen (mesh log viewer via shared `DebugViewModel`) |
| About | ✅ Shared `commonMain` screen (AboutLibraries KMP `produceLibraries` + per-platform JSON) |
| Packaging | ✅ Done — Native distribution pipeline in CI (DMG, MSI, DEB) |

## Near-Term Priorities (30 days)

1. **`core:testing` module** — ✅ Done (established shared fakes for cross-module `commonTest`)
2. **Feature `commonTest` bootstrap** — ✅ Done (131 shared tests across all 7 features covering integration and error handling)
3. **Radio transport abstraction** — ✅ Done: Defined `RadioTransport` interface in `core:repository/commonMain` and replaced `IRadioInterface`; Next: continue extracting remaining platform transports from `app/repository/radio/` into core modules
4. **`feature:connections` module** — ✅ Done: Extracted connections UI into KMP feature module with dynamic transport availability detection
5. **Navigation 3 parity baseline** — ✅ Done: shared `TopLevelDestination` in `core:navigation`; both shells use same enum; parity tests in `core:navigation/commonTest` and `desktop/test`
6. **iOS CI gate** — add `iosArm64()`/`iosSimulatorArm64()` to convention plugins and CI (compile-only, no implementations)
7. **Build-logic consolidation** — **Planned:** Consolidate expansive build-logic convention plugins. There is currently some duplication in Compose dependencies that should be factored into common conventions (`meshtastic.kmp.library.compose` vs manually specifying JetBrains CMP deps in feature modules).

## Medium-Term Priorities (60 days)

1. **App module thinning** — Extracted ChannelViewModel, NodeMapViewModel, NodeContextMenu, EmptyDetailPlaceholder to shared modules.
    - ✅ **Done:** Extracted remaining 5 ViewModels: `SettingsViewModel`, `RadioConfigViewModel`, `DebugViewModel`, `MetricsViewModel`, `UIViewModel` to shared KMP modules.
    - **Next:** Extract service/worker/radio files from `app` to `core:service/androidMain` and `core:network/androidMain`.
2. **Serial/USB transport** — direct radio connection on Desktop via jSerialComm
3. **MQTT transport** — cloud relay operation (KMP, benefits all targets)
4. **Evaluate KMP-native mocking** — Evaluate `mockative` or similar to replace `mockk` in `commonMain` of `core:testing` for iOS readiness.
5. **Desktop ViewModel auto-wiring** — ✅ Done: ensured Koin K2 Compiler Plugin generates ViewModel modules for JVM target; eliminated manual wiring in `DesktopKoinModule`
5. **KMP charting** — ✅ Done: Vico charts migrated to `feature:node/commonMain` using KMP artifacts; desktop wires them directly
6. **Navigation contract extraction** — ✅ Done: shared `TopLevelDestination` enum in `core:navigation`; icon mapping in `core:ui`; parity tests in place. Both shells derive from the same source of truth.
7. **Dependency stabilization** — track stable releases for CMP, Koin, Lifecycle, Nav3

## Longer-Term (90+ days)

1. **iOS proof target** — declare `iosArm64()`/`iosSimulatorArm64()` in KMP modules; BLE via Kable/CoreBluetooth
2. **Map on Desktop** — evaluate MapLibre for cross-platform maps
3. **`core:api` contract split** — separate transport-neutral service contracts from Android AIDL packaging
4. **Native packaging** — ✅ Done: DMG, MSI, DEB distributions for Desktop via release pipeline
5. **Module maturity dashboard** — living inventory of per-module KMP readiness

## Design Principles

1. **Solve in `commonMain` first.** If it doesn't need platform APIs, it belongs in `commonMain`.
2. **Interfaces in `commonMain`, implementations per-target.** The repository pattern is established — extend it.
3. **Stubs are a valid first implementation.** Every target starts with no-op stubs, then graduates to real implementations.
4. **Feature modules stay target-agnostic in `commonMain`.** Platform UI goes in platform source sets.
5. **Transport is a pluggable adapter.** BLE, serial, TCP, MQTT all implement `RadioInterfaceService`.
6. **CI validates every target.** If a module declares `jvm()`, CI compiles it. No exceptions.
7. **Test in `commonTest` first.** ViewModel and business logic tests belong in `commonTest` so every target runs them.
