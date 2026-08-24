# Feature Specification: UDP Label Rename

**Feature Branch**: `jamesarich/issue-5546-alignment-add-missing-network-config-fi-fbde6f`  
**Created**: 2025-05-20  
**Status**: Draft  
**Input**: User description: "Rename the `udp_enabled` string resource label from 'Enabled' to 'UDP broadcasting' to align with the naming pattern of other network config toggles and make the field identifiable during cross-platform audits."  
**Cross-Platform Spec**: N/A — platform-specific UI label correction only; no cross-platform behavior change

## Summary

The Network Config screen contains a toggle for UDP broadcasting that is labeled generically as "Enabled." This makes it indistinguishable from other toggles during cross-platform settings audits and confuses users who see sibling toggles labeled descriptively (e.g., "WiFi enabled," "Ethernet enabled"). This feature renames the label to "UDP broadcasting" so the toggle is self-describing and passes cross-platform validation checks.

## Goals

1. Make the UDP broadcasting toggle immediately identifiable by its label without requiring surrounding context
2. Align the naming convention with sibling network config toggles ("WiFi enabled," "Ethernet enabled")
3. Resolve the settings-validation-matrix discrepancy that marked UDP broadcasting as "Not present" on Android
4. Ensure the label change appears across all supported locales (English base; translators handle other locales via Crowdin)

## Non-Goals

- Changing the underlying functionality of the UDP broadcasting toggle
- Adding new network configuration fields or toggles
- Modifying the `enabled_protocols` bitmask logic or protobuf definitions
- Updating the Apple or other platform clients
- Adding tooltip or help text to the toggle (out of scope for this label fix)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Identifiable UDP Toggle (Priority: P1)

As a user configuring network settings, I want the UDP broadcasting toggle to have a clear, descriptive label so I can understand what it controls without relying on its position in the list.

**Why this priority**: This is the core deliverable — without a descriptive label, the toggle fails cross-platform audits and confuses users.

**Independent Test**: Can be fully tested by opening the Network Config screen and verifying the toggle label reads "UDP broadcasting."

**Acceptance Scenarios**:

1. **Given** a user navigates to the Network Config screen, **When** the screen renders, **Then** the UDP broadcasting toggle displays the label "UDP broadcasting"
2. **Given** a user has previously seen the toggle labeled "Enabled," **When** they update the app and open Network Config, **Then** the toggle now reads "UDP broadcasting" with no change in toggle state or behavior

---

### User Story 2 - Cross-Platform Audit Alignment (Priority: P2)

As a Meshtastic maintainer running the settings-validation-matrix audit, I want the Android UDP field to be identifiable by name so the audit correctly marks it as "Present."

**Why this priority**: Resolving the audit discrepancy prevents duplicate work and incorrect issue reports.

**Independent Test**: Can be tested by running the settings-validation-matrix audit tool against the updated Android client and confirming the UDP broadcasting field is marked as present.

**Acceptance Scenarios**:

1. **Given** the settings-validation-matrix audit inspects the Android Network Config screen, **When** it searches for a UDP broadcasting field, **Then** it finds the toggle by its label "UDP broadcasting" and marks it as present

---

### Edge Cases

- What happens when the user's locale has no translation for the updated string? The English default "UDP broadcasting" is displayed as a fallback.
- What happens if other toggles in the list also use generic labels? This spec addresses only the `udp_enabled` string; other label improvements are separate issues.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| NetworkConfigItemList | `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component/NetworkConfigItemList.kt` | Renders the UDP broadcasting toggle using the string resource |
| strings.xml | `core/resources/src/commonMain/composeResources/values/strings.xml` | Defines the `udp_enabled` string resource label |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The string resource `udp_enabled` MUST display the text "UDP broadcasting" in the English locale
- **FR-002**: The toggle behavior (enable/disable UDP broadcasting via `enabled_protocols` bitmask) MUST remain unchanged
- **FR-003**: The label MUST be visible and readable on all supported screen sizes without truncation on standard device widths

### Non-Functional Requirements

- **NFR-001**: The label change MUST NOT introduce any new accessibility regressions; TalkBack MUST announce the updated label correctly
- **NFR-002**: No new strings beyond modifying the existing `udp_enabled` value; no string key rename required

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | Modified: `strings.xml` (1 line change) | String resources live in commonMain |
| `androidMain` | None | No platform-specific changes |
| `jvmMain` | None | No platform-specific changes |

## Design Standards Compliance

- [x] New screens reviewed against design standards — N/A, no new screens
- [x] M3 component selection verified — existing `SwitchPreference` component unchanged
- [x] Accessibility: TalkBack will announce updated label; no touch target changes
- [x] Typography: No typography changes; existing text style preserved

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed
- [x] No new network calls that transmit user data
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of users see "UDP broadcasting" as the toggle label on the Network Config screen after the update
- **SC-002**: The settings-validation-matrix audit marks the Android UDP broadcasting field as "Present" (previously "Not present")
- **SC-003**: Zero user-reported confusion or support requests about the renamed toggle within 30 days of release

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- String resources are defined in `core/resources/src/commonMain/composeResources/values/strings.xml`
- The existing `udp_enabled` string key is retained (only the value changes) to avoid breaking existing translations in Crowdin
- Crowdin will pick up the value change and notify translators for localization
- The toggle's functional behavior tied to `Config.NetworkConfig.ProtocolFlags.UDP_BROADCAST` is correct and not part of this change
