# Feature Specification: Reorder Bottom Navigation Tab Labels

**Feature Branch**: `jamesarich/issue-5543-alignment-reorder-bottom-navigation-tab-91d55d`  
**Created**: 2025-05-20  
**Status**: Draft  
**Input**: User description: "Issue #5543 - Reorder bottom navigation tabs to canonical order per Menu Alignment Audit"  
**Cross-Platform Spec**: [Menu Alignment Audit](https://github.com/meshtastic/design/blob/master/standards/audits/menu-alignment-audit.md)

## Clarifications

### Session 2026-05-20

- Q: Should string resource keys be renamed (not just display values)? → A: Yes — create new keys `messages` and `connect` for nav tab labels. Old keys (`conversations`, `connections`) remain for screen titles in Contacts.kt and ConnectionsScreen.kt.
- Q: Should localized strings.xml files be updated in this PR? → A: No — defer to Crowdin. Only update English `values/strings.xml`; localized files pick up new keys in next translation sync.

## Summary

Rename two bottom navigation tab labels to match the cross-platform canonical naming convention defined in the Meshtastic Menu Alignment Audit. "Conversations" becomes "Messages" and "Connections" becomes "Connect". Tab order already matches the canonical order and requires no positional changes.

## Goals

1. Achieve cross-platform label consistency by aligning Android tab names with the canonical standard
2. Improve user familiarity by using industry-standard terminology ("Messages" for messaging)
3. Reduce cognitive friction for users switching between Meshtastic clients on different platforms
4. Maintain existing navigation behavior and tab ordering without regressions

## Non-Goals

- Changing the tab order (already matches canonical: Messages, Nodes, Map, Settings, Connect)
- Redesigning tab icons or visual styling
- Adding, removing, or merging navigation tabs
- Changing functionality behind any tab
- Modifying navigation deep link route identifiers (only user-visible labels change)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See Updated Tab Labels (Priority: P1)

As a Meshtastic Android user, I see "Messages" and "Connect" as tab labels so that the interface matches documentation and other platform clients.

**Why this priority**: This is the core deliverable — the visual label change that achieves cross-platform alignment.

**Independent Test**: Open the app after update; visually confirm bottom navigation bar shows "Messages" (position 1) and "Connect" (position 5).

**Acceptance Scenarios**:

1. **Given** the app is launched, **When** the bottom navigation bar is visible, **Then** the first tab reads "Messages" (not "Conversations")
2. **Given** the app is launched, **When** the bottom navigation bar is visible, **Then** the fifth tab reads "Connect" (not "Connections")
3. **Given** the app is launched, **When** the bottom navigation bar is visible, **Then** tab order is: Messages, Nodes, Map, Settings, Connect

---

### User Story 2 - Navigation Still Functions After Rename (Priority: P1)

As a user, I can tap the renamed tabs and navigate to the correct screens without any change in behavior.

**Why this priority**: Equal to P1 because broken navigation would be a blocking regression.

**Independent Test**: Tap "Messages" tab → messaging screen loads; tap "Connect" tab → connection/pairing screen loads.

**Acceptance Scenarios**:

1. **Given** the user is on any screen, **When** they tap "Messages", **Then** the messaging/conversations screen appears
2. **Given** the user is on any screen, **When** they tap "Connect", **Then** the device connection/pairing screen appears
3. **Given** the user taps between all five tabs in rapid succession, **When** each tab is selected, **Then** the correct corresponding screen displays without delay or error

---

### User Story 3 - Deep Links and State Restoration Work (Priority: P2)

As a user who receives a notification or restores the app from background, deep links and saved navigation state continue to route correctly.

**Why this priority**: Ensures no regression in system-level navigation behavior that relies on route identifiers.

**Independent Test**: Trigger a message notification → tap it → app opens to the messaging screen. Kill and restore the app → previously selected tab is restored.

**Acceptance Scenarios**:

1. **Given** the app is in the background and a message notification arrives, **When** the user taps the notification, **Then** the app navigates to the messaging screen (now labeled "Messages")
2. **Given** the user is on the "Connect" tab and the system kills the app, **When** the app is restored, **Then** the "Connect" tab is selected and the connection screen is displayed
3. **Given** a deep link targets the messaging section, **When** the link is activated, **Then** the app navigates to the messaging screen under the "Messages" tab

---

### Edge Cases

- What happens if a user has the old version cached and updates? Label change takes effect immediately as it is a string resource change.
- How does the app behave with localized strings? Localized translations for these labels should also be updated for all supported languages.
- What about accessibility services (TalkBack)? The new labels ("Messages", "Connect") are announced correctly by screen readers since they derive from the same string resources.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| Navigation bar strings | `core/resources/src/commonMain/composeResources/values/strings.xml` | New keys `messages` and `connect` for tab labels; old keys retained for screen titles |
| Localized strings | `core/resources/src/commonMain/composeResources/values-*/strings.xml` | Deferred — new keys picked up in next Crowdin sync |
| Bottom navigation composable | Navigation component referencing string resources | Displays tab labels in bottom bar |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The bottom navigation tab currently labeled "Conversations" MUST display "Messages" in English locale
- **FR-002**: The bottom navigation tab currently labeled "Connections" (or "Connection") MUST display "Connect" in English locale
- **FR-003**: Tab order MUST remain: Messages, Nodes, Map, Settings, Connect (positions 1–5)
- **FR-004**: Tapping any renamed tab MUST navigate to the same destination screen as before the rename
- **FR-005**: Deep links that previously routed to the Conversations or Connections screens MUST continue to function
- **FR-006**: State restoration MUST correctly restore the selected tab after process death

### Non-Functional Requirements

- **NFR-001**: Accessibility — screen readers MUST announce the updated labels ("Messages", "Connect") when tabs receive focus
- **NFR-002**: Localization — translated string values are deferred to next Crowdin sync; only English `values/strings.xml` is updated in this change
- **NFR-003**: No user-perceivable performance impact from the label change

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | Modified string resources | Tab labels are defined in shared compose resources |
| `androidMain` | None | No platform-specific changes needed |
| `jvmMain` | None | No desktop-specific changes needed |

## Design Standards Compliance

- [x] New screens reviewed against design standards — No new screens; existing screens unchanged
- [x] M3 component selection verified — No component changes
- [ ] Accessibility: TalkBack semantics, touch targets, color-independent info — Verify renamed labels are announced correctly
- [x] Typography: No typography changes

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed
- [x] No new network calls that transmit user data
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of users see "Messages" as the first tab label after updating the app
- **SC-002**: 100% of users see "Connect" as the fifth tab label after updating the app
- **SC-003**: Tab order matches canonical sequence (Messages, Nodes, Map, Settings, Connect) across all locales
- **SC-004**: Zero navigation regressions — all existing deep links and state restoration paths continue to function
- **SC-005**: Cross-platform label parity achieved — Android tab names match the Menu Alignment Audit specification

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- String resources are defined in `core/resources/src/commonMain/composeResources/values/strings.xml`
- New string resource keys (`messages`, `connect`) will be created for bottom navigation tab labels; existing keys (`conversations`, `connections`) are retained for screen titles in feature modules (Contacts.kt, ConnectionsScreen.kt)
- Tab order is already correct (positions 1–5 match canonical) and no reordering logic changes are needed
- Navigation route identifiers are independent of user-visible label strings (routes use programmatic keys, not display text)
- Localized translations are deferred to the next Crowdin translation sync cycle (not in-scope for this PR)
- The `doc_title_connections` string resource (used elsewhere, e.g., documentation titles) may need separate evaluation but is out of scope for this tab label change
