# Architecture Review — March 2026

> Status: **Active**
> Last updated: 2026-03-31

Re-evaluation of project modularity and architecture against modern KMP and Android best practices. Identifies gaps and actionable improvements across modularity, reusability, clean abstractions, DI, and testing.

## Executive Summary

The codebase is **~98% structurally KMP** — 18/20 core modules and 8/8 feature modules declare `jvm()` targets and cross-compile in CI. Shared `commonMain` code accounts for ~52K LOC vs ~18K platform-specific LOC (a 74/26 split). This is strong.

Of the five structural gaps originally identified, four are resolved and one remains in progress:

1. **`app` is a God module** — originally 90 files / ~11K LOC of transport, service, UI, and ViewModel code that should live in core/feature modules. *(✅ Resolved — app module reduced to 6 files: `MainActivity`, `MeshUtilApplication`, Nav shell, and DI config)*
2. ~~**Radio transport layer is app-locked**~~ — ✅ Resolved: `RadioTransport` interface in `core:repository/commonMain`; shared `StreamFrameCodec` + `TcpTransport` in `core:network`.
3. ~~**`java.*` APIs leak into `commonMain`**~~ — ✅ Resolved: `Locale`, `ConcurrentHashMap`, `ReentrantLock` purged.
4. ~~**Zero feature-level `commonTest`**~~ — ✅ Resolved: 193 shared tests across all 8 features; `core:testing` module established.
5. ~~**No `feature:connections` module**~~ — ✅ Resolved: KMP module with shared UI and dynamic transport detection.

## Source Code Distribution

| Source set | Files | ~LOC | Purpose |
|---|---:|---:|---|
| `core/*/commonMain` | 337 | 32,700 | Shared business/data logic |
| `feature/*/commonMain` | 146 | 19,700 | Shared feature UI + ViewModels |
| `feature/*/androidMain` | 62 | 14,700 | Platform UI (charts, previews, permissions) |
| `app/src/main` | 6 | ~300 | Android app shell (target achieved) |
| `desktop/src` | 26 | 4,800 | Desktop app shell |
| `core/*/androidMain` | 49 | 3,500 | Platform implementations |
| `core/*/jvmMain` | 11 | ~500 | JVM actuals |
| `core/*/jvmAndroidMain` | 4 | ~200 | Shared JVM+Android code |

**Key ratio:** 74% of production code is in `commonMain` (shared). Goal: 85%+.

---

## A. Critical Modularity Gaps

### A1. `app` module is a God module

The `app` module should be a thin shell (~20 files): `MainActivity`, DI assembly, nav host. Originally it held **90 files / ~11K LOC**, now completely reduced to a **6-file shell**:

| Area | Files | LOC | Where it should live |
|---|---:|---:|---|
| `repository/radio/` | 22 | ~2,000 | `core:service` / `core:network` |
| `service/` | 12 | ~1,500 | Extracted to `core:service/androidMain` ✓ |
| `navigation/` | ~1 | ~200 | Root Nav 3 host wiring stays in `app`. Feature graphs moved to `feature:*`. |
| `settings/` ViewModels | 3 | ~350 | Thin Android wrappers (genuine platform deps) |
| `widget/` | 4 | ~300 | Extracted to `feature:widget` ✓ |
| `worker/` | 4 | ~350 | Extracted to `core:service/androidMain` and `feature:messaging/androidMain` ✓ |
| DI + Application + MainActivity | 5 | ~500 | Stay in `app` ✓ |
| UI screens + ViewModels | 5 | ~1,200 | Stay in `app` (Android-specific deps) |

**Progress:** Extracted `ChannelViewModel` → `feature:settings/commonMain`, `NodeMapViewModel` → `feature:map/commonMain`, `NodeContextMenu` → `feature:node/commonMain`, `EmptyDetailPlaceholder` → `core:ui/commonMain`. Remaining extractions require radio/service layer refactoring (bigger scope).

### A2. Radio interface layer is app-locked and non-KMP

The core transport abstraction was previously locked in `app/repository/radio/` via `IRadioInterface`. This has been successfully refactored:

1. Defined `RadioTransport` interface in `core:repository/commonMain` (replacing `IRadioInterface`)
2. Moved `StreamFrameCodec`-based framing to `core:network/commonMain`
3. Moved TCP transport to `core:network/jvmAndroidMain`
4. The remaining `app/repository/radio/` implementations (BLE, Serial, Mock) now implement `RadioTransport`.

**Recommended next steps:**
1. Move BLE transport to `core:ble/androidMain`
2. Move Serial/USB transport to `core:service/androidMain`

### A3. No `feature:connections` module *(resolved 2026-03-12)*

Device discovery UI was duplicated:
- Android: `app/ui/connections/` (13 files: `ConnectionsScreen`, `ScannerViewModel`, 10 components)
- Desktop: `desktop/ui/connections/DesktopConnectionsScreen.kt` (separate implementation)

**Outcome:** Created `feature:connections` KMP module with:
- `commonMain`: `ScannerViewModel`, `ConnectionsScreen`, 11 shared UI components, `DeviceListEntry` sealed class, `GetDiscoveredDevicesUseCase` interface, `CommonGetDiscoveredDevicesUseCase` (TCP/recent devices)
- `androidMain`: `AndroidScannerViewModel` (BLE bonding, USB permissions), `AndroidGetDiscoveredDevicesUseCase` (BLE/NSD/USB discovery), `NetworkRepository`, `UsbRepository`, `SerialConnection`
- Desktop uses the shared `ConnectionsScreen` + `CommonGetDiscoveredDevicesUseCase` directly
- Dynamic transport detection via `RadioInterfaceService.supportedDeviceTypes`
- Module registered in both `AppKoinModule` and `DesktopKoinModule`

### A4. `core:api` AIDL coupling

`core:api` is Android-only (AIDL IPC). `ServiceClient` in `core:service/androidMain` wraps it. Desktop doesn't use it — it has `DirectRadioControllerImpl` in `core:service/commonMain`.

**Recommendation:** The `DirectRadioControllerImpl` pattern is correct. Ensure `RadioController` (already in `core:model/commonMain`) is the canonical interface; deprecate the AIDL-based path for in-process usage.

---

## B. KMP Platform Purity

### B1. `java.util.Locale` leaks in `commonMain` *(resolved 2026-03-11)*

| File | Usage |
|---|---|
| `core:data/.../TracerouteHandlerImpl.kt` | Replaced with `NumberFormatter.format(seconds, 1)` |
| `core:data/.../NeighborInfoHandlerImpl.kt` | Replaced with `NumberFormatter.format(seconds, 1)` |
| `core:prefs/.../MeshPrefsImpl.kt` | Replaced with locale-free `uppercase()` |

**Outcome:** The three `Locale` usages identified in March were removed from `commonMain`. Follow-up cleanup in the same sprint also moved `ReentrantLock`-based `SyncContinuation` to `jvmAndroidMain`, replaced prefs `ConcurrentHashMap` caches with atomic persistent maps, and pushed enum reflection behind `expect`/`actual` so no known `java.*` runtime calls remain in `commonMain`.

### B2. `ConcurrentHashMap` leaks in `commonMain` *(resolved 2026-03-11)*

Formerly found in 3 prefs files:
- `core:prefs/.../MeshPrefsImpl.kt`
- `core:prefs/.../UiPrefsImpl.kt`
- `core:prefs/.../MapConsentPrefsImpl.kt`

**Outcome:** These caches now use `AtomicRef<PersistentMap<...>>` helpers in `commonMain`, eliminating the last `ConcurrentHashMap` usage from shared prefs code.

### B3. MQTT (Resolved)

`MQTTRepositoryImpl` has been migrated to `commonMain` using KMQTT, replacing Eclipse Paho.

**Fix:** Completed.
- `kmqtt` library integrated for full KMP support.

### B4. Vico charts *(resolved)*

Vico chart screens (DeviceMetrics, EnvironmentMetrics, SignalMetrics, PowerMetrics, PaxMetrics) have been migrated to `feature:node/commonMain` using Vico's KMP artifacts (`vico-compose`, `vico-compose-m3`). Desktop wires them via shared composables. No Android-only chart code remains.

### B5. Cross-platform code deduplication *(resolved 2026-03-21)*

Comprehensive audit of `androidMain` vs `jvmMain` duplication across all feature modules. Extracted shared components:

| Component | Module | Eliminated from |
|---|---|---|
| `AlertHost` composable | `core:ui/commonMain` | Android `Main.kt`, Desktop `DesktopMainScreen.kt` |
| `SharedDialogs` composable | `core:ui/commonMain` | Android `Main.kt`, Desktop `DesktopMainScreen.kt` |
| `PlaceholderScreen` composable | `core:ui/commonMain` | 4 copies: `desktop/navigation`, `feature:map/jvmMain`, `feature:node/jvmMain` (×2) |
| `ThemePickerDialog` + `ThemeOption` | `feature:settings/commonMain` | Android `SettingsScreen.kt`, Desktop `DesktopSettingsScreen.kt` |
| `formatLogsTo()` + `redactedKeys` | `feature:settings/commonMain` (`LogFormatter.kt`) | Android + Desktop `LogExporter.kt` actuals |
| `handleNodeAction()` | `feature:node/commonMain` | Android `NodeDetailScreen.kt`, Desktop `NodeDetailScreens.kt` |
| `findNodeByNameSuffix()` | `feature:connections/commonMain` | Android USB matcher, TCP recent device matcher |

Also fixed `Dispatchers.IO` usage in `StoreForwardPacketHandlerImpl` (would break iOS), removed dead `UIViewModel.currentAlert` property, and added `firebase-debug.log` to `.gitignore`.

---

## C. DI Improvements

### C1. ~~Desktop manual ViewModel wiring~~ *(resolved 2026-03-13)*

`DesktopKoinModule.kt` originally had ~120 lines of hand-written `viewModel { ... }` blocks. These have been successfully replaced by including Koin modules from `commonMain` generated via the Koin K2 Compiler Plugin for automatic wiring.

### C2. ~~Desktop stubs lack compile-time validation~~ *(resolved 2026-03-13)*

`desktopPlatformStubsModule()` previously had stubs that were only validated at runtime.

**Outcome:** Added `DesktopKoinTest.kt` using Koin's `verify()` API. This test validates the entire Desktop DI graph (including platform stubs and DataStores) during the build. Discovered and fixed missing stubs for `CompassHeadingProvider`, `PhoneLocationProvider`, and `MagneticFieldProvider`.

### C3. DI module naming convention

Android uses `@Module`-annotated classes (`CoreDataModule`, `CoreBleAndroidModule`). Desktop imports them as `CoreDataModule().coreDataModule()`. This works but the double-invocation pattern is non-obvious.

**Recommendation:** Document the pattern in AGENTS.md. Consider if Koin Annotations 2.x supports a simpler import syntax.

---

## D. Test Architecture

### D1. Zero `commonTest` in feature modules *(resolved 2026-03-12)*

| Module | `commonTest` | `test`/`androidUnitTest` | `androidTest` |
|---|---:|---:|---:|
| `feature:settings` | 22 | 20 | 15 |
| `feature:node` | 24 | 9 | 0 |
| `feature:messaging` | 18 | 5 | 3 |
| `feature:connections` | 27 | 0 | 0 |
| `feature:firmware` | 15 | 25 | 0 |
| `feature:wifi-provision` | 62 | 0 | 0 |

**Outcome:** All 8 feature modules now have `commonTest` coverage (193 shared tests). Combined with 70 platform unit tests and 18 instrumented tests, feature modules have 281 tests total.

### D2. No shared test fixtures *(resolved 2026-03-12)*

`core:testing` module established with shared fakes (`FakeNodeRepository`, `FakeServiceRepository`, `FakeRadioController`, `FakePacketRepository`) and `TestDataFactory` builders. Used by all feature `commonTest` suites.

### D3. Core module test gaps

36 `commonTest` files exist but are concentrated in `core:domain` (22 files) and `core:data` (10 files). Limited or zero tests in:
- `core:service` (has `ServiceRepositoryImpl`, `DirectRadioControllerImpl`, `MeshServiceOrchestrator`)
- `core:network` (has `StreamFrameCodecTest` — 10 tests; `TcpTransport` untested)
- `core:prefs` (preference flows, default values)
- `core:ble` (connection state machine)
- `core:ui` (utility functions)

### D4. Desktop has 2 tests

`desktop/src/test/` contains `DesktopKoinTest.kt` and `DesktopTopLevelDestinationParityTest.kt`. Still needs:
- Navigation graph coverage

---

## E. Module Extraction Priority

Ordered by impact × effort:

| Priority | Extraction | Impact | Effort | Enables |
|---:|---|---|---|---|
| 1 | ~~`java.*` purge from `commonMain` (B1, B2)~~ | High | Low | ~~iOS target declaration~~ ✅ Done |
| 2 | Radio transport interfaces to `core:repository` (A2) | High | Medium | Transport unification |
| 3 | `core:testing` shared fixtures (D2) | Medium | Low | Feature commonTest |
| 4 | Feature `commonTest` (D1) | Medium | Medium | KMP test coverage |
| 5 | `feature:connections` (A3) | High | Medium | ~~Desktop connections~~ ✅ Done |
| 6 | Service/worker extraction from `app` (A1) | Medium | Medium | Thin app module |
| 7 | ~~Desktop Koin auto-wiring (C1, C2)~~ | Medium | Low | ✅ Resolved 2026-03-13 |
| 8 | MQTT KMP (B3) | Medium | High | Desktop/iOS MQTT |
| 9 | KMP charts (B4) | Medium | High | Desktop metrics |
| 10 | ~~iOS target declaration~~ | High | Low | ~~CI purity gate~~ ✅ Done |

---

## Scorecard Update

| Area | Previous | Current | Notes |
|---|---:|---:|---|
| Shared business/data logic | 8.5/10 | **9/10** | RadioTransport interface unified; all core layers shared |
| Shared feature/UI logic | 9.5/10 | **9/10** | All 8 KMP features; connections unified; cross-platform deduplication complete |
| Android decoupling | 8.5/10 | **9/10** | Connections, Navigation, Services, & Widgets extracted; GMS purged; app ~40->target 20 files |
| Multi-target readiness | 8/10 | **9/10** | Full JVM; release-ready desktop; iOS simulator builds compiling successfully |
| CI confidence | 8.5/10 | **9/10** | 26 modules validated; feature:connections + feature:wifi-provision + desktop in CI; native release installers |
| DI portability | 7/10 | **8/10** | Koin annotations in commonMain; supportedDeviceTypes injected per platform |
| Test maturity | — | **9/10** | Mokkery, Turbine, and Kotest integrated; property-based testing established; broad coverage across all 9 features |

---

## F. JVM/Desktop Database Lifecycle

Room KMP's `setAutoCloseTimeout` API is Android-only. On JVM/Desktop, once a Room database is built, its SQLite connections (5 per WAL-mode DB: 4 readers + 1 writer) remain open indefinitely until explicitly closed via `RoomDatabase.close()`.

### Problem

When a user switches between multiple mesh devices, the previous device's database remained open in the in-memory cache. Each idle database consumed ~32 MB (connection pool + prepared statement caches), leading to unbounded memory growth proportional to the number of devices ever connected in a session.

### Solution

`DatabaseManager.switchActiveDatabase()` now explicitly closes the previously active database via `closeCachedDatabase()` before activating the new one. The closed database is removed from the in-memory cache but its file is preserved, allowing transparent re-opening on next access.

Additional fixes applied:
1. **Init-order bug**: `dbCache` was declared after `currentDb`, causing NPE during `stateIn`'s `initialValue` evaluation. Reordered to ensure `dbCache` is initialized first.
2. **Corruption handlers**: `ReplaceFileCorruptionHandler` added to `createDatabaseDataStore()` on both JVM and Android, preventing DataStore corruption from crashing the app.
3. **`desktopDataDir()` deduplication**: Made public in `core:database/jvmMain` and removed the duplicate from `DesktopPlatformModule`, establishing a single source of truth for the desktop data directory.
4. **DataStore scope consolidation**: Replaced two separate `CoroutineScope` instances with a single shared `dataStoreScope` in `DesktopPlatformModule`.
5. **Coil cache path**: Desktop `Main.kt` updated to use `desktopDataDir()` instead of hardcoded `user.home`.

---

## References

- Current migration status: [`kmp-status.md`](./kmp-status.md)
- Roadmap: [`roadmap.md`](./roadmap.md)
- Agent guide: [`../AGENTS.md`](../AGENTS.md)
- Decision records: [`decisions/`](./decisions/)

