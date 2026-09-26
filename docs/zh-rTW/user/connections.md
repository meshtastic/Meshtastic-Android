---
title: 連線
parent: 使用者指南
nav_order: 2
last_updated: 2026-08-30
description: 透過藍牙、USB 或 TCP/IP 將您的手機或電腦連接至 Meshtastic 無線電裝置。
aliases:
  - 藍牙
  - usb
  - tcp
  - 配對
---

# 連線

Meshtastic supports multiple transport methods to communicate between your phone or desktop and a radio.

## 藍牙（BLE）

藍牙低功耗是 Android 上預設且最常見的連線方式。

### Pairing a Radio

1. Power on your radio. Most radios advertise over Bluetooth as soon as they boot — there is no pairing mode to enter. Radios with a color touchscreen ship with Bluetooth switched off, so turn it on from the radio's own on-screen menu first.
2. 開啟應用程式，並前往「連線」頁籤。
3. Tap **Scan for Bluetooth devices** — nearby Meshtastic radios will appear.
4. Select your radio from the list.
5. Android asks you to pair. If your radio has a screen, it shows a six-digit PIN — type that into the Android dialog. If your radio has no screen, the PIN is `123456`.

![Scanning for Bluetooth devices, with a discovered radio in the list](../../assets/screenshots/connections_bluetooth_scan.png)

You can change the pairing method, or turn Bluetooth on for a radio that ships with it off, under **Settings → Device configuration → Bluetooth** — see [Settings — Radio & User](settings-radio-user). For more information, see [Bluetooth configuration](https://meshtastic.org/docs/configuration/radio/bluetooth) on meshtastic.org.

Use the transport selector — a segmented button row below the connection card — to switch between the Bluetooth, Network, and USB transports (one is active at a time):

![Connections screen with the transport selector showing Bluetooth, Network, and USB](../../assets/screenshots/connections_transport_filters.png)

> 💡 **Tip:** If your radio doesn't appear, check that it isn't already connected to another phone, or out of range.

The screen names anything on the app's side that is blocking a scan, with the fix attached:

| What you see                                        | 代表意義                                                                                                                                                                       |
| --------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A card asking for **Nearby devices**                | The permission has not been granted. **Grant permission** requests it; once Android stops prompting, the button becomes **Open settings**. |
| **Bluetooth is off**                                | The adapter is disabled — the card opens Bluetooth settings.                                                                                               |
| **Bluetooth scanning also needs location services** | Android 11 and older only: the permission is held but the system location toggle is off.                                                   |
| No card, empty list                                 | Nothing on this side is blocking the scan — the radio is out of range, off, or already connected elsewhere.                                                |

The explanation lives in that card, not in the scan control: tapping **Scan for Bluetooth devices** after you have declined once asks Android again directly.

### 連線狀態

| 圖示 | 狀態    | 描述說明                                                                                                       |
| -- | ----- | ---------------------------------------------------------------------------------------------------------- |
| 🟢 | 已連線   | 無線電連線已建立                                                                                                   |
| 🟡 | 正在連線  | 交握進行中                                                                                                      |
| 🔴 | 已中斷連線 | No active connection. The app retries automatically, with a growing delay between attempts |
| ⚪  | 設備休眠中 | The radio is in light sleep — the app is waiting for it to wake and reconnect, not failing                 |

These are the four states the app models. "Device sleeping" is normal on power-saving configurations and needs no action.

When connecting, a status indicator shows the current connection state — tap **Stop Connecting** to abandon the attempt:

![Connecting status](../../assets/screenshots/connections_connecting.png)

若未找到任何裝置，應用程式將顯示空白畫面並提供操作說明：

![No devices found](../../assets/screenshots/connections_empty_state.png)

### 藍牙疑難排解

- **Radio not found:** Turn Bluetooth off and back on. On Android 11 and older, also check that system location services are switched on — those releases do not return scan results without them.
- **Bluetooth scan couldn't start:** Try again, and toggle Bluetooth off and on if it repeats.
- 連線中斷：請靠近無線電裝置，並檢查是否有訊號干擾。
- **Pairing failed, or pairing did not complete:** Check that the **Nearby devices** permission is granted, then pair again.
- 配對遭拒：請至 Android 藍牙設定中移除該裝置後重新配對。
- **Could not establish a stable connection after repeated attempts:** The app stopped retrying after three failed handshakes — a radio that keeps failing here is usually crashing on reconnect. Power-cycle the radio, then tap it again on the **Connect** tab to start a fresh attempt.

## USB 序列埠

USB 連線提供有線替代方案，適用於桌上型電腦或藍牙無法使用的情況。

### 設定

1. Connect your radio to your phone with a USB data cable. Charge-only cables carry no data lines, and a radio on one never appears in the list.
2. The app prompts for USB permission — tap **Allow**.
3. 連線將自動建立。

> ℹ️ **Note:** USB connections require OTG support on Android devices.

### Troubleshooting USB

- **USB permission denied:** Unplug the radio and plug it back in — Android asks again on reconnect.
- **No radio in the list:** Check that the cable carries data rather than only power, and that the phone supports OTG.

## TCP/IP (Network)

Some Meshtastic radios support Wi-Fi/Ethernet connectivity, allowing TCP-based connections over your local network. Get the radio onto your network first. Connect to it over Bluetooth or USB, open **Settings → Device configuration → Network**, and under **Wi-Fi Options** turn on **Wi-Fi enabled** and enter the **SSID** and **Password**. The Network screen appears only for radios whose hardware supports Wi-Fi or Ethernet. Once the radio has an address, come back and connect to it over the network.

### Connecting over the Network

1. Make sure the radio is on the same local network as your phone/desktop.
2. On the **Connect** tab, select **Network** in the transport selector.
3. Choose the radio one of two ways:
   - **Scan for network devices** — toggle this on to auto-discover radios that advertise themselves on the local network (mDNS / `_meshtastic._tcp`). Discovered devices appear in the list; tap one to connect.
   - **Add device manually…** — enter the radio's IP address (or hostname) and port (default: `4403`).
4. Previously-used network addresses are remembered under **Recent Network Devices** for quick reconnection (touch & hold to remove one).

Network discovery uses mDNS, which only works when both your phone and the radio are on the same subnet. If the phone is not on Wi-Fi at all, the app warns that a network scan may find nothing. On Android 17 and newer, the app needs the **Local network permission** to reach a radio on your own Wi-Fi at all — not only to discover it — so typing the address by hand does not work around a denied permission. Grant it from the card on the Network pane, or from the **Permissions** section of **Settings**. A radio on a public address, or one reached over a VPN, needs no permission.

### 何時使用 TCP

- 無線電裝置與裝置位於同一區域網路
- 使用模擬無線電裝置進行測試
- 藍牙訊號受干擾的環境

### mPWRD-OS 的 Wi-Fi 設定

**Settings → Wi-Fi Provisioning for mPWRD-OS** is a separate, narrower tool. It sends Wi-Fi credentials over Bluetooth to **mPWRD-OS** devices only, using their own protocol — it does not configure Wi-Fi on an ordinary Meshtastic radio. It is available on both Android and desktop.

1. Open the screen and wait while the app finds the device over Bluetooth.
2. Tap **Scan for Networks**, then pick a network from **Available Networks** — or turn on **Hidden network** and type the name into **Network Name (SSID)**.
3. Enter the **Password** and tap **Apply**.

If the scan reports **No networks found** or fails outright, move the phone closer to the device and scan again. If **Failed to apply Wi-Fi configuration** comes back, check the password and try again.

## After Your First Connection

Being connected is not the same as being able to transmit.

A radio leaves the factory with no LoRa region set, and it does not transmit until you set one. When you connect such a radio, the **Connect** tab shows a **Set your region** card; tap it to open the LoRa screen and choose the region you are in.

Once the region is set, the tab warns you if the radio is receive-only: a **Transmit is disabled** card, reading "This device can receive but will not send anything over LoRa." Tap it to open the same screen and turn **Transmit Enabled** back on. Only one of the two cards appears at a time — an unset region already stops the radio transmitting, so the app names that first and holds the transmit card back until you have set a region.

For more information, see [Settings — Radio & User](settings-radio-user#lora-config).

## 重新連線行為

The app reconnects to the last selected radio on startup. You can switch transports from the **Connect** tab at any time.

To disconnect, tap the disconnect button on the **Connect** tab:

![Disconnect from radio](../../assets/screenshots/connections_disconnect.png)

## 桌面版連線

在桌面版（Linux／macOS／Windows）上，應用程式支援：

- 藍牙（BLE）—— 透過 Kable 函式庫；支援 macOS、Linux 及 Windows
- USB 序列埠 — 主要的有線連線方式
- TCP/IP — 適用於透過網路連線的無線電裝置

請參閱〔桌面版應用程式〕(desktop) 以了解各平台的詳細說明與鍵盤快速鍵。

## 相關主題

- 〔快速入門〕(onboarding) — 首次啟動的設定與權限
- [Settings — Radio & User](settings-radio-user) — Bluetooth, region, and network configuration
- [Messages & Channels](messages-and-channels) — send your first message once the radio is connected
- [Nodes](nodes) — see who else is on your mesh
- 〔桌面版應用程式〕(desktop) — 桌面版連線詳細說明
- 〔支援的裝置〕(https://meshtastic.org/docs/hardware/devices)— meshtastic.org 上的完整相容無線電裝置清單
