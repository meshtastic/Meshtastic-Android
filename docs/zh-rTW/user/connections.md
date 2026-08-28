---
title: 連線
parent: 使用者指南
nav_order: 2
last_updated: 2026-08-27
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
3. Tap **Scan for Bluetooth devices** — nearby Meshtastic radios will appear.
4. 從清單中選取您的裝置。
5. 若出現藍牙配對提示，請點選接受。

![Scanning for Bluetooth devices, with a discovered radio in the list](../../assets/screenshots/connections_bluetooth_scan.png)

Use the transport selector — a segmented button row below the connection card — to switch between the Bluetooth, Network, and USB transports (one is active at a time):

![Transport selector](../../assets/screenshots/connections_transport_filters.png)

> 💡 **Tip:** If your device doesn't appear, check that the radio is not already connected to another device or out of range.

The screen names anything on the app's side that is blocking a scan, with the fix attached:

| What you see                                        | 代表意義                                                                                                                                                                       |
| --------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A card asking for **Nearby devices**                | The permission has not been granted. **Grant permission** requests it; once Android stops prompting, the button becomes **Open settings**. |
| **Bluetooth is off**                                | The adapter is disabled — the card opens Bluetooth settings.                                                                                               |
| **Bluetooth scanning also needs location services** | Android 11 and older only: the permission is held but the system location toggle is off.                                                   |
| No card, empty list                                 | Nothing on this side is blocking the scan — the radio is out of range, off, or already connected elsewhere.                                                |

Tapping **Scan** after you have declined the permission once explains what it is for before asking again, and lets you decline again without being cornered.

### 連線狀態

| 圖示 | 狀態    | 描述說明                                                                                       |
| -- | ----- | ------------------------------------------------------------------------------------------ |
| 🟢 | 已連線   | 無線電連線已建立                                                                                   |
| 🟡 | 正在連線  | 交握進行中                                                                                      |
| 🔴 | 已中斷連線 | No active connection; the app keeps trying to reconnect                                    |
| ⚪  | 設備休眠中 | The radio is in light sleep — the app is waiting for it to wake and reconnect, not failing |

These are the four states the app models. "Device sleeping" is normal on power-saving configurations and needs no action.

When connecting, a status indicator shows the current connection state — tap **Stop Connecting** to abandon the attempt:

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

> ℹ️ **Note:** USB connections require OTG support on Android devices.

## TCP/IP (Network)

Some Meshtastic radios support WiFi/Ethernet connectivity, allowing TCP-based connections over your local network. Get the radio onto your network first — using the radio's own WiFi settings (via the firmware web interface or another connection) — then connect to it from the app.

> ℹ️ **Note:** **Settings → Wi-Fi Provisioning for mPWRD-OS** is a separate, narrower tool. It provisions WiFi
> credentials over Bluetooth to **mPWRD-OS** devices only, using their own protocol — it does not
> configure WiFi on an ordinary Meshtastic radio. It scans over BLE, lists the networks the device
> can see (including an option for a hidden SSID), takes the password, and reports success or
> failure. Available on both Android and Desktop.

### Connecting over the Network

1. Make sure the radio is on the same local network as your phone/desktop.
2. On the Connect screen, select **Network** in the transport selector.
3. Choose the radio one of two ways:
   - **Scan for network devices** — toggle this on to auto-discover radios that advertise themselves on the local network (mDNS / `_meshtastic._tcp`). Discovered devices appear in the list; tap one to connect.
   - **Add device manually…** — enter the radio's IP address (or hostname) and port (default: `4403`).
4. Previously-used network addresses are remembered under **Recent Network Devices** for quick reconnection (long-press to remove one).

> 💡 **Tip:** Network discovery uses mDNS, which only works when both devices are on the same subnet. On Android 17+ the app needs the local-network permission for scanning; if discovery finds nothing, add the device manually by IP.

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

