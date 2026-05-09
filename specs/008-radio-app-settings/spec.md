# Feature Specification: Radio & App Settings

**Feature Branch**: `008-radio-app-settings`  
**Created**: 2025-07-17  
**Status**: Migrated  
**Input**: Brownfield migration of existing `feature/settings` module — radio config, device config, module config, channel config, app preferences, notification settings, debug panel, administration, filter settings, and About screen.

## Summary

The Radio & App Settings feature is the largest module in the Meshtastic app (~76 common source files, ~12,100 lines), providing the complete device and application configuration experience. It encompasses radio configuration (User, LoRa, Channels, Security), device configuration (Device, Position, Power, Network, Display, Bluetooth), module configuration (16 modules from MQTT to TAK), app preferences (theme, locale, analytics, location sharing, notifications, message filtering, homoglyph encoding), device administration (reboot, shutdown, factory reset, node DB reset), profile backup/restore (import/export DeviceProfile protobuf), a debug panel with log inspection/search/filter/export, node database cleanup, and an open-source acknowledgements screen.

## Goals

1. **G-001**: Provide a unified settings hub for all radio, device, module, and app configuration — accessible both locally and via remote administration.
2. **G-002**: Support reading and writing all protobuf-defined radio/module configurations through an async request–response pattern with progress tracking and timeout handling.
3. **G-003**: Deliver comprehensive app preference management (theme, locale, analytics, location, notifications, message filtering, database cache, mesh log retention).
4. **G-004**: Enable device administration actions (reboot, shutdown, factory reset, node DB reset) with confirmation dialogs and metadata-aware guards.
5. **G-005**: Provide a full-featured debug panel with log search, multi-filter (AND/OR), decoded protobuf payload inspection, log export, and retention management.

## Non-Goals

- **NG-001**: Node list layout, sorting, and density settings (covered by spec 002).
- **NG-002**: Node detail screens, metrics charts, and compass (covered by spec 007).
- **NG-003**: Firmware OTA update flow (covered by spec 006).
- **NG-004**: Messaging UI and direct message routing (covered by spec 004).
- **NG-005**: Platform-specific system notification channel management (handled by Android OS settings).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Configure Radio Settings (Priority: P1)

A user navigates to the settings screen to configure core radio parameters: user identity (long name, short name), LoRa region and modem preset, channel configuration with PSK, and security keys — either locally or on a remote node.

**Why this priority**: Radio configuration is the most critical settings function; a device cannot join a mesh without correct LoRa region, channel, and user identity.

**Independent Test**: Connect to a device, navigate to Radio Configuration, edit User config, save, and verify the device receives the admin message.

**Acceptance Scenarios**:

1. **Given** a connected device, **When** the user opens User config, **Then** the current owner info (long name, short name, hardware model) is fetched from the device and displayed in editable fields.
2. **Given** a connected device, **When** the user modifies LoRa config (region, modem preset, tx power) and saves, **Then** a `Config` protobuf is sent via `radioConfigUseCase.setConfig()` and the response state transitions Loading → Success.
3. **Given** a connected device, **When** the user opens Channel Config, **Then** all channels (up to `maxChannels`) are fetched sequentially and displayed with name, PSK, and role.
4. **Given** a remote node with `destNum ≠ myNodeNum`, **When** the user opens any config screen, **Then** the subtitle shows "Remotely administrating {node name}" and all read/write operations target the remote node.
5. **Given** a managed device (`is_managed == true`), **When** the user opens radio config, **Then** a "Device is managed" warning is displayed and config controls are disabled.

---

### User Story 2 — Configure Device Settings (Priority: P1)

A user configures device-level settings: device role, position (including fixed position), power management, network (WiFi/Ethernet), display, and Bluetooth — filtered by device hardware capabilities.

**Why this priority**: Device settings control physical behavior (power mode, display config) that directly affect battery life and usability.

**Independent Test**: Navigate to Device Configuration, verify that only applicable config routes are shown (e.g., Bluetooth hidden for devices without it), edit a config, and confirm the change is sent.

**Acceptance Scenarios**:

1. **Given** a device without Bluetooth metadata (`hasBluetooth == false`), **When** Device Configuration screen renders, **Then** the Bluetooth config route is excluded from the list.
2. **Given** a device without WiFi or Ethernet, **When** Device Configuration screen renders, **Then** the Network config route is excluded.
3. **Given** an open Position config, **When** the user sets a fixed position with coordinates, **Then** `radioConfigUseCase.setFixedPosition()` is called with the position.
4. **Given** an open Network config, **When** the screen loads, **Then** it additionally fetches `DeviceConnectionStatus` to display WiFi/Ethernet connection state.

---

### User Story 3 — Configure Module Settings (Priority: P2)

A user enables and configures one or more of the 16 supported modules (MQTT, Serial, External Notification, Store & Forward, Range Test, Telemetry, Canned Message, Audio, Remote Hardware, Neighbor Info, Ambient Lighting, Detection Sensor, Paxcounter, Status Message, Traffic Management, TAK).

**Why this priority**: Module configuration extends device functionality but is not required for basic mesh operation.

**Independent Test**: Navigate to Module Configuration, open MQTT config, toggle `enabled`, save, and verify the `ModuleConfig` protobuf is sent.

**Acceptance Scenarios**:

1. **Given** a device with `excluded_modules` bitfield set, **When** Module Configuration renders, **Then** excluded modules are hidden from the list.
2. **Given** the user has unlocked excluded modules, **When** Module Configuration renders, **Then** all 16 module routes are shown regardless of bitfield.
3. **Given** a device with role `TAK`, **When** filtering modules, **Then** the TAK module route is visible; for other roles, it is hidden.
4. **Given** the Canned Message module screen, **When** it loads, **Then** `getCannedMessages()` is called and the current messages are displayed for editing.
5. **Given** the External Notification module screen, **When** it loads, **Then** `getRingtone()` is called and the current ringtone is displayed for editing.

---

### User Story 4 — Manage App Preferences (Priority: P2)

A user customizes app-level preferences: theme (light/dark/system), locale, analytics opt-in/out, provide-location-to-mesh toggle, homoglyph encoding, notification settings (messages, node events, low battery), database cache limit, and mesh log retention.

**Why this priority**: App preferences personalize the experience but do not affect mesh operation.

**Independent Test**: Toggle each preference switch and verify the underlying `DataStore` preference is updated via the corresponding use case.

**Acceptance Scenarios**:

1. **Given** the Privacy section, **When** the user toggles "Provide location to mesh", **Then** if granted, `meshLocationUseCase.startProvidingLocation()` is called; if GPS is disabled, a toast is shown.
2. **Given** the Notification section, **When** the user toggles "Messages notifications" off, **Then** `notificationPrefs.messagesEnabled` becomes `false`.
3. **Given** the Persistence section, **When** the user adjusts the database cache slider, **Then** `setDatabaseCacheLimitUseCase` is called with the value clamped to `DatabaseConstants` bounds.
4. **Given** the Persistence section, **When** the user adjusts mesh log retention days, **Then** the value is clamped to `[MIN_RETENTION_DAYS, MAX_RETENTION_DAYS]` and logs older than the threshold are deleted.

---

### User Story 5 — Device Administration (Priority: P2)

A user performs administrative actions on a device: reboot, shutdown, factory reset, or node DB reset, with appropriate confirmation dialogs and metadata-aware guards.

**Why this priority**: Admin actions are destructive/disruptive and need safety guards, but they are essential for device management.

**Independent Test**: Navigate to Administration, trigger each action, verify the confirmation dialog appears, confirm, and verify the admin message is sent.

**Acceptance Scenarios**:

1. **Given** a connected device, **When** the user selects "Reboot" and confirms, **Then** `adminActionsUseCase.reboot(destNum)` is called.
2. **Given** a device where `metadata.canShutdown == false`, **When** the user selects "Shutdown", **Then** an error "Can't shutdown" is displayed instead of sending the command.
3. **Given** a "Node DB Reset" dialog, **When** the user toggles "Preserve favorites" and confirms, **Then** `adminActionsUseCase.nodedbReset()` is called with `preserveFavorites = true`.
4. **Given** a factory reset on the local device (`destNum == myNodeNum`), **When** confirmed, **Then** `factoryReset` is called with `isLocal = true` to additionally clear local state.

---

### User Story 6 — Profile Backup & Restore (Priority: P3)

A user exports a device profile to a file or imports a previously saved profile, applying it to the connected device.

**Why this priority**: Profile management is a power-user feature for device fleet management.

**Independent Test**: Export a profile, verify the file is written; import it back, verify the `DeviceProfile` protobuf is parsed and `installProfileUseCase` is called.

**Acceptance Scenarios**:

1. **Given** a connected local device, **When** the user taps Export, **Then** `exportProfileUseCase` writes a `DeviceProfile` protobuf to the selected URI.
2. **Given** a valid profile file, **When** the user taps Import and selects the file, **Then** `importProfileUseCase` parses it and presents the profile for confirmation before installing.
3. **Given** an invalid profile file, **When** import fails, **Then** the error is propagated and no profile is installed.

---

### User Story 7 — Debug Panel (Priority: P3)

A user inspects mesh packet logs with decoded protobuf payloads, multi-term search with match navigation, multi-tag filtering (AND/OR mode), log retention management, and CSV log export.

**Why this priority**: Debugging is essential for developers and power users but not required for normal app operation.

**Independent Test**: Navigate to Debug Panel, verify logs are displayed with decoded payloads, enter a search term, verify matches are highlighted and navigable.

**Acceptance Scenarios**:

1. **Given** mesh logs exist, **When** the Debug Panel opens, **Then** all logs are displayed as `UiMeshLog` entries with formatted dates, annotated node IDs (hex), and decoded protobuf payloads.
2. **Given** a search term is entered, **When** matches are found, **Then** the user can navigate forward/backward through matches across message, type, date, and payload fields.
3. **Given** multiple filter tags are active in AND mode, **When** filtering, **Then** only logs matching ALL tags are displayed.
4. **Given** the user taps "Clear logs" and confirms, **Then** `meshLogRepository.deleteAll()` is called.
5. **Given** log retention is set to 7 days, **When** saved, **Then** logs older than 7 days are deleted and the preference is persisted.

---

### User Story 8 — Message Filter Settings (Priority: P3)

A user manages a word filter that hides messages containing specific terms.

**Why this priority**: Content filtering is a niche feature for community operators.

**Independent Test**: Add a filter word, verify it appears in the list and the filter pattern is rebuilt.

**Acceptance Scenarios**:

1. **Given** the filter settings screen, **When** the user adds "spam", **Then** `filterPrefs.setFilterWords()` is called with the new set and `messageFilter.rebuildPatterns()` is triggered.
2. **Given** existing filter words, **When** the user removes one, **Then** the word is removed from prefs and patterns are rebuilt.
3. **Given** the filter toggle is off, **When** the user disables filtering, **Then** `filterPrefs.setFilterEnabled(false)` is called.

---

### User Story 9 — Clean Node Database (Priority: P3)

A user removes stale nodes from the local database based on configurable criteria (age threshold, unknown-only filter).

**Why this priority**: Database hygiene is a maintenance feature for long-running nodes.

**Independent Test**: Set "older than 30 days" and "unknown nodes only", preview the node list, confirm deletion.

**Acceptance Scenarios**:

1. **Given** the clean node database screen, **When** the user adjusts the slider and taps preview, **Then** `cleanNodeDatabaseUseCase.getNodesToClean()` returns matching nodes.
2. **Given** a preview list, **When** the user confirms cleaning, **Then** `cleanNodeDatabaseUseCase.cleanNodes()` deletes the listed node nums and the list is cleared.

---

### Edge Cases

- What happens when a config request times out (30-second deadline)? → `ResponseState.Error` with "Timeout" message.
- How does the system handle a shutdown command on hardware that doesn't support it? → Error message "Can't shutdown" based on `metadata.canShutdown`.
- What happens when the user edits channels and the PSK changes? → `packetRepository.migrateChannelsByPSK()` migrates existing messages to the new PSK.
- What happens when `destNum` changes mid-configuration? → `RadioConfigViewModel` re-initializes via `destNumFlow` + `combine`.
- What happens if MQTT probe fails with an exception? → Caught via `safeCatching`, result mapped to `MqttProbeStatus.Other(message)`.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| `SettingsViewModel` | `feature/settings/SettingsViewModel.kt` | App-level preferences: theme, locale, location, notifications, DB cache, mesh log, data export |
| `RadioConfigViewModel` | `feature/settings/radio/RadioConfigViewModel.kt` | Radio/device/module config read/write, admin actions, profile import/export, MQTT probe |
| `ChannelViewModel` | `feature/settings/channel/ChannelViewModel.kt` | Channel URL parsing, channel set management, LoRa region/TX config |
| `DebugViewModel` | `feature/settings/debugging/DebugViewModel.kt` | Mesh log display, search, filter, export, retention, protobuf payload decoding |
| `FilterSettingsViewModel` | `feature/settings/filter/FilterSettingsViewModel.kt` | Message word filter management |
| `CleanNodeDatabaseViewModel` | `feature/settings/radio/CleanNodeDatabaseViewModel.kt` | Node database cleanup with age/unknown filters |
| `RadioConfigState` | `feature/settings/radio/RadioConfigViewModel.kt` | Immutable state aggregating all config, channels, metadata, connection, response state |
| `ResponseState<T>` | `feature/settings/radio/ResponseState.kt` | Generic sealed class: Empty → Loading(progress) → Success / Error |
| `ConfigRoute` | `feature/settings/navigation/ConfigRoute.kt` | Enum of 10 device config routes with icons, titles, admin message types |
| `ModuleRoute` | `feature/settings/navigation/ModuleRoute.kt` | Enum of 16 module routes with capability/role filtering and excludability bitfield |
| `AdminRoute` | `feature/settings/radio/RadioConfig.kt` | Enum of admin actions: Reboot, Shutdown, Factory Reset, Node DB Reset |
| `SettingsNavigation` | `feature/settings/navigation/SettingsNavigation.kt` | Navigation 3 `settingsGraph` with `entry<>` for all settings routes |
| `FeatureSettingsModule` | `feature/settings/di/FeatureSettingsModule.kt` | Koin DI module with `@ComponentScan` |
| `PrivacySection` | `feature/settings/component/PrivacySection.kt` | Analytics, location sharing, homoglyph encoding toggles |
| `NotificationSection` | `feature/settings/component/NotificationSection.kt` | Messages, node events, low battery notification toggles |
| `ExpressiveSection` | `feature/settings/component/ExpressiveSection.kt` | Reusable M3 section container with title styling |
| `LogSearchManager` | `feature/settings/debugging/DebugViewModel.kt` | Multi-term regex search with match navigation |
| `LogFilterManager` | `feature/settings/debugging/DebugViewModel.kt` | Multi-tag AND/OR log filtering |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support reading and writing all protobuf `Config` and `ModuleConfig` types via admin messages with request-response tracking.
- **FR-002**: System MUST display loading progress (completed/total) during multi-packet config reads (e.g., channel enumeration).
- **FR-003**: System MUST timeout config requests after 30 seconds and display an error state.
- **FR-004**: System MUST support local and remote device administration (reboot, shutdown, factory reset, node DB reset) with confirmation dialogs.
- **FR-005**: System MUST guard shutdown actions against hardware that does not support shutdown (`metadata.canShutdown`).
- **FR-006**: System MUST filter device config routes based on hardware capabilities (Bluetooth, WiFi, Ethernet).
- **FR-007**: System MUST filter module routes based on `excluded_modules` bitfield, firmware capability checks, and device role applicability.
- **FR-008**: System MUST support importing and exporting `DeviceProfile` protobuf files.
- **FR-009**: System MUST support exporting security configuration separately.
- **FR-010**: System MUST provide app preference toggles for theme, locale, analytics, location sharing, homoglyph encoding.
- **FR-011**: System MUST provide notification preference toggles for messages, node events, and low battery.
- **FR-012**: System MUST support adjustable database cache limit and mesh log retention with bounded clamping.
- **FR-013**: System MUST provide a debug panel with searchable, filterable, decoded mesh packet logs.
- **FR-014**: System MUST decode mesh packet payloads for known portnums (Position, Telemetry, Routing, AdminMessage, etc.) into human-readable strings.
- **FR-015**: System MUST support message word filtering with add/remove and pattern rebuild.
- **FR-016**: System MUST support node database cleanup by age threshold and unknown-node filter.
- **FR-017**: System MUST migrate channel messages by PSK when channels are updated locally.
- **FR-018**: System MUST support MQTT broker connection probing with reachability/credentials feedback.
- **FR-019**: System MUST display managed device warnings and disable configuration when `is_managed == true`.
- **FR-020**: System MUST support CSV data export of persisted packet data with optional portnum filtering.

### Non-Functional Requirements

- **NFR-001**: All config screens must render at 60fps with smooth scrolling.
- **NFR-002**: Config response timeout must not exceed 30 seconds.
- **NFR-003**: All UI composables reside in `commonMain` — platform-specific code limited to `expect`/`actual` declarations for `SettingsMainScreen`, `LogExporter`, `TakPermissionUtil`, and 4 config screens (Device, ExternalNotification, Position, Security).
- **NFR-004**: All strings accessed via `stringResource(Res.string.key)` — no hardcoded text.
- **NFR-005**: All icons use `MeshtasticIcons` from `core/ui/icon/`.

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 76 source files (~12,100 lines) | All business logic, ViewModels, navigation, and UI composables |
| `androidMain` | 13 files | Platform-specific `SettingsScreen`, `AppInfoSection`, `AppearanceSection`, `PersistenceSection`, `SettingsMainScreen`, `LogExporter`, `LanguageUtils`, `TakPermissionUtil`, `PrefExporter`, and 4 config screen actuals |
| `jvmMain` | 8 files | Desktop `SettingsMainScreen`, `DesktopSettingsScreen`, `LogExporter`, `TakPermissionUtil`, `PrefExporter`, and 4 config screen actuals |
| `iosMain` | 8 files | iOS `SettingsNavigation`, `AboutLibrariesLoader`, `NoopStubs`, `TakPermissionUtil`, `PrefExporter`, and 4 config screen actuals |
| `jvmAndroidMain` | 1 file | Shared `AboutLibrariesLoader` |
| `commonTest` | 10 test files (~1,689 lines) | ViewModel and logic tests |

## Design Standards Compliance

- [x] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [x] M3 component selection verified — `SwitchListItem`, `ListItem`, `ExpressiveSection`, `MainAppBar`, `FilterChip`
- [x] Accessibility: Touch targets via `ListItem`, error colors in Administration section
- [x] Typography: M3 scale via `MaterialTheme.colorScheme` and `MaterialTheme.typography`

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed
- [x] Analytics toggle respects user opt-out (`analyticsPrefs.analyticsAllowed`)
- [x] Location sharing is user-initiated with permission checks and GPS-disabled guards
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)
- [x] Security config export is user-initiated write-to-file only

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 10 radio/device config routes and 16 module config routes render and accept user input when connected.
- **SC-002**: Config read/write round-trip completes within 30 seconds or displays a timeout error.
- **SC-003**: All app preference changes are persisted via DataStore and survive app restart.
- **SC-004**: Debug panel displays logs with decoded protobuf payloads for all known portnums.
- **SC-005**: Admin actions (reboot, shutdown, factory reset, node DB reset) execute successfully with confirmation guards.
- **SC-006**: Profile import/export round-trips a `DeviceProfile` protobuf without data loss.
- **SC-007**: ≥10 ViewModel unit test files pass in `commonTest` with full coverage of preference management, connection state, filter logic, and debug search.

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` (from `core/ui/icon/`)
- Float values pre-formatted with `NumberFormatter.format()` (CMP constraint)
- `SettingsMainScreen` uses `expect`/`actual` because Android uses an `OutlinedCard`-based layout while Desktop uses a different layout
- Remote administration uses the same `RadioConfigViewModel` with `destNum` targeting the remote node
- All protobuf types come from `core/proto` (read-only upstream submodule)
- Koin DI with `@KoinViewModel` and `@ComponentScan` for automatic registration

