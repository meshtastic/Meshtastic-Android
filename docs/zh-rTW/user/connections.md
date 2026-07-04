---
title: 連線
parent: 使用者指南
nav_order: 2
last_updated: 2026-05-20
description: 透過藍牙、USB 或 TCP/IP 將您的手機或電腦連接至 Meshtastic 無線電裝置。
aliases:
  - 藍牙
  - usb
  - tcp
  - 配對
---

# 連線

Meshtastic 支援多種傳輸方式，以便您的手機或電腦與無線電節點進行通訊。

## 藍牙（BLE）

藍牙低功耗是 Android 上預設且最常見的連線方式。

### 配對裝置

1. 請確認您的 Meshtastic 無線電裝置已開機，並進入配對模式。
2. 開啟應用程式，並前往「連線」頁籤。
3. 點選「掃描裝置」——附近的 Meshtastic 無線電裝置將會顯示。
4. 從清單中選取您的裝置。
5. 若出現藍牙配對提示，請點選接受。

![Device list item](../../assets/screenshots/connections_bluetooth_scan.png)

您可使用頂部的篩選標籤，依傳輸類型篩選裝置：

![Transport filter chips](../../assets/screenshots/connections_transport_filters.png)

> 💡 提示：若裝置未顯示，請確認已授予藍牙與位置權限，且該無線電裝置尚未連接至其他裝置。

### 連線狀態

| 圖示 | 狀態    | 描述說明     |
| -- | ----- | -------- |
| 🟢 | 已連線   | 無線電連線已建立 |
| 🟡 | 正在連線  | 交握進行中    |
| 🔴 | 已中斷連線 | 無作用中連線   |
| ⚪  | 尚未設定  | 未選擇裝置    |

連線過程中，狀態指示器會顯示目前的連線狀態：

![Connecting status](../../assets/screenshots/connections_connecting.png)

若未找到任何裝置，應用程式將顯示空白畫面並提供操作說明：

![No devices found](../../assets/screenshots/connections_empty_state.png)

### 藍牙疑難排解

- 找不到裝置：請關閉再開啟藍牙，並確認已啟用位置服務。
- 連線中斷：請靠近無線電裝置，並檢查是否有訊號干擾。
- 配對遭拒：請至 Android 藍牙設定中移除該裝置後重新配對。

## USB 序列埠

USB 連線提供有線替代方案，適用於桌上型電腦或藍牙無法使用的情況。

### 設定

1. 請使用 USB 傳輸線將無線電裝置連接至您的裝置。
2. 應用程式將提示要求 USB 權限——請點選「允許」。
3. 連線將自動建立。

> ⚠️ 注意：USB 連線需要 Android 裝置支援 OTG 功能。

## TCP/IP（Wi-Fi）

部分 Meshtastic 無線電裝置支援 Wi-Fi 連線，可使用 TCP 方式連接。

### 設定

1. 請透過無線電裝置的網頁介面或設定，將其連接至 Wi-Fi 網路。
2. 在應用程式中，前往「連線 → TCP」。
3. 輸入無線電裝置的 IP 位址與連接埠（預設：4403）。
4. 點選「連線」。

![WiFi scanning for devices](../../assets/screenshots/connections_wifi_scanning.png)

找到裝置時，將顯示於連線清單中：

![WiFi device found](../../assets/screenshots/connections_wifi_device_found.png)

連線成功後，狀態指示器將顯示確認訊息：

![WiFi connection success](../../assets/screenshots/connections_wifi_success.png)

### 何時使用 TCP

- 無線電裝置與裝置位於同一區域網路
- 使用模擬無線電裝置進行測試
- 藍牙訊號受干擾的環境

## 重新連線行為

應用程式啟動時將自動重新連接至上次選取的裝置。 您可隨時在連線畫面切換傳輸方式。

若要中斷連線，請點選連線畫面上的中斷連線按鈕：

![Disconnect from radio](../../assets/screenshots/connections_disconnect.png)

## 桌面版連線

在桌面版（Linux／macOS／Windows）上，應用程式支援：

- 藍牙（BLE）—— 透過 Kable 函式庫；支援 macOS、Linux 及 Windows
- USB 序列埠 — 主要的有線連線方式
- TCP/IP — 適用於透過網路連線的無線電裝置

請參閱〔桌面版應用程式〕(desktop) 以了解各平台的詳細說明與鍵盤快速鍵。

## 相關主題

- 〔快速入門〕(onboarding) — 首次啟動的設定與權限
- 〔設定 — 無線電與使用者〕(settings-radio-user) — 藍牙與網路設定
- 〔桌面版應用程式〕(desktop) — 桌面版連線詳細說明
- 〔支援的裝置〕(https://meshtastic.org/docs/hardware/devices)— meshtastic.org 上的完整相容無線電裝置清單

---

