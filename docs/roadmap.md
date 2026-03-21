# Roadmap

> Last updated: 2026-03-17

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
| **iOS CI gate (compile-only validation)** | High | Medium | ✅ |

## Active Work

### Desktop Feature Completion (Phase 4)

**Objective:** Complete desktop wiring for all features and ensure full integration.

**Current State (March 2026):**
- ✅ **Settings:** ~35 screens with real configuration, including theme/about parity and desktop language picker support
- ✅ **Nodes:** Adaptive list-detail with node management
- ✅ **Messaging:** Adaptive contacts with message view + send
- ✅ **Connections:** Dynamic discovery of platform-supported transports (TCP, Serial/USB, BLE)
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
    -   ✅ **MenuBar integration** and Keyboard shortcuts
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
| Serial/USB | Desktop (JVM) | ✅ Done — jSerialComm |
| MQTT | All (KMP) | ✅ Completed — KMQTT in commonMain |
| BLE | Android | ✅ Done — Kable |
| BLE | Desktop | ✅ Done — Kable (JVM) |
| BLE | iOS | ❌ Future — Kable/CoreBluetooth |

### Desktop Feature Gaps

| Feature | Status |
|---|---|
| Settings | ✅ ~35 real screens (6 desktop-specific) + desktop locale picker with in-place recomposition |
| Node list | ✅ Adaptive list-detail with real `NodeDetailContent` |
| Messaging | ✅ Adaptive contacts with real message view + send |
| Connections | ✅ Unified shared UI with dynamic transport detection |
| Metrics logs | ✅ TracerouteLog, NeighborInfoLog, HostMetricsLog |
| Map | ❌ Needs MapLibre or equivalent |
| QR Generation | ✅ Pure KMP generation via `qrcode-kotlin` |
| Charts | ✅ Vico KMP charts wired in commonMain (Device, Environment, Signal, Power, Pax) |
| Debug Panel | ✅ Real screen (mesh log viewer via shared `DebugViewModel`) |
| Notifications | ✅ Desktop native notifications with system tray icon support |
| MenuBar | ✅ Done — Native application menu bar with File/View menus |
| About | ✅ Shared `commonMain` screen (AboutLibraries KMP `produceLibraries` + per-platform JSON) |
| Packaging | ✅ Done — Native distribution pipeline in CI (DMG, MSI, DEB) |

## Near-Term Priorities (30 days)

1. **Evaluate KMP-native testing tools** — ✅ **Done:** Fully evaluated and integrated `Mokkery`, `Turbine`, and `Kotest` across the KMP modules. `mockk` has been successfully replaced, enabling property-based and Flow testing in `commonTest` for iOS readiness.
2. **Desktop Map Integration** — Address the major Desktop feature gap by implementing a raster map view using [**MapComposeMP**](https://github.com/p-lr/MapComposeMP).
    - Implement a `MapComposeProvider` for Desktop.
    - Implement a **Web Mercator Projection** helper in `feature:map/commonMain` to translate GPS coordinates to the 2D image plane.
    - Leverage the existing `BaseMapViewModel` contract.
3. **Unify `MapViewModel`** — Collapse the remaining Google and F-Droid specific `MapViewModel` classes in the `:app` module into a single `commonMain` implementation by isolating platform-specific settings (styles, tile sources) behind a repository interface.
4. **iOS CI gate** — add `iosArm64()`/`iosSimulatorArm64()` to convention plugins and CI (compile-only, no implementations) to ensure `commonMain` remains pure.

## Medium-Term Priorities (60 days)

1. **iOS proof target** — Begin stubbing iOS target implementations (`NoopStubs.kt` equivalent) and setup an Xcode skeleton project.
2. **`core:api` contract split** — separate transport-neutral service contracts from the Android AIDL packaging to support iOS/Desktop service layers.
3. **Decouple Firmware DFU** — `feature:firmware` relies on Android-only DFU libraries. Evaluate wrapping this in a shared KMP interface or extracting it to allow the core `feature:firmware` module to be utilized on desktop/iOS.

## Longer-Term (90+ days)

1. **Platform-Native UI Interop** — 
   - **iOS Maps & Camera:** Implement `MapLibre` or `MKMapView` via Compose Multiplatform's `UIKitView`. Leverage `AVCaptureSession` wrapped in `UIKitView` to fulfill the `LocalBarcodeScannerProvider` contract.
   - **Web (wasmJs) Integrations:** Leverage `HtmlView` to embed raw DOM elements (e.g., `<video>`, `<iframe>`, or canvas-based maps) directly into the Compose UI tree while binding the root app via `CanvasBasedWindow`.
2. **Module maturity dashboard** — living inventory of per-module KMP readiness.
3. **Shared UI vs Shared Logic split** — If the iOS target utilizes native SwiftUI instead of Compose Multiplatform, evaluate splitting feature modules into pure `sharedLogic` (business rules, ViewModels) and `sharedUI` (Compose Multiplatform) to prevent dragging Compose dependencies into pure native iOS apps.

## Design Principles

1. **Solve in `commonMain` first.** If it doesn't need platform APIs, it belongs in `commonMain`.
2. **Interfaces in `commonMain`, implementations per-target.** The repository pattern is established — extend it. Prefer dependency injection (Koin) with interfaces over `expect`/`actual` declarations whenever possible to keep architecture decoupled and highly testable.
3. **UI Interop Strategies.** When a Compose Multiplatform equivalent doesn't exist (e.g., Maps, Camera), use standard interop APIs rather than extracting the entire screen to native code. Use `AndroidView` for Android, `UIKitView` for iOS, `SwingPanel` for JVM/Desktop, and `HtmlView` for Web (`wasmJs`). Always wrap these in a shared `commonMain` interface contract (like `LocalBarcodeScannerProvider`).
4. **Stubs are a valid first implementation.** Every target starts with no-op stubs, then graduates to real implementations.
5. **Feature modules stay target-agnostic in `commonMain`.** Platform UI goes in platform source sets. Keep the UI layer dumb and rely on shared ViewModels (Unidirectional Data Flow) to drive state.
6. **Transport is a pluggable adapter.** BLE, serial, TCP, MQTT all implement `RadioTransport` and are orchestrated by a shared `RadioInterfaceService`.
7. **CI validates every target.** If a module declares `jvm()`, CI compiles it. No exceptions. Run tests on appropriate host runners (macOS for iOS, Linux for JVM/Android) to catch platform regressions.
8. **Test in `commonTest` first.** ViewModel and business logic tests belong in `commonTest` so every target runs them. Use shared `core:testing` utilities to minimize duplication.
9. **Zero Platform Leaks.** Never import `java.*` or `android.*` inside `commonMain`. Use KMP-native alternatives like `kotlinx-datetime` and `Okio`.
