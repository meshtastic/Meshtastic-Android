---
title: 桌面版應用程式
parent: 使用者指南
nav_order: 14
last_updated: 2026-07-07
description: 在 Linux、macOS 及 Windows 上安裝並使用 Meshtastic 桌面版應用程式——涵蓋連線方式、功能對等性與鍵盤快速鍵。
aliases:
  - 桌面
  - linux
  - macos
  - windows
  - jvm
---

# 桌面版應用程式

Meshtastic 桌面版應用程式透過 Kotlin Multiplatform 與 Android 版共用核心程式碼庫。 大多數功能在 Linux、macOS 及 Windows 上的運作方式完全相同。

## 安裝

### Linux

- 從發布頁面下載.deb 或.AppImage 安裝套件
- 或使用 ./gradlew :desktopApp:run 從原始碼自行建置

### macOS

- 從發布頁面下載.dmg 安裝套件
- 或從原始碼自行建置

### Windows

- 從發布頁面下載.msi 安裝程式
- 或從原始碼自行建置

## 連接您的無線電裝置

### USB 序列埠（主要方式）

桌面版上最穩定可靠的連線方式：

1. 請使用 USB 傳輸線連接您的 Meshtastic 無線電裝置。
2. 應用程式應會自動偵測序列埠。
3. 若未自動偵測到，請從連線選單中手動選取正確的序列埠。

### TCP/IP

若使用網路連線的無線電裝置：

1. 輸入無線電裝置的 IP 位址與連接埠（預設：4403）。
2. 點選「連線」。

### 藍牙（BLE）

桌面版透過 [Kable](https://github.com/JuulLabs/kable) 函式庫支援藍牙低功耗：

1. 請確認您的系統配備藍牙介面卡。
2. 應用程式將自動掃描附近的 Meshtastic 無線電裝置。
3. 從連線畫面中選取您的裝置。

## 功能對等性

| 功能                                           | Android | 桌面版 | 備註                                                                                        |
| -------------------------------------------- | ------- | --- | ----------------------------------------------------------------------------------------- |
| 訊息傳送                                         | ✓       | ✓   | 完全對等                                                                                      |
| 節點清單                                         | ✓       | ✓   | 完全對等                                                                                      |
| 地圖                                           | ✓       | ◐   | Map tab exists on desktop, but the interactive map view is Android-only                   |
| 設定                                           | ✓       | ✓   | 完全對等                                                                                      |
| 藍牙（BLE）                                      | ✓       | ✓   | 桌面版透過 Kable 支援                                                                            |
| 韌體更新                                         | ✓       | ✓   | In-app USB, BLE, and Wi-Fi (ESP32) update all work the same as Android |
| 通知                                           | ✓       | ✓   | 原生作業系統通知                                                                                  |
| 小工具                                          | ✓       | ✗   | 僅限 Android                                                                                |
| Android Auto                                 | ✓       | ✗   | Android-only — not available on Desktop or iOS                                            |
| AI 助理（Chirpy）                                | ✓\*     | ✗   | 僅限 Google 版 Android                                                                       |
| App Functions (system AI) | ✓†      | ✗   | 僅限 Google 版 Android                                                                       |

\*Chirpy AI 需要 Google 版 Android 14 以上版本，且須搭配支援的硬體。

†App Functions exposes app actions to the Android system AI on Google flavor builds. See [App Functions](app-functions).

## 介面差異

桌面版應用程式採用相同的 Compose Multiplatform 介面，並針對較大螢幕與桌面操作方式進行調整。

### 鍵盤快速鍵

All shortcuts use the **Meta** key — that's ⌘ (Command) on macOS and the Super / Windows key on Linux and Windows. (`Ctrl` is not bound.)

| 快速鍵        | 動作         |
| ---------- | ---------- |
| **Meta+Q** | 結束應用程式     |
| **Meta+,** | 開啟設定       |
| **Meta+1** | 切換至訊息頁籤    |
| **Meta+2** | 切換至節點頁籤    |
| **Meta+3** | 切換至地圖頁籤    |
| **Meta+4** | 切換至連線頁籤    |
| **Meta+/** | Open About |

### 視窗與系統匣

- 調整視窗大小 — 響應式版面配置可依視窗尺寸自動調整
- 系統匣 — 可最小化至系統匣，於背景持續進行 mesh 網路操作
- 匣列選單 — 在系統匣圖示上按右鍵，可顯示視窗或結束程式
- 滑鼠互動 — 支援停駐狀態與標準桌面導覽操作

### 通知偏好設定

桌面版應用程式提供應用程式內的切換開關，可控制顯示哪些通知 — 包含訊息、新節點及低電量警示。 請在應用程式中前往「設定 → 通知」進行設定。

## 內建文件瀏覽器

桌面版應用程式內建文件瀏覽器，無需離開應用程式即可快速存取說明內容。

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

- JDK 21
- 純桌面版建置不需要 Android SDK

## 已知限制

- The interactive map view is Android-only — the Map tab is present but does not render a map on desktop
- 部分 Android 專屬功能（小工具、特定通知頻道）無法使用
- 在低規格硬體上執行 Compose Desktop 時，效能可能有所不同
- 桌面版尚不支援 BLE 綁定（配對功能可在不綁定的情況下正常使用）

## 相關主題

- 〔連線〕(connections) — 連線方式概覽
- [Firmware Updates](firmware) — USB, BLE, and Wi-Fi update all work the same as on Android

---

