---
title: Настольное приложение
nav_order: 14
last_updated: 2026-10-21
description: Установка и использование приложения Meshtastic Desktop на Linux, macOS и Windows — подключения, функционал и сочетания клавиш.
aliases:
  - desktop
  - linux
  - macos
  - windows
  - jvm
---

# Настольное приложение

This page covers installing the Meshtastic desktop app, connecting a node, and how it differs from Android. The desktop app shares its core codebase with Android via Kotlin Multiplatform, so most features work identically across Linux, macOS, and Windows.

## Установка

### Linux

- Download the `.deb`, `.rpm`, or `.AppImage` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Each comes in an x86_64 and an arm64 (aarch64) build.
- The AppImage carries its own update information, so AppImageUpdate and other zsync-based updaters can update it in place to the latest production release.
- Or install from Flathub: `flatpak install flathub org.meshtastic.MeshtasticDesktop`
- Или соберите из исходного кода с помощью `./gradlew :desktopApp:run`

### macOS

- Download the `.dmg` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). It is built for Apple silicon only.
- Или соберите из исходных кодов

### Windows

- Download the `.msi` or `.exe` installer from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Both are x64 builds.
- Или соберите из исходных кодов

## Подключение твоей радиостанции

All three transports live on the **Connect** tab, the same as on Android. Pick one with the transport selector. See [Connections](connections) for the full walkthrough.

### Последовательная USB (Основной)

The most reliable connection method on desktop:

Connect your node by USB, select **USB** in the transport selector, and click the node's serial port in the list. The list refreshes on its own as ports appear and disappear.

### TCP/IP

For network-connected nodes, select **Network** in the transport selector, then either:

1. Turn on **Scan for network devices** and click a node the scan finds, or
2. Click **Add device manually…** and enter the node's IP address or hostname and port (default: `4403`).

### Bluetooth (BLE)

Bluetooth Low Energy is supported on desktop via the [Kable](https://github.com/JuulLabs/kable) library:

1. Ensure your system has a Bluetooth adapter, and select **Bluetooth** in the transport selector.
2. Click **Scan for Bluetooth devices**, then select your node from the list.

## Паритет функций

| Функция                                               | Android | Desktop | Заметки                                                                                                                                                                                                                                         |
| ----------------------------------------------------- | ------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Обмен сообщениями                                     | ✓       | ✓       | Полное равенство                                                                                                                                                                                                                                |
| Список узлов                                          | ✓       | ✓       | Полное равенство                                                                                                                                                                                                                                |
| Карта                                                 | ✓       | ✓       | Interactive MapLibre map, with base map and overlay pickers and custom tile sources. Offline terrain (hillshade and contours) can be downloaded; offline base-map packs and local `.mbtiles` archives cannot |
| Map layers (`.kml`/`.kmz`/GeoJSON) | ✓       | ✓       | Same layer store and sheet as Android; imported files draw on the desktop map                                                                                                                                                                   |
| Планировщик участков                                  | ✓       | ✓       | Computes the coverage estimate in the app and adds it to the map as a layer, as on Android                                                                                                                                                      |
| Настройки                                             | ✓       | ✓       | Полное равенство                                                                                                                                                                                                                                |
| Bluetooth (BLE)                    | ✓       | ✓       | Через Kable в настольном приложении                                                                                                                                                                                                             |
| Обновление прошивки                                   | ✓       | ✓       | In-app USB, BLE, and Wi-Fi (ESP32) update work the same as Android. The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only                                           |
| Уведомления                                           | ✓       | ✓       | Системные уведомления                                                                                                                                                                                                                           |
| Виджеты                                               | ✓       | ✗       | Только Android                                                                                                                                                                                                                                  |
| AI-ассистент (Chirpy)              | ✓\*     | ✗       | Только в Google-версии для Android                                                                                                                                                                                                              |
| Функции приложения (системный ИИ)  | ✓†      | ✗       | Только в Google-версии для Android                                                                                                                                                                                                              |

\*Chirpy AI требует Android 14+ в Google-версии на поддерживаемом оборудовании.

†Функции приложения предоставляют действия приложения системному ИИ Android в Google-версии. См. [Функции приложения](app-functions).

## Различия интерфейса

The desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### Сочетания клавиш

Сочетания клавиш используют ⌘ (Command) на macOS и Ctrl на Windows и Linux. (Клавиша Super / Windows не используется.)

| Сочетание    | Действие                         |
| ------------ | -------------------------------- |
| **⌘/Ctrl+Q** | Quit the app                     |
| **⌘/Ctrl+,** | Открыть настройки                |
| **⌘/Ctrl+1** | Перейти во вкладку "Сообщения"   |
| **⌘/Ctrl+2** | Перейти во вкладку "Узлы"        |
| **⌘/Ctrl+3** | Перейти во вкладку "Карта"       |
| **⌘/Ctrl+4** | Перейти во вкладку "Подключение" |
| **⌘/Ctrl+/** | Открыть "О программе"            |

### Окно и системный трей

- **Изменение размера окна** — адаптивный макет подстраивается под размеры окна
- **System tray** — closing the window minimizes to the system tray for background mesh operation, provided the tray icon can be clicked to bring the window back. On Linux that needs a desktop environment with a StatusNotifierItem tray. Where there is no such tray, closing quits the app instead
- **Tray menu** — right-click the tray icon for **Show Meshtastic** or **Quit**
- **Взаимодействие с мышью** — состояния при наведении и стандартная настольная навигация

### Настройки уведомлений

The desktop app provides in-app toggles for controlling which notifications are shown. Find them in the **App Notifications** section of the Settings screen: **Direct message notifications**, **New node notifications**, **Low battery notifications**, **Reaction notifications**, and **Waypoint notifications**.

## Встроенный браузер документации

The desktop app includes a built-in documentation browser for quick access to help content without leaving the app.

![Браузер документации с оглавлением](../../assets/screenshots/docs-browser_toc.png)

The browser searches page titles and keywords across all documentation:

![Поиск в браузере документации](../../assets/screenshots/docs-browser_search.png)

Отдельные страницы документации отображаются с полным форматированием:

![Страница документации](../../assets/screenshots/docs-browser_page.png)

## Сборка из исходного кода

```shell
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

Требования:

- JDK 25 to run Gradle (Gradle can provision it itself via foojay)
- JetBrains Runtime (JBR) 25 installed where Gradle's toolchain detection finds it. The desktop app compiles against it and bundles it, and foojay does not offer it, so Gradle cannot download it
- Для сборки только настольной версии Android SDK не требуется

## Известные ограничения

- Offline base-map downloads and local `.mbtiles` archives are not available on desktop. Offline terrain is — see [Map & Waypoints](map-and-waypoints).
- `.kml`/`.kmz`/GeoJSON layer import works — see
  [Map & Waypoints](map-and-waypoints#map-layers). Custom network tile sources work
  too — see [Map & Waypoints](map-and-waypoints#adding-your-own-tile-source)
- The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only, and the
  desktop app does not offer it. Use the [Web Flasher](https://flasher.meshtastic.org) instead
- Некоторые специфичные для Android функции (виджеты, отдельные каналы уведомлений) недоступны
- Производительность может варьироваться на маломощном оборудовании при запуске Compose Desktop
- BLE bonding is not supported on desktop (pairing works without bonding)

## Связанные темы

- [Подключения](connections) — обзор методов подключения
- [Firmware Updates](firmware) — in-app USB, BLE, and Wi-Fi update all work the same as on Android
- [Map & Waypoints](map-and-waypoints) — base maps, layers, custom tile sources, and what the desktop map does not do
