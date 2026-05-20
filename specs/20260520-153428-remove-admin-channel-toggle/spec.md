# Feature Specification: Remove Admin Channel Enabled Toggle

**Feature Branch**: `jamesarich-issue-5545-alignment-remove-admin-channel-enabled-ca777c`  
**Created**: 2025-05-20  
**Status**: Draft  
**Input**: User description: "Remove admin_channel_enabled toggle from the Security Config screen"  
**Cross-Platform Spec**: N/A — platform alignment removal (aligning Android with Apple client behavior)

## Summary

Remove the `admin_channel_enabled` toggle from the Security Config settings screen in the Android client. This toggle is Android-only, not present in the Apple client, and has been identified as a cross-platform discrepancy in the settings validation audit. With PKC-based administration becoming the default approach, this toggle creates user confusion and should be removed to align both clients.

## Goals

- Remove the `admin_channel_enabled` UI toggle from the Security Config screen
- Align the Android client's Security Config screen with the Apple client
- Reduce user confusion around legacy admin channel configuration
- Maintain full PKC-based admin functionality without regression

## Non-Goals

- Removing or modifying the underlying `admin_channel_enabled` proto field (it remains in the protobuf schema)
- Changing any backend/firmware behavior related to admin channels
- Modifying PKC-based administration logic
- Removing string resources from locale files (cleanup can be done separately)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Security Config Screen Without Legacy Toggle (Priority: P1)

As a user navigating to the Security Config screen, I no longer see the `admin_channel_enabled` toggle, resulting in a cleaner interface focused on PKC-based administration.

**Why this priority**: This is the core deliverable — removing the toggle from the UI.

**Independent Test**: Can be fully tested by navigating to the Security Config screen and verifying the toggle is absent, while all other security settings remain functional.

**Acceptance Scenarios**:

1. **Given** a user opens the Security Config screen, **When** the screen renders, **Then** the `admin_channel_enabled` toggle and its associated divider are not displayed.
2. **Given** a user opens the Security Config screen, **When** reviewing available settings, **Then** all other security configuration options remain visible and functional.

---

### User Story 2 - PKC Admin Functionality Unaffected (Priority: P1)

As a user performing administrative actions via PKC-based administration, the removal of the toggle does not affect my ability to manage nodes.

**Why this priority**: Ensuring no regression in core admin functionality is critical.

**Independent Test**: Can be tested by performing PKC-based admin operations (e.g., remote node configuration) after the toggle removal and verifying they succeed.

**Acceptance Scenarios**:

1. **Given** a user has PKC-based admin configured, **When** they perform a remote admin operation, **Then** the operation completes successfully as before.
2. **Given** a device had `admin_channel_enabled` previously set to true, **When** the user opens Security Config, **Then** the setting value persists in the proto config but is simply not shown in the UI.

---

### Edge Cases

- What happens when a device has `admin_channel_enabled` set to `true` in its stored config? The value remains in the proto; it is simply no longer surfaced or toggleable in the UI.
- What happens on config export/import? The field remains in the protobuf schema, so existing exports with the field set remain valid and importable.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| SecurityConfigScreen | `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component/SecurityConfigScreen.kt` | Screen where the toggle is removed |
| String resources | `core/resources/src/commonMain/composeResources/values*/strings.xml` | `legacy_admin_channel` string (unused after removal) |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Security Config screen MUST NOT display the `admin_channel_enabled` toggle (SwitchPreference) or its preceding HorizontalDivider
- **FR-002**: The Security Config screen MUST continue to display all other security configuration options unchanged
- **FR-003**: The underlying proto field `admin_channel_enabled` on `Config.SecurityConfig` MUST NOT be modified or removed

### Non-Functional Requirements

- **NFR-001**: The Security Config screen must render without visual artifacts or layout shifts where the toggle was previously positioned
- **NFR-002**: Existing screenshot tests (if any) for Security Config must be updated to reflect the removal

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | Modified: `SecurityConfigScreen.kt` (remove toggle + divider) | All UI is in commonMain per Constitution §I |
| `androidMain` | None | No platform-specific changes needed |
| `jvmMain` | None | No desktop-specific changes needed |

## Design Standards Compliance

- [x] New screens reviewed against design standards — N/A (removal only, no new UI)
- [x] M3 component selection verified — N/A (no new components)
- [x] Accessibility: TalkBack semantics — N/A (removing element, not adding)
- [x] Typography — N/A (no new text)

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed
- [x] No new network calls that transmit user data
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The Security Config screen displays zero instances of the admin channel enabled toggle
- **SC-002**: All existing PKC-based admin operations complete successfully after the change (no regression)
- **SC-003**: The Android Security Config screen field count matches the Apple client's Security Config screen (alignment achieved)

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- The proto field `admin_channel_enabled` remains available for firmware communication; only the UI toggle is removed
- String resource cleanup (`legacy_admin_channel`) is considered optional follow-up work and not required for this feature
- The Apple client's Security Config screen is the reference for cross-platform alignment
- No other screens or components reference the `admin_channel_enabled` toggle UI
