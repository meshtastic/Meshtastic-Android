# Implementation Plan: Wire Up Notifications

## Phase 1: Shared Abstraction (commonMain) [checkpoint: 930ce02]
- [x] Task: Define `NotificationManager` interface in `core:service/src/commonMain` 4f2107d
    - [x] Create `Notification` data model (title, message, type)
    - [x] Define `dispatch(notification: Notification)` method
- [x] Task: Create `NotificationPreferencesDataSource` using DataStore in `core:prefs` 346c2a4
    - [x] Define boolean preferences for categories (e.g., Messages, Node Events)
- [x] Task: Conductor - User Manual Verification 'Phase 1: Shared Abstraction (commonMain)' (Protocol in workflow.md)

## Phase 2: Migrate Android Implementation (androidMain) [checkpoint: 1eb3cb0]
- [x] Task: Audit existing Android notifications 930ce02
    - [x] Locate current implementation for local push notifications
    - [x] Analyze triggers and UX (channels, icons, sounds)
- [x] Task: Implement `AndroidNotificationManager` 31c2a1e
    - [x] Adapt existing Android notification code to the new `NotificationManager` interface
    - [x] Inject `Context` and `NotificationPreferencesDataSource`
    - [x] Respect user notification preferences
- [x] Task: Wire `AndroidNotificationManager` into Koin DI 31c2a1e
- [x] Task: Replace old Android notification calls with the new unified interface 81fd10b
- [x] Task: Conductor - User Manual Verification 'Phase 2: Migrate Android Implementation (androidMain)' (Protocol in workflow.md)

## Phase 3: Desktop Implementation (desktop)
- [ ] Task: Implement `DesktopNotificationManager`
    - [ ] Inject `TrayState` and `NotificationPreferencesDataSource`
    - [ ] Delegate `dispatch()` to `TrayState.sendNotification()` respecting user preferences
- [ ] Task: Wire `DesktopNotificationManager` into Koin DI
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Desktop Implementation (desktop)' (Protocol in workflow.md)

## Phase 4: UI Preferences Integration
- [ ] Task: Create UI for notification preferences
    - [ ] Add toggles for categories in the Settings screen
- [ ] Task: Conductor - User Manual Verification 'Phase 4: UI Preferences Integration' (Protocol in workflow.md)