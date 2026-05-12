# Implementation Plan: Messaging & Contacts

**Branch**: `004-messaging` | **Date**: 2026-07-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-messaging/spec.md`
**Status**: Migrated — all implementation complete, plan reverse-engineered from existing code.

## Summary

The Messaging & Contacts feature provides the complete chat experience for Meshtastic-Android: paginated message threads with emoji reactions, reply threading, Quick Chat shortcuts, unread tracking with auto-scroll, and a paginated contact list with batch operations. Implementation uses Compose Multiplatform, Paging 3 KMP, Koin DI, and Navigation 3 — all in `commonMain` with a single `androidMain` file for WorkManager message queuing.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Compose Multiplatform, Material 3 Adaptive, Koin 4.2+ (K2 Compiler Plugin), Room KMP, DataStore KMP, AndroidX Paging 3 KMP, Turbine (test), Mokkery (test)  
**Storage**: Room KMP for messages/contacts/quick-chat entities; DataStore KMP for UI preferences (show quick chat, emoji frequency, homoglyph encoding)  
**Testing**: KMP `allTests` for `feature:messaging` — 6 test files, ~695 LOC  
**Target Platform**: Android, Desktop (JVM), iOS — all via `commonMain`  
**Performance Goals**: 60fps scrolling on paginated message lists; O(1) node lookup via pre-calculated map  
**Constraints**: All UI in `commonMain`; no `java.*`/`android.*` in common; CMP float pre-formatting via `NumberFormatter.format()`; `safeLaunch`/`ioDispatcher` for coroutines  
**Scale/Scope**: 25 commonMain files (~5,253 LOC), 1 androidMain file (~44 LOC), 6 test files (~695 LOC)

## Constitution Check

*GATE: All checks pass — existing production code reviewed against Constitution v1.2.2.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | All logic in `commonMain`. Only `WorkManagerMessageQueue.kt` in `androidMain` uses `android.*`/`androidx.work` — correctly scoped to platform layer. |
| II. Zero Lint Tolerance | ✅ PASS | `detekt-baseline.xml` present in module. Suppression annotations used sparingly (`LongMethod`, `CyclomaticComplexMethod`, `TooManyFunctions`). |
| III. Compose Multiplatform UI | ✅ PASS | All UI uses CMP composables. `NumberFormatter.format()` used in `Contacts.kt` for mute duration. Navigation 3 `NavKey` routes in `ContactsNavigation.kt`. |
| IV. Privacy First | ✅ PASS | No PII/location/key logging. Message content not logged. Proto submodule read-only. |
| V. Design Standards Compliance | ✅ PASS | M3 components used throughout. Accessibility semantics on `MessageItem` (`a11y_message_from`). Content descriptions on all interactive icons. |
| VI. Verify Before Push | ✅ PASS | 6 test files covering ViewModels, utility functions, and composable rendering. Tests use `allTests` target. |
| VII. Coroutine Safety | ✅ PASS | All ViewModel coroutines use `safeLaunch {}` with `ioDispatcher`. No raw `runCatching {}` or `Dispatchers.IO` in common code. |
| VIII. Resource Discipline | ✅ PASS | All strings via `stringResource(Res.string.*)`. All icons from `MeshtasticIcons`. **Minor gap**: `SelectionToolbar` has 2 hardcoded English strings for mute/unmute content descriptions. |
| IX. Branch & Scope Hygiene | ✅ PASS | Feature module cleanly scoped to `feature/messaging`. DI via component scan. Routes defined in `ContactsNavigation.kt`. |

**Gate Result**: ✅ All principles satisfied (1 minor resource discipline gap noted)

## Project Structure

### Documentation (this feature)

```text
specs/004-messaging/
├── spec.md              # Feature specification (migrated)
├── plan.md              # This file (migrated)
└── tasks.md             # Task breakdown (migrated)
```

### Source Code (repository root)

```text
feature/messaging/
├── src/commonMain/kotlin/org/meshtastic/feature/messaging/
│   ├── Message.kt                         ← Main MessageScreen composable + MessageInput
│   ├── MessageViewModel.kt                ← ViewModel: send, react, delete, unread, filter, paging
│   ├── MessageListPaged.kt                ← Paginated message list with auto-scroll + unread divider
│   ├── MessageScreenEvent.kt              ← Sealed interface for UI events
│   ├── DeliveryInfoDialog.kt              ← Delivery status dialog
│   ├── QuickChat.kt                       ← Quick Chat management screen + edit dialog
│   ├── QuickChatViewModel.kt              ← Quick Chat CRUD ViewModel
│   ├── QuickChatPreviews.kt               ← Preview composables for Quick Chat
│   ├── UnreadUiDefaults.kt                ← Constants for unread UX behavior
│   ├── component/
│   │   ├── MessageItem.kt                 ← Message bubble with actions bottom sheet
│   │   ├── MessageItemPreviews.kt         ← Preview composables
│   │   ├── MessageActions.kt              ← Reaction + Reply + Status icon buttons
│   │   ├── MessageActionsBottomSheet.kt   ← Full actions sheet (react, reply, copy, select, delete)
│   │   ├── MessageBubble.kt               ← Shape logic for grouped bubbles
│   │   ├── MessageScreenComponents.kt     ← Toolbar, FAB, reply snippet, delete dialog, quick chat row
│   │   ├── MessageStatusIcon.kt           ← Delivery status icon composable
│   │   ├── Reaction.kt                    ← ReactionItem, ReactionRow, ReactionDialog
│   │   └── ReactionPreviews.kt            ← Preview composables
│   ├── navigation/
│   │   └── ContactsNavigation.kt          ← Navigation 3 graph (routes, entry providers)
│   ├── ui/contact/
│   │   ├── AdaptiveContactsScreen.kt      ← Adaptive wrapper for contacts
│   │   ├── Contacts.kt                    ← ContactsScreen + selection toolbar + paged list
│   │   ├── ContactsViewModel.kt           ← Contacts logic: paged, mute, delete, mark-read
│   │   └── ContactItem.kt                ← Contact card composable
│   ├── ui/sharing/
│   │   └── Share.kt                       ← Share-to-contact screen
│   └── di/
│       └── FeatureMessagingModule.kt      ← Koin module (component scan)
├── src/androidMain/kotlin/org/meshtastic/feature/messaging/worker/
│   └── WorkManagerMessageQueue.kt         ← Android WorkManager message queue
└── src/commonTest/kotlin/org/meshtastic/feature/messaging/
    ├── MessageViewModelTest.kt            ← 10 tests: init, title, connection, send, react, delete, unread
    ├── QuickChatViewModelTest.kt          ← 3 tests: init, flow updates, add action
    ├── HomoglyphCharacterTransformTest.kt ← 5 tests: homoglyph encoding optimization
    ├── UnreadUiDefaultsTest.kt            ← 1 test: default constant values
    ├── component/MessageItemTest.kt       ← 3 tests: MQTT icon, accessibility semantics
    └── ui/contact/ContactsViewModelTest.kt ← 2 tests: init, unread count flow
```

**Structure Decision**: The feature follows the standard KMP module layout. All UI and business logic in `commonMain`. The single `androidMain` file (`WorkManagerMessageQueue`) provides reliable background message sending via WorkManager — this is a legitimate platform concern that cannot be in common code.

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/messaging` (commonMain) | Existing | 25 | Low — stable, tested |
| `feature/messaging` (androidMain) | Existing | 1 | Low — thin WorkManager wrapper |
| `feature/messaging` (commonTest) | Existing | 6 | Low — comprehensive ViewModel tests |
| `core/model` | Dependency | N/A | None — read-only usage |
| `core/repository` | Dependency | N/A | None — uses `PacketRepository`, `NodeRepository`, `ServiceRepository` |
| `core/database` | Dependency | N/A | None — uses `QuickChatAction` entity |
| `core/resources` | Dependency | N/A | None — uses string resources |
| `core/ui` | Dependency | N/A | None — uses shared components (`MeshtasticDialog`, `NodeChip`, `AutoLinkText`, etc.) |

## Integration Points

- **Navigation**: Routes defined in `ContactsNavigation.kt` using Navigation 3 `NavKey` typed routes (`ContactsRoute.ContactsGraph`, `.Messages`, `.Share`, `.QuickChat`). Integrated with `ListDetailSceneStrategy` for adaptive layouts.
- **DI**: `FeatureMessagingModule` uses Koin `@ComponentScan` to auto-discover `@KoinViewModel`-annotated ViewModels.
- **Repositories**: `PacketRepository` (messages, contacts, unread counts), `NodeRepository` (node info), `ServiceRepository` (connection state, service actions), `RadioConfigRepository` (channels), `QuickChatActionRepository` (quick chat CRUD).
- **Preferences**: `UiPrefs` (show quick chat), `CustomEmojiPrefs` (emoji frequency), `HomoglyphPrefs` (encoding toggle).
- **Notifications**: `NotificationManager.cancel()` called when all unread messages are cleared.

## Design Constraints

- All UI lives in `commonMain` — not platform-specific
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- Error handling uses `safeCatching {}` not `runCatching {}`
- Dispatchers via `org.meshtastic.core.common.util.ioDispatcher`
- Float values must be pre-formatted with `NumberFormatter.format()` (CMP constraint)
- Paging uses AndroidX Paging 3 KMP with `cachedIn(viewModelScope)`
- Message byte limit is 200 bytes (UTF-8 encoded) — enforced at UI level

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Hardcoded strings in SelectionToolbar | Low | Low | Fix mute/unmute `contentDescription` to use `stringResource()` |
| Missing composable tests for ContactItem, ShareScreen | Low | Medium | Add UI tests for untested composables |
| Pagination edge case with Select All | Low | Low | Documented limitation — only selects loaded items |

## Phase Alignment with Tasks

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Data Layer | Message/contact repositories + DI | MSG-T001–MSG-T003 | None |
| 2. Message Thread | Core chat screen + message list | MSG-T004–MSG-T014 | Phase 1 |
| 3. Contacts | Contact list + management | MSG-T015–MSG-T022 | Phase 1 |
| 4. Polish & Test | Quick chat, share, tests | MSG-T023–MSG-T030 | Phases 2–3 |

### Critical Path

```
Phase 1 (Data) → Phase 2 (Messages) → Phase 3 (Contacts) → Phase 4 (Polish)
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |

