# Implementation Plan: WiFi Provisioning (ESP32 SoftAP)

**Branch**: `011-wifi-provisioning` | **Date**: 2026-06-15 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/011-wifi-provisioning/spec.md`

**Note**: This plan was reverse-engineered from an existing, fully implemented feature (brownfield migration).

## Summary

WiFi Provisioning enables users to configure an ESP32 device's WiFi connection over BLE using the nymea-networkmanager GATT profile. The implementation is a self-contained KMP feature module with a domain layer (nymea protocol codec + GATT client service), a ViewModel state machine, and a Compose Multiplatform UI — all in `commonMain`. The feature depends on `core:ble` for BLE abstraction and `core:testing` for fake BLE implementations.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Compose Multiplatform, Material 3 Expressive, Koin 4.2+ (K2 Compiler Plugin), kotlinx.serialization, Kermit logging  
**Storage**: N/A — no persistence; all state is in-memory scoped to the ViewModel session  
**Testing**: KMP `allTests` for `feature:wifi-provision`; `commonTest` with fake BLE implementations from `core:testing`  
**Target Platform**: Android, Desktop (JVM), iOS — all via `commonMain`  
**Performance Goals**: BLE connect + scan + provision under 30 seconds end-to-end  
**Constraints**: All UI in `commonMain`; no `java.*`/`android.*` in common; BLE packets capped at 20 bytes; `safeCatching {}` for error handling  
**Scale/Scope**: 11 source files, 5 test files across `feature/wifi-provision`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All 11 source files in `commonMain`. No `java.*`/`android.*` imports. BLE abstracted via `core:ble`. |
| II. Zero Lint Tolerance | ✅ PASS | `@Suppress` annotations present only for known Detekt rules (`TooManyFunctions`, `LongMethod`, `MagicNumber` in tests). |
| III. Compose Multiplatform UI | ✅ PASS | CMP composables throughout. Material 3 Expressive components. Navigation 3 `EntryProviderScope` pattern. |
| IV. Privacy First | ✅ PASS | WiFi passwords never logged (only command JSON logged at DEBUG level, passwords inside nymea `"p"` field). No PII stored. |
| V. Design Standards Compliance | ✅ PASS | M3 ListItem, Card, OutlinedTextField, FilledTonalButton, LoadingIndicator. Accessibility: `clickable` with role, content descriptions. |
| VI. Verify Before Push | ✅ PASS | Full verification: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`. |
| VII. Coroutine Safety | ✅ PASS | `safeCatching {}` used in `NymeaWifiService.connect()`, `scanNetworks()`, `provision()`, `fetchConnectionIpAddress()`. Project `CoroutineDispatchers` injected. |
| VIII. Resource Discipline | ✅ PASS | All strings via `stringResource(Res.string.wifi_provision_*)`. Icons via `MeshtasticIcons.*`. |
| IX. Branch & Scope Hygiene | ✅ PASS | Self-contained feature module. No cross-feature changes. |

**Gate Result**: ✅ All principles satisfied

## Project Structure

### Documentation (this feature)

```text
specs/011-wifi-provisioning/
├── spec.md              # Feature specification (migrated)
├── plan.md              # This file (migrated)
└── tasks.md             # Task breakdown (migrated)
```

### Source Code (repository root)

```text
feature/wifi-provision/
├── src/commonMain/kotlin/org/meshtastic/feature/wifiprovision/
│   ├── NymeaBleConstants.kt                 ← GATT UUIDs, command codes, timeouts
│   ├── WifiProvisionViewModel.kt            ← UI state machine (6 phases)
│   ├── di/
│   │   └── FeatureWifiProvisionModule.kt    ← Koin DI module
│   ├── domain/
│   │   ├── NymeaPacketCodec.kt              ← BLE packet encode/reassemble
│   │   ├── NymeaProtocol.kt                 ← JSON serialization models
│   │   └── NymeaWifiService.kt              ← GATT client: connect, scan, provision
│   ├── model/
│   │   └── WifiNetwork.kt                   ← WifiNetwork + ProvisionResult models
│   ├── navigation/
│   │   └── WifiProvisionNavigation.kt       ← Navigation 3 graph entries
│   └── ui/
│       ├── ProvisionStatusCard.kt           ← Inline status card composable
│       ├── WifiProvisionPreviews.kt         ← Compose preview definitions
│       └── WifiProvisionScreen.kt           ← Main screen with sub-composables
├── src/commonTest/kotlin/org/meshtastic/feature/wifiprovision/
│   ├── DeduplicateBySsidTest.kt             ← SSID dedup logic tests
│   ├── WifiProvisionViewModelTest.kt        ← ViewModel state machine tests
│   └── domain/
│       ├── NymeaPacketCodecTest.kt          ← Packet encode/reassemble tests
│       ├── NymeaProtocolTest.kt             ← JSON serialization round-trip tests
│       └── NymeaWifiServiceTest.kt          ← GATT client integration tests
```

**Structure Decision**: Self-contained feature module following `feature/{name}` convention. Domain layer lives within the feature (not in `core`) because the nymea protocol is specific to this provisioning flow and not reused elsewhere.

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/wifi-provision` | New | 16 files (11 src + 5 test) | Low — self-contained |
| `core/ble` | None (consumed) | 0 | Low — uses existing interfaces |
| `core/testing` | None (consumed) | 0 | Low — uses existing fakes |
| `core/resources` | Modify | 1 file (strings.xml) | Low — additive string resources |
| `core/ui` | None (consumed) | 0 | Low — reuses existing components |
| `core/navigation` | Modify | 1 file (WifiProvisionRoute) | Low — route registration |

## Integration Points

- **Navigation**: `WifiProvisionRoute.WifiProvisionGraph` and `WifiProvisionRoute.WifiProvision` registered in `wifiProvisionGraph()` extension function on `EntryProviderScope<NavKey>`.
- **DI**: `FeatureWifiProvisionModule` with `@ComponentScan` auto-discovers `@KoinViewModel`-annotated `WifiProvisionViewModel`.
- **BLE**: Injects `BleScanner` and `BleConnectionFactory` from `core:ble` DI graph.
- **Dispatchers**: Injects `CoroutineDispatchers` from `core:di` for testable coroutine contexts.
- **Shared UI**: Reuses `AutoLinkText`, `CopyIconButton` from `core:ui/component/`, `MeshtasticIcons` from `core:ui/icon/`, `AppTheme` from `core:ui/theme/`, `rememberOpenUrl` from `core:ui/util/`.

## Design Constraints

- All UI lives in `commonMain` — not platform-specific
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- Error handling uses `safeCatching {}` not `runCatching {}`
- Dispatchers via `org.meshtastic.core.di.CoroutineDispatchers`
- BLE packets capped at 20 bytes (no MTU negotiation)
- JSON codec uses `kotlinx.serialization` with lenient mode and `ignoreUnknownKeys`
- Navigation uses `dropUnlessResumed` for back stack safety

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| BLE connection instability | Medium | Medium | 10s scan timeout, 15s response timeout, typed error handling with user-visible messages |
| JSON protocol version mismatch | Low | High | `ignoreUnknownKeys = true` in JSON codec; lenient parsing |
| MTU > 20 bytes not leveraged | Low | Low | Protocol works at minimum MTU; larger MTU just means fewer packets |
| Password exposure in logs | Low | High | Passwords inside JSON payload only; DEBUG-level logging can be disabled |

## Phase Alignment with Tasks

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Domain Layer | Protocol models, codec, GATT service | WFP-T001 – WFP-T006 | None |
| 2. ViewModel | State machine, actions, error mapping | WFP-T007 – WFP-T010 | Phase 1 |
| 3. UI Layer | Screen composables, navigation, DI | WFP-T011 – WFP-T016 | Phase 2 |
| 4. Testing | Unit tests for all layers | WFP-T017 – WFP-T022 | Phases 1–3 |

### Critical Path

```
Phase 1 (Domain) → Phase 2 (ViewModel) → Phase 3 (UI) → Phase 4 (Testing)
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |

