---
title: Настольное приложение
parent: Руководство пользователя
nav_order: 14
last_updated: 2026-07-07
description: Установка и использование приложения Meshtastic Desktop на Linux, macOS и Windows — подключения, функционал и сочетания клавиш.
aliases:
  - desktop
  - linux
  - macos
  - windows
  - jvm
---

# Настольное приложение

Настольное приложение Meshtastic использует общий код с Android через Kotlin Multiplatform. Большинство функций работают одинаково на Linux, macOS и Windows.

## Установка

### Linux

- Скачайте пакет `.deb` или `.AppImage` со страницы релизов
- Or build from source using `./gradlew :desktopApp:run`

### macOS

- Скачайте пакет `.dmg` из релизов
- Или соберите из исходных кодов

### Windows

- Download the `.msi` installer from releases
- Или соберите из исходных кодов

## Подключение твоей радиостанции

### Последовательная USB (Основной)

Наиболее надежный способ подключения к компьютеру:

1. Connect your Meshtastic radio via USB cable.
2. The app should detect the serial port automatically.
3. If not detected, select the correct serial port from the Connect menu.

### TCP/IP

For network-connected radios:

1. Enter the radio's IP address and port (default: 4403).
2. Click **Connect**.

### Bluetooth (BLE)

Bluetooth Low Energy is supported on Desktop via the [Kable](https://github.com/JuulLabs/kable) library:

1. Ensure your system has a Bluetooth adapter.
2. The app scans for nearby Meshtastic radios automatically.
3. Select your device from the Connect screen.

## Feature Parity

| Feature                                      | Android | Desktop | Заметки                                                                                   |
| -------------------------------------------- | ------- | ------- | ----------------------------------------------------------------------------------------- |
| Обмен сообщениями                            | ✓       | ✓       | Полное равенство                                                                          |
| Список узлов                                 | ✓       | ✓       | Полное равенство                                                                          |
| Карта                                        | ✓       | ◐       | Map tab exists on desktop, but the interactive map view is Android-only                   |
| Настройки                                    | ✓       | ✓       | Полное равенство                                                                          |
| Bluetooth (BLE)           | ✓       | ✓       | Via Kable on desktop                                                                      |
| Обновление прошивки                          | ✓       | ✓       | In-app USB, BLE, and Wi-Fi (ESP32) update all work the same as Android |
| Notifications                                | ✓       | ✓       | Native OS notifications                                                                   |
| Виджеты                                      | ✓       | ✗       | Android-only                                                                              |
| Android Auto                                 | ✓       | ✗       | Android-only — not available on Desktop or iOS                                            |
| AI Assistant (Chirpy)     | ✓\*     | ✗       | Google flavor Android only                                                                |
| App Functions (system AI) | ✓†      | ✗       | Google flavor Android only                                                                |

\*Chirpy AI requires Android 14+ on Google flavor builds with supported hardware.

†App Functions exposes app actions to the Android system AI on Google flavor builds. See [App Functions](app-functions).

## UI Differences

The Desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### Keyboard Shortcuts

All shortcuts use the **Meta** key — that's ⌘ (Command) on macOS and the Super / Windows key on Linux and Windows. (`Ctrl` is not bound.)

| Shortcut   | Action                 |
| ---------- | ---------------------- |
| **Meta+Q** | Quit the application   |
| **Meta+,** | Open Settings          |
| **Meta+1** | Switch to Messages tab |
| **Meta+2** | Switch to Nodes tab    |
| **Meta+3** | Switch to Map tab      |
| **Meta+4** | Switch to Connect tab  |
| **Meta+/** | Open About             |

### Window & System Tray

- **Window resizing** — responsive layout adapts to window dimensions
- **System tray** — minimize to system tray for background mesh operation
- **Tray menu** — right-click the tray icon to show window or quit
- **Mouse interaction** — hover states and standard desktop navigation

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

- The interactive map view is Android-only — the Map tab is present but does not render a map on desktop
- Some Android-specific features (widgets, specific notification channels) are unavailable
- Performance may vary on low-spec hardware running Compose Desktop
- BLE bonding is not yet supported on desktop (pairing works without bonding)

## Related Topics

- [Connections](connections) — connection methods overview
- [Firmware Updates](firmware) — USB, BLE, and Wi-Fi update all work the same as on Android

---

