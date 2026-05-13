---
title: Desktop App
nav_order: 14
last_updated: 2026-05-12
aliases:
  - desktop
  - linux
  - macos
  - windows
  - jvm
---

# Desktop App

The Meshtastic Desktop application provides the same mesh communication features on Linux, macOS, and Windows.

## Overview

The Desktop app shares its core codebase with the Android app through Kotlin Multiplatform (KMP). Most features work identically across platforms.

## Installation

### Linux

- Download the `.deb` or `.AppImage` package from the releases page
- Or build from source using `./gradlew :desktop:run`

### macOS

- Download the `.dmg` package from releases
- Or build from source

### Windows

- Download the `.msi` installer from releases
- Or build from source

## Connecting Your Radio

### USB Serial (Primary)

The most reliable connection method on Desktop:

1. Connect your Meshtastic radio via USB cable.
2. The app should detect the serial port automatically.
3. If not detected, select the correct serial port from the connections menu.

### TCP/IP

For network-connected radios:

1. Enter the radio's IP address and port (default: 4403).
2. Click **Connect**.

### Bluetooth

> ⚠️ **Note:** Bluetooth is not currently supported on the Desktop app. Use USB or TCP connections.

## Feature Parity

| Feature | Android | Desktop | Notes |
|---------|---------|---------|-------|
| Messaging | ✓ | ✓ | Full parity |
| Node List | ✓ | ✓ | Full parity |
| Map | ✓ | ✓ | Full parity |
| Settings | ✓ | ✓ | Full parity |
| Bluetooth | ✓ | ✗ | USB/TCP on desktop |
| Firmware Update OTA | ✓ | ✗ | Use web flasher |
| Notifications | ✓ | ✓ | Native OS notifications |
| Widgets | ✓ | ✗ | Android-only |
| AI Assistant (Chirpy) | ✓* | ✗ | Google flavor Android only |

*Chirpy AI requires Android 14+ on Google flavor builds with supported hardware.

## UI Differences

The Desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| **⌘Q** / **Ctrl+Q** | Quit the application |
| **⌘,** / **Ctrl+,** | Open Settings |
| **⌘1** / **Ctrl+1** | Switch to Conversations tab |
| **⌘2** / **Ctrl+2** | Switch to Nodes tab |
| **⌘3** / **Ctrl+3** | Switch to Map tab |
| **⌘4** / **Ctrl+4** | Switch to Connections tab |

### Window & System Tray

- **Window resizing** — responsive layout adapts to window dimensions
- **System tray** — minimize to system tray for background mesh operation
- **Tray menu** — right-click the tray icon to show window or quit
- **Mouse interaction** — hover states and standard desktop navigation

### Notification Preferences

The Desktop app provides in-app toggles for controlling which notifications are shown — messages, new nodes, and low battery alerts:

<!-- TODO: Add screenshot settings_notifications.png -->

## Building from Source

```bash
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
git submodule update --init
./gradlew :desktop:run
```

Requirements:
- JDK 21
- No Android SDK required for desktop-only builds

## Known Limitations

- No Bluetooth support
- No OTA firmware updates (use web flasher)
- Some Android-specific features (widgets, specific notification channels) are unavailable
- Performance may vary on low-spec hardware running Compose Desktop

---

