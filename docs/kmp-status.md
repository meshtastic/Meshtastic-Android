# KMP Migration Status

> Last updated: 2026-03-16

Single source of truth for Kotlin Multiplatform migration progress. For the forward-looking roadmap, see [`roadmap.md`](./roadmap.md). For completed decision records, see [`decisions/`](./decisions/).

## Summary

Meshtastic-Android has completed its **Android-first structural KMP migration** across core logic and feature modules, with **full JVM cross-compilation validated in CI**. The desktop target has a working Navigation 3 shell, TCP transport with full mesh handshake, and multiple features wired with real screens.

Modules that share JVM-specific code between Android and desktop now standardize on the `meshtastic.kmp.jvm.android` convention plugin, which creates `jvmAndroidMain` via Kotlin's hierarchy template API instead of manual `dependsOn(...)` source-set wiring.

## Module Inventory

### Core Modules (20 total)

| Module | KMP? | JVM target? | Notes |
|---|:---:|:---:|---|
| `core:proto` | ✅ | ✅ | Protobuf definitions |
| `core:common` | ✅ | ✅ | Utilities, `jvmAndroidMain` source set |
| `core:model` | ✅ | ✅ | Domain models, `jvmAndroidMain` source set |
| `core:repository` | ✅ | ✅ | Domain interfaces |
| `core:di` | ✅ | ✅ | Dispatchers, qualifiers |
| `core:navigation` | ✅ | ✅ | Shared Navigation 3 routes |
| `core:resources` | ✅ | ✅ | Compose Multiplatform resources |
| `core:datastore` | ✅ | ✅ | Multiplatform DataStore |
| `core:database` | ✅ | ✅ | Room KMP |
| `core:domain` | ✅ | ✅ | UseCases |
| `core:prefs` | ✅ | ✅ | Preferences layer |
| `core:network` | ✅ | ✅ | Ktor, `StreamFrameCodec`, `TcpTransport`, `SerialTransport` |
| `core:data` | ✅ | ✅ | Data orchestration |
| `core:ble` | ✅ | ✅ | Kable multiplatform BLE abstractions in commonMain |
| `core:nfc` | ✅ | ✅ | NFC contract in commonMain; hardware in androidMain |
| `core:service` | ✅ | ✅ | Service layer; Android bindings in androidMain |
| `core:ui` | ✅ | ✅ | Shared Compose UI, pure KMP QR generator, `jvmAndroidMain` + `jvmMain` actuals |
| `core:testing` | ✅ | ✅ | Shared test doubles, fakes, and utilities for `commonTest` |
| `core:api` | ❌ | — | Android-only (AIDL). Intentional. |
| `core:barcode` | ❌ | — | Android-only (CameraX). Flavor split minimised to decoder factory only (ML Kit / ZXing). Shared contract in `core:ui`. |

**18/20** core modules are KMP with JVM targets. The 2 Android-only modules are intentionally platform-specific, with shared contracts already abstracted into `core:ui/commonMain`.

### Feature Modules (8 total — 7 KMP with JVM)

| Module | UI in commonMain? | Desktop wired? |
|---|:---:|:---:|
| `feature:settings` | ✅ | ✅ ~35 real screens; fully shared `settingsGraph` and UI |
| `feature:node` | ✅ | ✅ Adaptive list-detail; fully shared `nodesGraph`, `PositionLogScreen`, and `NodeContextMenu` |
| `feature:messaging` | ✅ | ✅ Adaptive contacts + messages; fully shared `contactsGraph`, `MessageScreen`, `ContactsScreen`, and `MessageListPaged` |
| `feature:connections` | ✅ | ✅ Shared `ConnectionsScreen` with dynamic transport detection |
| `feature:intro` | ✅ | — |
| `feature:map` | ✅ | Placeholder; shared `NodeMapViewModel` |
| `feature:firmware` | — | Placeholder; DFU is Android-only |
| `feature:widget` | ❌ | — | Android-only (Glance appwidgets). Intentional. |

### Desktop Module

Working Compose Desktop application with:
- Navigation 3 shell (`NavigationRail` + `NavDisplay`) using shared routes
- Full Koin DI graph (stubs + real implementations)
- TCP, Serial/USB, and BLE transports with auto-reconnect and full `want_config` handshake
- Adaptive list-detail screens for nodes and contacts
- **Dynamic Connections screen** with automatic discovery of platform-supported transports (TCP, Serial/USB, BLE)
- **Desktop language picker** backed by `UiPreferencesDataSource.locale`, with immediate Compose Multiplatform resource updates
- **Navigation-preserving locale switching** via `Main.kt` `staticCompositionLocalOf` recomposition instead of recreating the Nav3 backstack
- Node detail metrics screens (Device, Environment, Signal, Power, Pax) wired with shared KMP + Vico charts
- **Feature-driven Architecture:** Desktop navigation completely relies on feature modules via `commonMain` exported graphs (`settingsGraph`, `nodesGraph`, `contactsGraph`, etc.), reducing the desktop module to a simple host shell.
- **Native notifications and system tray icon** wired via `DesktopNotificationManager`
- **Native release pipeline** generating `.dmg` (macOS), `.msi` (Windows), and `.deb` (Linux) installers in CI

## Scorecard

| Area | Score | Notes |
|---|---|---|
| Shared business/data logic | **9/10** | All core layers shared; RadioTransport interface unified |
| Shared feature/UI logic | **8.5/10** | All 7 KMP; feature:connections unified with dynamic transport detection |
| Android decoupling | **9/10** | No known `java.*` calls in `commonMain`; app module extraction in progress (navigation, connections, background services, and widgets extracted) |
| Multi-target readiness | **8/10** | Full JVM; release-ready desktop; iOS not declared |
| CI confidence | **9/10** | 25 modules validated (including feature:connections); native release installers automated |
| DI portability | **8/10** | Koin annotations in commonMain; supportedDeviceTypes injected per platform |
| Test maturity | **9/10** | Mokkery, Turbine, and Kotest integrated; property-based testing established; broad coverage across all 8 features |

> See [`decisions/architecture-review-2026-03.md`](./decisions/architecture-review-2026-03.md) for the full gap analysis.

## Completion Estimates

| Lens | % |
|---|---:|
| Android-first structural KMP | ~100% |
| Shared business logic | ~98% |
| Shared feature/UI | ~95% |
| True multi-target readiness | ~75% |
| "Add iOS without surprises" | ~65% |

## Proposed Next Steps for KMP Migration

Based on the latest codebase investigation, the following steps are proposed to complete the multi-target and iOS-readiness migrations:

1. **Wire Desktop Features:** Complete desktop UI wiring for `feature:intro` and implement a shared fallback for `feature:map` (which is currently a placeholder on desktop).
2. **Decouple Firmware DFU:** `feature:firmware` relies on Android-only DFU libraries. Evaluate wrapping this in a shared KMP interface or extracting it into a separate plugin to allow the core `feature:firmware` module to be fully utilized on desktop/iOS.
3. **Prepare for iOS Target:** Set up an initial skeleton Xcode project to start validating `commonMain` compilation on Kotlin/Native (iOS).

## Key Architecture Decisions

| Decision | Status | Details |
|---|---|---|
| Navigation 3 parity model (shared `TopLevelDestination` + platform adapters) | ✅ Done | Both shells use shared enum + parity tests. See [`decisions/navigation3-parity-2026-03.md`](./decisions/navigation3-parity-2026-03.md) |
| Hilt → Koin | ✅ Done | See [`decisions/koin-migration.md`](./decisions/koin-migration.md) |
| BLE abstraction (Kable) | ✅ Done | See [`decisions/ble-strategy.md`](./decisions/ble-strategy.md) |
| Material 3 Adaptive (JetBrains) | ✅ Done | Version `1.3.0-alpha06` aligned with CMP `1.11.0-alpha04` |
| JetBrains lifecycle/nav3 alias alignment | ✅ Done | All forked deps use `jetbrains-*` prefix in version catalog; `core:data` commonMain uses JetBrains lifecycle runtime |
| Expect/actual consolidation | ✅ Done | 7 pairs eliminated; 15+ genuinely platform-specific retained |
| Transport deduplication | ✅ Done | `StreamFrameCodec`, `TcpTransport`, and `SerialTransport` shared in `core:network` |
| **Transport Lifecycle Unification** | ✅ Done | `SharedRadioInterfaceService` orchestrates auto-reconnect, connection state, and heartbeat uniformly across Android and Desktop. |
| **Database Parity** | ✅ Done | `DatabaseManager` is pure KMP, giving iOS and Desktop support for multiple connected nodes with LRU caching. |
| Emoji picker unification | ✅ Done | Single commonMain implementation replacing 3 platform variants |

## Navigation Parity Note

- Desktop and Android both use the shared `TopLevelDestination` enum from `core:navigation/commonMain` — no separate `DesktopDestination` remains.
- Both shells iterate `TopLevelDestination.entries` with shared icon mapping from `core:ui` (`TopLevelDestinationExt.icon`).
- Desktop locale changes now trigger a full subtree recomposition from `Main.kt` without resetting the shared Navigation 3 backstack, so translated labels update in place.
- Firmware remains available as an in-flow route instead of a top-level destination, matching Android information architecture.
- Android navigation graphs are decoupled and extracted into their respective feature modules, aligning with the Desktop architecture.
- Parity tests exist in `core:navigation/commonTest` (`NavigationParityTest`) and `desktop/test` (`DesktopTopLevelDestinationParityTest`).
- Remaining parity work is documented in [`decisions/navigation3-parity-2026-03.md`](./decisions/navigation3-parity-2026-03.md): serializer registration validation and platform exception tracking.

## App Module Thinning Status

All major ViewModels have now been extracted to `commonMain` and no longer rely on Android-specific subclasses. Platform-specific dependencies (like `android.net.Uri` or Location permissions) have been successfully isolated behind injected `core:repository` interfaces (e.g., `FileService`, `LocationService`).

**The extraction of all feature-specific navigation graphs, background services, and widgets out of `:app` is complete.** The `:app` module now only serves as the root DI assembler and NavHost container.

Extracted to shared `commonMain` (no longer app-only):
- `SettingsViewModel` → `feature:settings/commonMain`
- `RadioConfigViewModel` → `feature:settings/commonMain`
- `DebugViewModel` → `feature:settings/commonMain`
- `MetricsViewModel` → `feature:node/commonMain`
- `UIViewModel` → `core:ui/commonMain`
- `ChannelViewModel` → `feature:settings/commonMain`
- `NodeMapViewModel` → `feature:map/commonMain` (Shared logic for node-specific maps)
- `BaseMapViewModel` → `feature:map/commonMain` (Core contract for all maps)

Extracted to core KMP modules:
- Android Services, WorkManager Workers, and BroadcastReceivers → `core:service/androidMain`
- BLE and USB/Serial radio connections → `core:network/androidMain`
- TCP radio connections and mDNS/NSD Service Discovery → `core:network/commonMain` (with Android `NsdManager` and Desktop `JmDNS` implementations)

Remaining to be extracted from `:app` or unified in `commonMain`:
- `MapViewModel` (Unify Google/F-Droid flavors into a single `commonMain` class consuming a `MapConfigProvider` interface)
- Top-level UI composition (`ui/Main.kt`)

## Prerelease Dependencies

| Dependency | Version | Why |
|---|---|---|
| Compose Multiplatform | `1.11.0-alpha04` | Required for JetBrains Adaptive `1.3.0-alpha06` |
| Koin | `4.2.0-RC2` | Nav3 + K2 compiler plugin support |
| JetBrains Lifecycle | `2.10.0-beta01` | Multiplatform ViewModel/lifecycle |
| JetBrains Navigation 3 | `1.1.0-alpha04` | Multiplatform navigation |
| Kable BLE | `0.42.0` | Provides fully multiplatform BLE support |

**Policy:** Stable by default. RC when it unlocks KMP functionality. Alpha only behind hard abstraction seams. Do not downgrade CMP or Koin — they enable critical KMP features.

## References

- Roadmap: [`docs/roadmap.md`](./roadmap.md)
- Agent guide: [`AGENTS.md`](../AGENTS.md)
- Playbooks: [`docs/agent-playbooks/`](./agent-playbooks/)
- Decision records: [`docs/decisions/`](./decisions/)
