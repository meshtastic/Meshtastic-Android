# Specification: Desktop UX Enhancements

## Goal
To implement native desktop behaviors like a system tray, notifications, a menu bar, and persistent window state for the Compose Multiplatform Desktop app.

## Requirements
1. **System Tray & Notifications**: The app should show a tray icon with a basic context menu ("Open", "Settings", "Quit"). It should support a "Minimize to Tray" flow rather than exiting immediately when closed. Notifications should be dispatchable via `TrayState` for key mesh events.
2. **Window State Persistence**: The app should remember its last window size, position, and maximized state across launches.
3. **Menu Bar**: A native MenuBar (File, Edit, View, Window, Help) should provide standard navigation and controls.
4. **Keyboard Shortcuts**: Common actions should be bound to standard native keyboard shortcuts (e.g. `Cmd/Ctrl+,` for Settings).