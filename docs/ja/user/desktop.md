---
title: デスクトップアプリ
nav_order: 14
last_updated: 2026-10-21
description: Linux、macOS、Windows で Meshtastic デスクトップアプリをインストールして使う方法。接続、機能の対応状況、キーボードショートカットを説明します。
aliases:
  - desktop
  - linux
  - macos
  - windows
  - jvm
---

# デスクトップアプリ

This page covers installing the Meshtastic desktop app, connecting a node, and how it differs from Android. The desktop app shares its core codebase with Android via Kotlin Multiplatform, so most features work identically across Linux, macOS, and Windows.

## インストール

### Linux

- Download the `.deb`, `.rpm`, or `.AppImage` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Each comes in an x86_64 and an arm64 (aarch64) build.
- The AppImage carries its own update information, so AppImageUpdate and other zsync-based updaters can update it in place to the latest production release.
- Or install from Flathub: `flatpak install flathub org.meshtastic.MeshtasticDesktop`
- または、`./gradlew :desktopApp:run` を使ってソースからビルドします

### macOS

- Download the `.dmg` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). It is built for Apple silicon only.
- または、ソースからビルドします

### Windows

- Download the `.msi` or `.exe` installer from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Both are x64 builds.
- または、ソースからビルドします

## 無線機を接続する

All three transports live on the **Connect** tab, the same as on Android. Pick one with the transport selector. See [Connections](connections) for the full walkthrough.

### USB シリアル（主要）

The most reliable connection method on desktop:

Connect your node by USB, select **USB** in the transport selector, and click the node's serial port in the list. The list refreshes on its own as ports appear and disappear.

### TCP/IP

For network-connected nodes, select **Network** in the transport selector, then either:

1. Turn on **Scan for network devices** and click a node the scan finds, or
2. Click **Add device manually…** and enter the node's IP address or hostname and port (default: `4403`).

### Bluetooth（BLE）

Bluetooth Low Energy is supported on desktop via the [Kable](https://github.com/JuulLabs/kable) library:

1. Ensure your system has a Bluetooth adapter, and select **Bluetooth** in the transport selector.
2. Click **Scan for Bluetooth devices**, then select your node from the list.

## 機能の対応状況

| 機能                                                    | Android | デスクトップ | 備考                                                                                                                                                                                                                                              |
| ----------------------------------------------------- | ------- | ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| メッセージング                                               | ✓       | ✓      | 完全対応                                                                                                                                                                                                                                            |
| ノードリスト                                                | ✓       | ✓      | 完全対応                                                                                                                                                                                                                                            |
| マップ                                                   | ✓       | ✓      | Interactive MapLibre map, with base map and overlay pickers and custom tile sources. Offline terrain (hillshade and contours) can be downloaded; offline base-map packs and local `.mbtiles` archives cannot |
| Map layers (`.kml`/`.kmz`/GeoJSON) | ✓       | ✓      | Same layer store and sheet as Android; imported files draw on the desktop map                                                                                                                                                                   |
| サイトプランナー                                              | ✓       | ✓      | Computes the coverage estimate in the app and adds it to the map as a layer, as on Android                                                                                                                                                      |
| 設定                                                    | ✓       | ✓      | 完全対応                                                                                                                                                                                                                                            |
| Bluetooth（BLE）                                        | ✓       | ✓      | デスクトップでは Kable 経由                                                                                                                                                                                                                               |
| ファームウェアの更新                                            | ✓       | ✓      | In-app USB, BLE, and Wi-Fi (ESP32) update work the same as Android. The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only                                           |
| 通知                                                    | ✓       | ✓      | OS ネイティブの通知                                                                                                                                                                                                                                     |
| ウィジェット                                                | ✓       | ✗      | Android のみ                                                                                                                                                                                                                                      |
| AI アシスタント（Chirpy）                                     | ✓\*     | ✗      | Google 版の Android のみ                                                                                                                                                                                                                            |
| アプリ機能（システム AI）                                        | ✓†      | ✗      | Google 版の Android のみ                                                                                                                                                                                                                            |

\*Chirpy AI には、対応ハードウェアを備えた Google 版ビルドで Android 14 以降が必要です。

†アプリ機能は、Google 版ビルドで、アプリの操作を Android のシステム AI に公開します。[アプリ機能](app-functions) を参照してください。

## UI の違い

The desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### キーボードショートカット

ショートカットは、macOS では **⌘**（Command）、Windows と Linux では **Ctrl** を使用します。 （Super／Windows キーは割り当てられていません。）

| ショートカット      | 操作           |
| ------------ | ------------ |
| **⌘/Ctrl+Q** | Quit the app |
| **⌘/Ctrl+,** | 設定を開く        |
| **⌘/Ctrl+1** | メッセージタブに切り替え |
| **⌘/Ctrl+2** | ノードタブに切り替え   |
| **⌘/Ctrl+3** | マップタブに切り替え   |
| **⌘/Ctrl+4** | 接続タブに切り替え    |
| **⌘/Ctrl+/** | バージョン情報を開く   |

### ウィンドウとシステムトレイ

- **ウィンドウのサイズ変更**：レスポンシブなレイアウトがウィンドウの寸法に合わせて調整されます
- **System tray** — closing the window minimizes to the system tray for background mesh operation, provided the tray icon can be clicked to bring the window back. On Linux that needs a desktop environment with a StatusNotifierItem tray. Where there is no such tray, closing quits the app instead
- **Tray menu** — right-click the tray icon for **Show Meshtastic** or **Quit**
- **マウス操作**：ホバー状態と標準的なデスクトップのナビゲーション

### 通知の設定

The desktop app provides in-app toggles for controlling which notifications are shown. Find them in the **App Notifications** section of the Settings screen: **Direct message notifications**, **New node notifications**, **Low battery notifications**, **Reaction notifications**, and **Waypoint notifications**.

## 組み込みのドキュメントブラウザー

The desktop app includes a built-in documentation browser for quick access to help content without leaving the app.

![目次付きのドキュメントブラウザー](../../assets/screenshots/docs-browser_toc.png)

The browser searches page titles and keywords across all documentation:

![ドキュメントブラウザーの検索](../../assets/screenshots/docs-browser_search.png)

個々のドキュメントページは、完全な書式で表示されます：

![ドキュメントページ](../../assets/screenshots/docs-browser_page.png)

## ソースからビルドする

```shell
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

要件：

- JDK 25 to run Gradle (Gradle can provision it itself via foojay)
- JetBrains Runtime (JBR) 25 installed where Gradle's toolchain detection finds it. The desktop app compiles against it and bundles it, and foojay does not offer it, so Gradle cannot download it
- デスクトップのみのビルドに Android SDK は不要です

## 既知の制限

- Offline base-map downloads and local `.mbtiles` archives are not available on desktop. Offline terrain is — see [Map & Waypoints](map-and-waypoints).
- `.kml`/`.kmz`/GeoJSON layer import works — see
  [Map & Waypoints](map-and-waypoints#map-layers). Custom network tile sources work
  too — see [Map & Waypoints](map-and-waypoints#adding-your-own-tile-source)
- The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only, and the
  desktop app does not offer it. Use the [Web Flasher](https://flasher.meshtastic.org) instead
- 一部の Android 固有の機能（ウィジェット、特定の通知チャンネル）は利用できません
- Compose Desktop を実行する低スペックのハードウェアでは、パフォーマンスが変わることがあります
- BLE bonding is not supported on desktop (pairing works without bonding)

## 関連トピック

- [コネクション](connections)：接続方法の概要
- [Firmware Updates](firmware) — in-app USB, BLE, and Wi-Fi update all work the same as on Android
- [Map & Waypoints](map-and-waypoints) — base maps, layers, custom tile sources, and what the desktop map does not do
