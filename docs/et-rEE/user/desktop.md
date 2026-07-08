---
title: Desktop App
parent: User Guide
nav_order: 14
last_updated: 2026-07-07
description: Meshtastic arvuti rakendus pakub samu võrgusuhtluse funktsioone Linuxis, macOS-is ja Windowsis.
aliases:
  - töölaud
  - linux
  - macos
  - windows
  - jvm
---

# Desktop App

Meshtastic arvuti rakendus jagab oma põhikoodibaasi Androidiga Kotlin Multiplatformi kaudu. Most features work identically on Linux, macOS, and Windows.

## Installation

### Linux

- Download the `.deb` or `.AppImage` package from the releases page
- Or build from source using `./gradlew :desktopApp:run`

### macOS

- Download the `.dmg` package from releases
- Or build from source

### Windows

- Download the `.msi` installer from releases
- Or build from source

## Connecting Your Radio

### USB port (esmane)

The most reliable connection method on Desktop:

1. Ühenda oma Meshtastic raadio USB kaabliga.
2. Rakendus peaks jadapordi automaatselt tuvastama.
3. Kui seda ei tuvastata, vali menüüst Ühenda õige jadapordiga.

### TCP/IP

For network-connected radios:

1. Sisesta raadio IP-aadress ja port (vaikimisi: 4403).
2. Klõpsa **Ühenda**.

### Sinihammas (BLE)

Bluetooth Low Energy is supported on Desktop via the [Kable](https://github.com/JuulLabs/kable) library:

1. Ensure your system has a Bluetooth adapter.
2. Rakendus otsib automaatselt lähedalasuvaid Meshtastic raadioid.
3. Select your device from the Connect screen.

## Feature Parity

| Feature                                      | Android | Desktop | Sõnumid                                                                    |
| -------------------------------------------- | ------- | ------- | -------------------------------------------------------------------------- |
| Messaging                                    | ✓       | ✓       | Full parity                                                                |
| Node List                                    | ✓       | ✓       | Full parity                                                                |
| Kaart                                        | ✓       | ◐       | Map tab exists on desktop, but the interactive map view is Android-only    |
| Sätted                                       | ✓       | ✓       | Full parity                                                                |
| Sinihammas (BLE)          | ✓       | ✓       | Via Kable on desktop                                                       |
| Püsivara uuendus                             | ✓       | ◐       | Desktop supports in-app USB firmware update; BLE/Wi-Fi OTA is Android-only |
| Notifications                                | ✓       | ✓       | Native OS notifications                                                    |
| Widgets                                      | ✓       | ✗       | Android-only                                                               |
| Android auto                                 | ✓       | ✗       | Android-only — not available on Desktop or iOS                             |
| AI Assistant (Chirpy)     | ✓\*     | ✗       | Google flavor Android only                                                 |
| App Functions (system AI) | ✓†      | ✗       | Google flavor Android only                                                 |

\*Chirpy AI requires Android 14+ on Google flavor builds with supported hardware.

†App Functions exposes app actions to the Android system AI on Google flavor builds. See [App Functions](app-functions).

## UI Differences

The Desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### Keyboard Shortcuts

All shortcuts use the **Meta** key — that's ⌘ (Command) on macOS and the Super / Windows key on Linux and Windows. (`Ctrl` is not bound.)

| Shortcut   | Action                 |
| ---------- | ---------------------- |
| **Meta+Q** | Quit the application   |
| **Meta+,** | Ava sätted             |
| **Meta+1** | Switch to Messages tab |
| **Meta+2** | Switch to Nodes tab    |
| **Meta+3** | Switch to Map tab      |
| **Meta+4** | Switch to Connect tab  |
| **Meta+/** | Open About             |

### Window & System Tray

- **Window resizing** — responsive layout adapts to window dimensions
- **System tray** — minimize to system tray for background mesh operation
- **Salvemenüü** – paremklõpsa salveikoonil akna kuvamiseks või sulgemiseks
- **Hiire interaktsioon** — hõljumisseisundid ja standardne töölaua navigeerimine

### Notification Preferences

The Desktop app provides in-app toggles for controlling which notifications are shown — messages, new nodes, and low battery alerts. Access these from **Settings → Notifications** within the app.

## Built-in Documentation Browser

The Desktop app includes a built-in documentation browser for quick access to help content without leaving the application.

![Docs browser with table of contents](../../assets/screenshots/docs-browser_toc.png)

The browser supports full-text search across all documentation:

![Searching the docs browser](../../assets/screenshots/docs-browser_search.png)

Individual doc pages render with full formatting:

![A documentation page](../../assets/screenshots/docs-browser_page.png)

## Building from Source

```bash
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

Requirements:

- JDK 21
- No Android SDK required for desktop-only builds

## Known Limitations

- Firmware updates over the air (BLE/Wi-Fi) are Android-only; on desktop, use the in-app USB update or the [Web Flasher](https://flasher.meshtastic.org)
- The interactive map view is Android-only — the Map tab is present but does not render a map on desktop
- Some Android-specific features (widgets, specific notification channels) are unavailable
- Performance may vary on low-spec hardware running Compose Desktop
- Lauaarvutid ei toeta veel BLE liitmist (sidumine toimib ilma ühendamiseta)

## Related Topics

- [Connections](connections) — connection methods overview
- [Firmware Updates](firmware) — in-app USB update on desktop, or the [Web Flasher](https://flasher.meshtastic.org)

---

