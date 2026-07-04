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
last_updated: 2026-05-13
description: First-launch setup — permissions, onboarding flow, and next steps after connecting your radio.
aliases:
  - first-launch
  - setup
  - intro
---

# # 入門指南

Welcome to Meshtastic! This guide walks you through the initial setup of the Meshtastic Android app.

## First Launch

When you open the app for the first time, you'll be guided through an introductory flow that helps configure essential permissions and settings. Each step can be completed in order, or you can skip and configure permissions later in Android settings.

### Welcome Screen

The welcome screen introduces Meshtastic and its core capabilities:

- Off-grid mesh communication
- No cellular or internet required
- End-to-end encrypted messaging

Tap **Get Started** to proceed through the setup flow.

![Welcome screen](../../assets/screenshots/onboarding_welcome.png)

## Permissions

The app requests several permissions during setup. Each one serves a specific purpose, and some are required for core functionality.

### Bluetooth Permission

Bluetooth is the primary connection method between your phone and Meshtastic radio:

- **Bluetooth scanning** — discover nearby Meshtastic radios
- **Bluetooth connect** — establish and maintain connections with paired radios

Grant both permissions when prompted. Without Bluetooth, you'll need to use USB or TCP connections instead.

### Location Permission

> ⚠️ **Why is location required for Bluetooth?** Android requires location permission to discover nearby Bluetooth Low Energy devices. This is an Android system requirement, not a Meshtastic-specific choice.

Meshtastic also uses your location for:

- Showing your position on the mesh map
- Calculating distances to other nodes
- Sharing your GPS coordinates with other mesh members (if enabled)

Grant **"While using the app"** or **"Always"** depending on your preference:

- **While using the app** — position updates only when the app is open
- **Always** — enables background position updates for always-on mesh presence

If denied, Bluetooth scanning will not function and your node will not report a position.

### Notifications Permission

Notifications alert you to:

- Incoming messages from channels and direct messages
- Connection status changes (connected, disconnected, reconnecting)
- Firmware update availability

> 💡 **Tip:** You can fine-tune notification preferences later in Android system settings. The app creates separate notification channels for messages, connection events, and background service status.

### Critical Alerts Permission

On supported devices, the app may request permission for critical alerts:

- These are high-priority notifications that can break through Do Not Disturb mode
- Useful for emergency mesh alerts or urgent messages
- You can **skip** this step if you don't need breakthrough notifications
- Configure or revoke later in Android notification settings

## After Setup

Once permissions are granted, the app transitions to the main interface. Your first action should be connecting to a Meshtastic radio — see [Connections](connections) for detailed instructions.

> 💡 **Tip:** If you skipped any permissions during setup, you can grant them later through **Android Settings → Apps → Meshtastic → Permissions**. The app will prompt you again if a missing permission blocks a feature you try to use.

## What's Next?

Once connected to a radio, explore:

- [Connections](connections) — pair your first radio device
- [Messages & Channels](messages-and-channels) — send your first message
- [Nodes](nodes) — see who's on your mesh
- [Map & Waypoints](map-and-waypoints) — view node positions
- [Settings](settings-radio-user) — configure your radio and user profile

New to Meshtastic? The [getting started guide](https://meshtastic.org/docs/getting-started) on meshtastic.org covers hardware selection, initial radio configuration, and your first mesh setup.

---
