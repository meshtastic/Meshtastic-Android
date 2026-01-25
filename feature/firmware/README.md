# `:feature:firmware`

## Module dependency graph

<!--region graph-->
```mermaid
---
config:
  layout: elk
  elk:
    nodePlacementStrategy: SIMPLE
---
graph TB
  subgraph :feature
    direction TB
    :feature:firmware[firmware]:::android-library
  end
  subgraph :core
    direction TB
    :core:analytics[analytics]:::android-library
    :core:common[common]:::kmp-library
    :core:data[data]:::android-library
    :core:database[database]:::android-library
    :core:datastore[datastore]:::android-library
    :core:di[di]:::android-library
    :core:model[model]:::android-library
    :core:navigation[navigation]:::android-library
    :core:network[network]:::android-library
    :core:prefs[prefs]:::android-library
    :core:proto[proto]:::android-library
    :core:service[service]:::android-library
    :core:strings[strings]:::kmp-library
    :core:ui[ui]:::android-library
  end

  :core:analytics -.-> :core:prefs
  :core:data -.-> :core:analytics
  :core:data -.-> :core:database
  :core:data -.-> :core:datastore
  :core:data -.-> :core:di
  :core:data -.-> :core:model
  :core:data -.-> :core:network
  :core:data -.-> :core:prefs
  :core:data -.-> :core:proto
  :core:database -.-> :core:di
  :core:database -.-> :core:model
  :core:database -.-> :core:proto
  :core:database -.-> :core:strings
  :core:datastore -.-> :core:proto
  :core:model -.-> :core:common
  :core:model -.-> :core:proto
  :core:model -.-> :core:strings
  :core:network -.-> :core:di
  :core:network -.-> :core:model
  :core:service -.-> :core:database
  :core:service -.-> :core:model
  :core:service -.-> :core:prefs
  :core:service -.-> :core:proto
  :core:ui -.-> :core:data
  :core:ui -.-> :core:database
  :core:ui -.-> :core:model
  :core:ui -.-> :core:prefs
  :core:ui -.-> :core:proto
  :core:ui -.-> :core:service
  :core:ui -.-> :core:strings
  :feature:firmware -.-> :core:common
  :feature:firmware -.-> :core:data
  :feature:firmware -.-> :core:database
  :feature:firmware -.-> :core:datastore
  :feature:firmware -.-> :core:model
  :feature:firmware -.-> :core:navigation
  :feature:firmware -.-> :core:prefs
  :feature:firmware -.-> :core:proto
  :feature:firmware -.-> :core:service
  :feature:firmware -.-> :core:strings
  :feature:firmware -.-> :core:ui

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

<details><summary>📋 Graph legend</summary>

```mermaid
graph TB
  application[application]:::android-application
  feature[feature]:::android-feature
  library[library]:::android-library
  jvm[jvm]:::jvm-library
  kmp-library[kmp-library]:::kmp-library

  application -.-> feature
  library --> jvm

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

</details>
<!--endregion-->

## Firmware Update System

The `:feature:firmware` module provides a unified interface for updating Meshtastic devices across different platforms and connection types.

### Supported Platforms & Methods

Meshtastic-Android supports three primary firmware update flows:

#### 1. ESP32 Unified OTA (WiFi & BLE)
Used for modern ESP32 devices (e.g., Heltec V3, T-Beam S3). This method utilizes the **Unified OTA Protocol**, which enables high-speed transfers over TCP (port 3232) or BLE. The BLE transport uses the **Nordic Semiconductor Kotlin-BLE-Library** for architectural consistency with the rest of the application.

**Key Features:**
- **Pre-shared Hash Verification**: The app sends the firmware SHA256 hash in an initial `AdminMessage` trigger. The device stores this in NVS and verifies the incoming stream against it.
- **Connection Retry**: Robust logic to wait for the device to reboot and start the OTA listener.

```mermaid
sequenceDiagram
    participant App as Android App
    participant Radio as Mesh Node (Admin)
    participant OTA as ESP32 OTA Mode

    Note over App: Phase 1: Preparation
    App->>App: Calculate SHA256 Hash

    Note over App, Radio: Phase 2: Trigger Reboot
    App->>Radio: AdminMessage (ota_request = mode + hash)
    Radio->>Radio: Store Hash in NVS & Reboot

    Note over App, OTA: Phase 3: Connection & Update
    App->>OTA: Connect (TCP:3232 or BLE)
    App->>OTA: Handshake & Version Check
    App->>OTA: Start OTA (Size + Hash)
    loop Streaming
        App->>OTA: Stream Data Chunks
        OTA-->>App: ACK
    end
    App->>OTA: REBOOT Command
```

#### 2. nRF52 BLE DFU
The standard update method for nRF52-based devices (e.g., RAK4631). It leverages the **Nordic Semiconductor DFU library**.

```mermaid
sequenceDiagram
    participant App as Android App
    participant Radio as Mesh Node
    participant DFU as nRF DFU Bootloader

    App->>Radio: Trigger DFU Mode
    Radio->>Radio: Reboot into Bootloader
    App->>DFU: Connect via BLE
    App->>DFU: Initialize DFU Transaction
    loop Transfer
        App->>DFU: Stream ZIP Segments
        DFU-->>App: Progress
    end
    DFU->>DFU: Verify, Swap & Reboot
```

#### 3. USB / UF2 (RP2040, nRF52, STM32)
For devices supporting USB Mass Storage updates. The app triggers the device into its native bootloader mode, then guides the user to save the UF2 firmware file to the mounted drive.

```mermaid
sequenceDiagram
    participant App as Android App
    participant Radio as Mesh Node
    participant USB as USB Mass Storage

    App->>Radio: rebootToDfu()
    Radio->>Radio: Mounts as MESH_DRIVE
    App->>App: Prompt User to Save UF2
    App->>USB: Write firmware.uf2
    USB->>USB: Auto-Flash & Reboot
```

### Key Classes

- `UpdateHandler.kt`: Entry point for choosing the correct handler.
- `Esp32OtaUpdateHandler.kt`: Orchestrates the Unified OTA flow.
- `WifiOtaTransport.kt`: Implements the TCP/UDP transport logic for ESP32.
- `BleOtaTransport.kt`: Implements the BLE transport logic for ESP32 using the Nordic BLE library.
- `FirmwareRetriever.kt`: Handles downloading and extracting firmware assets (ZIP/BIN/UF2).
