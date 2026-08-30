---
title: 新手入門
parent: User Guide
nav_order: |-
  1.  **Overview**

      Meshtastic is a project that allows you to use inexpensive LoRa radios as a long range, off-grid, decentralized communication platform. These radios, combined with readily available and very affordable microcontrollers like the ESP32, nRF52, and RP2040, create a network that can be used to send text messages, share locations, and more without relying on cellular or WiFi infrastructure. It's perfect for hiking, camping, or any situation where you need to stay connected beyond the reach of traditional networks.

  2.  **Key Components**

      *   **LoRa Radios:** These radios provide the long-range communication capabilities. The RAK Wireless modules are a popular choice.
      *   **Microcontrollers:** The brains of the operation. ESP32, nRF52, and RP2040 are commonly used due to their low cost and capabilities.
      *   **GPS (Optional):** For location sharing. Many devices, like the T-Beam, have built-in GPS.
      *   **Battery (Optional):** For portable use.
      *   **Enclosure (Optional):** To protect the hardware.

  3.  **Popular Devices**

      *   **T-Beam:** A popular board with ESP32, LoRa, and GPS.
      *   **Heltec WiFi LoRa 32:** Another popular ESP32-based board with LoRa.
      *   **LilyGo Boards:** LilyGo offers a variety of ESP32 and nRF52 based boards with LoRa.

  4.  **Features**

      *   **Text Messaging:** Send and receive text messages over the LoRa network.
      *   **Location Sharing:** Share your location with other users on the network (requires GPS).
      *   **Encryption:** Messages can be encrypted for privacy.
      *   **Mesh Networking:** The network automatically routes messages through other nodes to reach the destination.
      *   **Off-Grid Communication:** No cellular or WiFi required.
      *   **Channel Settings:** Customize channel settings for different regions and use cases (Long Fast, Mid Slow, etc.).

  5.  **Software & Firmware**

      *   **Meshtastic Firmware:** The core software that runs on the microcontrollers.
      *   **Meshtastic Mobile App:** For configuring and interacting with the devices. Available on Android and iOS.
      *   **CLI (Command Line Interface):** For advanced configuration and debugging.
      *   **API (Application Programming Interface):** For integrating Meshtastic with other applications.

  6.  **Hardware Setup**

      *   **Flashing Firmware:** You'll need to flash the Meshtastic firmware onto your microcontroller. This is typically done using a USB connection and a flashing tool.
      *   **Connecting Peripherals:** Connect the LoRa radio and any other peripherals (GPS, battery) to the microcontroller.
      *   **Antenna:** Attach an appropriate antenna to the LoRa radio.

  7.  **Configuration**

      *   **Region Settings:** Configure the correct region settings for your location.
      *   **Channel Settings:** Choose a channel or create a custom channel.
      *   **Encryption:** Enable encryption for secure communication.
      *   **Power Settings:** Adjust power settings to optimize battery life.

  8.  **Technical Details**

      *   **LoRa Modulation:** Uses LoRa modulation for long-range communication.
      *   **Frequency Bands:** Operates on various frequency bands depending on the region (e.g., 915 MHz in North America, 868 MHz in Europe).
      *   **Microcontroller Interfaces:** Uses various interfaces for communication between the microcontroller and peripherals, including GPIO, USB, UART, SPI, and I2C.
      *   **BLE (Bluetooth Low Energy):** Used for initial configuration and communication with the mobile app.
      *   **WiFi:** Some devices support WiFi for OTA (Over-The-Air) firmware updates.
      *   **MQTT:** Supports MQTT for integration with other systems.

  9.  **Use Cases**

      *   **Hiking and Camping:** Stay connected with your group in areas without cellular coverage.
      *   **Emergency Communication:** Provide a backup communication system in case of emergencies.
      *   **Disaster Relief:** Establish communication networks in areas affected by disasters.
      *   **Rural Communication:** Connect communities in remote areas.
      *   **IoT Applications:** Use Meshtastic for various IoT applications that require long-range communication.

  10. **Resources**

      *   **Meshtastic Website:** [https://meshtastic.org/](https://meshtastic.org/)
      *   **Meshtastic Documentation:** [https://meshtastic.org/docs/](https://meshtastic.org/docs/)
      *   **Meshtastic Forums:** [https://meshtastic.discourse.group/](https://meshtastic.discourse.group/)
  1.  **概觀**

      Meshtastic 是一個專案，讓你可以使用便宜的 LoRa 無線電作為長距離、離線、去中心化的通訊平台。這些無線電，結合了容易取得且非常實惠的微控制器，像是 ESP32、nRF52 和 RP2040，創建了一個網路，可以用來傳送簡訊、分享位置等等，而不需要依賴行動網路或 WiFi 基礎設施。它非常適合健行、露營，或任何你需要保持連線，但又超出傳統網路覆蓋範圍的情況。

  2.  **主要組件**

      *   **LoRa 無線電:** 提供長距離通訊能力。 RAK Wireless 模組是一個很受歡迎的選擇。
      *   **微控制器:** 運作的大腦。 ESP32、nRF52 和 RP2040 因為它們的低成本和功能而被廣泛使用。
      *   **GPS (可選):** 用於位置分享。 許多裝置，像是 T-Beam，都有內建 GPS。
      *   **電池 (可選):** 用於攜帶型使用。
      *   **外殼 (可選):** 保護硬體。

  3.  **熱門裝置**

      *   **T-Beam:** 一個受歡迎的板子，具有 ESP32、LoRa 和 GPS。
      *   **Heltec WiFi LoRa 32:** 另一個受歡迎的基於 ESP32 的板子，具有 LoRa。
      *   **LilyGo Boards:** LilyGo 提供各種基於 ESP32 和 nRF52 的板子，具有 LoRa。

  4.  **功能**

      *   **簡訊傳輸:** 透過 LoRa 網路傳送和接收簡訊。
      *   **位置分享:** 與網路上其他使用者分享你的位置 (需要 GPS)。
      *   **加密:** 可以加密訊息以保護隱私。
      *   **網狀網路:** 網路會自動透過其他節點路由訊息，以到達目的地。
      *   **離線通訊:** 不需要行動網路或 WiFi。
      *   **頻道設定:** 客製化不同地區和使用案例的頻道設定 (Long Fast、Mid Slow 等)。

  5.  **軟體與 Firmware**

      *   **Meshtastic Firmware:** 在微控制器上執行的核心軟體。
      *   **Meshtastic Mobile App:** 用於配置和與裝置互動。 可在 Android 和 iOS 上使用。
      *   **CLI (Command Line Interface):** 用於進階配置和除錯。
      *   **API (Application Programming Interface):** 用於將 Meshtastic 與其他應用程式整合。

  6.  **硬體設定**

      *   **刷入 Firmware:** 你需要將 Meshtastic firmware 刷入你的微控制器。 這通常是使用 USB 連線和刷入工具來完成的。
      *   **連接週邊設備:** 將 LoRa 無線電和任何其他週邊設備 (GPS、電池) 連接到微控制器。
      *   **天線:** 將適當的天線連接到 LoRa 無線電。

  7.  **配置**

      *   **區域設定:** 為你的位置配置正確的區域設定。
      *   **頻道設定:** 選擇一個頻道或創建一個自定義頻道。
      *   **加密:** 啟用加密以進行安全通訊。
      *   **電源設定:** 調整電源設定以優化電池壽命。

  8.  **技術細節**

      *   **LoRa 調變:** 使用 LoRa 調變進行長距離通訊。
      *   **頻率範圍:** 根據地區在不同的頻率範圍上運作 (例如，北美為 915 MHz，歐洲為 868 MHz)。
      *   **微控制器介面:** 使用各種介面在微控制器和週邊設備之間進行通訊，包括 GPIO、USB、UART、SPI 和 I2C。
      *   **BLE (Bluetooth Low Energy):** 用於初始配置和與行動應用程式的通訊。
      *   **WiFi:** 某些裝置支援 WiFi 用於 OTA (Over-The-Air) firmware 更新。
      *   **MQTT:** 支援 MQTT 用於與其他系統整合。

  9.  **使用案例**

      *   **健行和露營:** 在沒有行動網路覆蓋的地區與你的團隊保持聯繫。
      *   **緊急通訊:** 在緊急情況下提供備份通訊系統。
      *   **災害救援:** 在受災害影響的地區建立通訊網路。
      *   **農村通訊:** 連接偏遠地區的社群。
      *   **IoT 應用:** 將 Meshtastic 用於各種需要長距離通訊的 IoT 應用。

  10. **資源**

      *   **Meshtastic Website:** [https://meshtastic.org/](https://meshtastic.org/)
      *   **Meshtastic Documentation:** [https://meshtastic.org/docs/](https://meshtastic.org/docs/)
      *   **Meshtastic Forums:** [https://meshtastic.discourse.group/](https://meshtastic.discourse.group/)
last_updated: 2026-08-29
description: First-launch setup — permissions, onboarding flow, and next steps after connecting your radio.
aliases:
  - first-launch
  - setup
  - intro
---

# # 入門指南

This page covers the first-launch flow of the Meshtastic Android app, what each permission is for, and how to revisit them later.

## First Launch

When you open the app for the first time, the app guides you through an introductory flow that configures essential permissions and settings. Complete each step in order or skip it — nothing here is a one-time offer. Every permission can be reviewed and granted later from **Settings → Permissions** inside the app.

### Welcome Screen

The welcome screen introduces Meshtastic with three feature rows:

|                               |                                  |
| ----------------------------- | -------------------------------- |
| **Stay Connected Anywhere**   | 無需手機訊號，也能與您的朋友和社群離線通訊。           |
| **Create Your Own Networks**  | 輕鬆設定私有網狀網絡，以實現偏遠地區安全可靠的通訊。       |
| **Track and Share Locations** | 透過整合的 GPS 功能，即時分享你的位置，並保持團隊協調一致。 |

Tap **Get started** to proceed through the setup flow.

![Welcome screen](../../assets/screenshots/onboarding_welcome.png)

## Permissions

The app requests several permissions during setup. Each one serves a specific purpose, and some are required for core functionality.

### Bluetooth Permission

Bluetooth is the primary connection method between your phone and Meshtastic radio:

- **Bluetooth scanning** — discover nearby Meshtastic radios
- **Bluetooth connect** — establish and maintain connections with paired radios

Grant both permissions when prompted. Without Bluetooth, you'll need to use USB or TCP connections instead.

### Location Permission

> ⚠️ **Is location required for Bluetooth?** **Android 11 and older** show one location step, on the Bluetooth screen, rather than two — those releases treat a Bluetooth scan as a location capability, so the app asks for Location instead of "Nearby devices". Asking twice would push you toward the point where Android stops offering the dialog at all (a second denial on Android 11; the "Don't ask again" checkbox on Android 10 and older). On **Android 12 and newer** the two are separate: "Nearby devices" is declared `neverForLocation`, and declining Location does not stop you finding or connecting to a radio.

Meshtastic also uses your location for:

- Showing your position on the mesh map
- Calculating distances to other nodes
- Sharing your GPS coordinates with other mesh members (if enabled)

Grant **"While using the app"**. The app does not request background location — `ACCESS_BACKGROUND_LOCATION` is not in its manifest — so Android will not offer an "Always" option, and position updates happen while the app is in the foreground or running its foreground service.

Declining leaves the rest of the app working: on Android 12 and newer, Bluetooth is unaffected and only the map position and position sharing are disabled. On Android 11 and older, Bluetooth scanning also stops, because that is the permission Android gates it behind — and system **Location Services** must also be switched on for a scan to return anything.

### Notifications Permission

Notifications alert you to:

- Incoming messages from channels and direct messages
- New nodes joining the mesh
- Low battery on a remote node

> 💡 **Tip:** You can fine-tune notification preferences later in Android system settings — the app creates a separate notification channel per category (plus a few internal ones, like the background service), so you can enable or silence them individually.

### Critical Alerts Permission

Critical alerts are high-priority notifications that break through Do Not Disturb — for emergency mesh alerts and urgent messages.

This step is not a runtime permission prompt. There is no grant/deny dialog: the button opens the Android system settings page for the app's **Alerts** notification channel, where you turn the breakthrough behavior on yourself. You can **skip** it, and reach the same page later from Android notification settings.

### Reviewing permissions later

**Settings → Permissions** summarizes where every runtime permission stands. It covers five: **Nearby devices** (Bluetooth), **Location**, **Notifications**, **Camera** (scanning channel and contact QR codes) and **Local network** (finding radios over Wi-Fi by mDNS) — the last two are never asked for during setup, only when a feature first needs them. It reads _All allowed_ when no permission needs attention; the row names the count and the Permissions screen opens automatically when something does. Tap the row to see the full list at any time:

| 狀態                                          | What tapping the row does                                                                    |
| ------------------------------------------- | -------------------------------------------------------------------------------------------- |
| **Allowed**                                 | Opens the system page, so you can review or revoke it                                        |
| **Not asked yet**                           | Requests it                                                                                  |
| **Denied — tap to allow**                   | Explains what the permission is for, then asks again if you agree                            |
| **Blocked — tap to open system settings**   | Android will no longer show its dialog, so this opens the page where you can turn it back on |
| **Not required on this version of Android** | Nothing — the permission does not exist on your device                                       |

This matters most for notifications. If you decline them during setup, this row is the way back: Android stops showing the dialog once you have declined firmly (a second denial), at which point this row switches to **Blocked** and sends you to the system settings page instead. The notification prompt exists only on Android 13 and newer — on older versions notifications are on by default and managed from Android's own settings.

## After Setup

After you grant permissions, the app opens the main interface. Your first action should be connecting to a Meshtastic radio — see [Connections](connections) for detailed instructions.

> 💡 **Tip:** If you skipped any permissions during setup, open **Settings → Permissions** in the app. Every runtime permission is listed there with its current state and a way back to it — including notifications, which the system will not prompt for a second time on its own.

Features also ask in context. Tapping **Scan** on the Connections screen with Bluetooth permission missing explains what it is for and offers to request it; once Android stops prompting, the same control opens the system settings page instead of doing nothing.

New to Meshtastic? The [getting started guide](https://meshtastic.org/docs/getting-started) on meshtastic.org covers hardware selection, initial radio configuration, and your first mesh setup.

## 相關主題

- [Connections](connections) — pair your first radio
- [Messages & Channels](messages-and-channels) — send your first message
- [Nodes](nodes) — see who else is on your mesh
- [Map & Waypoints](map-and-waypoints) — view node positions
- [Settings — Radio & User](settings-radio-user) — configure your radio and user profile
