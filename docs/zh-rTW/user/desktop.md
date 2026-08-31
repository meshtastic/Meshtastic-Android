---
title: 桌面版應用程式
parent: 使用者指南
nav_order: 14
last_updated: 2026-08-30
description: 在 Linux、macOS 及 Windows 上安裝並使用 Meshtastic 桌面版應用程式——涵蓋連線方式、功能對等性與鍵盤快速鍵。
aliases:
  - 桌面
  - linux
  - macos
  - windows
  - jvm
---

# 桌面版應用程式

This page covers installing the Meshtastic desktop app, connecting a radio, and how it differs from Android. The desktop app shares its core codebase with Android via Kotlin Multiplatform, so most features work identically across Linux, macOS, and Windows.

## 安裝

### Linux

- Download the `.deb`, `.rpm`, or `.AppImage` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases)
- Or install from Flathub: `flatpak install flathub org.meshtastic.MeshtasticDesktop`
- 或使用 ./gradlew :desktopApp:run 從原始碼自行建置

### macOS

- Download the `.dmg` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases)
- 或從原始碼自行建置

### Windows

- Download the `.msi` or `.exe` installer from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases)
- 或從原始碼自行建置

## 連接您的無線電裝置

### USB 序列埠（主要方式）

The most reliable connection method on desktop:

Connect your radio via USB. The app detects the serial port automatically; if it doesn't, select the port from the Connect menu.

### TCP/IP

若使用網路連線的無線電裝置：

1. 輸入無線電裝置的 IP 位址與連接埠（預設：4403）。
2. 點選「連線」。

### 藍牙（BLE）

Bluetooth Low Energy is supported on desktop via the [Kable](https://github.com/JuulLabs/kable) library:

1. 請確認您的系統配備藍牙介面卡。 應用程式將自動掃描附近的 Meshtastic 無線電裝置。
2. Select your radio from the Connect screen.

## 功能對等性

| 功能                                                    | Android | 桌面版 | 備註                                                                                                                                                                                                    |
| ----------------------------------------------------- | ------- | --- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 訊息傳送                                                  | ✓       | ✓   | 完全對等                                                                                                                                                                                                  |
| 節點清單                                                  | ✓       | ✓   | 完全對等                                                                                                                                                                                                  |
| 地圖                                                    | ✓       | ✓   | Interactive MapLibre map, with base map and overlay pickers and custom tile sources. No offline downloads or local `.mbtiles` archives                                                |
| Map layers (`.kml`/`.kmz`/GeoJSON) | ✓       | ✓   | Same layer store and sheet as Android; imported files draw on the desktop map                                                                                                                         |
| Site Planner                                          | ✓       | ✓\* | \*Opens in your browser on desktop; the estimate is not drawn on the desktop map                                                                                                                      |
| 設定                                                    | ✓       | ✓   | 完全對等                                                                                                                                                                                                  |
| 藍牙（BLE）                                               | ✓       | ✓   | 桌面版透過 Kable 支援                                                                                                                                                                                        |
| 韌體更新                                                  | ✓       | ✓   | In-app USB, BLE, and Wi-Fi (ESP32) update work the same as Android. The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only |
| 通知                                                    | ✓       | ✓   | 原生作業系統通知                                                                                                                                                                                              |
| 小工具                                                   | ✓       | ✗   | 僅限 Android                                                                                                                                                                                            |
| AI 助理（Chirpy）                                         | ✓\*     | ✗   | 僅限 Google 版 Android                                                                                                                                                                                   |
| App Functions (system AI)          | ✓†      | ✗   | 僅限 Google 版 Android                                                                                                                                                                                   |

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
- **System tray** — closing the window minimizes to the system tray for background mesh operation. On a desktop environment with no tray, there is nowhere to minimize to, so closing quits the app instead
- 匣列選單 — 在系統匣圖示上按右鍵，可顯示視窗或結束程式
- 滑鼠互動 — 支援停駐狀態與標準桌面導覽操作

### 通知偏好設定

The desktop app provides in-app toggles for controlling which notifications are shown. Find them in the **App Notifications** section of the Settings screen: **Direct message notifications**, **New node notifications**, and **Low battery notifications**.

## 內建文件瀏覽器

The desktop app includes a built-in documentation browser for quick access to help content without leaving the app.

![Docs browser with table of contents](../../assets/screenshots/docs-browser_toc.png)

瀏覽器支援跨所有文件的全文搜尋：

![Searching the docs browser](../../assets/screenshots/docs-browser_search.png)

各文件頁面以完整格式呈現：

![A documentation page](../../assets/screenshots/docs-browser_page.png)

## 從原始碼建置

```bash
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

需求：

- JDK 25 (Gradle can provision the toolchain itself via foojay)
- 純桌面版建置不需要 Android SDK

## 已知限制

- Offline tile downloads and local `.mbtiles` archives are not available on desktop.
- `.kml`/`.kmz`/GeoJSON layer import works — see
  [Map & Waypoints](map-and-waypoints#map-layers). Site Planner opens in your browser
  rather than in the app; to bring its coverage estimate onto the map, click the transmitter pin
  in the browser and use the planner's GeoJSON export, then add the file as a layer — not the KML
  export, which is a ground-overlay image this map cannot draw. Custom network tile sources work
  too — see [Map & Waypoints](map-and-waypoints#adding-your-own-tile-source)
- The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only. The
  desktop app still shows the option, but it cannot complete there
- 部分 Android 專屬功能（小工具、特定通知頻道）無法使用
- 在低規格硬體上執行 Compose Desktop 時，效能可能有所不同
- 桌面版尚不支援 BLE 綁定（配對功能可在不綁定的情況下正常使用）

## 相關主題

- 〔連線〕(connections) — 連線方式概覽
- [Firmware Updates](firmware) — in-app USB, BLE, and Wi-Fi update all work the same as on Android
- [Map & Waypoints](map-and-waypoints) — base maps, layers, custom tile sources, and what the desktop map does not do
