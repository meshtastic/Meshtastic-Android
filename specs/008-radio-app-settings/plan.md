# Implementation Plan: Radio & App Settings

**Branch**: `008-radio-app-settings` | **Date**: 2025-07-17 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/008-radio-app-settings/spec.md`
**Status**: Migrated — reverse-engineered from existing `feature/settings` module.

## Summary

The Radio & App Settings feature provides the complete device and application configuration experience across 76 common source files (~12,100 lines). It uses a `RadioConfigViewModel` with async protobuf request–response tracking to read/write all radio, device, and module configurations, a `SettingsViewModel` for app-level preferences via DataStore, a `DebugViewModel` for log inspection with search/filter/export, plus supporting ViewModels for channels, filter settings, and node database cleanup. Navigation uses Navigation 3 `settingsGraph` with `entry<>` pattern. All UI is Compose Multiplatform in `commonMain` with platform-specific `expect`/`actual` declarations for 5 screens.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21
**Primary Dependencies**: Compose Multiplatform, Material 3 Adaptive, Koin 4.2+ (K2 Compiler Plugin), Room KMP, DataStore KMP, Wire (protobuf), Turbine (testing), Mokkery (mocking), Kotest (assertions)
**Storage**: DataStore KMP for app preferences (theme, locale, analytics, notifications, mesh log, filter words); Room KMP for mesh logs and node database
**Testing**: KMP `allTests` for `feature:settings` module — 10 test files, ~1,689 lines
**Target Platform**: Android, Desktop (JVM), iOS — all via `commonMain`
**Performance Goals**: 60fps scrolling on all config screens; config response timeout ≤ 30 seconds
**Constraints**: All UI in `commonMain`; no `java.*`/`android.*` in common; CMP float pre-formatting via `NumberFormatter.format()`; Wire protobuf messages use `ConfigState<T>` with `rememberSaveable` for process death survival
**Scale/Scope**: 76 commonMain files, 13 androidMain, 8 jvmMain, 8 iosMain, 1 jvmAndroidMain, 10 commonTest

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All business logic and ViewModels in `commonMain`. Platform code limited to `expect`/`actual` for 5 screens + utilities. |
| II. Zero Lint Tolerance | ✅ PASS | `spotlessApply` + `detekt` pass. `@Suppress` annotations used for justified violations (`LongParameterList`, `CyclomaticComplexMethod`, `MagicNumber`). |
| III. Compose Multiplatform UI | ✅ PASS | All composables use CMP APIs. `NumberFormatter.format()` used for floats. Navigation 3 `settingsGraph` pattern with `entry<>`. |
| IV. Privacy First | ✅ PASS | Analytics respects opt-out toggle. Location sharing is user-initiated with permission checks. Security config export is user-initiated. No PII logging. |
| V. Design Standards Compliance | ✅ PASS | M3 components: `ListItem`, `SwitchListItem`, `SwitchPreference`, `DropDownPreference`, `ExpressiveSection`, `MainAppBar`. Error colors for admin actions. |
| VI. Verify Before Push | ✅ PASS | Full verification: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`. |
| VII. Coroutine Safety | ✅ PASS | `safeCatching {}` used (not `runCatching {}`). `safeLaunch(tag = ...)` for coroutine scope. Project `ioDispatcher` via `CoroutineDispatchers`. |
| VIII. Resource Discipline | ✅ PASS | All strings via `stringResource(Res.string.key)`. Icons via `MeshtasticIcons`. |
| IX. Branch & Scope Hygiene | ✅ PASS | Brownfield migration — feature is stable on main. |

**Gate Result**: ✅ All principles satisfied

## Project Structure

### Documentation (this feature)

```text
specs/008-radio-app-settings/
├── spec.md              # Feature specification (migrated)
├── plan.md              # This file (migrated)
└── tasks.md             # Task breakdown (migrated)
```

### Source Code (repository root)

```text
feature/settings/
├── src/commonMain/kotlin/org/meshtastic/feature/settings/
│   ├── di/
│   │   └── FeatureSettingsModule.kt          ← Koin DI module (@ComponentScan)
│   ├── navigation/
│   │   ├── SettingsNavigation.kt             ← Nav 3 settingsGraph with entry<>
│   │   ├── SettingsNavUtils.kt               ← Shared navigation helpers
│   │   ├── ConfigRoute.kt                    ← 10 device config routes enum
│   │   ├── ModuleRoute.kt                    ← 16 module config routes enum
│   │   └── AboutLibrariesLoader.kt           ← expect/actual for OSS licenses
│   ├── radio/
│   │   ├── RadioConfigViewModel.kt           ← Core config VM (769 lines)
│   │   ├── RadioConfig.kt                    ← Radio config list, AdminRoute enum
│   │   ├── RadioConfigState.kt (inline)      ← Immutable config state data class
│   │   ├── ResponseState.kt                  ← Generic sealed class: Empty/Loading/Success/Error
│   │   ├── CleanNodeDatabaseViewModel.kt     ← Node DB cleanup VM
│   │   ├── CleanNodeDatabaseScreen.kt        ← Node DB cleanup UI
│   │   ├── channel/
│   │   │   ├── ChannelConfigScreen.kt        ← Channel editor screen
│   │   │   ├── ChannelScreen.kt              ← Channel display
│   │   │   ├── ChannelsNavigation.kt         ← Channel sub-navigation
│   │   │   └── component/                    ← ChannelCard, EditChannelDialog, etc.
│   │   └── component/                        ← 28 config screens (one per config type)
│   │       ├── ConfigState.kt                ← Generic config state holder with Saver
│   │       ├── DeviceConfigScreen.kt         ← Device config (expect/actual)
│   │       ├── SecurityConfigScreen.kt       ← Security config (expect/actual)
│   │       ├── PositionConfigScreen.kt       ← Position config (expect/actual)
│   │       ├── ExternalNotificationConfigScreen.kt ← ExtNotif (expect/actual)
│   │       ├── LoRaConfigItemList.kt         ← LoRa settings
│   │       ├── MQTTConfigItemList.kt         ← MQTT settings with probe
│   │       ├── UserConfigItemList.kt         ← User identity settings
│   │       ├── BluetoothConfigItemList.kt    ← Bluetooth settings
│   │       └── ... (15 more module config screens)
│   ├── channel/
│   │   └── ChannelViewModel.kt               ← Channel URL parsing, channel set mgmt
│   ├── debugging/
│   │   ├── Debug.kt                          ← Debug screen composables (460 lines)
│   │   ├── DebugViewModel.kt                 ← Log display/search/filter/export VM
│   │   ├── DebugSearch.kt                    ← Search bar + filter bar composables
│   │   ├── DebugFilters.kt                   ← Filter logic composables
│   │   ├── LogExporter.kt                    ← expect/actual platform log export
│   │   └── LogFormatter.kt                   ← Log message formatting
│   ├── filter/
│   │   ├── FilterSettingsScreen.kt           ← Message word filter UI
│   │   └── FilterSettingsViewModel.kt        ← Filter preferences VM
│   ├── component/
│   │   ├── PrivacySection.kt                 ← Analytics + location toggles
│   │   ├── NotificationSection.kt            ← Notification toggles
│   │   ├── ExpressiveSection.kt              ← Reusable M3 section container
│   │   ├── HomoglyphSetting.kt               ← Homoglyph encoding toggle
│   │   └── ThemePickerDialog.kt              ← Theme selection dialog
│   ├── tak/
│   │   ├── TakPermissionUtil.kt              ← expect/actual TAK permissions
│   │   └── PrefExporter.kt                   ← expect/actual XML pref export
│   ├── util/
│   │   ├── SettingsIntervals.kt              ← Shared interval constants
│   │   ├── FixedUpdateIntervals.kt           ← Fixed telemetry intervals
│   │   └── Formatting.kt                     ← Value formatting helpers
│   ├── SettingsViewModel.kt                  ← App preferences VM (195 lines)
│   ├── DeviceConfigurationScreen.kt          ← Device config list screen
│   ├── ModuleConfigurationScreen.kt          ← Module config list screen
│   ├── AdministrationScreen.kt               ← Admin actions screen
│   └── AboutScreen.kt                        ← OSS acknowledgements screen
│
├── src/androidMain/                          ← 13 platform-specific files
├── src/jvmMain/                              ← 8 platform-specific files
├── src/iosMain/                              ← 8 platform-specific files
├── src/jvmAndroidMain/                       ← 1 shared JVM/Android file
└── src/commonTest/                           ← 10 test files (~1,689 lines)
    ├── radio/RadioConfigViewModelTest.kt     ← 535 lines, 13 tests
    ├── radio/component/EditDeviceProfileDialogTest.kt
    ├── radio/component/MapReportingPreferenceTest.kt
    ├── radio/CleanNodeDatabaseViewModelTest.kt
    ├── SettingsViewModelTest.kt              ← 264 lines, 13 tests
    ├── debugging/DebugViewModelTest.kt       ← 189 lines, 6 tests
    ├── debugging/DebugSearchTest.kt          ← 188 lines, 5 compose UI tests
    ├── debugging/LogFormatterTest.kt
    ├── channel/CommonChannelViewModelTest.kt ← 103 lines, 4 tests
    └── filter/FilterSettingsViewModelTest.kt ← 72 lines, 3 tests
```

**Structure Decision**: The feature is organized by functional area (radio, debugging, filter, channel, component) within a single `feature/settings` module. This is appropriate given all areas share the `RadioConfigViewModel` and navigation graph. Platform-specific code is isolated to `expect`/`actual` declarations.

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/settings` | Existing | 76 commonMain + 30 platform | Low (stable) |
| `core/domain` | Dependency | ~15 use cases | Low (consumed only) |
| `core/repository` | Dependency | ~12 repositories/prefs | Low (consumed only) |
| `core/navigation` | Dependency | `SettingsRoute` enum | Low (route definitions) |
| `core/ui` | Dependency | `ListItem`, `SwitchListItem`, `MainAppBar`, `MeshtasticIcons` | Low (shared components) |
| `core/resources` | Dependency | strings.xml, drawables | Low (resource references) |

## Integration Points

- **Navigation**: `SettingsRoute` sealed class in `core/navigation` defines all route keys. `settingsGraph()` registers `entry<>` providers for each route.
- **DI**: `FeatureSettingsModule` uses `@ComponentScan` for automatic registration of `@KoinViewModel` classes.
- **DataStore**: App preferences flow through repository interfaces (`UiPrefs`, `MeshLogPrefs`, `NotificationPrefs`, `FilterPrefs`, `MapConsentPrefs`, `AnalyticsPrefs`, `HomoglyphPrefs`).
- **RadioController**: Admin messages and config writes go through `RadioConfigUseCase` → `RadioController`.
- **ServiceRepository**: `meshPacketFlow` provides real-time response packets for `processRadioResponseUseCase`.
- **MqttManager**: MQTT probe connects directly to broker for reachability/credential testing.

## Design Constraints

- All UI lives in `commonMain` — not platform-specific
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- Error handling uses `safeCatching {}` not `runCatching {}`
- Dispatchers via `org.meshtastic.core.common.util.ioDispatcher`
- Float values must be pre-formatted with `NumberFormatter.format()` (CMP constraint)
- Wire protobuf messages wrapped in `ConfigState<T>` with `rememberSaveable` Saver for process death
- Config request timeout is 30 seconds, enforced by `registerRequestId` coroutine

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Config response race condition (multiple rapid navigations) | Low | Medium | `clearPacketResponse()` on screen exit; `requestIds` set tracking |
| Remote admin timeout on slow mesh | Medium | Low | 30-second timeout with user-visible error and retry |
| Platform `expect`/`actual` drift across Android/Desktop/iOS | Low | Medium | Shared `commonMain` logic; platform code is thin wrappers |
| Module route bitfield changes in firmware | Low | Low | `Capabilities` version-gating; `isSupported` lambda per route |

## Phase Alignment with Tasks

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Foundation | DI, navigation, state models | SET-T001–SET-T005 | None |
| 2. Radio Config | User, LoRa, Channels, Security config | SET-T006–SET-T015 | Phase 1 |
| 3. Device Config | Device, Position, Power, Network, Display, Bluetooth | SET-T016–SET-T022 | Phase 1 |
| 4. Module Config | 16 module config screens | SET-T023–SET-T030 | Phase 1 |
| 5. App Preferences | Theme, locale, analytics, notifications, persistence | SET-T031–SET-T038 | Phase 1 |
| 6. Administration & Advanced | Admin actions, profile backup, node DB cleanup | SET-T039–SET-T045 | Phase 2 |
| 7. Debug Panel | Log display, search, filter, export | SET-T046–SET-T051 | Phase 1 |
| 8. Testing | ViewModel tests, compose UI tests | SET-T052–SET-T061 | All prior |

### Critical Path

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7 → Phase 8
                                                                          ↑
                                                          (all phases feed testing)
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |

