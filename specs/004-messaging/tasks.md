# Tasks: Messaging & Contacts

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Status**: Migrated — all existing tasks marked complete. Gap tasks marked incomplete.  
**Task Prefix**: `MSG-T`

---

## Phase 1 — Data Layer & DI

### MSG-T001: Koin DI module setup [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/di/FeatureMessagingModule.kt`
- Created `FeatureMessagingModule` with `@ComponentScan` for auto-discovery of `@KoinViewModel` classes.
- **Test**: Module loads without error in app startup.

### MSG-T002: MessageViewModel — core state and repository wiring [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageViewModel.kt`
- Injects `NodeRepository`, `RadioConfigRepository`, `PacketRepository`, `ServiceRepository`, `QuickChatActionRepository`, `UiPrefs`, `CustomEmojiPrefs`, `HomoglyphPrefs`, `NotificationManager`, `SendMessageUseCase`.
- Exposes `nodeList`, `ourNodeInfo`, `connectionState`, `channels`, `showQuickChat`, `quickChatActions`, `contactSettings`, `frequentEmojis`, `homoglyphEncodingEnabled`.
- Uses `SavedStateHandle` for `contactKey` initialization.
- **Test**: `MessageViewModelTest.kt` — 10 tests covering init, title, connection state, send, react, delete, unread count, clear unread, node integration.

### MSG-T003: ContactsViewModel — contact list and management [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/ui/contact/ContactsViewModel.kt`
- Provides both non-paginated (`contactList`) and paginated (`contactListPaged`) contact flows.
- Supports `deleteContacts`, `markAllAsRead`, `setMuteUntil`, `setContactFilteringDisabled`, `getTotalMessageCount`.
- **Test**: `ContactsViewModelTest.kt` — 2 tests covering init, unread count total flow.

---

## Phase 2 — Message Thread Screen

### MSG-T004: MessageScreenEvent sealed interface [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageScreenEvent.kt`
- Defines events: `SendMessage`, `SendReaction`, `DeleteMessages`, `ClearUnreadCount`, `NodeDetails`, `SetTitle`, `NavigateToNodeDetails`, `NavigateBack`, `CopyToClipboard`.

### MSG-T005: MessageScreen composable [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/Message.kt`
- Main `Scaffold` with top bar (normal + action mode), message list, quick chat row, reply snippet, message input.
- Handles contact key parsing, channel resolution, mismatch key detection.
- Manages unread scroll logic: initial scroll to first unread with 5-message context.

### MSG-T006: MessageInput composable [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/Message.kt` (private composable)
- `OutlinedTextField` with byte counter, 200-byte limit enforcement, send-on-Enter (desktop keyboard), multi-line (max 3), homoglyph encoding support.
- Disabled state when device is disconnected.
- Preview composables for normal, disabled, over-limit, and multi-byte character states.

### MSG-T007: MessageListPaged composable [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageListPaged.kt`
- Reverse-layout `LazyColumn` with `LazyPagingItems`.
- Groups consecutive messages from same sender (bubble shape optimization).
- Unread divider placement based on `firstUnreadMessageUuid`.
- Status dialog for ERROR messages with resend option.
- Reaction dialog showing who reacted, metadata (SNR/RSSI/hops), and delivery status.
- Animation disabled during scroll for performance.

### MSG-T008: Auto-scroll and unread tracking [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageListPaged.kt`
- `AutoScrollToBottomPaged`: Caches "at bottom" state when scroll is idle to prevent stuttering. Scrolls to item 0 on new message if cached position is at bottom.
- `UpdateUnreadCountPaged`: Uses `snapshotFlow` + 500ms `debounce` to mark visible unread messages as read after scroll settles. Lifecycle-aware (pauses on background).

### MSG-T009: UnreadUiDefaults constants [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/UnreadUiDefaults.kt`
- `VISIBLE_CONTEXT_COUNT = 5`, `AUTO_SCROLL_BOTTOM_OFFSET_TOLERANCE = 8`, `SCROLL_DEBOUNCE_MILLIS = 500L`.
- **Test**: `UnreadUiDefaultsTest.kt` — 1 test validating constant values.

### MSG-T010: MessageItem composable [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/component/MessageItem.kt`
- Node chip, sender name, auto-link text, SNR/RSSI/hops metadata, transport icon, timestamp.
- BEL character detection (red border + 🔔 icon).
- Filtered message alpha + label.
- `ModalBottomSheet` with `MessageActionsContent` or `EmojiPickerDialog`.
- Original message reply snippet with clickable navigation.
- Accessibility: merged semantics with `a11y_message_from` content description.
- **Test**: `MessageItemTest.kt` — 3 tests: MQTT icon visibility, semantic content description.

### MSG-T011: MessageBubble shape logic [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/component/MessageBubble.kt`
- `getMessageBubbleShape()` returns corner-based shape based on sender direction and grouping (hasSamePrev/hasSameNext).

### MSG-T012: Message actions and status icons [x]

- **Files**:
  - `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/component/MessageActions.kt`
  - `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/component/MessageActionsBottomSheet.kt`
  - `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/component/MessageStatusIcon.kt`
- `MessageActions` row: reaction button + reply button + status button.
- `MessageActionsContent` bottom sheet: quick emoji row (6 + "more"), reply, copy, select, delete, status.
- `MessageStatusIcon`: Maps `MessageStatus` enum to `MeshtasticIcons.*` vectors.

### MSG-T013: Reaction composables [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/component/Reaction.kt`
- `ReactionItem`: Single emoji with count, sending/error states (alpha, error container).
- `ReactionRow`: Grouped emoji row with add-reaction button.
- `ReactionDialog`: Bottom sheet showing who reacted, timestamps, SNR/RSSI/hops, status dialog for own reactions.
- `AddReactionButton`: Opens emoji picker dialog.

### MSG-T014: Message screen toolbar components [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/component/MessageScreenComponents.kt`
- `MessageTopBar`: Title + security icon + PKC key status + overflow menu.
- `ActionModeTopBar`: Selection count + copy/delete/select-all actions.
- `ScrollToBottomFab`: FAB with unread badge.
- `ReplySnippet`: Animated reply-to bar with original message snippet (50 char limit).
- `DeleteMessageDialog`: Confirmation with plural string.
- `QuickChatRow`: Horizontal action buttons with 🔔 alert action prepended.
- `handleQuickChatAction()`: Instant (send) vs. Append (add to input) with byte-limit enforcement.
- `UnreadMessagesDivider`: Styled horizontal divider with "New messages" label.
- `MessageStatusDialog`: Delivery info dialog with resend option.
- Utility functions: `ellipsize()`, `limitBytes()`.

---

## Phase 3 — Contacts Screen

### MSG-T015: ContactItem composable [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/ui/contact/ContactItem.kt`
- `Card` with `AssistChip` (node short name with colors), long name, last message time, last message text preview.
- Unread count badge (capped at 99+), mute icon, security icon for broadcast channels.
- Selected/active/outlined card states.

### MSG-T016: ContactsScreen with paginated list [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/ui/contact/Contacts.kt`
- `Scaffold` with `MainAppBar` showing "Mark All as Read" when unreads exist.
- Channel placeholder generation for empty channels.
- Paginated `LazyColumn` with `LazyPagingItems`.
- Loading indicator for append state.
- `MeshtasticImportFAB` for sharing/importing channels when connected.

### MSG-T017: Contact selection mode [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/ui/contact/Contacts.kt`
- Long-press enters selection mode. Toolbar shows count, close, mute/unmute, delete, select-all.
- `DeleteConfirmationDialog` with plural strings.
- `MuteNotificationsDialog` with radio options: unmute, 8 hours, 1 week, always. Shows current mute status per contact.

### MSG-T018: AdaptiveContactsScreen wrapper [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/ui/contact/AdaptiveContactsScreen.kt`
- Bridges `ContactsScreen` with Navigation 3 `NavBackStack` for adaptive (list-detail) layout.

### MSG-T019: ContactsNavigation graph [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/navigation/ContactsNavigation.kt`
- Defines `entryProviderScope` with routes:
  - `ContactsRoute.ContactsGraph` / `ContactsRoute.Contacts` → `ContactsEntryContent` (list pane)
  - `ContactsRoute.Messages(contactKey)` → `MessageScreen` (detail pane)
  - `ContactsRoute.Share(message)` → `ShareScreen` (extra pane)
  - `ContactsRoute.QuickChat` → `QuickChatScreen` (extra pane)
- Uses `ListDetailSceneStrategy` for adaptive panes.
- `dropUnlessResumed` for safe navigation callbacks.

---

## Phase 4 — Quick Chat, Share, Polish & Tests

### MSG-T020: QuickChatViewModel [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/QuickChatViewModel.kt`
- CRUD operations: `addQuickChatAction`, `deleteQuickChatAction`, `updateActionPositions`.
- **Test**: `QuickChatViewModelTest.kt` — 3 tests: init, flow reflection, add action delegation.

### MSG-T021: QuickChatScreen with drag-to-reorder [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/QuickChat.kt`
- `LazyColumn` with `dragDropItemsIndexed` for reordering.
- `EditQuickChatDialog`: name (5 chars), message (200 bytes), Instant/Append toggle, delete option.
- FAB to add new action.
- Auto-generates short name from message text.

### MSG-T022: ShareScreen [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/ui/sharing/Share.kt`
- Contact picker using non-paginated `contactList` from `ContactsViewModel`.
- Single-select with send button. Navigates to `Messages` route with pre-filled message.

### MSG-T023: DeliveryInfoDialog [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/DeliveryInfoDialog.kt`
- Generic delivery info dialog with title, text, relay count (pluralized), optional resend button.

### MSG-T024: Homoglyph encoding support [x]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/Message.kt` (MessageInput)
- When `homoglyphEncodingEnabled`, applies `HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs()` to reduce byte usage for Cyrillic text.
- **Test**: `HomoglyphCharacterTransformTest.kt` — 5 tests: shrink with homoglyphs, half-size all-homoglyphs, no transform for non-homoglyphs, no transform for Latin, no transform for Arabic.

### MSG-T025: WorkManagerMessageQueue (Android) [x]

- **File**: `feature/messaging/src/androidMain/kotlin/org/meshtastic/feature/messaging/worker/WorkManagerMessageQueue.kt`
- Android-only `MessageQueue` implementation using `OneTimeWorkRequestBuilder<SendMessageWorker>`.
- Uses `ExistingWorkPolicy.REPLACE` with unique work name per packet.

### MSG-T026: MessageViewModel tests [x]

- **File**: `feature/messaging/src/commonTest/kotlin/org/meshtastic/feature/messaging/MessageViewModelTest.kt`
- 10 tests: initialization, set title, connection state, toggle quick chat, frequent emojis, send message, send reaction, delete messages, unread count, clear unread count, node repository integration.
- Uses Mokkery mocks, Turbine for flow testing, `StandardTestDispatcher`.

### MSG-T027: QuickChatViewModel tests [x]

- **File**: `feature/messaging/src/commonTest/kotlin/org/meshtastic/feature/messaging/QuickChatViewModelTest.kt`
- 3 tests: initialization, actions flow reflects repo updates, add action delegates to repo.

### MSG-T028: MessageItem UI tests [x]

- **File**: `feature/messaging/src/commonTest/kotlin/org/meshtastic/feature/messaging/component/MessageItemTest.kt`
- 3 tests: MQTT icon displayed when `viaMqtt=true`, MQTT icon absent when `viaMqtt=false`, correct semantic `contentDescription`.
- Uses `runComposeUiTest` from `androidx.compose.ui.test.v2`.

---

## Gap Tasks (Identified During Migration)

### MSG-T029: Fix hardcoded English strings in SelectionToolbar [ ]

- **File**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/ui/contact/Contacts.kt` (lines 468–470)
- **Issue**: `contentDescription` for mute/unmute icons uses hardcoded `"Mute selected"` / `"Unmute selected"` instead of `stringResource()`.
- **Constitution**: Violates Principle VIII (Resource Discipline).
- **Fix**: Add `mute_selected` and `unmute_selected` string resources; replace hardcoded strings with `stringResource(Res.string.mute_selected)` / `stringResource(Res.string.unmute_selected)`.

### MSG-T030: Add missing composable tests [ ]

- **Gap**: No tests for `ContactItem`, `ShareScreen`, `ReactionRow`, `ReactionDialog`, `QuickChatRow`, `MessageBubble` composables.
- **Recommended**: Add `ContactItemTest.kt` covering unread badge display, mute icon, selection states. Add `ShareScreenTest.kt` covering contact selection and send flow. Add `ReactionRowTest.kt` covering emoji grouping and add-reaction button.
- **Priority**: Low — existing ViewModel tests cover core logic; composable tests are incremental improvement.

---

## Summary

| Status | Count | Description |
|--------|-------|-------------|
| ✅ Completed | 28 | All existing implementation tasks |
| ⬜ Gap | 2 | 1 resource discipline fix, 1 test coverage gap |
| **Total** | **30** | |

