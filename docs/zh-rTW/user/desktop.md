---
title: 桌面版應用程式
nav_order: 14
last_updated: 2026-10-21
description: 在 Linux、macOS 及 Windows 上安裝並使用 Meshtastic 桌面版應用程式——涵蓋連線方式、功能對等性與鍵盤快速鍵。
aliases:
  - 桌面
  - linux
  - macos
  - windows
  - jvm
---

# 桌面版應用程式

This page covers installing the Meshtastic desktop app, connecting a node, and how it differs from Android. The desktop app shares its core codebase with Android via Kotlin Multiplatform, so most features work identically across Linux, macOS, and Windows.

## 安裝

### Linux

- Download the `.deb`, `.rpm`, or `.AppImage` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Each comes in an x86_64 and an arm64 (aarch64) build.
- The AppImage carries its own update information, so AppImageUpdate and other zsync-based updaters can update it in place to the latest production release.
- Or install from Flathub: `flatpak install flathub org.meshtastic.MeshtasticDesktop`
- 或使用 ./gradlew :desktopApp:run 從原始碼自行建置

### macOS

- Download the `.dmg` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). It is built for Apple silicon only.
- 或從原始碼自行建置

### Windows

- Download the `.msi` or `.exe` installer from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Both are x64 builds.
- 或從原始碼自行建置

## 連接您的無線電裝置

All three transports live on the **Connect** tab, the same as on Android. Pick one with the transport selector. See [Connections](connections) for the full walkthrough.

### USB 序列埠（主要方式）

The most reliable connection method on desktop:

Connect your node by USB, select **USB** in the transport selector, and click the node's serial port in the list. The list refreshes on its own as ports appear and disappear.

### TCP/IP

For network-connected nodes, select **Network** in the transport selector, then either:

1. Turn on **Scan for network devices** and click a node the scan finds, or
2. Click **Add device manually…** and enter the node's IP address or hostname and port (default: `4403`).

### 藍牙（BLE）

Bluetooth Low Energy is supported on desktop via the [Kable](https://github.com/JuulLabs/kable) library:

1. Ensure your system has a Bluetooth adapter, and select **Bluetooth** in the transport selector.
2. Click **Scan for Bluetooth devices**, then select your node from the list.

## 功能對等性

| 功能                                                    | Android | 桌面版 | 備註                                                                                                                                                                                                                                              |
| ----------------------------------------------------- | ------- | --- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 訊息傳送                                                  | ✓       | ✓   | 完全對等                                                                                                                                                                                                                                            |
| 節點清單                                                  | ✓       | ✓   | 完全對等                                                                                                                                                                                                                                            |
| 地圖                                                    | ✓       | ✓   | Interactive MapLibre map, with base map and overlay pickers and custom tile sources. Offline terrain (hillshade and contours) can be downloaded; offline base-map packs and local `.mbtiles` archives cannot |
| Map layers (`.kml`/`.kmz`/GeoJSON) | ✓       | ✓   | Same layer store and sheet as Android; imported files draw on the desktop map                                                                                                                                                                   |
| Site Planner                                          | ✓       | ✓   | Computes the coverage estimate in the app and adds it to the map as a layer, as on Android                                                                                                                                                      |
| 設定                                                    | ✓       | ✓   | 完全對等                                                                                                                                                                                                                                            |
| 藍牙（BLE）                                               | ✓       | ✓   | 桌面版透過 Kable 支援                                                                                                                                                                                                                                  |
| 韌體更新                                                  | ✓       | ✓   | In-app USB, BLE, and Wi-Fi (ESP32) update work the same as Android. The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only                                           |
| 通知                                                    | ✓       | ✓   | 原生作業系統通知                                                                                                                                                                                                                                        |
| 小工具                                                   | ✓       | ✗   | 僅限 Android                                                                                                                                                                                                                                      |
| AI 助理（Chirpy）                                         | ✓\*     | ✗   | 僅限 Google 版 Android                                                                                                                                                                                                                             |
| App Functions (system AI)          | ✓†      | ✗   | 僅限 Google 版 Android                                                                                                                                                                                                                             |

\*Chirpy AI 需要 Google 版 Android 14 以上版本，且須搭配支援的硬體。

†App Functions exposes app actions to the Android system AI on Google flavor builds. See [App Functions](app-functions).

## 介面差異

The desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### 鍵盤快速鍵

Shortcuts use **⌘** (Command) on macOS and **Ctrl** on Windows and Linux. (The Super / Windows key is not bound.)

| 快速鍵          | 動作           |
| ------------ | ------------ |
| **⌘/Ctrl+Q** | Quit the app |
| **⌘/Ctrl+,** | 開啟設定         |
| **⌘/Ctrl+1** | 切換至訊息頁籤      |
| **⌘/Ctrl+2** | 切換至節點頁籤      |
| **⌘/Ctrl+3** | 切換至地圖頁籤      |
| **⌘/Ctrl+4** | 切換至連線頁籤      |
| **⌘/Ctrl+/** | Open About   |

### 視窗與系統匣

- 調整視窗大小 — 響應式版面配置可依視窗尺寸自動調整
- **System tray** — closing the window minimizes to the system tray for background mesh operation, provided the tray icon can be clicked to bring the window back. On Linux that needs a desktop environment with a StatusNotifierItem tray. Where there is no such tray, closing quits the app instead
- **Tray menu** — right-click the tray icon for **Show Meshtastic** or **Quit**
- 滑鼠互動 — 支援停駐狀態與標準桌面導覽操作

### 通知偏好設定

The desktop app provides in-app toggles for controlling which notifications are shown. Find them in the **App Notifications** section of the Settings screen: **Direct message notifications**, **New node notifications**, **Low battery notifications**, **Reaction notifications**, and **Waypoint notifications**.

## 內建文件瀏覽器

The desktop app includes a built-in documentation browser for quick access to help content without leaving the app.

![Docs browser with table of contents](../../assets/screenshots/docs-browser_toc.png)

The browser searches page titles and keywords across all documentation:

![Searching the docs browser](../../assets/screenshots/docs-browser_search.png)

各文件頁面以完整格式呈現：

![A documentation page](../../assets/screenshots/docs-browser_page.png)

## 從原始碼建置

```shell
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

需求：

- JDK 25 to run Gradle (Gradle can provision it itself via foojay)
- JetBrains Runtime (JBR) 25 installed where Gradle's toolchain detection finds it. The desktop app compiles against it and bundles it, and foojay does not offer it, so Gradle cannot download it
- 純桌面版建置不需要 Android SDK

## 已知限制

- Offline base-map downloads and local `.mbtiles` archives are not available on desktop. Offline terrain is — see [Map & Waypoints](map-and-waypoints).
- `.kml`/`.kmz`/GeoJSON layer import works — see
  [Map & Waypoints](map-and-waypoints#map-layers). Custom network tile sources work
  too — see [Map & Waypoints](map-and-waypoints#adding-your-own-tile-source)
- The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only, and the
  desktop app does not offer it. Use the [Web Flasher](https://flasher.meshtastic.org) instead
- 部分 Android 專屬功能（小工具、特定通知頻道）無法使用
- 在低規格硬體上執行 Compose Desktop 時，效能可能有所不同
- BLE bonding is not supported on desktop (pairing works without bonding)

## 相關主題

- 〔連線〕(connections) — 連線方式概覽
- [Firmware Updates](firmware) — in-app USB, BLE, and Wi-Fi update all work the same as on Android
- [Map & Waypoints](map-and-waypoints) — base maps, layers, custom tile sources, and what the desktop map does not do
