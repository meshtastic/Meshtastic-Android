# Feature Specification: Messaging & Contacts

**Feature Branch**: `004-messaging`  
**Created**: 2026-07-10  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `feature/messaging` module

## Summary

Messaging & Contacts is the primary communication feature of Meshtastic-Android. It provides a full-featured chat experience over the Meshtastic mesh network, including paginated message threads, contact/channel management, emoji reactions, reply threading, quick-chat shortcuts, unread tracking, message filtering, mute/notification controls, and a share-to-contact flow. All business logic and Compose UI reside in `commonMain`, with a single `androidMain` file for WorkManager-based background message queuing.

## Goals

1. Enable users to send and receive text messages over the Meshtastic mesh, to both individual nodes (DMs) and broadcast channels.
2. Provide a contact list that surfaces all conversations, with unread counts, mute controls, and multi-select batch operations.
3. Support emoji reactions on messages with delivery status tracking.
4. Offer Quick Chat shortcuts for common messages (instant send or append to input).
5. Deliver paginated, performant message lists with unread divider, auto-scroll, and scroll-to-bottom FAB.

## Non-Goals

- End-to-end encryption management — handled by the radio firmware and `core/proto`.
- File or image attachments — only text messages are supported.
- Push notifications routing — handled by `core/service` and `core/repository`.
- Group chat creation or channel provisioning — managed by `feature/settings` and channel config.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Send a Text Message (Priority: P1)

A user opens a conversation, types a message, and sends it over the mesh. The message appears in the thread with a delivery status icon that updates as the mesh acknowledges it.

**Why this priority**: Core messaging is the fundamental capability of the app — all other features depend on it.

**Independent Test**: Send a message to a connected node; verify the message appears locally with QUEUED → ENROUTE → RECEIVED status progression.

**Acceptance Scenarios**:

1. **Given** a connected device and an open conversation, **When** the user types "Hello" and taps Send, **Then** the message appears in the thread with status QUEUED and the input clears.
2. **Given** a message with status ENROUTE, **When** the remote node acknowledges receipt, **Then** the status icon updates to RECEIVED.
3. **Given** a message exceeding 200 bytes, **When** the user types, **Then** the byte counter turns red, the send button is disabled, and the message cannot be sent.
4. **Given** the device is disconnected, **When** the user views the message input, **Then** the input field is disabled and the send button is non-interactive.

---

### User Story 2 — View Contact List & Navigate to Conversations (Priority: P1)

A user sees a paginated list of all conversations (DM contacts + broadcast channels). Channel placeholders appear even when no messages exist yet. Tapping a contact opens its message thread.

**Why this priority**: The contact list is the entry point for all messaging interactions.

**Independent Test**: Open the app with existing conversations; verify contacts display with last message preview, time, and unread badges.

**Acceptance Scenarios**:

1. **Given** three conversations with messages, **When** the contacts screen loads, **Then** all three appear sorted by most recent message.
2. **Given** a contact with 5 unread messages, **When** viewing the contacts list, **Then** a badge shows "5" next to the contact.
3. **Given** two configured channels with no messages, **When** viewing contacts, **Then** placeholder entries for each channel appear.
4. **Given** the contacts list, **When** the user taps a contact, **Then** the app navigates to the message thread for that contact.

---

### User Story 3 — Unread Message Tracking & Auto-Scroll (Priority: P1)

When a user opens a conversation with unread messages, the list scrolls to the first unread message and shows an "Unread Messages" divider. As the user scrolls through messages, they are marked as read after a debounce period.

**Why this priority**: Unread tracking is critical UX for a messaging app — users must know what's new.

**Independent Test**: Receive messages while the app is backgrounded; reopen the thread and verify the divider appears and read-marking works.

**Acceptance Scenarios**:

1. **Given** a conversation with 10 unread messages, **When** the user opens the thread, **Then** the list scrolls to the first unread message with 5 context messages visible above the divider.
2. **Given** the user is reading messages and stops scrolling for 500ms, **When** unread messages are visible, **Then** those messages are marked as read and the notification is cleared if all are read.
3. **Given** new messages arrive while the user is at the bottom and no unread divider is present, **When** a new message appears, **Then** the list auto-scrolls to show it.

---

### User Story 4 — Emoji Reactions (Priority: P2)

A user can react to any message with an emoji. Reactions appear below the message bubble, grouped by emoji, with a count. Users can view reaction details in a dialog.

**Why this priority**: Reactions add expressiveness without consuming mesh bandwidth for full messages.

**Independent Test**: Long-press a message, select an emoji reaction, and verify it appears below the bubble.

**Acceptance Scenarios**:

1. **Given** a received message, **When** the user long-presses and selects 👍, **Then** a 👍 reaction appears below the message bubble.
2. **Given** two users react with the same emoji, **When** viewing the message, **Then** the reaction shows the emoji with count "2".
3. **Given** a user already reacted with 👍, **When** the user taps 👍 again, **Then** the duplicate reaction is prevented (no re-send).
4. **Given** a conversation with reactions, **When** the user long-presses a reaction, **Then** a dialog shows who reacted, timestamps, and SNR/RSSI metadata.

---

### User Story 5 — Quick Chat Shortcuts (Priority: P2)

Users can create, reorder, and use Quick Chat actions — short pre-configured messages that either send instantly or append to the current input.

**Why this priority**: Quick Chat enables fast communication on constrained mesh networks (low bandwidth, slow typing).

**Independent Test**: Create a Quick Chat action, toggle Instant mode, use it during a conversation.

**Acceptance Scenarios**:

1. **Given** the Quick Chat panel is visible, **When** the user taps an Instant action, **Then** its message is sent immediately.
2. **Given** the Quick Chat panel is visible, **When** the user taps an Append action, **Then** its text is appended to the input field.
3. **Given** the Quick Chat options screen, **When** the user drags an action to a new position, **Then** the order is persisted and reflected in the chat panel.

---

### User Story 6 — Contact Management (Mute, Delete, Multi-Select) (Priority: P2)

Users can long-press contacts to enter selection mode. Selected contacts can be batch-deleted, muted for a duration, or unmuted. A "Mark All as Read" action clears all unread badges.

**Why this priority**: Contact management keeps the conversation list organized, especially on busy meshes.

**Independent Test**: Long-press a contact, select multiple, mute them, verify the mute icon appears and persists.

**Acceptance Scenarios**:

1. **Given** a contact list, **When** the user long-presses a contact, **Then** selection mode activates with a toolbar showing count, delete, mute, and select-all actions.
2. **Given** two selected contacts, **When** the user chooses "Mute for 1 week", **Then** both contacts show a mute icon and notifications are suppressed for 7 days.
3. **Given** selected contacts with messages, **When** the user taps delete and confirms, **Then** the contacts and all associated messages are removed.
4. **Given** 3 conversations with unread messages, **When** the user taps "Mark All as Read" in the app bar, **Then** all unread badges clear to 0.

---

### Edge Cases

- What happens when a message reply references a message not yet loaded in the paged list? → The app attempts to scroll to it; if not found in the snapshot, no scroll occurs (graceful no-op).
- How does the system handle the BEL character (`\u0007`) in messages? → A 🔔 icon is displayed and the message bubble gets a red border to indicate an alert/bell message.
- What happens when "Select All" is used with pagination? → Only currently loaded items are selected; a full-list select is not possible with paging.
- How are filtered messages handled? → Messages can be filtered per-contact; filtered messages appear at reduced alpha (0.5) with a "Filtered" label. Users can toggle filter visibility per-contact.
- What happens when message byte size approaches the 200-byte limit with multi-byte characters? → The byte counter correctly counts UTF-8 bytes (not characters), preventing oversized packets.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| `MessageScreen` | `feature/messaging/Message.kt` | Main chat screen composable with input, top bar, quick chat |
| `MessageViewModel` | `feature/messaging/MessageViewModel.kt` | Business logic: send, react, delete, unread tracking, paging |
| `MessageListPaged` | `feature/messaging/MessageListPaged.kt` | Paginated lazy list with auto-scroll, unread divider |
| `MessageItem` | `feature/messaging/component/MessageItem.kt` | Individual message bubble with reactions, status, reply snippet |
| `MessageActionsContent` | `feature/messaging/component/MessageActionsBottomSheet.kt` | Bottom sheet with quick emojis, reply, copy, select, delete |
| `MessageBubble` | `feature/messaging/component/MessageBubble.kt` | Shape logic for grouped message bubbles |
| `Reaction*` | `feature/messaging/component/Reaction.kt` | Reaction item, row, dialog composables |
| `MessageScreenComponents` | `feature/messaging/component/MessageScreenComponents.kt` | Toolbar, FAB, reply snippet, delete dialog, quick chat row, utility functions |
| `MessageStatusIcon` | `feature/messaging/component/MessageStatusIcon.kt` | Animated delivery status icon |
| `ContactsScreen` | `feature/messaging/ui/contact/Contacts.kt` | Paginated contacts list with selection, mute, delete |
| `ContactsViewModel` | `feature/messaging/ui/contact/ContactsViewModel.kt` | Contact list logic: paged contacts, mute, delete, mark-read |
| `ContactItem` | `feature/messaging/ui/contact/ContactItem.kt` | Individual contact card with unread badge, mute icon |
| `AdaptiveContactsScreen` | `feature/messaging/ui/contact/AdaptiveContactsScreen.kt` | Navigation wrapper for contacts |
| `ShareScreen` | `feature/messaging/ui/sharing/Share.kt` | Contact picker for message sharing |
| `QuickChatScreen` | `feature/messaging/QuickChat.kt` | Quick chat management with drag-to-reorder |
| `QuickChatViewModel` | `feature/messaging/QuickChatViewModel.kt` | CRUD for quick chat actions |
| `ContactsNavigation` | `feature/messaging/navigation/ContactsNavigation.kt` | Navigation 3 route definitions |
| `FeatureMessagingModule` | `feature/messaging/di/FeatureMessagingModule.kt` | Koin DI module (component scan) |
| `WorkManagerMessageQueue` | `feature/messaging/worker/WorkManagerMessageQueue.kt` | Android-only WorkManager message queuing |
| `UnreadUiDefaults` | `feature/messaging/UnreadUiDefaults.kt` | Shared constants for unread UX behavior |
| `MessageScreenEvent` | `feature/messaging/MessageScreenEvent.kt` | Sealed interface for UI events |
| `DeliveryInfoDialog` | `feature/messaging/DeliveryInfoDialog.kt` | Message delivery status dialog |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST send text messages to individual nodes (DM) and broadcast channels.
- **FR-002**: System MUST display messages in a reverse-chronological paginated list with sender identification.
- **FR-003**: System MUST show delivery status icons for locally-sent messages (QUEUED, ENROUTE, RECEIVED, ERROR, etc.).
- **FR-004**: System MUST enforce a 200-byte message limit with real-time byte counter display.
- **FR-005**: System MUST support emoji reactions on messages with deduplication (prevent re-sending same reaction).
- **FR-006**: System MUST track unread messages per contact and display counts as badges.
- **FR-007**: System MUST show an "Unread Messages" divider and scroll to first unread on conversation open.
- **FR-008**: System MUST auto-scroll to new messages when the user is at/near the bottom of the list.
- **FR-009**: System MUST mark messages as read after a 500ms scroll debounce and clear notifications when all are read.
- **FR-010**: System MUST support reply threading — replying to a specific message and displaying the original as a snippet.
- **FR-011**: System MUST provide Quick Chat actions (Instant and Append modes) with drag-to-reorder.
- **FR-012**: System MUST display a paginated contact list with last message preview, time, and unread badge.
- **FR-013**: System MUST support multi-select contact operations: delete, mute (8h/1week/always), unmute, select all.
- **FR-014**: System MUST show channel placeholder contacts for all configured channels even without messages.
- **FR-015**: System MUST support message selection mode with copy-to-clipboard, delete, and select-all actions.
- **FR-016**: System MUST allow message resend from the status dialog when status is ERROR.
- **FR-017**: System MUST support homoglyph encoding optimization for Cyrillic text to reduce byte usage.
- **FR-018**: System MUST provide a Share screen for forwarding messages to a selected contact.

### Non-Functional Requirements

- **NFR-001**: Message list MUST use Paging 3 (`LazyPagingItems`) for memory-efficient rendering of large conversations.
- **NFR-002**: Node lookup in message list MUST be O(1) via pre-calculated `nodeMap` (not O(N) per item).
- **NFR-003**: List item animations MUST be disabled during scroll to prevent jank/stutter.
- **NFR-004**: All string resources MUST use `stringResource(Res.string.*)` — no hardcoded user-facing text in `commonMain`.
- **NFR-005**: All icons MUST use `MeshtasticIcons.*` exclusively.
- **NFR-006**: Coroutines MUST use `safeLaunch` (not raw `launch`) and `ioDispatcher` (not `Dispatchers.IO`).

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 25 files — all logic and UI | Business logic + CMP composables per Constitution §I, §III |
| `androidMain` | 1 file — `WorkManagerMessageQueue.kt` | Platform-specific WorkManager integration for reliable background message send |
| `commonTest` | 6 files — ViewModel and component tests | KMP test coverage per Constitution §VI |

## Design Standards Compliance

- [x] New screens reviewed against design standards (existing code, production-validated)
- [x] M3 component selection verified — `OutlinedTextField`, `Scaffold`, `TopAppBar`, `Card`, `ListItem`, `ModalBottomSheet`, `FloatingActionButton`, `AssistChip`, `Badge`
- [x] Accessibility: TalkBack semantics on `MessageItem` (`a11y_message_from`), content descriptions on all icons, haptic feedback on long-press
- [x] Typography: M3 type scale used throughout (`bodyLarge`, `labelSmall`, `titleMedium`, etc.)

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed
- [x] No new network calls that transmit user data (messages routed through existing service layer)
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can send a text message and see it delivered to a connected mesh node.
- **SC-002**: Message delivery status updates correctly through QUEUED → ENROUTE → RECEIVED lifecycle.
- **SC-003**: Unread count badges on the contacts list accurately reflect unread messages per conversation.
- **SC-004**: Opening a conversation with unreads scrolls to the first unread message with the divider visible.
- **SC-005**: Emoji reactions are delivered, displayed, grouped, and deduplicated correctly.
- **SC-006**: Quick Chat actions (Instant and Append) work correctly and persist reordering.
- **SC-007**: Contact multi-select operations (delete, mute, unmute) apply correctly to all selected contacts.
- **SC-008**: Message list scrolls at 60fps with no visible jank during paged loading of large conversations.

## Assumptions

- All business logic and UI composables reside in `commonMain` source set.
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`.
- Icons use `MeshtasticIcons` (from `core/ui/icon/`).
- Float values pre-formatted with `NumberFormatter.format()` (CMP constraint).
- Message routing and service communication handled by `core/repository` and `core/service`.
- Paging provided by AndroidX Paging 3 KMP (`androidx.paging`).
- Navigation uses Navigation 3 typed `NavKey` routes via `core/navigation`.
- DI uses Koin 4.2+ with K2 Compiler Plugin component scanning.

