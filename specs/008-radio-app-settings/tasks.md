# Tasks: Radio & App Settings

**Branch**: `008-radio-app-settings` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Status**: Migrated — all implemented tasks marked `[x]`. Gap tasks marked `[ ]`.

---

## Phase 1 — Foundation (DI, Navigation, State Models)

- [x] **SET-T001**: Create `FeatureSettingsModule` with `@ComponentScan` for Koin DI auto-registration
  - File: `di/FeatureSettingsModule.kt`
  - Validates: FR-001

- [x] **SET-T002**: Define `ConfigRoute` enum with 10 device config routes (User, Channels, Device, Position, Power, Network, Display, LoRa, Bluetooth, Security) with icons, titles, and admin message type mappings
  - File: `navigation/ConfigRoute.kt`
  - Validates: FR-006

- [x] **SET-T003**: Define `ModuleRoute` enum with 16 module routes including `excluded_modules` bitfield filtering, `isSupported` capability checks, and `isApplicable` role filtering
  - File: `navigation/ModuleRoute.kt`
  - Validates: FR-007

- [x] **SET-T004**: Implement `settingsGraph()` Navigation 3 entry provider registering all settings routes with `entry<>` pattern and shared `RadioConfigViewModel` scoping via `getRadioConfigViewModel()`
  - File: `navigation/SettingsNavigation.kt`
  - Validates: FR-001

- [x] **SET-T005**: Implement `ResponseState<T>` sealed class (Empty, Loading with progress tracking, Success, Error with UiText) and `RadioConfigState` data class aggregating all config, metadata, channels, and response state
  - Files: `radio/ResponseState.kt`, `radio/RadioConfigViewModel.kt` (data class)
  - Validates: FR-002, FR-003

---

## Phase 2 — Radio Configuration

- [x] **SET-T006**: Implement `RadioConfigViewModel` core init: `destNumFlow` + `nodeDBbyNum` combine for node resolution, flow collectors for `localConfigFlow`, `channelSetFlow`, `moduleConfigFlow`, `deviceUIConfig`, `fileManifest`, connection state, and `deviceProfileFlow`
  - File: `radio/RadioConfigViewModel.kt`
  - Validates: FR-001

- [x] **SET-T007**: Implement `setResponseStateLoading()` dispatch: route-specific admin message requests for all `ConfigRoute`, `ModuleRoute`, and `AdminRoute` entries with request ID registration and timeout handling
  - File: `radio/RadioConfigViewModel.kt`
  - Validates: FR-001, FR-002, FR-003

- [x] **SET-T008**: Implement `processPacketResponse()` handler: dispatch `RadioResponseResult` variants (Metadata, ChannelResponse, Owner, ConfigResponse, ModuleConfigResponse, CannedMessages, Ringtone, ConnectionStatus, Success, Error) and sequential channel fetching
  - File: `radio/RadioConfigViewModel.kt`
  - Validates: FR-001, FR-002

- [x] **SET-T009**: Implement `setOwner()`, `setConfig()`, `setModuleConfig()` write operations with optimistic state update and request-response tracking
  - File: `radio/RadioConfigViewModel.kt`
  - Validates: FR-001

- [x] **SET-T010**: Implement `UserConfigItemList` composable for user identity editing (long name, short name, licensed operator)
  - File: `radio/component/UserConfigItemList.kt`
  - Validates: FR-001, US-1

- [x] **SET-T011**: Implement `LoRaConfigItemList` composable for LoRa region, modem preset, bandwidth, hop limit, tx power, PA fan control
  - File: `radio/component/LoRaConfigItemList.kt`
  - Validates: FR-001, US-1

- [x] **SET-T012**: Implement `SecurityConfigScreen` (expect/actual) for admin key, encryption keys, managed device flag
  - File: `radio/component/SecurityConfigScreen.kt`
  - Validates: FR-001, FR-009, FR-019

- [x] **SET-T013**: Implement channel configuration system: `ChannelConfigScreen`, `ChannelScreen`, `ChannelCard`, `EditChannelDialog`, `ChannelConfigHeader`, `ChannelLegend`, sequential channel fetch and `updateChannels()` with PSK migration
  - Files: `radio/channel/*.kt`, `radio/channel/component/*.kt`
  - Validates: FR-001, FR-017, US-1

- [x] **SET-T014**: Implement `ChannelViewModel` for channel URL parsing, channel set management, LoRa region/TX config, share tracking
  - File: `channel/ChannelViewModel.kt`
  - Validates: FR-001, US-1

- [x] **SET-T015**: Implement `ConfigState<T>` generic state holder with Wire `Message.Adapter` Saver for `rememberSaveable` process death survival, and `rememberConfigState()` composable
  - File: `radio/component/ConfigState.kt`
  - Validates: NFR-001

---

## Phase 3 — Device Configuration

- [x] **SET-T016**: Implement `DeviceConfigurationScreen` with hardware-filtered config route list (`filterExcludedFrom` for Bluetooth, WiFi, Ethernet metadata)
  - File: `DeviceConfigurationScreen.kt`
  - Validates: FR-006, US-2

- [x] **SET-T017**: Implement `DeviceConfigScreen` (expect/actual) for device role, rebroadcast mode, serial debug, node info broadcast interval
  - File: `radio/component/DeviceConfigScreen.kt`
  - Validates: FR-001, US-2

- [x] **SET-T018**: Implement `PositionConfigScreen` (expect/actual) for GPS, fixed position set/remove, position broadcast, smart position
  - File: `radio/component/PositionConfigScreen.kt`
  - Validates: FR-001, US-2

- [x] **SET-T019**: Implement `PowerConfigScreen` for power management settings
  - File: `radio/component/PowerConfigItemList.kt`
  - Validates: FR-001, US-2

- [x] **SET-T020**: Implement `NetworkConfigScreen` with `DeviceConnectionStatus` fetch for WiFi/Ethernet state display
  - File: `radio/component/NetworkConfigItemList.kt`
  - Validates: FR-001, US-2

- [x] **SET-T021**: Implement `DisplayConfigScreen` for OLED/E-Ink display settings
  - File: `radio/component/DisplayConfigItemList.kt`
  - Validates: FR-001, US-2

- [x] **SET-T022**: Implement `BluetoothConfigScreen` for Bluetooth settings (filtered by `hasBluetooth` metadata)
  - File: `radio/component/BluetoothConfigItemList.kt`
  - Validates: FR-001, FR-006, US-2

---

## Phase 4 — Module Configuration

- [x] **SET-T023**: Implement `ModuleConfigurationScreen` with role-filtered, capability-filtered, excludability-filtered module route list and `unlockExcludedModules` support
  - File: `ModuleConfigurationScreen.kt`
  - Validates: FR-007, US-3

- [x] **SET-T024**: Implement MQTT config screen with `MQTTConfigItemList` and broker connection probe (`probeMqttConnection`, `MqttProbeStatus` display)
  - File: `radio/component/MQTTConfigItemList.kt`
  - Validates: FR-018, US-3

- [x] **SET-T025**: Implement Canned Message config screen with `getCannedMessages()` fetch and `setCannedMessages()` save
  - File: `radio/component/CannedMessageConfigItemList.kt`
  - Validates: FR-001, US-3

- [x] **SET-T026**: Implement External Notification config screen (expect/actual) with `getRingtone()` fetch and `setRingtone()` save
  - File: `radio/component/ExternalNotificationConfigScreen.kt`
  - Validates: FR-001, US-3

- [x] **SET-T027**: Implement remaining module config screens: Serial, Store & Forward, Range Test, Telemetry, Audio, Remote Hardware, Neighbor Info, Ambient Lighting, Detection Sensor, Paxcounter
  - Files: `radio/component/{Serial,StoreForward,RangeTest,Telemetry,Audio,RemoteHardware,NeighborInfo,AmbientLighting,DetectionSensor,Paxcounter}ConfigItemList.kt`
  - Validates: FR-001, US-3

- [x] **SET-T028**: Implement Status Message config screen (firmware capability gated via `supportsStatusMessage`)
  - File: `radio/component/StatusMessageConfigItemList.kt`
  - Validates: FR-007, US-3

- [x] **SET-T029**: Implement Traffic Management config screen (firmware capability gated via `supportsTrafficManagementConfig`)
  - File: `radio/component/TrafficManagementConfigItemList.kt`
  - Validates: FR-007, US-3

- [x] **SET-T030**: Implement TAK config screen (role-gated: `TAK` or `TAK_TRACKER` only) with `TakPermissionUtil` expect/actual
  - Files: `radio/component/TAKConfigItemList.kt`, `tak/TakPermissionUtil.kt`
  - Validates: FR-007, US-3

---

## Phase 5 — App Preferences

- [x] **SET-T031**: Implement `SettingsViewModel` with theme, locale, app intro, provide-location, DB cache limit, mesh log retention, and notification preference management via use cases
  - File: `SettingsViewModel.kt`
  - Validates: FR-010, FR-011, FR-012, US-4

- [x] **SET-T032**: Implement `PrivacySection` composable: analytics opt-in/out toggle, provide-location-to-mesh with GPS/permission checks, homoglyph encoding toggle
  - File: `component/PrivacySection.kt`
  - Validates: FR-010, US-4

- [x] **SET-T033**: Implement `NotificationSection` composable: messages, node events, low battery notification toggles
  - File: `component/NotificationSection.kt`
  - Validates: FR-011, US-4

- [x] **SET-T034**: Implement `ThemePickerDialog` for theme selection (light/dark/system)
  - File: `component/ThemePickerDialog.kt`
  - Validates: FR-010, US-4

- [x] **SET-T035**: Implement `HomoglyphSetting` composable for homoglyph character encoding toggle
  - File: `component/HomoglyphSetting.kt`
  - Validates: FR-010

- [x] **SET-T036**: Implement `MapReportingPreference` composable for position map reporting consent toggle
  - File: `radio/component/MapReportingPreference.kt`
  - Validates: FR-010

- [x] **SET-T037**: Implement MQTT proxy connection state display (`mqttConnectionState` flow) in settings UI
  - File: `radio/RadioConfigViewModel.kt` (mqttConnectionState)
  - Validates: FR-018

- [x] **SET-T038**: Implement `ExpressiveSection` reusable M3 section container with title styling
  - File: `component/ExpressiveSection.kt`
  - Validates: NFR-001, Design Standards

---

## Phase 6 — Administration & Advanced

- [x] **SET-T039**: Implement `AdministrationScreen` with `AdminRoute` enum (Reboot, Shutdown, Factory Reset, Node DB Reset), confirmation dialogs (`ShutdownConfirmationDialog`, `WarningDialog`), and metadata-aware shutdown guard
  - Files: `AdministrationScreen.kt`, `radio/RadioConfig.kt` (AdminRoute), `radio/component/ShutdownConfirmationDialog.kt`, `radio/component/WarningDialog.kt`
  - Validates: FR-004, FR-005, US-5

- [x] **SET-T040**: Implement admin action dispatch in `RadioConfigViewModel.sendAdminRequest()`: reboot, shutdown (with `canShutdown` guard), factory reset (with `isLocal` flag), node DB reset (with `preserveFavorites`)
  - File: `radio/RadioConfigViewModel.kt`
  - Validates: FR-004, FR-005, US-5

- [x] **SET-T041**: Implement profile import/export: `importProfile()` with file read and `DeviceProfile` parse, `exportProfile()` with file write, `installProfile()` with device application
  - File: `radio/RadioConfigViewModel.kt`
  - Validates: FR-008, US-6

- [x] **SET-T042**: Implement security config export: `exportSecurityConfig()` writing `SecurityConfig` protobuf to file
  - File: `radio/RadioConfigViewModel.kt`
  - Validates: FR-009

- [x] **SET-T043**: Implement `EditDeviceProfileDialog` for previewing and confirming imported profiles before installation
  - File: `radio/component/EditDeviceProfileDialog.kt`
  - Validates: FR-008, US-6

- [x] **SET-T044**: Implement `CleanNodeDatabaseViewModel` and `CleanNodeDatabaseScreen` for node database cleanup with age threshold and unknown-node filter
  - Files: `radio/CleanNodeDatabaseViewModel.kt`, `radio/CleanNodeDatabaseScreen.kt`
  - Validates: FR-016, US-9

- [x] **SET-T045**: Implement `RadioConfigItemList` composable organizing all settings sections (Radio Config, Device Config, Module Settings, Backup/Restore, Administration, Advanced) with managed device warnings and `LoadingOverlay` + `PacketResponseStateDialog`
  - Files: `radio/RadioConfig.kt`, `radio/component/LoadingOverlay.kt`, `radio/component/PacketResponseStateDialog.kt`, `radio/component/RadioConfigScreenList.kt`
  - Validates: FR-019, US-1–5

---

## Phase 7 — Debug Panel

- [x] **SET-T046**: Implement `DebugViewModel` with `UiMeshLog` model, mesh log observation, protobuf payload decoding (Position, Telemetry, Routing, AdminMessage, etc.), `LogSearchManager`, and `LogFilterManager`
  - File: `debugging/DebugViewModel.kt`
  - Validates: FR-013, FR-014, US-7

- [x] **SET-T047**: Implement `DebugScreen` composable with `LazyColumn`, sticky search/filter header, auto-scroll, selectable log items with decoded payloads, copy-to-clipboard, and annotated node IDs
  - File: `debugging/Debug.kt`
  - Validates: FR-013, FR-014, US-7

- [x] **SET-T048**: Implement `DebugSearchBar` and search state composables with multi-term regex search, match navigation (next/previous), match count display, and search highlighting
  - File: `debugging/DebugSearch.kt`
  - Validates: FR-013, US-7

- [x] **SET-T049**: Implement `DebugFilterBar` and filter composables with preset filters (node ID, broadcast, portnums, date), custom filter input, AND/OR mode toggle, and active filter chip display
  - File: `debugging/DebugFilters.kt`
  - Validates: FR-013, US-7

- [x] **SET-T050**: Implement `LogExporter` (expect/actual) for platform-specific log export to file with timestamped filename
  - File: `debugging/LogExporter.kt`
  - Validates: FR-013, US-7

- [x] **SET-T051**: Implement `LogFormatter` for mesh log message formatting
  - File: `debugging/LogFormatter.kt`
  - Validates: FR-014

---

## Phase 8 — Message Filtering & About

- [x] **SET-T052**: Implement `FilterSettingsScreen` with filter enable toggle, word add/remove, regex pattern support, and pattern rebuild
  - File: `filter/FilterSettingsScreen.kt`
  - Validates: FR-015, US-8

- [x] **SET-T053**: Implement `FilterSettingsViewModel` with `FilterPrefs` and `MessageFilter` integration for add/remove/toggle/rebuild
  - File: `filter/FilterSettingsViewModel.kt`
  - Validates: FR-015, US-8

- [x] **SET-T054**: Implement `AboutScreen` with open-source library acknowledgements via `AboutLibrariesLoader` (expect/actual)
  - Files: `AboutScreen.kt`, `navigation/AboutLibrariesLoader.kt`
  - Validates: Design Standards

- [x] **SET-T055**: Implement `PrefExporter` (expect/actual) for XML preference export for TAK integration
  - File: `tak/PrefExporter.kt`

- [x] **SET-T056**: Implement shared utility files: `SettingsIntervals`, `FixedUpdateIntervals`, `Formatting`
  - Files: `util/SettingsIntervals.kt`, `util/FixedUpdateIntervals.kt`, `util/Formatting.kt`

- [x] **SET-T057**: Implement CSV data export (`saveDataCsv`) with optional portnum filtering via `ExportDataUseCase`
  - File: `SettingsViewModel.kt`
  - Validates: FR-020

- [x] **SET-T058**: Implement platform-specific `SettingsMainScreen` (expect/actual) with Android `OutlinedCard` layout and Desktop layout
  - Files: `navigation/SettingsNavigation.kt` (expect), `androidMain/`, `jvmMain/`, `iosMain/`
  - Validates: NFR-003

---

## Phase 9 — Testing (Implemented)

- [x] **SET-T059**: Implement `RadioConfigViewModelTest` — 13 tests covering `setConfig`, `setOwner`, `setModuleConfig`, `updateChannels`, `setRingtone`, `setCannedMessages`, `setFixedPosition`, `removeFixedPosition`, `installProfile`, admin actions (reboot, shutdown, factory reset, node DB reset), packet response processing, request timeout, `initDestNum`, `setPreserveFavorites`, analytics toggle, homoglyph toggle
  - File: `commonTest/radio/RadioConfigViewModelTest.kt` (535 lines)
  - Validates: SC-002, SC-005, SC-007

- [x] **SET-T060**: Implement `SettingsViewModelTest` — 13 tests covering initialization, `isConnected` flow, `isOtaCapable`, notification settings, mesh log logging, `unlockExcludedModules`, `provideLocation` flow, mesh location use case calls, property-based bounds testing for retention days, theme/locale/app-intro prefs, `setDbCacheLimit` clamping
  - File: `commonTest/SettingsViewModelTest.kt` (264 lines)
  - Validates: SC-003, SC-007

- [x] **SET-T061**: Implement `DebugViewModelTest` — 6 tests covering retention days update, logging disable + log deletion, search filtering, AND/OR filter modes, preset filters, delete confirmation alert
  - File: `commonTest/debugging/DebugViewModelTest.kt` (189 lines)
  - Validates: SC-004, SC-007

- [x] **SET-T062**: Implement `DebugSearchTest` — 5 Compose UI tests covering search bar placeholder, clear button, match navigation arrows, filter bar display, custom filter add/display, clear-all filters
  - File: `commonTest/debugging/DebugSearchTest.kt` (188 lines)
  - Validates: SC-004, SC-007

- [x] **SET-T063**: Implement `CommonChannelViewModelTest` — 4 tests covering `isManaged` security config, `txEnabled`, share tracking, channel URL request parsing
  - File: `commonTest/channel/CommonChannelViewModelTest.kt` (103 lines)
  - Validates: SC-001, SC-007

- [x] **SET-T064**: Implement `FilterSettingsViewModelTest` — 3 tests covering `setFilterEnabled`, `addFilterWord` with pattern rebuild, `removeFilterWord` with pattern rebuild
  - File: `commonTest/filter/FilterSettingsViewModelTest.kt` (72 lines)
  - Validates: SC-007

- [x] **SET-T065**: Implement `EditDeviceProfileDialogTest` — profile dialog compose tests
  - File: `commonTest/radio/component/EditDeviceProfileDialogTest.kt`
  - Validates: SC-006

- [x] **SET-T066**: Implement `MapReportingPreferenceTest` — map consent preference tests
  - File: `commonTest/radio/component/MapReportingPreferenceTest.kt`

- [x] **SET-T067**: Implement `CleanNodeDatabaseViewModelTest` — node database cleanup tests
  - File: `commonTest/radio/CleanNodeDatabaseViewModelTest.kt`
  - Validates: SC-007

- [x] **SET-T068**: Implement `LogFormatterTest` — log message formatting tests
  - File: `commonTest/debugging/LogFormatterTest.kt`
  - Validates: SC-004

---

## Phase 10 — Gap Tasks (Not Yet Implemented)

- [ ] **[DEFERRED]** **SET-T069**: Add Compose UI tests for `RadioConfigItemList` composable — verify section rendering, managed device message display, enabled/disabled state based on connection — *Deferred: requires Compose UI test infrastructure.*
  - Target: `commonTest/radio/RadioConfigItemListTest.kt`
  - Gap: No UI test coverage for the main radio config list

- [ ] **[DEFERRED]** **SET-T070**: Add Compose UI tests for `AdministrationScreen` — verify all admin route items render, confirmation dialogs appear on click, metadata-aware shutdown guard UX — *Deferred: requires Compose UI test infrastructure.*
  - Target: `commonTest/AdministrationScreenTest.kt`
  - Gap: No UI test for admin screen composable

- [ ] **[DEFERRED]** **SET-T071**: Add Compose UI tests for `FilterSettingsScreen` — verify filter enable toggle, word add/remove flow, regex indicator display — *Deferred: requires Compose UI test infrastructure.*
  - Target: `commonTest/filter/FilterSettingsScreenTest.kt`
  - Gap: Only ViewModel is tested, not the composable

- [ ] **[DEFERRED]** **SET-T072**: Add Compose UI tests for `CleanNodeDatabaseScreen` — verify slider interaction, preview list, confirm deletion flow — *Deferred: requires Compose UI test infrastructure.*
  - Target: `commonTest/radio/CleanNodeDatabaseScreenTest.kt`
  - Gap: Only ViewModel is tested, not the composable

- [x] **SET-T073**: Add integration test for profile import → export round-trip verifying `DeviceProfile` protobuf fidelity
  - Target: `commonTest/radio/ProfileRoundTripTest.kt`
  - Gap: Import and export are tested individually but not end-to-end

- [x] **SET-T074**: Add test for MQTT probe timeout and error path (`probeMqttConnection` exception handling, `clearMqttProbeStatus`)
  - Target: `commonTest/radio/RadioConfigViewModelTest.kt` (extend)
  - Gap: MQTT probe not tested

- [ ] **[DEFERRED]** **SET-T075**: Add accessibility tests — verify TalkBack semantics, touch target sizes, and color-independent information for admin action error colors — *Deferred: requires accessibility testing infrastructure (TalkBack, touch target verification).*
  - Target: `commonTest/AdministrationAccessibilityTest.kt`
  - Gap: No accessibility testing exists

- [x] **SET-T076**: Add test for `SettingsViewModel.saveDataCsv()` verifying CSV export through `FileService` and `ExportDataUseCase`
  - Target: `commonTest/SettingsViewModelTest.kt` (extend)
  - Gap: CSV export function exists but is not tested

---

## Summary

| Phase | Tasks | Completed | Gaps |
|-------|-------|-----------|------|
| 1. Foundation | 5 | 5 | 0 |
| 2. Radio Config | 10 | 10 | 0 |
| 3. Device Config | 7 | 7 | 0 |
| 4. Module Config | 8 | 8 | 0 |
| 5. App Preferences | 8 | 8 | 0 |
| 6. Administration & Advanced | 7 | 7 | 0 |
| 7. Debug Panel | 6 | 6 | 0 |
| 8. Filtering & About | 9 | 9 | 0 |
| 9. Testing (Implemented) | 10 | 10 | 0 |
| 10. Gap Tasks | 8 | 0 | 8 |
| **Total** | **78** | **70** | **8** |

