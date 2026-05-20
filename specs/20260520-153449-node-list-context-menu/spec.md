# Feature Specification: Node List Context Menu Alignment

**Feature Branch**: `jamesarich/issue-5544-alignment-align-node-list-long-press-co-1d63b1`  
**Created**: 2025-05-20  
**Status**: Draft  
**Input**: GitHub Issue #5544 — Align node list long-press context menu to canonical order  
**Cross-Platform Spec**: Menu Alignment Audit (cross-platform canonical order)

## Summary

The node list long-press context menu currently shows 4 items (Favorite, Mute, Ignore, Remove) in a non-standard order. This feature aligns the context menu to the cross-platform canonical order by reordering existing items, renaming "Mute Always" to "Mute notifications", and adding two new actions ("Message" and "Trace Route") for a total of 6 menu items. This improves cross-platform consistency and provides quick access to frequently used node actions.

## Goals

1. Match the cross-platform canonical menu order exactly as defined in the Menu Alignment Audit
2. Add "Message" action to the context menu so users can quickly start a conversation with a node
3. Add "Trace Route" action to the context menu so users can diagnose connectivity without navigating away
4. Rename "Mute Always" to "Mute notifications" for clearer, user-friendly labeling
5. Maintain consistent behavior across Android and Desktop adaptive node list screens

## Clarifications

### Session 2026-05-20

- Q: Should the context menu appear for the local node (self) on long-press? → A: Suppress context menu entirely for the local node (no menu on long-press of self)

## Non-Goals

- Redesigning the visual appearance of the dropdown menu (colors, typography, spacing)
- Adding new functionality behind the "Message" or "Trace Route" actions — these wire to existing capabilities
- Changing context menu behavior on the map view or other screens
- Modifying the underlying favorite/ignore/mute/remove logic

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Canonical Menu Order (Priority: P1)

A user long-presses a node in the node list and sees all 6 context menu items in the expected canonical order, matching other Meshtastic platforms.

**Why this priority**: Core requirement — the entire feature is about establishing the correct menu order for cross-platform consistency.

**Independent Test**: Long-press any node in the node list and verify the menu displays exactly 6 items in the specified order.

**Acceptance Scenarios**:

1. **Given** a node list with at least one node, **When** the user long-presses a node, **Then** the context menu displays items in this order: (1) Add to favorites / Remove from favorites, (2) Mute notifications / Unmute, (3) Message, (4) Trace Route, (5) Ignore / Remove from ignored, (6) Remove
2. **Given** a node that cannot be muted (canMuteNode is false), **When** the user long-presses the node, **Then** "Mute notifications" is hidden but all other items remain in their canonical positions

---

### User Story 2 - Message Action (Priority: P2)

A user long-presses a node and selects "Message" to navigate directly to a conversation with that node.

**Why this priority**: Adds a frequently needed shortcut — messaging is a primary use case in Meshtastic.

**Independent Test**: Long-press a node, tap "Message", and verify navigation to the messaging screen for that node.

**Acceptance Scenarios**:

1. **Given** a node in the node list, **When** the user long-presses and selects "Message", **Then** the app navigates to the direct message conversation with that node
2. **Given** an ignored node, **When** the user long-presses the node, **Then** the "Message" action is disabled (consistent with other actions on ignored nodes)

---

### User Story 3 - Trace Route Action (Priority: P2)

A user long-presses a node and selects "Trace Route" to initiate route tracing to that node.

**Why this priority**: Provides quick access to a diagnostic action without navigating to node details.

**Independent Test**: Long-press a node, tap "Trace Route", and verify the trace route operation is initiated.

**Acceptance Scenarios**:

1. **Given** a node in the node list, **When** the user long-presses and selects "Trace Route", **Then** a trace route request is initiated to that node
2. **Given** an ignored node, **When** the user long-presses the node, **Then** the "Trace Route" action is disabled

---

### User Story 4 - Mute Notifications Rename (Priority: P3)

The mute action displays "Mute notifications" instead of the previous "Mute Always" label.

**Why this priority**: Label improvement for clarity — lower priority since functionality is unchanged.

**Independent Test**: Long-press a node that supports muting and verify the label reads "Mute notifications" (not "Mute Always").

**Acceptance Scenarios**:

1. **Given** a node that can be muted and is not currently muted, **When** the user long-presses the node, **Then** the menu shows "Mute notifications" (not "Mute Always")
2. **Given** a node that is currently muted, **When** the user long-presses the node, **Then** the menu shows "Unmute" (unchanged)

---

### Edge Cases

- What happens when the user long-presses the local node (self)? The context menu MUST NOT appear — long-press on self is a no-op.
- What happens if the node disappears from the mesh while the context menu is open? The menu should dismiss gracefully.
- What happens when "Message" is selected for a node with no prior conversation? A new conversation should be created.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| NodeContextMenu | `feature/node/component/NodeContextMenu.kt` | Shared context menu composable — reorder items and add new entries |
| NodeListScreen | `feature/node/list/NodeListScreen.kt` | Wires context menu callbacks including new Message and Trace Route actions |
| String Resources | `core/resources/.../strings.xml` | Add "mute_notifications", "message", "trace_route" strings; deprecate "mute_always" |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Context menu MUST display exactly 6 items in canonical order: Favorite, Mute notifications, Message, Trace Route, Ignore, Remove
- **FR-002**: "Mute Always" string MUST be renamed to "Mute notifications" across the app
- **FR-003**: "Message" menu item MUST navigate the user to the direct message screen for the selected node
- **FR-004**: "Trace Route" menu item MUST initiate a trace route request to the selected node
- **FR-005**: "Ignore" MUST appear at position 5 (before Remove)
- **FR-006**: "Remove" MUST appear at position 6 (last item in the menu)
- **FR-007**: "Message" and "Trace Route" MUST be disabled for ignored nodes (consistent with Favorite behavior)
- **FR-008**: "Mute notifications" item MUST remain conditionally visible based on node's canMuteNode capability
- **FR-009**: Context menu MUST NOT appear when the user long-presses the local node (self)

### Non-Functional Requirements

- **NFR-001**: Context menu MUST appear within 300ms of long-press to maintain responsive feel
- **NFR-002**: All new menu items MUST have proper accessibility labels for TalkBack/screen readers
- **NFR-003**: Menu MUST work identically on both Android and Desktop adaptive screens

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | Modified: NodeContextMenu.kt, NodeListScreen.kt, strings.xml | All business logic and UI per Constitution §I, §III |
| `androidMain` | None | No platform-specific changes needed |
| `jvmMain` | None | No platform-specific changes needed |

## Design Standards Compliance

- [ ] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [ ] M3 component selection verified (DropdownMenuItem with leadingIcon pattern maintained)
- [ ] Accessibility: TalkBack semantics on new menu items, touch targets adequate
- [ ] Typography: Consistent with existing menu item text styling

## Privacy Assessment

- [ ] No PII, location data, or cryptographic keys logged or exposed
- [ ] No new network calls that transmit user data
- [ ] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Context menu displays exactly 6 items in the canonical order on 100% of long-press interactions
- **SC-002**: Users can reach "Message" for any node in 1 long-press + 1 tap (vs. previous multi-step navigation)
- **SC-003**: Users can initiate "Trace Route" for any node in 1 long-press + 1 tap (vs. previous multi-step navigation)
- **SC-004**: No regressions in existing Favorite, Mute, Ignore, and Remove functionality
- **SC-005**: Menu label reads "Mute notifications" in all locales where translation is available

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` (from `core/ui/icon/`) — suitable icons exist or will be added for Message and Trace Route
- The existing "Message" navigation and "Trace Route" request functionality already exists in the app and only needs to be wired into the context menu
- The Menu Alignment Audit canonical order is the authoritative source for item positioning
- "Mute notifications" / "Unmute" toggle logic remains identical to current "Mute Always" / "Unmute" behavior
- Disabled state for ignored nodes applies to Message and Trace Route (matching existing Favorite disabled behavior)
