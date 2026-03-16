# Implementation Plan: Desktop UX Enhancements

## Phase 1: Tray & Notifications (Current Focus)
- [x] Add `isAppVisible` state to `Main.kt`.
- [x] Introduce `rememberTrayState()` and the `Tray` composable.
- [x] Update `Window` `onCloseRequest` to toggle visibility instead of exiting the app.
- [x] Add a `DesktopNotificationService` interface and implementation using `TrayState`.

## Phase 2: Window State Persistence
- [x] Create `DesktopPreferencesDataSource` via DataStore.
- [x] Intercept window bounds changes and write to preferences.
- [x] Read preferences on startup to initialize `rememberWindowState(...)`.

## Phase 3: Menu Bar & Shortcuts
- [x] Integrate the `MenuBar` composable into the `Window`.
- [x] Implement global application shortcuts.