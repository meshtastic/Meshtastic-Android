# Implementation Plan: Device Connections

**Branch**: `005-device-connections` | **Date**: 2026-07-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-device-connections/spec.md`
**Status**: Migrated — reverse-engineered from existing `feature/connections` module

## Summary

Device Connections provides BLE scanning, USB/Serial enumeration, TCP/NSD network discovery, manual IP entry, and device selection/disconnection from a unified Connections screen. The implementation follows a platform-subclass pattern: `ScannerViewModel` in `commonMain` handles scan state, device lists, and selection logic; `AndroidScannerViewModel` and `JvmScannerViewModel` override bonding/permission flows. All UI is Compose Multiplatform in `commonMain`. Device discovery is delegated to `GetDiscoveredDevicesUseCase` with platform-specific implementations.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21
**Primary Dependencies**: Compose Multiplatform, Material 3 Adaptive, Koin 4.2+ (K2 Compiler Plugin), DataStore KMP, Navigation 3
**Storage**: DataStore KMP for preferences (`UiPrefs`: auto-scan, transport visibility); `RecentAddressesDataSource` for recent TCP addresses
**Testing**: KMP `allTests` for `feature:connections` — 3 test files, 26 tests (Turbine + Mokkery + Kotest matchers)
**Target Platform**: Android, Desktop (JVM) — all via `commonMain`
**Performance Goals**: BLE scan results within 1 scan interval; RSSI throttled to 2s; RSSI read timeout 1s
**Constraints**: All UI in `commonMain`; no `java.*`/`android.*` in common; CMP float pre-formatting via `NumberFormatter.format()`
**Scale/Scope**: 20 commonMain files, 3 androidMain files, 4 jvmMain files, 3 commonTest files

## Constitution Check

*GATE: All principles verified against existing implementation.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All business logic and UI in `commonMain`. Platform code limited to `androidMain` (bonding, USB permission) and `jvmMain` (direct GATT connect, JVM USB stub). No `java.*`/`android.*` in common. |
| II. Zero Lint Tolerance | ✅ PASS | `detekt-baseline.xml` present. Suppressions documented (`LongParameterList`, `TooManyFunctions`, `CyclomaticComplexMethod`). |
| III. Compose Multiplatform UI | ✅ PASS | CMP composables throughout. Navigation 3 via `connectionsGraph()`. `stringResource(Res.string.*)` for all labels. |
| IV. Privacy First | ✅ PASS | Device addresses anonymized via `anonymize()` in all log output. No PII logged. Proto submodule read-only. NSD is local-only. |
| V. Design Standards Compliance | ✅ PASS | M3 components: `FilterChip`, `OutlinedButton`, `Card`, `ListItem`, `ModalBottomSheet`. Accessibility: `selectable`, `Role.RadioButton`, `combinedClickable` with `onClickLabel`. |
| VI. Verify Before Push | ✅ PASS | 26 tests pass via `allTests`. `spotlessApply` + `detekt` required before merge. |
| VII. Coroutine Safety | ✅ PASS | `safeLaunch` used for all coroutine launches. `safeCatchingAll` in use case. Project `CoroutineDispatchers` injected (not `Dispatchers.IO`). |
| VIII. Resource Discipline | ✅ PASS | `stringResource(Res.string.*)` for all UI text. `MeshtasticIcons` for all icons. |
| IX. Branch & Scope Hygiene | ✅ PASS | Module scoped to `feature/connections`. Clean separation of concerns across source sets. |

**Gate Result**: ✅ All principles satisfied

## Project Structure

### Documentation (this feature)

```text
specs/005-device-connections/
├── spec.md              # Feature specification (migrated)
├── plan.md              # This file (migrated)
└── tasks.md             # Task list (migrated)
```

### Source Code (repository root)

```text
feature/connections/
├── build.gradle.kts
├── detekt-baseline.xml
├── src/commonMain/kotlin/org/meshtastic/feature/connections/
│   ├── Constants.kt                         ← Address prefixes: NO_DEVICE_SELECTED, TCP, BLE, MOCK
│   ├── ScannerViewModel.kt                  ← Platform-neutral ViewModel: scan, select, disconnect
│   ├── di/
│   │   └── FeatureConnectionsModule.kt      ← Koin @Module + @ComponentScan
│   ├── domain/usecase/
│   │   ├── CommonGetDiscoveredDevicesUseCase.kt  ← Platform-agnostic TCP + USB + mock aggregation
│   │   ├── TcpDiscoveryHelpers.kt           ← Shared: processTcpServices, matchNodes, buildRecent
│   │   └── UsbScanner.kt                    ← Interface for platform USB enumeration
│   ├── model/
│   │   ├── DeviceListEntry.kt               ← Sealed class: Ble, Usb, Tcp, Mock
│   │   └── DiscoveredDevices.kt             ← Data class + GetDiscoveredDevicesUseCase interface
│   ├── navigation/
│   │   └── ConnectionsNavigation.kt         ← Navigation 3: connectionsGraph()
│   └── ui/
│       ├── ConnectionsScreen.kt             ← Top-level screen: status card + list + filter chips
│       └── components/
│           ├── ConnectingDeviceInfo.kt       ← Connecting state card
│           ├── ConnectionActionButton.kt    ← Shared icon+label button (4 styles)
│           ├── ConnectionActionButtonStyle.kt ← Enum: Filled, Tonal, Outlined, Text
│           ├── CurrentlyConnectedInfo.kt    ← Connected card: battery, RSSI polling, node chip
│           ├── DeviceList.kt                ← LazyColumn: BLE/Network/USB sections + AddDialog
│           ├── DeviceListItem.kt            ← Device row: icon, name, RSSI, radio button
│           ├── DeviceSectionHeader.kt       ← Section header with progress + trailing action
│           ├── DisconnectButton.kt          ← Error-tinted OutlinedButton
│           ├── EmptyStateContent.kt         ← Full-page empty state (unused — inline variant in DeviceList)
│           └── TransportFilterChips.kt      ← BLE/Network/USB filter chips
├── src/androidMain/kotlin/org/meshtastic/feature/connections/
│   ├── AndroidScannerViewModel.kt           ← createBond() + USB permission flow
│   ├── domain/usecase/
│   │   └── AndroidGetDiscoveredDevicesUseCase.kt ← Bonded BLE + USB serial + TCP
│   └── model/
│       └── AndroidUsbDeviceData.kt          ← Wraps UsbSerialDriver
├── src/jvmMain/kotlin/org/meshtastic/feature/connections/
│   ├── JvmScannerViewModel.kt               ← Direct GATT connect (no explicit bonding)
│   ├── domain/usecase/
│   │   ├── JvmGetDiscoveredDevicesUseCase.kt ← Wraps CommonGetDiscoveredDevicesUseCase
│   │   └── JvmUsbScanner.kt                 ← Stub (empty list)
│   └── model/
│       └── JvmUsbDeviceData.kt              ← Stub UsbDeviceData
└── src/commonTest/kotlin/org/meshtastic/feature/connections/
    ├── ScannerViewModelTest.kt              ← 11 tests: scan state, device selection, NSD gating, sort order
    ├── domain/usecase/
    │   ├── CommonGetDiscoveredDevicesUseCaseTest.kt ← 10 tests: TCP discovery, node matching, mock
    │   └── TcpDiscoveryHelpersTest.kt       ← 10 tests: processTcpServices, matchNodes, buildRecent, findByNameSuffix
```

**Structure Decision**: Feature module follows the standard KMP pattern. Platform-specific ViewModel subclasses are in `androidMain`/`jvmMain` and bound via Koin `@KoinViewModel(binds = [...])`. Use case interface is in `commonMain`; implementations are platform-specific `@Single` bindings.

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/connections` (commonMain) | Full feature | 20 files | Low — self-contained |
| `feature/connections` (androidMain) | Platform impl | 3 files | Medium — OS bonding/permissions |
| `feature/connections` (jvmMain) | Platform stubs | 4 files | Low — thin wrappers |
| `feature/connections` (commonTest) | Tests | 3 files | Low |
| `core/ble` | Dependency | 0 (consumed) | Low — read-only |
| `core/network` | Dependency | 0 (consumed) | Low — read-only |
| `core/datastore` | Dependency | 0 (consumed) | Low — read-only |
| `core/resources` | Modify | strings.xml entries | Low |

## Integration Points

- **Navigation**: `ConnectionsRoute.Connections` and `ConnectionsRoute.ConnectionsGraph` registered via `connectionsGraph()` in `ConnectionsNavigation.kt`. Uses Navigation 3 `entry<>` pattern.
- **DI**: `FeatureConnectionsModule` with `@ComponentScan("org.meshtastic.feature.connections")`. Android binds `AndroidScannerViewModel` → `ScannerViewModel` via `@KoinViewModel(binds = [...])`. Android binds `AndroidGetDiscoveredDevicesUseCase` → `GetDiscoveredDevicesUseCase` via `@Single(binds = [...])`.
- **DataStore Keys**: `UiPrefs.bleAutoScan`, `UiPrefs.networkAutoScan`, `UiPrefs.showBleTransport`, `UiPrefs.showNetworkTransport`, `UiPrefs.showUsbTransport`.
- **Radio Controller**: `RadioController.setDeviceAddress()` for device selection/disconnection.
- **Service Repository**: `ServiceRepository.connectionProgress` flow for status chatter; `ServiceRepository.setErrorMessage()` for bonding failures.
- **Settings Integration**: Imports `RadioConfigViewModel` and `ConfigRoute.LORA` for the "Set your region" flow. Depends on `feature/settings` module.

## Design Constraints

- All UI lives in `commonMain` — not platform-specific
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- Error handling uses `safeCatching {}` / `safeCatchingAll {}` not `runCatching {}`
- Dispatchers via injected `CoroutineDispatchers` — not `Dispatchers.IO`
- Float values must be pre-formatted with `NumberFormatter.format()` (CMP constraint)
- RSSI polling throttled to 2-second intervals with 1-second read timeout
- NSD scanning gated behind user toggle to avoid Android 15+ system consent on screen entry

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| BLE bonding flakiness on some Android OEMs | Medium | Medium | `requestBonding()` catches all exceptions; known "bond state 11" handled as non-error |
| Android 15+ NSD consent dialog disrupts UX | Low | Low | NSD gated behind explicit user toggle; `ACCESS_LOCAL_NETWORK` requested via launcher |
| RSSI read timeout blocking UI | Low | Medium | `withTimeout(1.seconds)` + `TimeoutCancellationException` caught gracefully |
| USB permission denial | Low | Low | Permission flow surfaces denial via log; user can re-tap to retry |

## Phase Alignment with Tasks

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Setup | Constants, DI, build config | DC-T001–DC-T004 | None |
| 2. Models & Domain | Data models, use cases, helpers | DC-T005–DC-T011 | Phase 1 |
| 3. US1 — BLE Discovery | ViewModel + BLE scan + device list | DC-T012–DC-T016 | Phase 2 |
| 4. US2/US3 — TCP/Network | NSD discovery + manual add | DC-T017–DC-T019 | Phase 2 |
| 5. US4 — USB/Serial | USB enumeration + permission | DC-T020–DC-T021 | Phase 2 |
| 6. US5 — Connection Status | Status card states + disconnect | DC-T022–DC-T025 | Phase 3 |
| 7. US6 — Transport Filters | Filter chips + persistence | DC-T026–DC-T027 | Phase 3 |
| 8. Tests & Verification | All test files + lint | DC-T028–DC-T032 | All prior |

### Critical Path

```
Phase 1 → Phase 2 → Phase 3 (BLE/ViewModel) → Phase 6 (Status) → Phase 8 (Tests)
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |

